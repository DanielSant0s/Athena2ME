import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * @param {string} rootDir - repo root (parent of tools/preproc)
 * @param {string} [configPath]
 */
export function loadConfig(rootDir, configPath = "preproc.config.json") {
  const abs = path.isAbsolute(configPath)
    ? configPath
    : path.join(rootDir, configPath);
  const raw = JSON.parse(fs.readFileSync(abs, "utf8"));
  const globalsPath = path.isAbsolute(raw.globals)
    ? raw.globals
    : path.join(rootDir, raw.globals);
  const globalsJson = JSON.parse(fs.readFileSync(globalsPath, "utf8"));
  return {
    rootDir,
    srcDir: path.join(rootDir, raw.srcDir || "res"),
    outDir: path.join(rootDir, raw.outDir || "build/res"),
    entry: raw.entry || "/main.js",
    modes: raw.modes || ["perfile", "bundle"],
    bundleOut: raw.bundleOut || "/app.js",
    skipBundle: new Set(raw.skipBundle || []),
    es5Target: raw.es5Target !== false,
    minifyLocals: raw.minifyLocals || "off",
    shapeHints: raw.shapeHints !== false,
    stripDebug: raw.stripDebug !== false,
    globals: new Set(globalsJson.globals || []),
    pureCallRoots: new Set(globalsJson.pureCallRoots || ["Math", "Number"]),
    allowDynamicRequire: new Set(
      (raw.allowDynamicRequire || []).map((s) => {
        const x = s.startsWith("/") ? s : "/" + s;
        return x.replace(/\\/g, "/");
      })
    ),
  };
}
