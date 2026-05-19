import { stripDebugSource } from "./stripDebug.mjs";
import { transformConstFold } from "./constFold.mjs";
import { transformDCE } from "./dce.mjs";
import { transformConstInline } from "./constInline.mjs";
import { transformBitopStrength } from "./bitopStrengthRed.mjs";
import { transformShapeHints } from "./shapeHints.mjs";
import { transformMinifyLocals } from "./minifyLocals.mjs";
import { transformHoistInvariants } from "./hoistInvariants.mjs";

/**
 * @param {import("estree").Program} ast
 * @param {{ globals: Set<string>, stripDebug: boolean, shapeHints: boolean, minifyLocals: boolean }} cfg
 */
export function runOptimize(ast, cfg) {
  if (cfg.stripDebug) {
    /* source-level strip applied before parse in pipeline */
  }
  transformConstInline(ast, cfg.globals);
  transformDCE(ast);
  transformHoistInvariants(ast);
  transformConstFold(ast);
  transformBitopStrength(ast);
  if (cfg.shapeHints) transformShapeHints(ast);
  transformMinifyLocals(ast, cfg.globals, cfg.minifyLocals);
  transformConstFold(ast);
}
