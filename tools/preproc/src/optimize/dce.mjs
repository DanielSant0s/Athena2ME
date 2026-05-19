import { walk } from "../util/estree-walk.mjs";

/**
 * Remove if(false) / unwrap if(true) at block/ program level.
 * @param {import("estree").Program} ast
 */
export function transformDCE(ast) {
  /** @type {{ parent: import("estree").Program|import("estree").BlockStatement, index: number, kind: string, stmts?: import("estree").Statement[] }[]} */
  const ops = [];
  walk(ast, (node, parent, key, index) => {
    if (node.type !== "IfStatement") return;
    if (key !== "body" || typeof index !== "number") return;
    if (parent.type !== "Program" && parent.type !== "BlockStatement") return;
    const t = node.test;
    if (t.type === "Literal" && t.value === false) {
      if (node.alternate) {
        const alt = node.alternate;
        if (alt.type === "BlockStatement") ops.push({ parent, index, kind: "splice", stmts: alt.body });
        else ops.push({ parent, index, kind: "splice", stmts: [alt] });
      } else ops.push({ parent, index, kind: "delete" });
    } else if (t.type === "Literal" && t.value === true && node.consequent) {
      const c = node.consequent;
      if (c.type === "BlockStatement") ops.push({ parent, index, kind: "splice", stmts: c.body });
      else ops.push({ parent, index, kind: "splice", stmts: [c] });
    }
  });
  ops.sort((a, b) => b.index - a.index);
  for (const op of ops) {
    if (op.kind === "delete") op.parent.body.splice(op.index, 1);
    else if (op.kind === "splice" && op.stmts) op.parent.body.splice(op.index, 1, ...op.stmts);
  }
}
