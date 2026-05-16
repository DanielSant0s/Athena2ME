# Athena2ME performance & size guide

This document summarizes interpreter tuning, rendering paths, I/O pooling, build-time shrinking, and how to benchmark changes. For a shorter **narrative overview** aimed at users, see **[README.md § Performance and size](../README.md#performance-and-size)**.

## 1. Interpreter hot path
- **`NativeFunctionFast`**: prefer for JS-visible natives; avoids per-call `arguments` packing when combined with **`RocksInterpreter.fastNativeArgPooling`** (default on).
- **Call-site cache** (`RocksInterpreter`): polymorphic call sites record shapes for faster dispatch.
- **Ctor `this` pool**: `RocksInterpreter.ctorPoolEnabled` + `os.setCtorPoolEnabled` reuse short-lived `this` objects for constructors (off by default; enable when profiling shows ctor churn).

## 2. Literals & shapes
- **`LiteralOpRv`** records **`RhashShape`** for object literals at a bytecode site when `literalShapeCacheEnabled` is on (`os.setLiteralShapeCacheEnabled`).
- **`escapeOptForLiterals`** (`os.setEscapeOptForLiterals`): when enabled, **`Es6PreprocFacade`** sets **`Es6Preproc.ESCAPE_OPT_FOR_LITERALS`** for that preprocess run so **`rewriteTemplate`** copies long static spans with **`String.substring`** into the generated `"..."` pieces instead of one **`append(char)`** per character. The fast path only applies to spans that do not contain backtick, backslash, double quote, CR/LF, or a `${` interpolation start.

## 3. Hidden classes / maps
- Today **`RhashShape`** tracks insertion order for interned shapes. Full slot-based hidden classes (shared transitions, direct slot indices) are **future work**—large change touching `Rhash.putEntry` and PIC consumers.

## 4. Typed / int fast stack
- **`Rv.binary`** already fast-paths integer `+` without heap when both operands are non-float numbers.
- Dedicated **IADD-style opcodes** + hybrid operand stack would require compiler + VM changes; not shipped in this snapshot.

## 5. PIC / inline caches
- **`Rv.LvalueInlineCache`**: multi-slot PIC, LRU touches, and a negative cache for absent properties on repeated reads.

## 6. Arrays & `getIdx`
- **`StdLib.getIdx`** uses **`Rv.INT_STR`** for small indices and a small ring cache for **`i ≥ 512`** to cut `Integer.toString` churn.
- **`Array.reduce` / `reduceRight`** reuse **`RocksInterpreter.reduceReusePack`** for callback argument lists.

## 7. Module load & ES6 preprocess
- **`require` / `loadScript`** run through **`Es6PreprocFacade`** when `ri.es6PreprocessEnabled` is true, with an in-RAM **`modulePreprocMemCache`** keyed by path + source hash (bounded; clears at 128 entries).
- After a RAM miss, **`es6PreprocessCachedSource`** may load a matching blob from RMS store **`A2MjsMod`** and, after a successful preprocess, save one (up to **64** records, body cap **240 KiB** UTF‑8); metadata includes source hash and ES6 mode so stale entries are rejected.
- **Build-time prebake (`tools/preproc`)**: `ant release` runs **`preproc`** (Node) which copies `res/` to **`build/res/`**, rewrites every `*.js` to ES5-friendly RockScript, prefixes **`// @a2m:prebaked v1`**, and optionally emits **`build/res/app.js`** (bundled `require` graph from `preproc.config.json`). **`Es6PreprocFacade.process`** is a no-op for sources that already carry that marker, so the device skips the Java ES6 pass and loads smaller token streams.
- **`node tools/preproc/bin/a2m-preproc.mjs --check`** (also run in CI) validates that `require()` uses string-literal paths unless the module path is listed under **`allowDynamicRequire`** in `preproc.config.json` (e.g. `main.js` uses `require(demos[i].path)`).

## 8. Promises
- **`PromiseRuntime`** uses a **256-slot** ring microtask queue; overflow drops the oldest job and logs once to `System.out`.

## 9. Render3D zero-copy (software)
- **`Render3DSoftBackend`** accepts **`Float32View`** for positions, normals, and **texture UVs** (pairs per vertex) without copying when the JS side passes typed-array views.
- **`Render3D.uploadStaticMesh` / `useUploadedMesh` / `freeUploadedMesh`**: store strip descriptors keyed by integer handle (software backend only). Do not mutate backing buffers while a handle is live.

## 10. Sprite / rect batching
- **`AthenaCanvas`**: sprite batch arrays cap at **4096** entries; beyond that the batch flushes mid-frame.
- **`Screen.reserveBatch(n)`** pre-grows storage (`AthenaCanvas.reserveSpriteBatch`).

## 11. Sound
- **`AthenaSound.playSfx`** reuses the MMAPI **`Player`** when the same `byte[]` identity is replayed on a channel (stop → `setMediaTime(0)` → `start`).
- **`loadResource`** uses **`IoByteBufferPool`** for streaming reads.

## 12. Network & files
- **`AthenaRequest`**, **`AthenaFile`**, **`BootIniConfig`**: shared **`IoByteBufferPool`** for transient read buffers.
- **`Socket.recv`** / **`BTSocket.recv`**: receive into a pooled **`byte[]`**; release in **`finally`**. If the read fills the buffer (**`n == buf.length`**), ownership is transferred to **`newUint8Array`** (same pattern as other zero-copy handoffs).

## 13. Build profiles
- **`ant all`** — dev JAR + preverify (existing); packages live `res/` JavaScript (no Node preproc).
- **`ant all-preproc`** — same shape as **`ant all`** (JAR + preverify + JAD), but runs **`preproc`** first and packages **`build/res/`** (prebaked scripts, full tree including **`demos/`** and **`tests.js`** — not the slim **`jar-release`** excludes). Use **`-Dbuild.profile=release`** for preproc minify profile.
- **`ant release`** — optional `strip-features` (Node) → **`preproc`** (Node: `tools/preproc` → `build/res/`) → **`jar-release`** (packages `build/res/`; by default excludes **`tests.js`** and **`demos/**`** for a smaller JAR) → **ProGuard shrink** → JAD. To ship demos or the test harness (e.g. `main.js` uses `require(demos[i].path)`), add **`-Drelease.include.demos=true`** and/or **`-Drelease.include.tests=true`** to the `ant release` (or `jar-release`) invocation.
- **`ATHENA_FEATURES`** — comma list for `scripts/strip-features.mjs` (see script header).
- **`preproc.config.json`** — `entry`, `bundleOut`, `skipBundle`, `allowDynamicRequire`, `minifyLocals`, etc. (see **`tools/preproc/README.md`**).

## 14. ProGuard & size gates
- Release shrinking keeps the MIDlet, **`NativeFunctionFast`** subclasses, **`Canvas`** subclasses, and public **`RocksInterpreter`** API.
- CI runs **`node scripts/check-baseline.mjs`** against **`bench/baseline.json`** (`jarMaxBytes`).

## 15. Benchmarking
- **`res/tests.js`** includes **`testPerfPlanMicrobenches`** (Draw, Pad, typed arrays, Promises) plus named **scene** timers (`bench scene *`) for quick regression logging on device.
- Record numbers in **`bench/baseline.json`** comments when you change hardware targets.

## 16. OS tuning API (JS)
- `os.setFastNativeArgPooling(on)`
- `os.setCtorPoolEnabled(on)`
- `os.setLiteralShapeCacheEnabled(on)`
- `os.setEscapeOptForLiterals(on)` — ES6 template rewrite fast path (see section 2)
- `os.getPerfStats()` / `os.getMemoryStats()` for lightweight counters and heap hints.
