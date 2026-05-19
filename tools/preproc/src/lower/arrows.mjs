import { walkPost, replaceChild, walk } from "../util/estree-walk.mjs";

/**
 * Find nearest traditional function or null if module / program top.
 * @param {import("estree").Node|null} startParent
 */
function findEnclosingTraditionalFunction(startParent) {
  let p = startParent;
  while (p) {
    if (p.type === "FunctionDeclaration" || p.type === "FunctionExpression") return p;
    if (p.type === "ArrowFunctionExpression") {
      p = p._a2mParent;
      continue;
    }
    p = p._a2mParent;
  }
  return null;
}

/** Annotate estree parent pointers */
function annotateParents(ast) {
  walk(ast, (node, parent) => {
    node._a2mParent = parent;
  });
}

function clearParentAnnot(ast) {
  walkPost(ast, (node) => {
    delete node._a2mParent;
  });
}

/**
 * @param {import("estree").Program} ast
 */
export function transformArrows(ast) {
  annotateParents(ast);
  let thisUid = 0;
  const outerThisName = new Map(); // outer Func -> identifier name

  walkPost(ast, (node, parent, key, index) => {
    if (node.type !== "ArrowFunctionExpression") return;

    const outer = findEnclosingTraditionalFunction(parent);
    let thisName = null;
    let usesThis = false;
    walkPost(node.body, (n) => {
      if (n.type === "ThisExpression") usesThis = true;
    });
    if (usesThis) {
      if (!outer) {
        thisName = null;
      } else {
        if (!outerThisName.has(outer)) {
          outerThisName.set(outer, `_a2m_t${thisUid++}`);
        }
        thisName = outerThisName.get(outer);
        walkPost(node.body, (n, np, nk, ni) => {
          if (n.type === "ThisExpression" && np) {
            replaceChild(np, nk, ni, { type: "Identifier", name: thisName });
          }
        });
      }
    }

    let body = node.body;
    if (body.type !== "BlockStatement") {
      body = {
        type: "BlockStatement",
        body: [{ type: "ReturnStatement", argument: /** @type {import("estree").Expression} */ (body) }],
      };
    }

    const fn = {
      type: "FunctionExpression",
      id: null,
      params: node.params,
      body,
      generator: false,
      expression: false,
    };
    replaceChild(parent, key, index, fn);
  });

  /** Inject var _a2m_tN = this at start of each outer that needed it */
  for (const [fn, name] of outerThisName) {
    const b = fn.body;
    if (b.type !== "BlockStatement") continue;
    const init = {
      type: "VariableDeclaration",
      kind: "var",
      declarations: [
        {
          type: "VariableDeclarator",
          id: { type: "Identifier", name },
          init: { type: "ThisExpression" },
        },
      ],
    };
    b.body.unshift(init);
  }

  clearParentAnnot(ast);
}
