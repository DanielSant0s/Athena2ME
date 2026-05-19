import { walkPost, replaceChild } from "../util/estree-walk.mjs";

/**
 * Collapse `(x | 0) | 0` → `x | 0`
 * @param {import("estree").Program} ast
 */
export function transformBitopStrength(ast) {
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "BinaryExpression" || node.operator !== "|" || !parent) return;
    const L = node.left;
    if (L.type !== "BinaryExpression" || L.operator !== "|") return;
    const R = node.right;
    if (R.type !== "Literal" || R.value !== 0) return;
    const R2 = L.right;
    if (R2.type !== "Literal" || R2.value !== 0) return;
    replaceChild(parent, key, index, L);
  });
}
