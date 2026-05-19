import { walkPost, replaceChild } from "../util/estree-walk.mjs";

/** @param {import("estree").Expression} e */
function evalUnary(e) {
  if (e.type !== "UnaryExpression") return null;
  const arg = evalLiteral(e.argument);
  if (arg === null) return null;
  if (e.operator === "-") return -arg;
  if (e.operator === "+") return +arg;
  if (e.operator === "!") return !arg;
  if (e.operator === "~") return ~arg;
  return null;
}

/** @param {import("estree").Expression} e */
function evalLiteral(e) {
  if (e.type === "Literal" && typeof e.value === "number") return e.value;
  if (e.type === "UnaryExpression" && e.operator === "-" && e.argument.type === "Literal") {
    const v = /** @type {import("estree").Literal} */ (e.argument).value;
    if (typeof v === "number") return -v;
  }
  if (e.type === "UnaryExpression" && e.operator === "+" && e.argument.type === "Literal") {
    const v = /** @type {import("estree").Literal} */ (e.argument).value;
    if (typeof v === "number") return +v;
  }
  return null;
}

/**
 * Fold numeric binary ops on literals (conservative).
 * @param {import("estree").Program} ast
 */
export function transformConstFold(ast) {
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "BinaryExpression" || !parent) return;
    const L = evalLiteral(node.left) ?? evalUnary(node.left);
    const R = evalLiteral(node.right) ?? evalUnary(node.right);
    if (L === null || R === null) return;
    let v = null;
    switch (node.operator) {
      case "+":
        v = L + R;
        break;
      case "-":
        v = L - R;
        break;
      case "*":
        v = L * R;
        break;
      case "/":
        v = L / R;
        break;
      case "%":
        v = L % R;
        break;
      case "|":
        v = L | R;
        break;
      case "&":
        v = L & R;
        break;
      case "^":
        v = L ^ R;
        break;
      case "<<":
        v = L << R;
        break;
      case ">>":
        v = L >> R;
        break;
      case ">>>":
        v = L >>> R;
        break;
      default:
        return;
    }
    if (v !== v || v === Infinity || v === -Infinity) return;
    replaceChild(parent, key, index, { type: "Literal", value: v, raw: String(v) });
  });
}
