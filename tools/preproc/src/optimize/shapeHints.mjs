import { walkPost } from "../util/estree-walk.mjs";

/**
 * Sort non-computed object literal properties by key name (stable shape hint).
 * @param {import("estree").Program} ast
 */
export function transformShapeHints(ast) {
  walkPost(ast, (node) => {
    if (node.type !== "ObjectExpression") return;
    const props = node.properties.filter((p) => p.type === "Property" && !p.computed && p.key.type === "Identifier");
    const rest = node.properties.filter((p) => !props.includes(p));
    props.sort((a, b) => {
      const an = /** @type {import("estree").Identifier} */ (a.key).name;
      const bn = /** @type {import("estree").Identifier} */ (b.key).name;
      return an < bn ? -1 : an > bn ? 1 : 0;
    });
    node.properties = [...props, ...rest];
  });
}
