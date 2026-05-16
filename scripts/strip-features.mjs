#!/usr/bin/env node
/**
 * Optional build step: remove //@feature <name> ... //@end blocks from sources
 * when a feature is not listed in ATHENA_FEATURES (comma-separated).
 *
 * Usage:
 *   node scripts/strip-features.mjs
 *   ATHENA_FEATURES=es6preproc,debug node scripts/strip-features.mjs
 *
 * Default: all @feature regions are kept (no env = no stripping).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "src");
const enabled = new Set(
  (process.env.ATHENA_FEATURES || "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean)
);

function stripFile(text) {
  const lines = text.split(/\r?\n/);
  const out = [];
  let skip = false;
  let curFeature = "";
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const m = /^\s*\/\/\s*@feature\s+(\S+)/.exec(line);
    if (m) {
      curFeature = m[1];
      skip = enabled.size > 0 && !enabled.has(curFeature);
      if (!skip) {
        out.push(line);
      }
      continue;
    }
    if (/^\s*\/\/\s*@end\b/.test(line)) {
      if (!skip) {
        out.push(line);
      }
      skip = false;
      curFeature = "";
      continue;
    }
    if (!skip) {
      out.push(line);
    }
  }
  return out.join("\n");
}

function walk(dir) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      walk(p);
    } else if (ent.isFile() && ent.name.endsWith(".java")) {
      const before = fs.readFileSync(p, "utf8");
      const after = stripFile(before);
      if (after !== before) {
        fs.writeFileSync(p, after, "utf8");
        console.log("strip-features:", p);
      }
    }
  }
}

if (enabled.size === 0) {
  console.log("strip-features: ATHENA_FEATURES empty — no files changed.");
} else {
  walk(root);
}
