import { walkPost } from "../util/estree-walk.mjs";

/** @param {import("estree").Statement} st */
function isBadAsyncStmt(st) {
  const t = st.type;
  return (
    t === "IfStatement" ||
    t === "ForStatement" ||
    t === "ForInStatement" ||
    t === "ForOfStatement" ||
    t === "WhileStatement" ||
    t === "DoWhileStatement" ||
    t === "SwitchStatement" ||
    t === "TryStatement"
  );
}

/** @param {import("estree").Node} node */
function containsAwait(node) {
  let found = false;
  walkPost(node, (n) => {
    if (n.type === "AwaitExpression") found = true;
  });
  return found;
}

/**
 * @param {import("estree").BlockStatement} body
 */
function isLinearAsyncBody(body) {
  for (const st of body.body) {
    if (isBadAsyncStmt(st)) return false;
    if (containsAwait(st) && st.type === "BlockStatement") return false;
  }
  return true;
}

/**
 * @param {import("estree").Statement[]} stmts
 * @param {number} idx
 * @returns {import("estree").Expression}
 */
function emitChain(stmts, idx) {
  if (idx >= stmts.length) {
    return {
      type: "CallExpression",
      callee: {
        type: "MemberExpression",
        object: { type: "Identifier", name: "Promise" },
        property: { type: "Identifier", name: "resolve" },
        computed: false,
      },
      arguments: [{ type: "Identifier", name: "undefined" }],
      optional: false,
    };
  }
  const s = stmts[idx];
  if (!s || s.type === "EmptyStatement") return emitChain(stmts, idx + 1);

  if (s.type === "ReturnStatement") {
    const arg = s.argument;
    if (!arg) {
      return {
        type: "CallExpression",
        callee: {
          type: "MemberExpression",
          object: { type: "Identifier", name: "Promise" },
          property: { type: "Identifier", name: "resolve" },
          computed: false,
        },
        arguments: [{ type: "Identifier", name: "undefined" }],
        optional: false,
      };
    }
    if (arg.type === "AwaitExpression") {
      const inner = arg.argument;
      return {
        type: "CallExpression",
        callee: { type: "Identifier", name: "__awaitStep" },
        arguments: [
          inner,
          {
            type: "FunctionExpression",
            id: { type: "Identifier", name: "__rFn" },
            params: [{ type: "Identifier", name: "__r" }],
            body: {
              type: "BlockStatement",
              body: [
                {
                  type: "ReturnStatement",
                  argument: {
                    type: "CallExpression",
                    callee: {
                      type: "MemberExpression",
                      object: { type: "Identifier", name: "Promise" },
                      property: { type: "Identifier", name: "resolve" },
                      computed: false,
                    },
                    arguments: [{ type: "Identifier", name: "__r" }],
                    optional: false,
                  },
                },
              ],
            },
            generator: false,
          },
        ],
        optional: false,
      };
    }
    return {
      type: "CallExpression",
      callee: {
        type: "MemberExpression",
        object: { type: "Identifier", name: "Promise" },
        property: { type: "Identifier", name: "resolve" },
        computed: false,
      },
      arguments: [{ type: "ParenthesizedExpression", expression: arg }],
      optional: false,
    };
  }

  if (s.type === "ExpressionStatement" && s.expression.type === "AwaitExpression") {
    const expr = s.expression.argument;
    return {
      type: "CallExpression",
      callee: { type: "Identifier", name: "__awaitStep" },
      arguments: [
        expr,
        {
          type: "FunctionExpression",
          id: null,
          params: [{ type: "Identifier", name: "__aw" }],
          body: {
            type: "BlockStatement",
            body: [{ type: "ReturnStatement", argument: emitChain(stmts, idx + 1) }],
          },
          generator: false,
        },
      ],
      optional: false,
    };
  }

  if (s.type === "VariableDeclaration" && s.declarations.length === 1) {
    const d = s.declarations[0];
    if (d.id.type === "Identifier" && d.init && d.init.type === "AwaitExpression") {
      const name = d.id.name;
      const expr = d.init.argument;
      return {
        type: "CallExpression",
        callee: { type: "Identifier", name: "__awaitStep" },
        arguments: [
          expr,
          {
            type: "FunctionExpression",
            id: null,
            params: [{ type: "Identifier", name }],
            body: {
              type: "BlockStatement",
              body: [{ type: "ReturnStatement", argument: emitChain(stmts, idx + 1) }],
            },
            generator: false,
          },
        ],
        optional: false,
      };
    }
  }

  /** Fallback: Promise.resolve(undefined).then(function(){ stmt; return chain; }) */
  const stmtExpr =
    s.type === "ExpressionStatement"
      ? s.expression
      : s.type === "VariableDeclaration"
        ? { type: "Identifier", name: "undefined" }
        : { type: "Identifier", name: "undefined" };

  return {
    type: "CallExpression",
    callee: {
      type: "MemberExpression",
      object: {
        type: "CallExpression",
        callee: {
          type: "MemberExpression",
          object: { type: "Identifier", name: "Promise" },
          property: { type: "Identifier", name: "resolve" },
          computed: false,
        },
        arguments: [{ type: "Identifier", name: "undefined" }],
        optional: false,
      },
      property: { type: "Identifier", name: "then" },
      computed: false,
    },
    arguments: [
      {
        type: "FunctionExpression",
        id: null,
        params: [],
        body: {
          type: "BlockStatement",
          body: [
            ...(s.type === "VariableDeclaration" ? [/** @type {import("estree").Statement} */ (s)] : [{ type: "ExpressionStatement", expression: stmtExpr }]),
            { type: "ReturnStatement", argument: emitChain(stmts, idx + 1) },
          ],
        },
        generator: false,
      },
    ],
    optional: false,
  };
}

/**
 * @param {import("estree").Program} ast
 */
export function transformAsyncAwait(ast) {
  walkPost(ast, (node) => {
    const isAsyncFn =
      (node.type === "FunctionDeclaration" || node.type === "FunctionExpression") && node.async;
    if (!isAsyncFn) return;
    const body = node.body;
    if (body.type !== "BlockStatement") return;
    if (!isLinearAsyncBody(body)) {
      console.warn("[a2m-preproc] non-linear async function left unchanged");
      return;
    }
    node.async = false;
    const stmts = body.body;
    const chain = emitChain(stmts, 0);
    node.body = {
      type: "BlockStatement",
      body: [{ type: "ReturnStatement", argument: chain }],
    };
  });
}
