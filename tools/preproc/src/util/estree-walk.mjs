/**
 * Depth-first ESTree walk (skips type/loc/range).
 * @typedef {(n: import("estree").Node, parent: import("estree").Node|null, key: string|null, index: number) => void} Visitor
 */

function skipKey(k) {
  return (
    k === "type" ||
    k === "loc" ||
    k === "range" ||
    k === "start" ||
    k === "end" ||
    (typeof k === "string" && k.startsWith("_a2m"))
  );
}

/** Post-order: children first (safe for in-place replacement). */
export function walkPost(node, visitor, parent = null, key = null, index = -1) {
  if (!node || typeof node !== "object") return;
  for (const k of Object.keys(node)) {
    if (skipKey(k)) continue;
    const v = /** @type {any} */ (node)[k];
    if (!v) continue;
    if (Array.isArray(v)) {
      for (let i = 0; i < v.length; i++) {
        const ch = v[i];
        if (ch && typeof ch === "object" && ch.type) walkPost(ch, visitor, node, k, i);
      }
    } else if (typeof v === "object" && v.type) {
      walkPost(v, visitor, node, k, -1);
    }
  }
  visitor(node, parent, key, index);
}

/** Pre-order walk */
export function walk(node, visitor, parent = null, key = null, index = -1) {
  if (!node || typeof node !== "object") return;
  visitor(node, parent, key, index);
  for (const k of Object.keys(node)) {
    if (skipKey(k)) continue;
    const v = /** @type {any} */ (node)[k];
    if (!v) continue;
    if (Array.isArray(v)) {
      for (let i = 0; i < v.length; i++) {
        const ch = v[i];
        if (ch && typeof ch === "object" && ch.type) walk(ch, visitor, node, k, i);
      }
    } else if (typeof v === "object" && v.type) {
      walk(v, visitor, node, k, -1);
    }
  }
}

/**
 * @param {import("estree").Node} parent
 * @param {string} key
 * @param {number} index
 * @param {import("estree").Node} newNode
 */
export function replaceChild(parent, key, index, newNode) {
  if (index >= 0) {
    parent[key][index] = newNode;
  } else {
    parent[key] = newNode;
  }
}

/**
 * @param {import("estree").Node} root
 * @param {string} type
 */
export function findNodes(root, type) {
  const out = [];
  walk(root, (n) => {
    if (n.type === type) out.push(n);
  });
  return out;
}
