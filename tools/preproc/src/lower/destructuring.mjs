import { walk } from "../util/estree-walk.mjs";

let dSeq = 0;

/**
 * var {a,b}=o → var __dN=o; var a=__dN.a; var b=__dN.b;
 * @param {import("estree").Program} ast
 */
export function transformDestructuringDecl(ast) {
  /** @type {{ parent: import("estree").Node, index: number, node: import("estree").VariableDeclaration }[]} */
  const hits = [];
  walk(ast, (node, parent, key, index) => {
    if (node.type !== "VariableDeclaration") return;
    if (key !== "body" || typeof index !== "number") return;
    if (parent.type !== "Program" && parent.type !== "BlockStatement") return;
    const decls = node.declarations;
    if (decls.length !== 1) return;
    const d0 = decls[0];
    const id = d0.id;
    if (id.type !== "ObjectPattern" && id.type !== "ArrayPattern") return;
    if (!d0.init) return;
    hits.push({ parent, index, node });
  });
  hits.sort((a, b) => b.index - a.index);
  for (const { parent, index, node } of hits) {
    const d0 = node.declarations[0];
    const id = /** @type {import("estree").ObjectPattern|import("estree").ArrayPattern} */ (d0.id);
    const tmp = `__d${dSeq++}`;
    const holder = { type: "Identifier", name: tmp };
    const first = {
      type: "VariableDeclaration",
      kind: "var",
      declarations: [{ type: "VariableDeclarator", id: holder, init: d0.init }],
    };
    /** @type {import("estree").VariableDeclaration[]} */
    const rest = [];
    if (id.type === "ObjectPattern") {
      for (const prop of id.properties) {
        if (prop.type !== "Property" || prop.kind !== "init") continue;
        const pk = prop.key;
        const val = prop.value;
        if (val.type !== "Identifier") continue;
        const computed = pk.type !== "Identifier";
        const propNode =
          pk.type === "Identifier"
            ? { type: "Identifier", name: pk.name }
            : { type: "Literal", value: /** @type {import("estree").Literal} */ (pk).value, raw: /** @type {import("estree").Literal} */ (pk).raw };
        const rhs = {
          type: "MemberExpression",
          object: holder,
          property: /** @type {any} */ (propNode),
          computed,
        };
        rest.push({
          type: "VariableDeclaration",
          kind: node.kind === "const" ? "var" : node.kind,
          declarations: [{ type: "VariableDeclarator", id: val, init: rhs }],
        });
      }
    } else {
      id.elements.forEach((el, idx) => {
        if (!el || el.type !== "Identifier") return;
        rest.push({
          type: "VariableDeclaration",
          kind: node.kind === "const" ? "var" : node.kind,
          declarations: [
            {
              type: "VariableDeclarator",
              id: el,
              init: {
                type: "MemberExpression",
                object: holder,
                property: { type: "Literal", value: idx, raw: String(idx) },
                computed: true,
              },
            },
          ],
        });
      });
    }
    parent.body.splice(index, 1, first, ...rest);
  }
}
