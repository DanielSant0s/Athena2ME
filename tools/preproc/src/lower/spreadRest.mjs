import { walkPost, replaceChild } from "../util/estree-walk.mjs";

/**
 * @param {import("estree").CallExpression} call
 * @returns {boolean}
 */
function isSpreadOnlyCall(call) {
  if (!call.arguments.length) return false;
  const a0 = call.arguments[0];
  return a0.type === "SpreadElement";
}

/**
 * f(...x) → f.apply(null, x)  (single spread, all args)
 * @param {import("estree").Program} ast
 */
export function transformCallSpread(ast) {
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "CallExpression") return;
    if (!isSpreadOnlyCall(node)) return;
    const callee = node.callee;
    const spread = /** @type {import("estree").SpreadElement} */ (node.arguments[0]);
    const arr = spread.argument;
    const applyMember = {
      type: "MemberExpression",
      object: callee,
      property: { type: "Identifier", name: "apply" },
      computed: false,
      optional: false,
    };
    const newCall = {
      type: "CallExpression",
      callee: applyMember,
      arguments: [{ type: "Literal", value: null, raw: "null" }, arr],
      optional: false,
    };
    replaceChild(parent, key, index, newCall);
  });
}

/**
 * function f(...rest) → function f(){ var rest = [].slice.call(arguments,0); }
 * @param {import("estree").Program} ast
 */
export function transformRestParams(ast) {
  walkPost(ast, (node) => {
    if (node.type !== "FunctionDeclaration" && node.type !== "FunctionExpression") return;
    const params = node.params;
    const restIdx = params.findIndex((p) => p.type === "RestElement");
    if (restIdx < 0) return;
    const rest = /** @type {import("estree").RestElement} */ (params[restIdx]);
    const restName = rest.argument.type === "Identifier" ? rest.argument.name : null;
    if (!restName) return;
    const fixedParams = [];
    for (let i = 0; i < params.length; i++) {
      if (i === restIdx) continue;
      if (params[i].type === "RestElement") return;
      fixedParams.push(params[i]);
    }
    node.params = fixedParams;
    const fixedCount = fixedParams.length;
    const body = node.body;
    if (body.type !== "BlockStatement") return;
    const sliceCall = {
      type: "CallExpression",
      callee: {
        type: "MemberExpression",
        object: {
          type: "MemberExpression",
          object: { type: "MemberExpression", object: { type: "Identifier", name: "Array" }, property: { type: "Identifier", name: "prototype" }, computed: false },
          property: { type: "Identifier", name: "slice" },
          computed: false,
        },
        property: { type: "Identifier", name: "call" },
        computed: false,
      },
      arguments: [
        { type: "Identifier", name: "arguments" },
        { type: "Literal", value: fixedCount, raw: String(fixedCount) },
      ],
      optional: false,
    };
    const decl = {
      type: "VariableDeclaration",
      kind: "var",
      declarations: [
        {
          type: "VariableDeclarator",
          id: { type: "Identifier", name: restName },
          init: sliceCall,
        },
      ],
    };
    body.body.unshift(decl);
  });
}

/**
 * [...a, x] → [].concat(a).concat([x])  (simplified for common cases)
 * @param {import("estree").Program} ast
 */
export function transformArraySpread(ast) {
  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "ArrayExpression") return;
    if (!node.elements.some((e) => e && e.type === "SpreadElement")) return;
    /** @type {import("estree").Expression[]} */
    const parts = [];
    /** @type {import("estree").Expression|null} */
    let cur = null;
    for (const el of node.elements) {
      if (!el) continue;
      if (el.type === "SpreadElement") {
        if (cur) {
          parts.push(cur);
          cur = null;
        }
        parts.push(el.argument);
      } else {
        if (!cur) {
          cur = { type: "ArrayExpression", elements: [el] };
        } else {
          cur.elements.push(el);
        }
      }
    }
    if (cur) parts.push(cur);
    if (parts.length === 0) {
      replaceChild(parent, key, index, { type: "ArrayExpression", elements: [] });
      return;
    }
    let out = parts[0];
    for (let i = 1; i < parts.length; i++) {
      const emptyArr = { type: "ArrayExpression", elements: [] };
      const concatMember = {
        type: "MemberExpression",
        object: emptyArr,
        property: { type: "Identifier", name: "concat" },
        computed: false,
      };
      out = {
        type: "CallExpression",
        callee: concatMember,
        arguments: [out, parts[i]],
        optional: false,
      };
    }
    replaceChild(parent, key, index, out);
  });
}

export function transformSpreadRest(ast) {
  transformArraySpread(ast);
  transformCallSpread(ast);
  transformRestParams(ast);
}
