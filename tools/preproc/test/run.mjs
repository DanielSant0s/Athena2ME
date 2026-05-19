import assert from "assert";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { loadConfig } from "../src/config.mjs";
import { preprocessSource } from "../src/pipeline.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, "..", "..", "..");

function norm(s) {
  return s.replace(/\r\n/g, "\n").trimEnd() + "\n";
}

function testTemplateFixture() {
  const cfg = loadConfig(root);
  const input = fs.readFileSync(path.join(__dirname, "fixtures", "template.in.js"), "utf8");
  const out = preprocessSource(input, cfg, "dev");
  assert(out.startsWith("// @a2m:prebaked v1"), "marker");
  assert(out.includes('"a"'), "template desugar to string concat");
  assert(!out.includes("`"), "no backticks");
  const golden = fs.readFileSync(
    path.join(__dirname, "fixtures", "template.golden.js"),
    "utf8"
  );
  assert.strictEqual(norm(out), norm(golden), "golden snapshot (template + fold)");
}

testTemplateFixture();
console.log("tools/preproc tests: OK");
console.log(
  "Note: full AST parity vs Es6PreprocFacade (Java) is not in CI — extend with a small java harness if needed."
);
