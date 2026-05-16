#!/usr/bin/env node
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const jar = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "build", "Athena2ME.jar");
const baselinePath = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "bench", "baseline.json");

if (!fs.existsSync(jar)) {
  console.error("check-baseline: missing", jar);
  process.exit(2);
}
const st = fs.statSync(jar);
const b = JSON.parse(fs.readFileSync(baselinePath, "utf8"));
if (typeof b.jarMaxBytes !== "number" || b.jarMaxBytes <= 0) {
  console.error("check-baseline: invalid jarMaxBytes in baseline");
  process.exit(2);
}
if (st.size > b.jarMaxBytes) {
  console.error(`check-baseline: JAR ${st.size} bytes exceeds jarMaxBytes ${b.jarMaxBytes}`);
  process.exit(1);
}
console.log(`check-baseline: JAR ${st.size} bytes <= ${b.jarMaxBytes} OK`);
