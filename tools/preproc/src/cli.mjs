import path from "path";
import { fileURLToPath } from "url";
import { loadConfig } from "./config.mjs";
import { runPerFile, runBundle, checkBundleable } from "./pipeline.mjs";
import { parseScript } from "./parse.mjs";
import { stripDebugSource } from "./optimize/stripDebug.mjs";
import fs from "fs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
/** Repo root (Athena2ME): from tools/preproc/src → up 3 levels */
const repoRoot = path.join(__dirname, "..", "..", "..");

export async function main(argv) {
  let configPath = "preproc.config.json";
  let mode = "dev";
  let checkOnly = false;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--config" && argv[i + 1]) {
      configPath = argv[++i];
    } else if (a === "--mode" && argv[i + 1]) {
      mode = argv[++i];
    } else if (a === "--check") {
      checkOnly = true;
    } else if (a === "--help" || a === "-h") {
      console.log(`Usage: node tools/preproc/bin/a2m-preproc.mjs [--config path] [--mode dev|release] [--check]`);
      return;
    }
  }

  const cfg = loadConfig(repoRoot, path.isAbsolute(configPath) ? configPath : path.join(repoRoot, configPath));

  if (checkOnly) {
    const walkJs = (dir) => {
      for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, ent.name);
        if (ent.isDirectory()) {
          if (ent.name === ".athenastudio") continue;
          walkJs(p);
        } else if (ent.isFile() && ent.name.endsWith(".js")) {
          const raw = fs.readFileSync(p, "utf8");
          if (raw.startsWith("// @a2m:prebaked")) continue;
          const ast = parseScript(cfg.stripDebug ? stripDebugSource(raw) : raw, p);
          const rel = "/" + path.relative(cfg.srcDir, p).split(path.sep).join("/");
          checkBundleable(ast, rel, cfg);
        }
      }
    };
    walkJs(cfg.srcDir);
    console.log("preproc --check: OK");
    return;
  }

  runPerFile(cfg, mode);
  const skip = cfg.skipBundle.has(cfg.entry) || cfg.skipBundle.has(path.normalize(cfg.entry));
  if (!skip && cfg.modes.includes("bundle")) {
    runBundle(cfg, mode);
  }
  console.log("preproc: wrote", cfg.outDir);
}
