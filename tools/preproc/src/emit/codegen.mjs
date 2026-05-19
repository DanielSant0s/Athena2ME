import { generate, GENERATOR } from "astring";

const PREBAKED_MARKER = "// @a2m:prebaked v1";

/** astring has no ParenthesizedExpression; keep ours for RockScript precedence fixes. */
const ROCK_GENERATOR = {
  ...GENERATOR,
  ParenthesizedExpression(node, state) {
    state.write("(");
    this[node.expression.type](node.expression, state);
    state.write(")");
  },
};

/**
 * RockScript drops ASI after `}`; insert explicit empty statements so astring
 * emits `;` between consecutive top-level `function` declarations / statements.
 * @param {import("estree").Program} program
 */
export function insertTopLevelSemiAfterFuncs(program) {
  const b = program.body;
  for (let i = 0; i < b.length - 1; i++) {
    const cur = b[i];
    if (cur.type === "FunctionDeclaration") {
      const next = b[i + 1];
      if (
        next.type === "FunctionDeclaration" ||
        next.type === "VariableDeclaration" ||
        next.type === "ClassDeclaration" ||
        next.type === "ExpressionStatement"
      ) {
        b.splice(i + 1, 0, { type: "EmptyStatement" });
        i++;
      }
    }
  }
}

/**
 * @param {import("estree").Program} ast
 * @param {{ comments?: boolean }} [opts]
 */
export function emitProgram(ast, opts = {}) {
  insertTopLevelSemiAfterFuncs(ast);
  return generate(ast, {
    comments: opts.comments === true,
    generator: ROCK_GENERATOR,
  });
}

export function withPrebakedMarker(code) {
  if (code.startsWith(PREBAKED_MARKER)) return code;
  return `${PREBAKED_MARKER}\n${code}`;
}

export { PREBAKED_MARKER };
