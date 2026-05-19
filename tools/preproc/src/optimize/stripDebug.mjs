/**
 * Remove //@debug ... //@end regions (line-based).
 * @param {string} code
 */
export function stripDebugSource(code) {
  const lines = code.split(/\r?\n/);
  const out = [];
  let skip = false;
  for (const line of lines) {
    if (/^\s*\/\/\s*@debug\b/.test(line)) {
      skip = true;
      continue;
    }
    if (/^\s*\/\/\s*@end\b/.test(line)) {
      skip = false;
      continue;
    }
    if (!skip) out.push(line);
  }
  return out.join("\n");
}
