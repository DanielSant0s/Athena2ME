import * as acorn from "acorn";

/**
 * @param {string} code
 * @param {string} [sourceFile]
 */
export function parseScript(code, sourceFile = "<input>") {
  return acorn.parse(code, {
    ecmaVersion: 2022,
    sourceType: "script",
    locations: Boolean(sourceFile),
    allowHashBang: true,
    sourceFile: sourceFile || "<input>",
  });
}
