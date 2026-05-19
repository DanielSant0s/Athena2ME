import { walkPost, replaceChild } from "../util/estree-walk.mjs";

/**
 * { a, b: c } shorthand → { a: a, b: c }
 * @param {import("estree").Program} ast
 */
export function transformShorthandProps(ast) {
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "Property") return;
    if (!node.shorthand) return;
    const k = node.key;
    if (k.type !== "Identifier") return;
    node.shorthand = false;
    node.value = { type: "Identifier", name: k.name };
    node.kind = "init";
  });
}
