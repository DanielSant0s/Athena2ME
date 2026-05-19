import { transformClasses } from "./classes.mjs";
import { transformForOf } from "./forOf.mjs";
import { transformTemplates } from "./templates.mjs";
import { transformArrows } from "./arrows.mjs";
import { transformDefaultParams } from "./defaultParams.mjs";
import { transformDestructuringDecl } from "./destructuring.mjs";
import { transformShorthandProps } from "./shorthandProps.mjs";
import { transformSpreadRest } from "./spreadRest.mjs";
import { transformAsyncAwait } from "./asyncAwait.mjs";

/**
 * ES6 lowering in the same order as Es6Preproc.process
 * @param {import("estree").Program} ast
 */
export function runLowering(ast) {
  transformClasses(ast);
  transformForOf(ast);
  transformTemplates(ast);
  transformArrows(ast);
  transformDefaultParams(ast);
  transformShorthandProps(ast);
  transformDestructuringDecl(ast);
  transformSpreadRest(ast);
  transformAsyncAwait(ast);
}
