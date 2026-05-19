import { walkPost } from "../util/estree-walk.mjs";

/**
 * Default params: only Identifier with AssignmentPattern in params list.
 * @param {import("estree").Program} ast
 */
export function transformDefaultParams(ast) {
  walkPost(ast, (node) => {
    if (node.type !== "FunctionDeclaration" && node.type !== "FunctionExpression") return;
    const body = node.body;
    if (body.type !== "BlockStatement") return;
    const prefix = [];
    const newParams = [];
    for (const p of node.params) {
      if (p.type === "AssignmentPattern" && p.left.type === "Identifier") {
        const name = p.left.name;
        newParams.push({ type: "Identifier", name });
        prefix.push({
          type: "IfStatement",
          test: {
            type: "BinaryExpression",
            operator: "===",
            left: { type: "Identifier", name },
            right: { type: "Identifier", name: "undefined" },
          },
          consequent: {
            type: "ExpressionStatement",
            expression: {
              type: "AssignmentExpression",
              operator: "=",
              left: { type: "Identifier", name },
              right: p.right,
            },
          },
          alternate: null,
        });
      } else {
        newParams.push(p);
      }
    }
    if (prefix.length === 0) return;
    node.params = newParams;
    body.body.unshift(...prefix);
  });
}
