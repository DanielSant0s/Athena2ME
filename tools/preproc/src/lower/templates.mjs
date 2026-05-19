import { walkPost, replaceChild } from "../util/estree-walk.mjs";

/**
 * Build balanced + tree from expression parts (avoids deep left-nesting stack overflow).
 * @param {import("estree").Expression[]} parts
 */
function balancedPlus(parts) {
  if (parts.length === 1) return parts[0];
  const mid = (parts.length / 2) | 0;
  return {
    type: "BinaryExpression",
    operator: "+",
    left: balancedPlus(parts.slice(0, mid)),
    right: balancedPlus(parts.slice(mid)),
  };
}

/**
 * TemplateLiteral → BinaryExpression with '+'
 * @param {import("estree").Program} ast
 */
export function transformTemplates(ast) {
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "TemplateLiteral" || !parent) return;
    const { quasis, expressions } = node;
    /** @type {import("estree").Expression[]} */
    const parts = [];
    parts.push({
      type: "Literal",
      value: quasis[0].value.cooked ?? "",
      raw: JSON.stringify(quasis[0].value.cooked ?? ""),
    });
    for (let i = 0; i < expressions.length; i++) {
      parts.push({ type: "ParenthesizedExpression", expression: expressions[i] });
      parts.push({
        type: "Literal",
        value: quasis[i + 1].value.cooked ?? "",
        raw: JSON.stringify(quasis[i + 1].value.cooked ?? ""),
      });
    }
    replaceChild(parent, key, index, balancedPlus(parts));
  });
}
