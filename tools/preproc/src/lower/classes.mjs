import { walkPost, walk, replaceChild } from "../util/estree-walk.mjs";

/**
 * @param {import("estree").BlockStatement} body
 * @param {string|null} parentName
 */
function rewriteSuperInMethod(body, parentName) {
  if (!parentName) return;
  walkPost(body, (node, p, k, i) => {
    if (node.type !== "CallExpression") return;
    const c = node.callee;
    if (c.type !== "MemberExpression") return;
    if (c.object.type !== "Super") return;
    const prop = c.property;
    const mname = prop.type === "Identifier" ? prop.name : null;
    if (!mname) return;
    const args = node.arguments;
    const newCallee = {
      type: "MemberExpression",
      object: {
        type: "MemberExpression",
        object: {
          type: "MemberExpression",
          object: { type: "Identifier", name: parentName },
          property: { type: "Identifier", name: "prototype" },
          computed: false,
        },
        property: { type: "Identifier", name: mname },
        computed: false,
      },
      property: { type: "Identifier", name: "call" },
      computed: false,
    };
    const newArgs = [{ type: "ThisExpression" }, ...args];
    const rep = { type: "CallExpression", callee: newCallee, arguments: newArgs, optional: false };
    replaceChild(p, k, i, rep);
  });
  walkPost(body, (node, p, k, i) => {
    if (node.type !== "CallExpression") return;
    if (node.callee.type !== "Super") return;
    const args = node.arguments;
    const newCallee = {
      type: "MemberExpression",
      object: { type: "Identifier", name: parentName },
      property: { type: "Identifier", name: "call" },
      computed: false,
    };
    const newArgs = [{ type: "ThisExpression" }, ...args];
    replaceChild(p, k, i, { type: "CallExpression", callee: newCallee, arguments: newArgs, optional: false });
  });
}

/**
 * @param {import("estree").ClassDeclaration} cls
 * @returns {import("estree").Statement[]}
 */
export function desugarClass(cls) {
  const name = cls.id.name;
  const superCls = cls.superClass;
  const parentName = superCls && superCls.type === "Identifier" ? superCls.name : null;

  /** @type {import("estree").FunctionExpression|null} */
  let ctorFn = null;
  /** @type {import("estree").Statement[]} */
  const protoMethods = [];
  /** @type {import("estree").Statement[]} */
  const staticMethods = [];

  for (const el of cls.body.body) {
    if (el.type !== "MethodDefinition") continue;
    if (el.kind === "get" || el.kind === "set") {
      throw new Error(`class ${name}: getters/setters are not supported`);
    }
    const key = el.key;
    const mname = key.type === "Identifier" ? key.name : null;
    if (!mname) throw new Error(`class ${name}: only identifier method names supported`);
    const fn = /** @type {import("estree").FunctionExpression} */ (el.value);
    const body = /** @type {import("estree").BlockStatement} */ (JSON.parse(JSON.stringify(fn.body)));

    if (!el.static && mname !== "constructor") {
      rewriteSuperInMethod(body, parentName);
    }
    if (mname === "constructor") {
      ctorFn = { ...fn, body };
      if (parentName) rewriteSuperInMethod(body, parentName);
    } else if (el.static) {
      staticMethods.push({
        type: "ExpressionStatement",
        expression: {
          type: "AssignmentExpression",
          operator: "=",
          left: {
            type: "MemberExpression",
            object: { type: "Identifier", name },
            property: { type: "Identifier", name: mname },
            computed: false,
          },
          right: { type: "FunctionExpression", id: null, params: fn.params, body, generator: false },
        },
      });
    } else {
      protoMethods.push({
        type: "ExpressionStatement",
        expression: {
          type: "AssignmentExpression",
          operator: "=",
          left: {
            type: "MemberExpression",
            object: {
              type: "MemberExpression",
              object: { type: "Identifier", name },
              property: { type: "Identifier", name: "prototype" },
              computed: false,
            },
            property: { type: "Identifier", name: mname },
            computed: false,
          },
          right: { type: "FunctionExpression", id: null, params: fn.params, body, generator: false },
        },
      });
    }
  }

  const ctorParams = ctorFn ? ctorFn.params : [];
  const ctorBody = ctorFn ? ctorFn.body : { type: "BlockStatement", body: [] };

  /** @type {import("estree").Statement[]} */
  const out = [
    {
      type: "FunctionDeclaration",
      id: { type: "Identifier", name },
      params: ctorParams,
      body: ctorBody,
      generator: false,
    },
  ];

  if (parentName) {
    out.push({
      type: "ExpressionStatement",
      expression: {
        type: "AssignmentExpression",
        operator: "=",
        left: {
          type: "MemberExpression",
          object: { type: "Identifier", name },
          property: { type: "Identifier", name: "prototype" },
          computed: false,
        },
        right: {
          type: "CallExpression",
          callee: {
            type: "MemberExpression",
            object: { type: "Identifier", name: "Object" },
            property: { type: "Identifier", name: "create" },
            computed: false,
          },
          arguments: [
            {
              type: "MemberExpression",
              object: { type: "Identifier", name: parentName },
              property: { type: "Identifier", name: "prototype" },
              computed: false,
            },
          ],
          optional: false,
        },
      },
    });
    out.push({
      type: "ExpressionStatement",
      expression: {
        type: "AssignmentExpression",
        operator: "=",
        left: {
          type: "MemberExpression",
          object: {
            type: "MemberExpression",
            object: { type: "Identifier", name },
            property: { type: "Identifier", name: "prototype" },
            computed: false,
          },
          property: { type: "Identifier", name: "constructor" },
          computed: false,
        },
        right: { type: "Identifier", name },
      },
    });
  }

  out.push(...protoMethods, ...staticMethods);
  return out;
}

/**
 * @param {import("estree").Program} ast
 */
export function transformClasses(ast) {
  /** @type {{ parent: import("estree").Node, index: number, node: import("estree").ClassDeclaration }[]} */
  const hits = [];
  walk(ast, (node, parent, key, index) => {
    if (node.type !== "ClassDeclaration") return;
    if (key !== "body" || typeof index !== "number") return;
    if (parent.type !== "Program" && parent.type !== "BlockStatement") return;
    hits.push({ parent, index, node });
  });
  hits.sort((a, b) => b.index - a.index);
  for (const { parent, index, node } of hits) {
    const stmts = desugarClass(node);
    parent.body.splice(index, 1, ...stmts);
  }
}
