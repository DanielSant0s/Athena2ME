import { walk } from "../util/estree-walk.mjs";

/**
 * RockScript Pratt table treats shifts above additive differently from ECMAScript
 * printing (astring). Without parens, `a + b + 1 >> 1` is parsed as `a + b + (1 >> 1)`
 * on device — breaks binary search, layout `| 0`, etc.
 *
 * Insert ESTree ParenthesizedExpression (tagged `_a2mKeepParens`) around operands that
 * must stay grouped for RockScript. {@link stripParenthesized} keeps these; codegen
 * emits real parentheses for them.
 * @param {import("estree").Program} ast
 */
export function insertRockScriptParens(ast) {
  /** @param {import("estree").Node|null|undefined} sub */
  const needsWrap = (sub) =>
    sub && (sub.type === "BinaryExpression" || sub.type === "LogicalExpression");

  walk(ast, (node) => {
    if (node.type !== "BinaryExpression") return;
    const op = node.operator;
    const isShift = op === "<<" || op === ">>" || op === ">>>";
    const isBit = op === "|" || op === "&" || op === "^";
    if (!isShift && !isBit) return;

    if (needsWrap(node.left)) {
      node.left = {
        type: "ParenthesizedExpression",
        expression: node.left,
        _a2mKeepParens: true,
      };
    }
    if ((isShift || isBit) && needsWrap(node.right)) {
      node.right = {
        type: "ParenthesizedExpression",
        expression: node.right,
        _a2mKeepParens: true,
      };
    }
  });
}
