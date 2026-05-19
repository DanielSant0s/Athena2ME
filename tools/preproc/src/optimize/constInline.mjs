import { walkPost } from "../util/estree-walk.mjs";

/**
 * Inline module-scope `const id = literal` into identifier references (best-effort).
 * @param {import("estree").Program} ast
 * @param {Set<string>} globals
 */
export function transformConstInline(ast, globals) {
  /** @type {Map<string, import("estree").Literal|import("estree").UnaryExpression>} */
  const map = new Map();
  for (const st of ast.body) {
    if (st.type !== "VariableDeclaration" || st.kind !== "const") continue;
    for (const d of st.declarations) {
      if (d.id.type !== "Identifier") continue;
      const name = d.id.name;
      if (globals.has(name)) continue;
      if (!d.init) continue;
      if (d.init.type === "Literal") map.set(name, d.init);
      else if (d.init.type === "UnaryExpression" && d.init.operator === "-" && d.init.argument.type === "Literal") {
        map.set(name, d.init);
      }
    }
  }
  if (!map.size) return;
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "Identifier" || !parent) return;
    if (key === "property" && parent.type === "MemberExpression" && !parent.computed) return;
    if (key === "object" && parent.type === "MemberExpression") return;
    const lit = map.get(node.name);
    if (!lit) return;
    if (parent.type === "VariableDeclarator" && key === "id") return;
    if (parent.type === "FunctionDeclaration" && key === "id") return;
    if (parent.type === "FunctionExpression" && key === "id") return;
    if (parent.type === "Property" && key === "key") return;
    if (typeof index === "number") {
      parent[key][index] = JSON.parse(JSON.stringify(lit));
    } else {
      parent[key] = JSON.parse(JSON.stringify(lit));
    }
  });
}
