import { walkPost, replaceChild } from "./estree-walk.mjs";

/** astring has no ParenthesizedExpression — unwrap before codegen. */
export function stripParenthesized(ast) {
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "ParenthesizedExpression" || !parent) return;
    if (node._a2mKeepParens) return;
    replaceChild(parent, key, index, node.expression);
  });
}
