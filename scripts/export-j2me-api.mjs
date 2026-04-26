#!/usr/bin/env node
/**
 * Emit build/j2me-api.json from src/Athena2ME.java (same schema as AthenaStudio targets/j2me-api.json).
 * Used in Athena2ME CI and as the canonical artifact for cross-repo sync / releases.
 *
 * Usage: node scripts/export-j2me-api.mjs [path/to/Athena2ME.java]
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..");
const SCHEMA_VERSION = 1;
const OUT_JSON = path.join(REPO_ROOT, "build", "j2me-api.json");

const javaPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(REPO_ROOT, "src", "Athena2ME.java");

function extractNatives(text) {
  const re = /NativeFunctionListEntry\s*\(\s*"([^"]+)"/g;
  const set = new Set();
  let m;
  while ((m = re.exec(text)) !== null) {
    set.add(m[1]);
  }
  return [...set].sort();
}

function byPrefix(natives) {
  const map = Object.create(null);
  for (const n of natives) {
    const p = n.includes(".") ? n.slice(0, n.indexOf(".")) : n;
    map[p] = (map[p] ?? 0) + 1;
  }
  return map;
}

if (!fs.existsSync(javaPath)) {
  console.error("[export-j2me-api] File not found:", javaPath);
  process.exit(1);
}

const text = fs.readFileSync(javaPath, "utf8");
const natives = extractNatives(text);
const payload = {
  schemaVersion: SCHEMA_VERSION,
  target: "j2me",
  source: {
    kind: "athena2me-java",
    relativeFile: "src/Athena2ME.java",
    repository: "Athena2ME",
  },
  generatedAt: new Date().toISOString(),
  nativeCount: natives.length,
  natives,
  nativesByPrefix: byPrefix(natives),
};

fs.mkdirSync(path.dirname(OUT_JSON), { recursive: true });
fs.writeFileSync(OUT_JSON, JSON.stringify(payload, null, 2) + "\n", "utf8");
console.log("[export-j2me-api] Wrote", path.relative(REPO_ROOT, OUT_JSON));
console.log("[export-j2me-api] Native count:", natives.length);
