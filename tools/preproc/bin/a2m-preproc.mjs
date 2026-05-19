#!/usr/bin/env node
import { main } from "../src/cli.mjs";

main(process.argv.slice(2)).catch((e) => {
  console.error(e);
  process.exit(1);
});
