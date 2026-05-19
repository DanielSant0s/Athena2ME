import fs from "fs";
import path from "path";
import { parseScript } from "./parse.mjs";
import { runLowering } from "./lower/index.mjs";
import { runOptimize } from "./optimize/index.mjs";
import { emitProgram, withPrebakedMarker } from "./emit/codegen.mjs";
import { insertRockScriptParens } from "./emit/rockScriptParens.mjs";
import { stripDebugSource } from "./optimize/stripDebug.mjs";
import { walk } from "./util/estree-walk.mjs";
import { stripParenthesized } from "./util/stripParens.mjs";

/**
 * @param {string} code
 * @param {{ globals: Set<string>, stripDebug: boolean, shapeHints: boolean, minifyLocals: string }} cfg
 * @param {string} mode
 */
export function preprocessSource(code, cfg, mode) {
  let src = code;
  if (cfg.stripDebug) src = stripDebugSource(src);
  const ast = parseScript(src);
  runLowering(ast);
  const minify = mode === "release" && cfg.minifyLocals === "release";
  runOptimize(ast, {
    globals: cfg.globals,
    stripDebug: false,
    shapeHints: cfg.shapeHints,
    minifyLocals: minify,
  });
  insertRockScriptParens(ast);
  stripParenthesized(ast);
  return withPrebakedMarker(emitProgram(ast));
}

/**
 * @param {import("estree").Program} ast
 * @param {string} relPath canonical /path from res root
 * @param {{ allowDynamicRequire?: Set<string> }} cfg
 */
export function checkBundleable(ast, relPath, cfg) {
  const allow = (cfg && cfg.allowDynamicRequire) || new Set();
  const canon = (s) => (s.startsWith("/") ? s : "/" + s).replace(/\\/g, "/");
  const rp = canon(relPath);
  if (allow.has(rp)) return;
  walk(ast, (node) => {
    if (node.type !== "CallExpression") return;
    const c = node.callee;
    if (c.type !== "Identifier" || c.name !== "require") return;
    const a0 = node.arguments[0];
    if (!a0 || a0.type !== "Literal" || typeof a0.value !== "string") {
      throw new Error("dynamic require() is not supported — use a string literal path or loadScript()");
    }
  });
}

/**
 * @param {any} cfg
 * @param {string} mode
 */
export function runPerFile(cfg, mode) {
  fs.mkdirSync(cfg.outDir, { recursive: true });
  fs.cpSync(cfg.srcDir, cfg.outDir, { recursive: true });
  const walkJs = (dir) => {
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        if (ent.name === ".athenastudio") continue;
        walkJs(p);
      } else if (ent.isFile() && ent.name.endsWith(".js")) {
        const raw = fs.readFileSync(p, "utf8");
        if (raw.startsWith("// @a2m:prebaked")) continue;
        const toParse = cfg.stripDebug ? stripDebugSource(raw) : raw;
        const rel = "/" + path.relative(cfg.outDir, p).split(path.sep).join("/");
        const ast = parseScript(toParse, p);
        checkBundleable(ast, rel, cfg);
        const out = preprocessSource(raw, cfg, mode);
        fs.writeFileSync(p, out, "utf8");
      }
    }
  };
  walkJs(cfg.outDir);
  fs.writeFileSync(path.join(cfg.outDir, ".a2m-preproc-done"), new Date().toISOString(), "utf8");
}

/**
 * @param {any} cfg
 * @param {string} mode
 */
export function runBundle(cfg, mode) {
  const canon = (s) => {
    const x = s.startsWith("/") ? s : "/" + s;
    return x.replace(/\\/g, "/");
  };
  const entry = canon(cfg.entry);
  const entryFs = path.join(cfg.srcDir, entry.replace(/^\//, ""));
  if (!fs.existsSync(entryFs)) throw new Error("bundle entry missing: " + entryFs);

  /** @type {Set<string>} */
  const mods = new Set();
  /** @type {string[]} */
  const queue = [entry];
  while (queue.length) {
    const cur = canon(queue.shift());
    if (mods.has(cur)) continue;
    const disk = path.join(cfg.srcDir, cur.replace(/^\//, ""));
    if (!fs.existsSync(disk)) throw new Error("require not found: " + cur);
    mods.add(cur);
    const raw = fs.readFileSync(disk, "utf8");
    const toParse = cfg.stripDebug ? stripDebugSource(raw) : raw;
    const ast = parseScript(toParse, disk);
    checkBundleable(ast, cur, cfg);
    walk(ast, (node) => {
      if (node.type !== "CallExpression") return;
      if (node.callee.type !== "Identifier" || node.callee.name !== "require") return;
      const a0 = node.arguments[0];
      if (a0.type === "Literal" && typeof a0.value === "string") {
        const dep = canon(a0.value);
        if (!mods.has(dep)) queue.push(dep);
      }
    });
  }

  const parts = [];
  parts.push(`// @a2m:prebaked v1`);
  parts.push(`var __a2mMods={};`);
  parts.push(`function __a2mDef(p,fn){__a2mMods[p]={fn:fn,ex:null};}`);
  parts.push(
    `function require(p){var m=__a2mMods[p];if(!m)throw new Error("module not found:"+p);if(m.ex!==null)return m.ex;var x={},mod={exports:x};m.fn(x,mod,require);m.ex=mod.exports||x;return m.ex;}`
  );
  const ordered = [...mods].sort();
  const minify = mode === "release" && cfg.minifyLocals === "release";
  for (const m of ordered) {
    const disk = path.join(cfg.srcDir, m.replace(/^\//, ""));
    let t = fs.readFileSync(disk, "utf8");
    if (cfg.stripDebug) t = stripDebugSource(t);
    const ast = parseScript(t, m);
    runLowering(ast);
    runOptimize(ast, {
      globals: cfg.globals,
      stripDebug: false,
      shapeHints: cfg.shapeHints,
      minifyLocals: minify,
    });
    insertRockScriptParens(ast);
    stripParenthesized(ast);
    const body = emitProgram(ast);
    parts.push(`__a2mDef(${JSON.stringify(m)},function(exports,module,require){\n${body}\n});`);
  }
  parts.push(`require(${JSON.stringify(entry)});`);
  const bundlePath = path.join(cfg.outDir, cfg.bundleOut.replace(/^\//, ""));
  fs.mkdirSync(path.dirname(bundlePath), { recursive: true });
  fs.writeFileSync(bundlePath, parts.join("\n"), "utf8");
}
