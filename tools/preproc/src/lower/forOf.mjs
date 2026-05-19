import { replaceChild, walk } from "../util/estree-walk.mjs";

let forOfSeq = 0;

/**
 * for (var/let/const x of arr) → indexed for with length (arrays / strings)
 * @param {import("estree").Program} ast
 */
export function transformForOf(ast) {
  /** @type {{ parent: import("estree").Node, key: string, index: number, node: import("estree").ForOfStatement }[]} */
  const hits = [];
  walk(ast, (node, parent, key, index) => {
    if (node.type !== "ForOfStatement") return;
    hits.push({ parent, key, index, node });
  });
  for (const { parent, key, index, node } of hits) {
    const suf = forOfSeq++;
    const it = `__it${suf}`;
    const src = `__src${suf}`;
    const len = `__len${suf}`;
    const left = node.left;
    let loopVarName = null;
    let kind = "var";
    if (left.type === "VariableDeclaration") {
      kind = left.kind === "const" ? "var" : left.kind;
      const d0 = left.declarations[0];
      if (d0.id.type === "Identifier") loopVarName = d0.id.name;
      else continue;
    } else if (left.type === "Identifier") {
      loopVarName = left.name;
      kind = "var";
    } else {
      continue;
    }

    const coll = node.right;
    const init = {
      type: "VariableDeclaration",
      kind: "var",
      declarations: [
        { type: "VariableDeclarator", id: { type: "Identifier", name: it }, init: { type: "Literal", value: 0, raw: "0" } },
        {
          type: "VariableDeclarator",
          id: { type: "Identifier", name: src },
          init: coll,
        },
        {
          type: "VariableDeclarator",
          id: { type: "Identifier", name: len },
          init: {
            type: "ConditionalExpression",
            test: {
              type: "BinaryExpression",
              operator: "!==",
              left: {
                type: "MemberExpression",
                object: { type: "Identifier", name: src },
                property: { type: "Identifier", name: "length" },
                computed: false,
              },
              right: { type: "Identifier", name: "undefined" },
            },
            consequent: {
              type: "MemberExpression",
              object: { type: "Identifier", name: src },
              property: { type: "Identifier", name: "length" },
              computed: false,
            },
            alternate: { type: "Literal", value: 0, raw: "0" },
          },
        },
      ],
    };

    const test = {
      type: "BinaryExpression",
      operator: "<",
      left: { type: "Identifier", name: it },
      right: { type: "Identifier", name: len },
    };

    const update = {
      type: "UpdateExpression",
      operator: "++",
      prefix: false,
      argument: { type: "Identifier", name: it },
    };

    const elemExpr = {
      type: "MemberExpression",
      object: { type: "Identifier", name: src },
      property: { type: "Identifier", name: it },
      computed: true,
    };

    const innerDecl = {
      type: "VariableDeclaration",
      kind,
      declarations: [
        {
          type: "VariableDeclarator",
          id: { type: "Identifier", name: loopVarName },
          init: elemExpr,
        },
      ],
    };

    let innerBody = node.body;
    if (innerBody.type !== "BlockStatement") {
      innerBody = { type: "BlockStatement", body: [{ type: "ExpressionStatement", expression: innerBody }] };
    }
    innerBody = {
      type: "BlockStatement",
      body: [innerDecl, ...innerBody.body],
    };

    const classic = {
      type: "ForStatement",
      init,
      test,
      update,
      body: innerBody,
    };
    replaceChild(parent, key, index, classic);
  }
}
