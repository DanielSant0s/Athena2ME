import { walkPost, walk, replaceChild } from "../util/estree-walk.mjs";

const RESERVED = new Set(
  "break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new return super switch this throw try typeof var void while with yield enum await".split(
    " "
  )
);

let nameGen = 0;
function nextShort() {
  return "_" + nameGen++;
}

function annotateParents(root) {
  walk(root, (node, parent) => {
    node._a2mP = parent;
  });
}

function clearParents(root) {
  walkPost(root, (node) => {
    delete node._a2mP;
  });
}

/** @param {import("estree").Node} node */
function enclosingFunction(node) {
  let p = node._a2mP;
  while (p) {
    if (p.type === "FunctionDeclaration" || p.type === "FunctionExpression") return p;
    p = p._a2mP;
  }
  return null;
}

/**
 * @param {import("estree").FunctionDeclaration|import("estree").FunctionExpression} fn
 * @param {Set<string>} globals
 */
function minifyOneFunction(fn, globals) {
  const declared = new Set();
  /** @param {import("estree").Pattern} pat */
  function addPat(pat) {
    if (pat.type === "Identifier") declared.add(pat.name);
    if (pat.type === "ObjectPattern") {
      for (const p of pat.properties) {
        if (p.type === "Property" && p.value.type === "Identifier") declared.add(p.value.name);
      }
    }
  }
  for (const p of fn.params) addPat(p);

  walkPost(fn.body, (node) => {
    if (node.type === "VariableDeclaration" && enclosingFunction(node) === fn) {
      for (const d of node.declarations) addPat(d.id);
    }
    if (node.type === "FunctionDeclaration" && node.id && enclosingFunction(node) === fn) {
      declared.add(node.id.name);
    }
  });

  const rename = new Map();
  for (const name of declared) {
    if (RESERVED.has(name) || globals.has(name) || name === "arguments") continue;
    let nn = nextShort();
    while (declared.has(nn) || globals.has(nn) || RESERVED.has(nn)) nn = nextShort();
    rename.set(name, nn);
  }
  if (!rename.size) return;

  walkPost(fn.body, (node, parent, key, index) => {
    if (node.type !== "Identifier" || !parent) return;
    if (enclosingFunction(node) !== fn) return;
    const nn = rename.get(node.name);
    if (!nn) return;
    if (key === "property" && parent.type === "MemberExpression" && !parent.computed) return;
    if (parent.type === "Property" && key === "key" && !parent.computed) return;
    if (parent.type === "LabeledStatement" && key === "label") return;
    replaceChild(parent, key, index, { type: "Identifier", name: nn });
  });
}

/**
 * Only minifies top-level `function` declarations (safe v1).
 * @param {import("estree").Program} ast
 * @param {Set<string>} globals
 * @param {boolean} enabled
 */
export function transformMinifyLocals(ast, globals, enabled) {
  if (!enabled) return;
  annotateParents(ast);
  nameGen = 0;
  for (const st of ast.body) {
    if (st.type === "FunctionDeclaration") minifyOneFunction(st, globals);
  }
  clearParents(ast);
}
