# Athena2ME `tools/preproc` — Node ES6 preprocessor & bundler

Build-time pipeline that mirrors (and extends) the on-device [`Es6Preproc.java`](../src/net/cnjm/j2me/tinybro/Es6Preproc.java) lowering, runs static optimizations, and optionally bundles `require()` graphs.

## Requirements

- Node.js 18+ (CI uses 20)
- Dependencies: `npm install --prefix tools/preproc`

## CLI

```bash
node tools/preproc/bin/a2m-preproc.mjs [--config preproc.config.json] [--mode dev|release] [--check]
```

- **`--mode release`** — enables top-level function local minify (`minifyLocals: "release"` in config).
- **`--check`** — parses every `res/**/*.js`, rejects non–string-literal `require()` unless the file’s canonical path is listed in **`allowDynamicRequire`** in the config.

## Output

1. Copies `res/` → `build/res/`.
2. Rewrites each `*.js` (unless it already starts with `// @a2m:prebaked v1`) and prefixes that marker so **`Es6PreprocFacade.process`** skips work on device.
3. Writes **`build/res/.a2m-preproc-done`** when finished.
4. If `modes` includes `bundle` and `entry` is not in `skipBundle`, emits **`build/res/app.js`** (registry + synthetic `require`).

## RockScript vs astring (parentheses)

RockScript’s **operator precedence** (see `RocksInterpreter` Pratt table) does **not** match ECMAScript for **additive vs bitwise shift** (and related cases). The emitter (`astring`) follows ECMAScript, so expressions like `(lo + hi + 1) >> 1` or `(menuScrollY / stride) | 0` would print without grouping and **parse differently on device**. The pipeline runs **`insertRockScriptParens`** before codegen and keeps marked **`ParenthesizedExpression`** nodes so those groups print correctly.

## `preproc.config.json` (repo root)

| Field | Meaning |
|-------|---------|
| `srcDir` / `outDir` | Input tree (`res`) and output tree (`build/res`). |
| `entry` | Bundle root module path (e.g. `/main.js`). |
| `bundleOut` | Output bundle filename under `outDir`. |
| `skipBundle` | List of entry paths that skip the bundle step. |
| `allowDynamicRequire` | Canonical `/path.js` files allowed to use non-literal `require(expr)`. |
| `minifyLocals` | `"off"` or `"release"` (only with `--mode release`). |
| `shapeHints` | Sort object literal keys (best-effort shape stability). |
| `stripDebug` | Strip `// @debug` … `// @end` regions. |
| `globals` | Path to `host-globals.json` (identifiers never minified / treated as opaque for inlining). |

## Ant integration

- **`ant preproc`** — `npm install` under `tools/preproc` + run the CLI (`build.profile` → `--mode`).
- **`ant all-preproc`** — full dev JAR + JAD like **`ant all`**, but resources come from **`build/res/`** after **`preproc`** (full `res/` tree, including **`demos/`**).
- **`ant release`** → `jar-release` depends on **`preproc`** and packages **`build/res/`** (slim excludes) for the release JAR.

## Unsupported / conservative behaviour

- **Dynamic `require`** — only string literals unless whitelisted (`allowDynamicRequire`).
- **Class getters/setters** — rejected during class desugar.
- **Non-linear `async` bodies** — left as `async` with a console warning (same class of limitation as the Java preprocessor).
- **Generators** — not desugared (native runtime).
- **Local minify** — only top-level `function` declarations in v1 (nested scopes skipped to avoid shadowing bugs).

## Tests

```bash
npm test --prefix tools/preproc
```
