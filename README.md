<br />
<p align="center">
  <a href="https://github.com/DanielSant0s/Athena2ME/">
    <img src="https://github.com/DanielSant0s/AthenaEnv/assets/47725160/f507ad9b-f9a1-4000-a454-ff824bc9d70b" alt="Logo" width="100%" height="auto">
  </a>

  <p align="center">
    Enhanced JavaScript environment for J2ME Devices
    <br />
  </p>
</p>  


<details open="open">
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-athena2me">About Athena2ME</a>
      <ul>
        <li><a href="#modules">Modules</a></li>
        <li><a href="#progress">Progress</a></li>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#coding">Coding</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#boot-splash-bootini">Boot splash (<code>boot.ini</code>)</a></li>
        <li><a href="#features">Features</a></li>
        <li><a href="#functions-classes-and-consts">Functions and classes</a></li>
      </ul>
    </li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
    <li><a href="#thanks">Thanks</a></li>
  </ol>
</details>

## About Athena2ME

Athena2ME is a project that seeks to facilitate and at the same time brings a complete kit for users to create homebrew software for Java ME mobile devices using the JavaScript language. It has dozens of built-in functions, both for creating games and apps. The main advantage over using Athena2ME project instead of Sun Wireless Toolkit or Nokia S40 SDK is above all the practicality, you will use one of the simplest possible languages to create what you have in mind, besides not having to compile, just script and test, fast and simple.

### Modules
* os: OS- and device-related helpers, including `file://` I/O (`os.open` / `read` / `write` / …), time, process-style hooks (`os.sleep`, `os.startFrameLoop`), pools, and JS-visible sync primitives (`os.Mutex`, and so on).
* Image: Image drawing.
* Draw: Shape drawing, rectangles, triangles etc.
* Screen: The entire screen of your project, enable or disable parameters.
* Font: Functions that control the texts that appear on the screen, loading texts, drawing and unloading from memory.
* Pad: Game-key input (`getKeyStates` / soft buttons), **polling** (`pressed` / `justPressed`) and **event listeners** (`addListener` / `clearListener` with `JUST_PRESSED` and `NON_PRESSED` kinds) — see [Pad module](#pad-module).
* Keyboard: Basic keypad support.
* Timer: Control the time precisely in your code, it contains several timing functions.
* Request: HTTP/HTTPS client returning **Promises** (`get` / `post` / `download`).
* Socket: TCP/UDP sockets (`javax.microedition.io`).
* WebSocket: Minimal `ws://` client (RFC 6455 framing over TCP).
* Bluetooth: JSR-82 inquiry, `btspp://` client (`BTSocket`), optional `/lib/bluetooth.js` helper — see [Bluetooth (JSR-82)](#bluetooth-jsr-82).
* **Sound** — BGM `Sound.Stream` and short `Sound.Sfx` with a channel pool (MMAPI; see [Sound module](#sound-module)).
* **Render3D** — immediate 3D: **M3G** (JSR-184) when available, else **software**; shared API includes `getBackend` / `getCapabilities` / `setBackend` / `init` / perspective & camera / `setTexture` + `setTexCoords` + `setTextureFilter` / `setTextureWrap` / mesh (`setTriangleStripMesh`, `setIndexedMesh`) / `setDepthBuffer` (soft only) / `setMaxTriangles` (soft) / matrix stack / `begin` · `render` · `end`, plus **M3G-only** `load(.m3g)` and `worldAnimate` / `m3gNode*` / `m3gAnim*` (see [Render3D (JSR-184)](#render3d-jsr-184); helpers [res/lib/mesh3d.js](res/lib/mesh3d.js), e.g. [res/lib/cube_strips.js](res/lib/cube_strips.js)).
* **Threads & sync** — `os.spawn`, `os.Thread.start`, `os.Mutex`, `os.Semaphore`, `os.AtomicInt` (single shared JS runtime; see [Threads and concurrency](#threads-and-concurrency)).

New types are always being added and this list can grow a lot over time, so stay tuned.

### Progress

- [x] Image basic functions
- [x] Screen basic functions
- [x] OS Font functions
- [x] Physical pad functions (`pressed` / `justPressed`, bitmask masks)
- [x] Pad event listeners (`addListener` / `clearListener`; `PRESSED` / `JUST_PRESSED` / `NON_PRESSED`)
- [x] Keypad functions
- [x] Timer functions
- [x] Sound: `Stream` (BGM) + `Sfx` (channels) via MMAPI — see [Sound module](#sound-module)
- [x] Arrow functions with lexical `this`
- [x] Template literals (`` `hi ${x}` ``)
- [x] Default params / object shorthand / computed keys
- [x] Spread / rest operators (call-site, rest params)
- [x] Destructuring (object and array) in declarations
- [x] `for...of` over arrays, strings and Map/Set
- [x] ES6+ classes, `extends`, `super`, `static`
- [x] `Map`, `Set`, `Symbol` built-ins
- [x] Array prototype: map/filter/reduce/find/some/every/includes/…
- [x] Object: keys/values/entries/assign/freeze
- [x] String: trim/includes/startsWith/repeat/padStart/replaceAll/…
- [x] JSON.parse / JSON.stringify
- [x] Extended Number / Math (parseInt, sqrt, pow, sin/cos/tan, …)
- [x] OS file I/O — `os.open` / `os.close` / `os.read` / `os.write` / `os.seek` / `os.fstat` for `file://` paths (see [os module](#os-module))
- [x] OS / device info — `os.platform`, `os.getSystemInfo`, `os.getProperty`, `os.getStorageStats` (not a full native “platform SDK”; screen size remains `Screen.width` / `Screen.height`)
- [x] Thread functions (`os.spawn`, `os.Thread.start`, `os.Mutex`, `os.Semaphore`, `os.AtomicInt`; serialized JS runtime — see [Threads and concurrency](#threads-and-concurrency))
- [x] 3D render (`Render3D`: M3G / JSR-184 when available, **software** fallback; optional JSR-184 in build for M3G on device)
- [x] HTTP/HTTPS, TCP/UDP sockets, WebSocket (`ws://`) — see [Request / Socket / WebSocket](#request-module) (limits: `wss://`, `SOCK_RAW`)
- [ ] Archive (zip, 7zip, tar, rar) system
- [x] Add float support
- [x] Add ArrayBuffer support (`ArrayBuffer`, `Uint8Array`, `Int32Array`, `Float32Array`, `DataView` subset — see standard library list)
- [x] Block-scoped `let`/`const`
- [x] **`Promise`** (`then` / `catch`, `Promise.resolve` / `Promise.reject`, `new Promise(executor)`, thenable assimilation); microtasks drain on `os.sleep`, `os.flushPromises`, `os.startFrameLoop`, and after the main script finishes
- [x] Constant folding in the ES6 pre-processor (literal/const-folding in `Es6Preproc` before tokenize, incl. `Math`/`Number` constants; partial `const` propagation)
- [x] **`async`/`await`** (linear `async function` bodies only — desugared before parse; see [Promise / async](#promise-minimal)); no `async`/`await` in the grammar itself
- [x] Runtime JAR modules: **`require`** (CommonJS `exports`) and **`loadScript`** (global) — see [Global script loading](#global-script-loading-require-loadscript)
- [ ] Generators, regex literals

### Built With

* [WTK](https://www.oracle.com/java/technologies/sun-java-wireless-toolkit.html) (or your Java ME 3 / MSA SDK toolchain, depending on how you import this project)
* [RockScript](https://code.google.com/archive/p/javascript4me/)

`project.properties` in this tree targets **MSA** with optional **JSR-184** (M3G / `Render3D`), **JSR-239**, and **SATSA-JCRMI** flags; adjust the platform line for your WTK or SDK profile.

## Coding

In this section you will have some information about how to code using Athena2ME, from prerequisites to useful functions and information about the language.

### Prerequisites

Using Athena2ME you only need one way to code and one way to test your code, that is, if you want, you can even create your code on yout J2ME device, but I'll leave some recommendations below.

* PC: [Visual Studio Code](https://code.visualstudio.com)(with JavaScript extension) and some J2ME emulator or a real device.

* Android: [QuickEdit](https://play.google.com/store/apps/details?id=com.rhmsoft.edit&hl=pt_BR&gl=US) and some J2ME emulator or a real device.

Oh, and I also have to mention that an essential prerequisite for using Athena2ME is knowing how to code in JavaScript.

### Boot splash (`boot.ini`)

On cold start, the MIDlet shows a **boot splash** canvas while a background thread loads `main.js` and prepares the JS runtime. Layout and timing are driven by **`/boot.ini`** in the JAR (UTF-8, optional BOM; lines starting with `#` are comments). If the file is missing or cannot be parsed, the loader uses a **minimal config** (no slides) so startup still works.

**Sections (INI keys are case-insensitive when parsed):**

| Section | Keys | Role |
| --- | --- | --- |
| `[tick]` | `ms` | Period in **milliseconds** between splash canvas repaints (default **50**). Drives a `java.util.Timer`; must be &gt; 0 to override the default. |
| `[boot]` | `slides` | **Optional.** Non-negative integer: how many slides to allocate. If set, it defines slide count; otherwise the count is derived from the highest `[splash.N]` index + 1. |
| `[boot]` | `handoff` | When the game canvas replaces the splash after the interpreter is ready: **`immediate`** — hand off as soon as cold start finishes (may cut the current slide), or **`after_slide`** (default) — wait until the **current** slide’s `holdMs` has elapsed, then hand off. |
| `[boot]` | `es6` | **Optional.** If **`true`** (default), the **ES6 preprocessor** runs on the script pipeline (faster *after* the first run thanks to the RMS cache of preprocessed `main.js`, but a cold cache pays preprocessing cost). If **`false`**, preprocessing is **disabled for the whole session** (faster cold start when the cache is empty; use **legacy/ES5-style** scripts only, since features desugared by the preprocessor are unavailable). Recognized falsy tokens: `false`, `0`, `no`, `off`, `legacy` (case-insensitive). |
| `[splash.N]` | see below | One slide per index `N` (0, 1, …). |

**`[boot] es6` in practice:** With **`es6=false`**, the ES6 preprocessor is skipped for the **entire** session (any full script the runtime loads, not only `main.js`), which speeds up cold start when the cache is still cold. The RMS cache for the startup script stores whether the saved text was produced with or without preprocessing, so you can switch this option without the cache serving the wrong mode.

**`[splash.N]` per slide**

* **`background`** — `#RRGGBB` (optional `#`); 6- or 8-digit hex; if 8 digits, alpha is ignored and RGB is used.
* **`holdMs`** — How long the slide stays visible in **milliseconds** (default **800**; clamped with `Math.max(0, …)` in code).

**Text — simple mode (one line) or indexed mode (many lines):**

* **Simple mode** — use legacy keys: `text`, `textX`, `textY`, `textSize`, `textColor`, `textAlign`, and optionally one `image` with `imageX`, `imageY` (see the sample in [`res/boot.ini`](res/boot.ini)).
* **Indexed mode** — if **any** key exists among `text.M`, `textX.M`, `textY.M`, `textSize.M`, or `textColor.M` for `M ≥ 0`, that slide is built from indexed entries. Use `text.0`, `text.1`, … with per-line `textX.N`, `textY.N`, `textSize.N`, `textColor.N`, **`textAlign.N`**. If `text.0` is omitted, the plain `text` key is used as a fallback for index 0. The same pattern applies to **`image.N`** vs legacy `image`, with `imageX.N` / `imageY.N`.

**`textSize` / `textSize.N`** — One of: **`SMALL`**, **`MEDIUM`**, or **`LARGE`** (case-insensitive). These map to the same `javax.microedition.lcdui.Font` size constants the runtime uses for splash text.

**`textAlign` / `textAlign.N` — horizontal alignment only (splash):**  
Values (case-insensitive): **`left`**, **`center`** / **`centre`** / **`middle`**, or **`right`**. The point **`(textX, textY)`** is the anchor: **left** = string starts at that X; **center** = horizontal center at X; **right** = string ends at X. *(Splash text always uses a vertical anchor consistent with `Graphics` top baseline behaviour; this is *not* the full per-axis `Font.ALIGN_*` set used in scripts — see [Font module](#font-module).)*

**Screen macros (expanded at draw time; `W` / `H` = canvas size in pixels):**

* `%W%` — full width  
* `%H%` — full height  
* `%W2%` — `width / 2` (integer)  
* `%H2%` — `height / 2`  

Longer tokens are substituted first so `%W2%` is not broken by a `%W%` pass. **Coordinates** (`textX`, `textY`, `imageX`, `imageY`, including indexed `*.N`) may use macros and then **add/subtract** integer terms, e.g. `textX.0=%W2%+20`, `textY.0=%H%-8` (the parser accepts chains like `a+b-c` after whitespace is stripped for evaluation).

**`text` and `text.N` strings** also run through the same macro expansion, so you can embed `%W%` / `%H%` in the visible string if needed.

**Images:** `image` or `image.N` is a JAR path (e.g. `/logo.png`). Empty path skips the image. Images are drawn with **`Graphics.TOP | Graphics.LEFT`** at the resolved (`imageX`, `imageY`).

A full commented example lives at [`res/boot.ini`](res/boot.ini) (ship it in the built JAR as `/boot.ini` next to `main.js`).

## Quick start with Athena

Hello World (ES6+ style):
```js
const font = new Font("default");
const WHITE = Color.new(255, 255, 255);

class Counter {
    constructor() { this.n = 0; }
    tick() { this.n++; }
}
const c = new Counter();

os.setExitHandler(() => os.stopFrameLoop());

// Native frame loop: key sampling, Pad listener dispatch, then your callback, then Screen.update().
os.startFrameLoop(() => {
    Screen.clear();
    c.tick();
    font.print(`Hello from Athena2ME! frame=${c.n}`, 15, 15);
}, 60);
```

Classic ES5 source still works unchanged; the new syntax is opt-in and is
rewritten to ES5 by a source-level preprocessor before parsing.

The legacy pattern (`while (running) { Screen.clear(); …; Screen.update(); os.sleep(16); }`) still works, but `os.startFrameLoop` is strongly preferred: it runs in a dedicated native `Thread`, paces frames precisely, and removes the interpreter from the frame critical path. On resource-constrained phones this is the difference between smooth 60 FPS and an "application not responding" dialog.

## Features

Athena2ME ships a heavily forked version of the RockScript interpreter. On top
of the upstream ES3/ES5 core it adds a large subset of ES6/ES7 syntax
(let/const, arrows, template literals, classes, destructuring, spread/rest,
for…of) implemented as a source-level preprocessor, plus a modern standard
library (Array/Object/String/JSON/Number/Math, Map/Set/Symbol) exposed through
a fast native-binding path.

On devices with **RMS** available, the MIDlet may **cache the ES6-preprocessed** source of `main.js` (keyed by a hash of the original file) so a cold start can skip the preprocessor on the next run when the hash matches. This is internal to `Athena2ME` and does not change script behaviour.

### Changes from upstream RockScript / javascript4me

This fork diverges substantially from the original [RockScript](https://code.google.com/archive/p/javascript4me/) sources that ship in [`src/net/cnjm/j2me/tinybro/`](src/net/cnjm/j2me/tinybro/). The performance-oriented changes keep the upstream JavaScript surface intact (legacy ES3/ES5 sources run as-is); the ES6+ changes are strictly additive on top. All modifications were driven by profiling on S40/SE mid-range hardware (4–8 MB heap, ARM9 200–400 MHz), where the original interpreter is dominated by hashtable lookups and short-lived allocations in the `eval` loop.

#### New files

* [`NativeFunctionFast.java`](src/net/cnjm/j2me/tinybro/NativeFunctionFast.java) — abstract subclass of `NativeFunction` with a raw signature:
  ```java
  public abstract Rv callFast(boolean isNew, Rv thiz,
                              Pack args, int start, int num,
                              RocksInterpreter ri);
  ```
  Native bindings that extend this class are invoked **without** building an `arguments` `Rv`, without allocating a call-scope `funCo`, and without hashing `"0"`/`"1"`/… string keys for positional arguments. Legacy `NativeFunction` subclasses keep working unchanged (the default `func()` implementation bridges back to `callFast`).

* [`Es6Preproc.java`](src/net/cnjm/j2me/tinybro/Es6Preproc.java) — source-level
  preprocessor that runs before the tokenizer. Each pass rewrites one piece of
  ES6+ syntax into ES3/ES5 that the original parser already understands:
  template literals → string concatenation, arrow functions → `function`
  expressions (with `this` captured via a temporary), classes/`extends`/`super`
  → `function` + `prototype` + explicit parent calls, `for...of` → index-based
  `for`, default params → leading `if (x === undefined) x = …;`, object
  destructuring → individual `var` assignments, array destructuring → indexed
  reads, shorthand props `{a, b}` → `{a: a, b: b}`, computed keys `{[k]: v}`
  → object build + `obj[k] = v`, call-site spread `f(...xs)` → `f.apply(null,
  xs)`, rest params `function f(...r)` → `var r = Array.prototype.slice.call
  (arguments, N)`. Each pass is string-to-string, O(n) in source length, and
  runs exactly once per `reset()`.

* [`StdLib.java`](src/net/cnjm/j2me/tinybro/StdLib.java) — installs the modern
  built-ins under their standard paths: `Array.prototype.*`, `Array.of/from/
  isArray`, `Object.keys/values/entries/assign/freeze/…`, `String.prototype.*`
  (trim, includes, startsWith, repeat, padStart/End, replace, replaceAll, …),
  `JSON.parse` / `JSON.stringify` (with optional indent), `Number.isInteger/
  isFinite/isNaN/parseInt/parseFloat`, extended `Math` (sqrt, pow, sin/cos/tan,
  atan/atan2, exp, log, `trunc`, PI, E — **radians**; runtime via `CldcMath` on
  CLDC), and the `Map`/`Set`/`Symbol` constructors backed
  by `Rhash` + `Pack`. Every binding is a `NativeFunctionFast` so it
  participates in the zero-allocation dispatch path. `installStdLib(Rv go)` is
  called from `initGlobalObject()`; the MIDlet only has to register its own
  bindings on top.

#### `RocksInterpreter`

* **Dense operator tables.** The per-token `Rhash` tables `htOptrIndex` and `htOptrType` were replaced by `static final int[2048]` arrays indexed directly by token id. The hottest read in the interpreter — the operator dispatch inside `eval()` — is now a single array load instead of `hashCode() % len` + bucket walk + `String.equals`. Same change applies to `Rv.binary` and `expression()`.
* **Operand `Pack` pool.** `eval()` used to `new Pack(-1, 10)` for the RPN operand stack on every evaluated expression. The fork now keeps a per-interpreter pool `Pack[] opndPool` indexed by a re-entrancy counter `evalDepth`; each frame of recursion borrows a `Pack`, resets `oSize = 0`, and returns it in `try/finally`. Steady-state expression evaluation is zero-allocation for the operand stack.
* **Frame-local `Rv` temp pool.** `eval()` also keeps a per-depth pool of scratch `Rv` cells for non-escaping intermediates, currently the short-lived `LVALUE` refs produced when symbols are resolved for assignments, deletes, increments and function calls. If such a scratch would be the final expression result, it is materialised with `evalVal()` before the frame clears the pool, so JavaScript never observes a recycled cell as object identity.
* **Fast native dispatch.** `call()` checks `function.obj instanceof NativeFunctionFast` and, when true, short-circuits the entire `new Rv(ARGUMENTS) / putl("callee") / putl("this") / Integer.toString(i)` prologue (upstream lines ~811–839) and invokes `callFast()` directly against the operand `Pack`.
* **Direct native reference.** `addNativeFunction` now stores the concrete `NativeFunction` instance in the resulting `Rv.obj`. `callNative` uses that direct reference instead of performing `function_list.get(function.str)` on every invocation — one `Hashtable<String, NativeFunction>` lookup per native call removed.
* **Graceful destroy.** The interpreter no longer relies on the JS side to exit cleanly; see the `os.startFrameLoop` / `os.stopFrameLoop` bindings added at the MIDlet layer.
* **Preprocessor hook.** `reset()` now invokes `Es6Preproc.process(src)` on
  the top-level script before tokenization. The output is plain ES3/ES5, so
  every subsequent stage (tokenizer, parser, RPN builder, evaluator) remained
  untouched.
* **Tokenizer additions.** `let` and `const` are recognised as keywords and
  emitted as `RC.TOK_VAR` (block scope is left for a future phase). `=>`
  produces `RC.TOK_ARROW`, the parser then folds the preceding parameter list
  into a `function` expression with a captured lexical `this`. Template
  literals (`` ` `` … `` ` ``) enter a sub-lexer that alternates string chunks
  with embedded `${…}` expressions.
* **`invokeJS(fn, thiz, Pack args, start, num)`** helper. Exposes a single
  entry point used by every native binding that calls back into JS
  (`Array.forEach`, `Array.map`, `os.startFrameLoop`, …). Centralises
  `funCo`/`callObj` save-restore and error propagation.
* **Call-site inline cache (lazy).** RPN `TOK_INVOKE` / `new` use an `InvokeOpRv` placeholder (instead of a bare `Rv(0)`) to hold a 4-way polymorphic cache: **direct** calls (`f()` with `f` a `FUNCTION` value) and **member** calls (`o.m` with `o` an `LVALUE`) store enough state (holder + key + `Rhash` identity + `gen` + `layoutFp`) to skip repeated `get()` for the callee, mirroring the `LvalueInlineCache` invariants. `new Foo()` / `TOK_INIT` never use this fast path to preserve constructor / prototype setup semantics.
* **`funCo` pool.** The scope object passed into `call()` for every JS and native (slow-path) callback is recycled from a per-interpreter free list (invoke sites, `invokeJS`, and `js_call_apply` / `Function.call|apply` glue) with `Rhash.reset(11)` in `recycleCallObject` instead of allocating `new Rv(OBJECT, _Object)` on every invocation.
* **Flat RPN bytecode (`Node` + `eval`).** Expression bodies (`TOK_MUL`) compile to a dense stack machine: `int[] rpnOps`, parallel `Object[] rpnConsts`, and `rpnLen`, instead of stashing RPN tokens in a `Pack` on `Node.children`. AST children and evaluation bytecode are clearly separated; `eval()` walks `rpnOps`/`rpnConsts` directly. Jump patching for short-circuit `&&`/`||` uses the flat op array.
* **Token stream normalization.** After tokenizer refactors (payloads as `Rv`, `String`, or legacy `Object[]` pairs), helper accessors (e.g. `tokenSymbolName`, `tokenValue`, `rpnOperandValue`) feed `statements()` / `expression()` / RPN emission so the parser does not cast token payloads to a single shape—avoiding `ClassCastException` on function names, `catch` bindings, and symbol operands.

#### `Rv`

* **Numbers (int fast path + IEEE double).** Primitive `NUMBER` values store
  either a 32-bit integer in `num` (`f == false`) or an IEEE 754 double in `d`
  (`f == true`). When both operands are ints and the result still fits 32 bits,
  `+`, `-`, `*`, relational compares, `==`/`===`, and `++`/`--` stay on the int
  path (important on slow J2ME CPUs). Otherwise mixed operations promote as in
  ECMAScript: `/` and `%` use real arithmetic; bitwise ops apply
  `ToInt32`/`ToUint32`; `===` distinguishes `+0` and `-0` and treats `NaN` as
  never equal to itself.
* **`INT_STR` cache.** A `static final String[] INT_STR` of size 512 caches `Integer.toString(i)` for small indices. `putl(int,…)`, `shift`, `keyArray`, and every `Array.*` built-in (`push`, `pop`, `unshift`, `slice`, `sort`, `reverse`, `concat`, `join`) now use `intStr(i)` instead of allocating a fresh `String` per element operation. For `i ≥ 512` it transparently falls back to `Integer.toString(i)`.
* **Symbol interning.** `Rv.symbol(String)` routes through a `static java.util.Hashtable _symbolPool`. Identical symbol literals (`"Draw"`, `"rect"`, `"draw"`, property names produced by the parser, …) share a single canonical `Rv` instance. Combined with the hash cache below, repeated property accesses become `==`-cheap.
* **Cached `hashCode`.** New field `public int hash`, populated once for `SYMBOL`/`STRING` values. Used as the key hash when the `Rv` is fed into `Rhash` lookups, removing the recomputation of `String.hashCode()` on every property access.
* **`getByKey(Rv key)`** — variant of `get` that consumes the cached `key.hash` so `evalVal(TOK_SYMBOL)` can resolve scope chains without re-hashing.
* **Strict-equality fix.** Upstream `Rv.isIden()` (which implements `===` /
  `!==`) had the type-compare branch inverted — it returned `false` whenever
  the operands shared a type. Silent, and catastrophic for any `x === literal`
  check (they all resolved to `false`, driving code into default branches).
  The fork restores the correct semantics: same type ⇒ compare values, else
  return `false`.
* **`for (init; cond; update)` header fix.** Upstream `shouldIgnoreSemicolon`
  dropped any `;` whose previous token was `)`, `]`, `}` or `\n`. Inside a
  `for` header that quietly swallowed the mandatory init/cond separator
  whenever the init expression ended in `)` — e.g. `for (var a = (1+2);
  cond; upd)` or anything produced by the ES6 preprocessor's
  `for…of` desugaring. The collapsed init+cond clause then tripped
  `eatUntil` with an unmatched `)` at parse time (`ArrayIndexOutOfBoundsException`
  in `Pack.getInt`). The fork narrows the heuristic to `}` and `\n` only,
  preserving the `;` separator inside any parenthesised sub-expression.
* **String indexing.** `Rv.get("N")` on a primitive `STRING` value now
  returns the one-character substring at index `N` (`"abc"[1] === "b"`),
  matching ES5 semantics. Upstream returned `undefined` for every
  non-`length` property, which forced runtime branches whenever generic
  container code (for…of desugaring, iterator helpers, …) wanted to walk
  a string the same way it walks an array.
* **Polymorphic inline cache (PIC) for `LVALUE` (6 slots, lazy).** A member-read RPN site allocates `Rv.LvalueInlineCache` on first `Rv.get()`; each slot stores holder `Rv`, resolved value, key, the **backing** `Rhash`, that map’s `gen` stamp, and its **`layoutFp`** (see `Rhash`). A hit validates `rhash == holder.prop`, `stamp == holder.prop.gen`, and `layout == holder.prop.layoutFp` (O(1) per slot, linear probe), then returns the cached value and skips the prototype-chain walk. On a miss, the slow path runs and the result is written to the next slot, round-robin, evicting the oldest entry. Relying on `gen` alone is insufficient: several `Array.*` natives (`unshift`, `sort`, `reverse`, …) **replace** `thiz.prop` with a freshly built `Rhash` whose own `gen` can collide with an unrelated map — the `Rhash` **identity** check prevents stale reads (e.g. `arr[0]` after `unshift`). The PIC is still keyed by **receiver identity**: more than six distinct holders at the same bytecode site can thrash; full *hidden classes* (shared shapes across instances) are not implemented, but `layoutFp` gives a second structural signature.
* **Typed arrays: indexed write + `getByKey` PIC.** For `Uint8Array` / `Int32Array` / `Float32Array`, element assignment through `Rv.put` (string index key) bumps the **receiver’s** `gen` after a successful store so `getByKey`’s `symPic*` monomorphic cache does not return an outdated boxed value once the `ArrayBuffer` view changes.
* **Typed arrays: no `LvalueInlineCache` on `arr[i]` reads.** `Rv.get()` for `LVALUE` **skips** `LvalueInlineCache` when the holder is a typed array: element payloads live in the view’s `byte[]`, not in `holder.prop`, and indexed writes do not advance `holder.prop.gen`. If the LVALUE PIC were used unchanged, its `stamp == prop.gen` check would stay hot forever and every `rx[k]`, `bx[k]`, `ralive[k]`, etc. would replay the **first** read (symptoms: motionless bullets/asteroids, spawn logic that always sees slot 0 as free). Ordinary objects and JS `Array` still use the 6-slot PIC as before.
* **`Rv.shift()` / `Array.pop` + `length` cache.** `Array.pop` and `Array.shift` are implemented via `Rv.shift(idx)`. When removing the **last** element, the inner copy loop runs zero times (no `put` calls), so historically `Rhash.gen` did not change even though `num` (logical length) did. Any inline cache keyed by `gen` could then keep returning the **old** length. The fork always increments `prop.gen` and `this.gen` at the end of `shift()`, so `arr.length` and tight cleanup loops like `while (arr.length > w) arr.pop()` stay consistent (this showed up as a device freeze once particles started dying and the particle array was trimmed every frame).
* **`isCallable()`** public helper. Replaces ad-hoc checks against the package-private constants `FUNCTION` / `NATIVE` so the MIDlet layer can validate callback arguments without exposing interpreter internals.
* **Fixed slab for `Rhash` entry `Rv`s.** Property-table nodes are internal `Rv` cells (`key -> value`), not observable JavaScript values, so they can be recycled safely without changing object identity, prototypes, closures, promises, or PIC holders. `Rv` now pre-allocates a fixed 1024-entry slab; `Rhash.put` borrows from it, replacement/removal/reset clear references and return nodes to the slab, and overflow falls back to normal Java allocation.

#### `Rhash`

* **Generation counter.** New field `public int gen`, incremented in `reset()`, `putEntry()` (on both insert and replace) and `remove()`. Acts as the structural-change stamp that feeds the `Rv` inline cache.
* **Layout fingerprint (`layoutFp`).** A rolling XOR of `layoutKeyMix` for each **new key** on insert and the same on remove. Value-only `putEntry` **replace** does not change `layoutFp` — a cheap *key-set* signal used alongside `gen` in the LVALUE PIC. Resets with `Rhash.reset()`.
* **`getEntryH(int hash, String key)` / `getH(int hash, String key)`.** Lookup variants that accept a pre-computed hash, exposed for callers (notably `Rv.get` / `Rv.has` / `evalVal`) that already carry a cached hash on the lookup key.
* **Entry lifecycle hooks.** `removeAndRelease(...)` is used by hot paths that only need to delete a key, letting removed entry nodes go straight back to the fixed `Rv` slab instead of waiting for Java's stop-the-world GC.

#### Net effect

On the reference scene of `res/main.js` (~10 native calls per frame, one `Pad.justPressed` branch, one text draw, four `Image.draw`s and a `Draw.rect`) the combined changes deliver, compared to upstream RockScript with the same bindings:

* ~0 allocations per frame in the `eval` loop (was ~10+ `Pack` + ~N `Rv` per expression).
* Repeated property writes/deletes reuse fixed `Rhash` entry cells instead of allocating one `Rv` wrapper per table mutation.
* 1 array load per operator dispatch (was 1 hash + 1 modulo + ≥1 `equals`).
* 1 direct call per native invocation (was 1 `Hashtable.get` + 1 `arguments` object build + up to `argc` `Integer.toString`).
* Warm LVALUE property reads: best case one PIC slot (same as before, a few field reads + `int` compare); in the small-polymorphic case, up to six such probes per read.

#### Related non-interpreter changes (binding layer)

While migrating hot-path bindings to `NativeFunctionFast`, a few correctness bugs in the native glue were fixed:

* [`AthenaCanvas.drawImageRegion`](src/AthenaCanvas.java) / `_drawImageRegion` — was passing `endx`/`endy` to `Graphics.drawRegion`, which expects `width`/`height`. Regions with `startx > 0` or `starty > 0` now render correctly. `drawImageRegion` also supports optional sprite batching (`Screen.beginBatch` / `flushBatch` / `endBatch`).
* [`AthenaCanvas.drawFont`](src/AthenaCanvas.java) — previously ignored the `anchor` and always used `TOP | LEFT`, so `font.align` had no effect. The implementation now calls `Graphics.drawString` with a **normalized** anchor mask: if you pass only horizontal bits, vertical defaults to **TOP**; only vertical bits default horizontal to **LEFT** — matching the single-flag style used in AthenaEnv. Game text alignment is therefore fully consistent with J2ME `Graphics` string anchoring.
* [`AthenaCanvas.loadImage`](src/AthenaCanvas.java) — caches `Image` objects by path in a `Hashtable`. Repeated `new Image("/foo.png")` no longer re-decodes the PNG.
* [`AthenaTimer`](src/AthenaTimer.java) — unified time base. `get()`/`set()` used to mix a `tick`-relative counter with `System.currentTimeMillis()`; all methods now operate on a consistent relative-ms scale with a proper pause/resume accumulator.
* New MIDlet-level bindings **`os.sleep(ms)`** and **`os.startFrameLoop(fn, fps)` / `os.stopFrameLoop()`** (see the native frame loop section below). The former was required to fix ANR on real devices when JS code used a `while (running) { … }` main loop; the latter moves pacing, per-frame key sampling, **Pad listener dispatch**, Promise microtask drain, the script frame callback, and `Screen.update` to a dedicated Java `Thread`, removing the interpreted loop from the critical path entirely.
* **Pad input** — [`Pad.addListener`](#pad-module) / `Pad.clearListener`, kinds **`PRESSED`**, **`JUST_PRESSED`**, **`NON_PRESSED`**, and canvas-side **edge + mask** helpers (`padJustPressed`, `padNotPressed`) so games can use **event-style** input and “no button held” conditions without scanning every `pressed` bit in the frame body. The bundled demo [`res/main.js`](res/main.js) uses **`JUST_PRESSED`** listeners for a one-shot feel.

### JavaScript syntax (ES6+)

Everything below is supported out of the box. The preprocessor rewrites it to
an equivalent ES5 program before parsing, so there is no runtime cost for
sources that do not use the feature. The final pass `preprocessConstantFold`
conservatively folds pure literal sub-expressions to reduce token count (enabled
by default). Disable with `RocksInterpreter.setPreprocLiteralFold(false)`.

```js
// let / const (real block scope; const immutability is WIP)
let hp = 100;
const MAX = 255;

// Arrow functions with lexical `this`
const square = n => n * n;
const add = (a, b) => a + b;
button.on("click", () => this.fire());

// Template literals
const msg = `player ${name} scored ${score} pts`;

// Object shorthand + computed keys
const x = 1, y = 2;
const point = { x, y, [`tag_${x}`]: true };

// Default parameters
function greet(name = "world") { return `hi ${name}`; }

// Destructuring (declarations)
const [first, second] = list;
const { width, height } = screen;

// Rest / spread at the call site and in parameters
function sum(...xs) { return xs.reduce((a, b) => a + b, 0); }
sum(...[1, 2, 3]);                        // => 6

// for...of over arrays, strings, Map, Set
for (const v of array) total += v;
for (const ch of "abc") out += ch.toUpperCase();

// Classes, inheritance, static methods, super
class Enemy {
    constructor(hp) { this.hp = hp; }
    damage(n) { this.hp -= n; }
    static spawn(hp) { return new Enemy(hp); }
}
class Boss extends Enemy {
    constructor(hp) { super(hp); this.phase = 1; }
    damage(n) { super.damage(n / 2 | 0); }
}
```

Known limitations versus full ES6: `const` does not yet enforce immutability at
runtime (but block scoping works); no regex literals, no generators; no
`async`/`await` in the parser grammar—only **linear** `async function` bodies are
rewritten to `Promise` chains before tokenize (see [Promise (minimal)](#promise-minimal));
no tagged templates, no `Proxy`/`Reflect`, no symbols as
object keys (they compare by identity but do not participate in property
lookup), and no numeric separators. See [`res/tests.js`](res/tests.js) for a
runnable smoke suite covering every feature listed above.

### JavaScript standard library

Hot paths (Array/Object/String/JSON/Number/Math/binary views/Map/Set/Symbol) are implemented
as `NativeFunctionFast` bindings in [`StdLib.java`](src/net/cnjm/j2me/tinybro/StdLib.java)
and are resolved with the fast-dispatch path described above.

* **Object** — `toString`, `hasOwnProperty`, `Object.keys`, `Object.values`,
  `Object.entries`, `Object.assign`, `Object.freeze`, `Object.isFrozen`,
  `Object.getPrototypeOf`, `Object.create` (minimal)
* **Function** — `call`, `apply`, `bind` (via preprocessor)
* **Number** — `MAX_VALUE`, `MIN_VALUE`, `NaN`, `EPSILON`,
  `MAX_SAFE_INTEGER`, `valueOf`, `Number.isInteger`, `Number.isFinite`,
  `Number.isNaN`, `Number.parseInt`, `Number.parseFloat`
* **String** — `fromCharCode`, `valueOf`, `charAt`, `charCodeAt`, `indexOf`,
  `lastIndexOf`, `substring`, `split`, `slice`, `trim`, `trimStart`,
  `trimEnd`, `includes`, `startsWith`, `endsWith`, `repeat`, `padStart`,
  `padEnd`, `replace`, `replaceAll`, `toLowerCase`, `toUpperCase`, `concat`
* **Array** — `concat`, `join`, `push`, `pop`, `shift`, `unshift`, `slice`,
  `sort`, `reverse`, `map`, `filter`, `reduce`, `reduceRight`, `forEach`,
  `find`, `findIndex`, `some`, `every`, `includes`, `indexOf`, `lastIndexOf`,
  `fill`, `flat`, `copyWithin`, `Array.isArray`, `Array.of`, `Array.from`
* **JSON** — `JSON.parse`, `JSON.stringify(value, replacer?, indent?)`
* **Math** — `random`, `min`, `max`, `abs`, `floor`, `ceil`, `round`, `sign`,
  `trunc`, `sqrt`, `pow`, `sin`, `cos`, `tan`, `atan`, `atan2`, `exp`, `log`, `PI`,
  `E` (trigonometric / transcendental functions are implemented in
  [`CldcMath`](src/net/cnjm/j2me/tinybro/CldcMath.java) for predictable behaviour on CLDC 1.1)
* **ArrayBuffer** — `byteLength`, `slice`
* **Uint8Array** — `length`, `buffer`, `byteOffset`, `byteLength`, `subarray`,
  numeric index `u[i]` (read/write 0–255), `for...of` via index desugaring
* **Int32Array** — `length`, `byteLength`, `buffer`, `byteOffset`, `BYTES_PER_ELEMENT` (= 4),
  `subarray`, numeric index `a[i]` (32-bit signed, **little-endian** in the underlying `ArrayBuffer`); `new Int32Array(jsArray)` copies element-wise from a JavaScript **Array**
* **Float32Array** — same fields as `Int32Array` (also `BYTES_PER_ELEMENT` 4), IEEE-754 floats **little-endian**; `new Float32Array(jsArray)` copies from a JavaScript **Array**; `Render3D.setTriangleStripMesh` and **`Render3D.setObjectMatrix`** read typed views without per-element `Rv` access
  * **Performance:** for large meshes and UVs in the game loop, prefer **`Float32Array` / `Int32Array`** for positions, indices, and texcoords — the native glue avoids per-index string keys and extra `Rv` boxing on the hot path. For **indexed reads and writes in pure JS** (`arr[i]` in tight loops), the interpreter keeps **typed-array element access coherent** with its inline caches (see [Changes from upstream RockScript / javascript4me](#changes-from-upstream-rockscript--javascript4me) → **`Rv`** → typed-array bullets).
* **DataView** — `getUint8`/`setUint8`, `getUint16`/`setUint16`, `getInt32`/`setInt32`
  with optional `littleEndian`
* **Map** / **Set** / **Symbol** — constructors, `size`, `get`/`set`/`has`/
  `delete`, `keys`/`values`/`entries`, iteration via `for...of`
* **Date** — `now`, `getTime`, `setTime`
* **Error** — `name`, `message`, `toString`
* **Misc** — `console.log`, `isNaN`, `parseInt`, `eval`, `es - evalString` (do **not** use `eval` to load whole scripts from the JAR; use **`require`** / **`loadScript`** below)

**How to run it**

Athena is basically a JavaScript loader, so it loads .js files inside .jar file (which is a zip file). It runs `main.js` by default. Other scripts in the JAR can be pulled in at runtime with **`require`** (module `exports`) or **`loadScript`** (global execution). To run the regression suite, rename [`res/tests.js`](res/tests.js) to `main.js` (or paste its contents on top of your main). That suite includes a **`Render3D` soft-path smoke + timing** (`testRender3DBench`) when the module is bound.

## Functions, classes and consts

Below is the list of usable functions of Athena2ME project currently, this list is constantly being updated.

P.S.: *Italic* parameters refer to optional parameters
    
### os module
* os.setExitHandler(func) - Set *func* to be called when the device run any action to exit Athena2ME.
* os.platform - Return a string representing the platform: "j2me".
* **File descriptor flags (numbers)** — `os.O_RDONLY`, `os.O_WRONLY`, `os.O_RDWR`, `os.O_NDELAY`, `os.O_APPEND`, `os.O_CREAT`, `os.O_TRUNC`, `os.O_EXCL` (same values as `AthenaFile`); **`os.SEEK_SET`**, **`os.SEEK_CUR`**, **`os.SEEK_END`** for `os.seek`.
* os.open(path, flags) / os.close(fd) / os.seek(fd, offset, whence) — Open a `file://…` path with bitmask *flags*, close a descriptor, or reposition; `seek` returns the new position or `-1` on error.
* os.read(fd, maxBytes) — Read up to *maxBytes* bytes (clamped to `[1, 1048576]`; if *maxBytes* is below 1, defaults to 1024). Returns a **`Uint8Array`** (empty if EOF, error, or nothing read). Same underlying behaviour as `AthenaFile.read`.
* os.write(fd, data) — Writes *data* as **`Uint8Array`** or UTF-8 string. Returns the number of bytes written, or `-1` on error.
* os.fstat(fd) — On success, `{ size, isDirectory, lastModified }` (numbers; `isDirectory` is 0 or 1). On failure, `{ error }` only.
* os.getProperty(key) — `System.getProperty(key)`; returns a string or `null` if missing / unsupported (same pattern as fields in `getSystemInfo`).
* os.currentTimeMillis() — Wall-clock milliseconds (`System.currentTimeMillis()`).
* os.uptimeMillis() — Milliseconds since the interpreter booted (`System.currentTimeMillis()` minus internal boot timestamp).
* os.gc() — Calls `Runtime.getRuntime().gc()` (hint only; behaviour is JVM-dependent).
* os.pool(*Constructor*, *size*) — Returns a **`Pool`** handle that pre-allocates up to *size* reusable JS object shells (same `Rv` identity per slot) for *Constructor*. *size* is clamped to **`8192`**; non-callable *Constructor* yields an **`Error`**. Typical for particles, bullets, and entities that spawn/die every frame (reduces GC churn).
  * *Pool*.**acquire**(*...args*) — Takes a free instance, runs *Constructor* with that object as `this` and *args* (initializer semantics), returns the instance; returns **`null`** when the pool is exhausted.
  * *Pool*.**release**(*obj*) — Returns *obj* to the pool if it belongs to this pool and is checked out; duplicate or foreign objects are ignored (no-op).
  * *Pool*.**free**() — Number of instances currently available for `acquire`.
  * *Pool*.**capacity**() — Total slots (`size` passed to `os.pool`).
  * *Pool*.**inUse**() — Instances checked out and not yet `release`d.
  * Global **`Pool`** exists for `instanceof` / prototype; prefer **`os.pool(...)`** to construct a populated pool.
* os.threadYield() — Calls `Thread.yield()`.
* os.getSystemInfo() - Object with `microedition.platform`, `microedition.configuration`, `microedition.profiles`, `microedition.locale`, and `microedition.encoding` (each is a string or `null` if the property is not exposed). These are the standard J2ME `System.getProperty` keys; the runtime does not expose physical RAM, CPU name, or GPU. Screen size remains on `Screen.width` / `Screen.height`.
* os.getMemoryStats(*optRunGc*) - Object with `heapTotal`, `heapFree`, and `heapUsed` in **bytes** (Java heap for this MIDlet, not total device RAM). If *optRunGc* is passed and truthy, `System.gc()` runs first (slower, changes meaning of a single sample). Values vary with the garbage collector.
* os.getStorageStats(*fileUrl*) - **fileUrl** (string) is **required** — a `file://…` URL the implementation can open (often a file-system root, device-specific). On success, returns `total` and `free` in bytes. On failure, returns `error` (string) and no `total`/`free`. The emulator and real handsets may accept different paths.
* os.sleep(ms) - Yield the current thread for *ms* milliseconds. Before sleeping, pending **Promise** microtasks are flushed on the JS thread (`PromiseRuntime.drain`). Use this in manual loops so I/O callbacks can run and the UI thread stays healthy.
* os.flushPromises() - Run all queued Promise microtasks once (same drain used by `os.sleep` and the frame loop). Use if you neither `sleep` nor use `startFrameLoop`.
* os.startFrameLoop(fn, fps) - Hand the main loop over to native code. Java will run a dedicated `Thread` that, every frame: samples keys (`GameCanvas` key state / `padUpdate` equivalent), dispatches **Pad** listeners (same work as the tail of `Pad.update()` in JS), **then** drains Promise microtasks, **then** calls *fn*, **then** flushes the screen (`Screen.update` / `screenUpdate` equivalent), then sleeps until the next frame deadline. A positive *fps* sets the target rate (`1000 / fps` ms per frame, integer); non-positive *fps* paces with `Thread.yield()` only (no sleep). With this entry point you normally **do not** need to call `Pad.update()` yourself; use **`Pad.pressed` / `Pad.justPressed` inside *fn* or register `Pad.addListener`**. Recommended entry point for every new script.
* os.stopFrameLoop() - Ask the native frame loop to terminate after the current frame. Typical usage is from an exit handler.
* **Concurrency (Java threads + JS scheduling)** — There is **one** `RocksInterpreter` for the whole MIDlet. All JavaScript execution and `PromiseRuntime.drain` for that interpreter are serialized on a single lock so `jsThread`, the native frame loop thread, and microtasks never corrupt interpreter state. Background Java threads (HTTP, `os.spawn`, etc.) must **not** call into the interpreter directly; they enqueue microtasks instead (same pattern as `Request`).
* os.spawn(*fn*) — Starts a short-lived Java `Thread` that immediately enqueues a microtask. When the microtask runs (on the next drain), *fn* is invoked with no arguments and the returned **Promise** settles with *fn*'s return value or rejection. *fn* runs on the same serialized JS runtime as everything else; `spawn` only defers work to the next microtask batch.
* os.Thread.start(*fn*) — Same behaviour as `os.spawn` (alias for scripts that prefer a `Thread` namespace).
* os.Mutex() — Returns a **non-reentrant** mutex with methods: `lock()`, `tryLock()` (returns `1` / `0`), `unlock()`. Blocking `lock()` from JavaScript ties up the interpreter thread; prefer `tryLock` or keep critical sections in native-backed flows. `unlock` without ownership is a no-op.
* os.Semaphore(*initial*, *max*) — Counting semaphore with `acquire()`, `tryAcquire()` (`1` / `0`), `release()`, `availablePermits()`. `release` cannot raise the count above *max*.
* os.AtomicInt(*initial*) — `get()`, `set(n)`, `addAndGet(delta)`.
* os.bluetoothGetCapabilities() — Synchronous object `{ jsr82, available, powered, name, address, error }` (numeric flags use `0`/`1`; `error` is a string, empty when OK). Uses **JSR-82** (`javax.bluetooth`). Requires `jsr082.jar` (or equivalent) on the **compile** classpath; runtime still needs a device or emulator stack that exposes Bluetooth.
* os.bluetoothInquiry(*timeoutMs*) — Returns a **`Promise`** that fulfills with a dense array of `{ address, friendlyName, majorDeviceClass }`. *timeoutMs* is clamped internally: values `≤ 0` use a **30s** default. Only **one** inquiry may run at a time; a second call rejects with `Bluetooth inquiry busy`. A timer cancels the inquiry when *timeoutMs* elapses.

### Threads and concurrency

Athena2ME is not a multi-runtime environment: you do **not** get parallel JavaScript heaps or Web Workers. You get **Java** `Thread` primitives (including the existing HTTP client and frame loop) plus **synchronization objects** that coordinate those threads with the single JS engine.

**Safe pattern:** a background thread performs blocking or slow work in Java only, then calls `PromiseRuntime.enqueue` (used internally by `Request`, `os.spawn`, etc.) so callbacks and promise settlements run during `drain`, while the global interpreter lock is held.

**Deadlock caution:** if JS code calls `mutex.lock()` and holds the mutex across an operation that needs another thread to run microtasks (for example, waiting for a promise only settled from a worker), the runtime can stall. Keep mutex-held sections tiny; avoid blocking the JS thread on conditions that only a concurrent JS turn could satisfy.

### Global script loading (`require`, `loadScript`)

Ship extra `.js` files in the JAR (same layout as `main.js`) and load them while the MIDlet runs.

**Paths:** Use absolute paths from the JAR root, e.g. `/lib/helpers.js` or the bundled demo [`res/lib/demo_math.js`](res/lib/demo_math.js) (add it to your built JAR as `/lib/demo_math.js`). If the string has no leading `/`, one is prepended; `\` is normalized to `/`.

* **`require(path)`** — Loads the file **once**, CommonJS-style, and returns the module **`exports`** object. Inside the file, use **`exports.foo = …`** and/or **`module.exports = …`**. Execution is **synchronous**; only **classpath / JAR** resources are loaded (not HTTP). The same canonical path returns the **cached** exports object.

* **`loadScript(path)`** — Reads the file and runs it in the **global** object (no `exports` / `module` wrapper). Use when the file only defines globals (`function`, `var` at top level, etc.).

**Avoid `eval` for loading files:** the built-in `eval` re-tokenizes against a new source buffer without restoring the host program’s lexer state and can corrupt the running script. Prefer **`require`** or **`loadScript`**.

**Limitations:** ES module syntax **`import` / `export`** is not supported; use **`require`**. Circular **`require`** graphs are not handled. Keep module top-level code mostly **synchronous**; deferred **Promise** work combined with lazy parsing edge cases in nested functions is untested.

```js
// /lib/math.js — add this path inside the built JAR next to main.js
exports.add = function (a, b) { return a + b; };

// main.js
var m = require("/lib/math.js");
console.log(m.add(1, 2));
```

### Request module

HTTP/HTTPS via MIDP `HttpConnection`. HTTPS depends on the device TLS stack and certificates.

**Instance properties (defaults after `new Request()`):** `keepalive` (0/1), `useragent`, `userpwd` (`user:password` for Basic auth), `headers` (array of string pairs: `[name0, value0, name1, value1, …]`). After each completed request, the same instance fields are updated: `responseCode`, `error`, `contentLength`.

**Promises:** `get(url)`, `post(url, data)` (`data` = string or `Uint8Array`), `download(url, fileUrl)` (`fileUrl` = `file://…` path your runtime accepts) each return a **`Promise`**. Use `.then` / `.catch`, or a **linear** `async function` that the preprocessor rewrites to Promises (see [Promise (minimal)](#promise-minimal)). Only one request may be in flight per `Request` instance; if you call `get`/`post`/`download` while busy, the returned Promise rejects with `Request busy`.

**Fulfilled value for `get` / `post`:** plain object `{ responseCode, error, contentLength, body }` where `body` is a **`Uint8Array`**.

**Fulfilled value for `download`:** `{ responseCode, error, contentLength, fileUrl }` (response body is written to disk, not returned).

**Rejection:** the runtime rejects with an **`Error`**-style object (e.g. use `e.message` in `.catch`).

Microtasks run when you call **`os.sleep`**, **`os.flushPromises`**, during **`os.startFrameLoop`**, and briefly after the main script returns so short demos can finish I/O.

```js
var r = new Request();
r.get("http://example.com/").then(function (res) {
  var u8 = res.body;
  console.log(res.responseCode, res.contentLength);
}).catch(function (e) {
  console.log(e.message);
});
```

### Promise (minimal)

Global **`Promise`**: `then`, `catch`, `Promise.resolve`, `Promise.reject`, and **`new Promise(function (resolve, reject) { ... })`**. The resolve function passed to the executor applies the usual **resolution** algorithm: plain values fulfill immediately; native promises are chained; objects with a callable **`then`** are assimilated (thenables). Resolving a promise with itself yields a `TypeError`.

**`async` / `await`:** There is no `async`/`await` in the parser. Before tokenization, **`async function`** declarations whose body is a **flat** list of statements (separated by `;` at the top level of the function, with **no** `if` / `for` / `while` / `switch` / `try` / `do` starting a statement) are rewritten to `function` + `Promise` chains using the runtime helper **`__awaitStep`**. Supported patterns per statement include: `var x = await expr;`, `let`/`const`, bare `await expr;`, `return await expr;`, and `return expr;`. Bodies with blocks or control flow are **not** rewritten—use `.then` or split into smaller async functions.

At runtime, **`await`** is a reserved unary operator: if it still appears in source (unsupported body), you get a clear error—simplify the function body or use promises explicitly.

### Socket module

Constants: `Socket.AF_INET`, `Socket.SOCK_STREAM`, `Socket.SOCK_DGRAM`, `Socket.SOCK_RAW` (RAW is **unsupported** — throws on `connect`/`bind`).

* `var s = new Socket(Socket.AF_INET, Socket.SOCK_STREAM)` — TCP client: `connect(host, port)`, `send(uint8)`, `recv(maxBytes)` → `Uint8Array`, `close()`.
* TCP server: `bind(host, port)`, `listen()`, `accept()` → new `Socket` (J2ME extension for `accept`; needed after `listen`).
* UDP: `new Socket(Socket.AF_INET, Socket.SOCK_DGRAM)`, `bind` or `connect`, `send` / `recv`. On server-style `datagram://:port`, the first `recv` records the peer; subsequent `send` uses that address until the next `recv`.

### WebSocket module

* `var ws = new WebSocket("ws://host:port/path")` — only **`ws://`** is implemented; **`wss://`** is not (TLS). On failure, `ws.error` is set and methods no-op.
* `send(uint8)` — binary frame (opcode 2).
* `recv()` — blocks until one text/binary data frame; returns `Uint8Array` (empty if closed/error). Ping/pong handled internally.

### Bluetooth (JSR-82)

**Build:** add **`jsr082.jar`** (or your OEM JSR-82 API jar), e.g. from Java ME SDK / WTK `lib/jsr082.jar`, to the project compile classpath. The MIDlet JAR does not bundle this API; the handset or emulator must ship a working JSR-82 implementation.

**Native globals:** `os.bluetoothGetCapabilities`, `os.bluetoothInquiry` (see **os module** above), and **`BTSocket`**.

**`BTSocket`:** `new BTSocket()` starts with no connection. **`connect(url)`** returns a **`Promise`** that fulfills with the same instance once `btspp://…` is open (rejects on failure). Then: **`send(uint8)`** → bytes written or `-1`; **`recv(maxBytes)`** → `Uint8Array`; **`close()`**. Do not call `send`/`recv` until `connect` has settled. Only one `connect` per instance.

**JS helper module** (ship as `/lib/bluetooth.js` in the JAR): `require("/lib/bluetooth.js")` exports **`getCapabilities()`** (wraps `os.bluetoothGetCapabilities` with a fallback if natives are missing), **`discoverDevices({ timeoutMs })`** → Promise (wraps `os.bluetoothInquiry`), and **`sppUrl(address, channel, params)`** to build `btspp://` URLs (hex address, colons stripped; default `authenticate=false;encrypt=false`).

Pending Bluetooth work (inquiry + async `connect`) is tracked like HTTP; the host waits for **`AthenaBluetooth.getBluetoothInFlight()`** to reach zero before tearing down the JS runtime after `main.js` finishes.

**Limitations:** no UUID **service search** in this release (you must know channel / URL). `authenticate` / `encrypt` depend on the peer and stack. Many emulators expose no real radio — expect `available: 0` or non-empty `error` from **`getCapabilities`**.

```js
var BT = require("/lib/bluetooth.js");
console.log(BT.getCapabilities());
BT.discoverDevices({ timeoutMs: 15000 }).then(function (devices) {
  var i;
  for (i = 0; i < devices.length; i++) {
    console.log(devices[i].address, devices[i].friendlyName);
  }
});
var sock = new BTSocket();
sock.connect(BT.sppUrl("00112233445566", 1)).then(function (s) {
  // s.send(u8); var u8 = s.recv(1024); s.close();
}).catch(function (e) { console.log(e.message); });
```

### Color module
* var col = Color.new(r, g, b, *a*) - Returns a color object from the specified RGB(A) parameters.

### Image Module  

Construction:  

* var image = new Image(path);  
  path - Path to the file, E.g.: "/test.png".  

```js
var test = new Image("/owl.png"); 
``` 

Properties:

* width, height - Image drawing size, default value is the original image size.
* startx, starty - Beginning of the area that will be drawn from the image, the default value is 0.0.
* endx, endy - End of the area that will be drawn from the image, the default value is the original image size.

Methods:

* draw(x, y) - Draw loaded image on the **current** render target (main `GameCanvas` buffer or an offscreen layer set with `Screen.setLayer`). Uses `Graphics.drawRegion` for the `startx`/`starty`/`endx`/`endy` window. Example: `image.draw(15, 100);`
* free() - Free content immediately. 

**Sprite batching:** After `Screen.beginBatch()`, each `Image.draw` **enqueues** one region on the native side (no per-draw Java allocations). The queue is emitted when you call `Screen.flushBatch()` or `Screen.endBatch()`, and is also flushed automatically before `Screen.clear`, `Screen.update`, `Screen.setLayer`, `Screen.drawLayer`, and `Screen.clearLayer` if a batch was left pending. `Draw.*` and `Font.print` never go through the batch—they paint immediately on the current target—so interleave `Screen.flushBatch()` if you need vector/text **between** batched sprites in z-order.

```js
Screen.beginBatch();
spriteA.draw(0, 0);
spriteB.draw(10, 0);
Screen.endBatch(); // flush + disable batching
```
  
### Draw module
* Draw.rect(x, y, width, height, color) - Draws a rectangle on the specified color, position and size on the screen.
* Draw.line(x, y, x2, y2, color) - Draws a line on the specified colors and position on the screen.
* Draw.triangle(x, y, x2, y2, x3, y3, color) - Draws a triangle on the specified points positions and colors on the screen.
  
### Screen module
* `Screen.width` / `Screen.height` — read-only canvas size in pixels (set at startup from the `GameCanvas`).

* Screen.clear(*color*) - Clears the **current** target: the full `GameCanvas` when drawing to the screen, or the active offscreen layer when `Screen.setLayer(layer)` is in use. Default color is black. Flushes any pending sprite batch first.
* Screen.update() - Flushes any pending sprite batch, then `flushGraphics()` (next frame).

**Sprite batch (optional, reduces native `drawRegion` overhead):**
* Screen.beginBatch() - Start accumulating `Image.draw` calls on the current target.
* Screen.flushBatch() - Draw all queued regions in one Java loop (batching stays active).
* Screen.endBatch() - Flush, then turn batching off.

**Offscreen layers (background / gameplay / HUD):** each layer is a full RGB buffer (`width * height` pixels in heap/VRAM—`Screen.createLayer(Screen.width, Screen.height)` may fail on small devices; handle `null`).

* Screen.createLayer(width, height) - Returns a layer object `{ width, height }` with native buffer, or **`null`** on failure (e.g. OOM).
* Screen.setLayer(layer) - Direct `Draw.*`, `Font.print`, and `Image.draw` to the layer’s `Graphics`. Pass no argument or `null` to return to the main screen buffer. Flushes any pending sprite batch first.
* Screen.clearLayer(layer, color) - `fillRect` the entire layer without changing the current target.
* Screen.drawLayer(layer, x, y) - Blit the full layer image onto the **current** target at `(x, y)`. Flushes any pending sprite batch first.
* Screen.freeLayer(layer) - Release the buffer; if that layer was active, the target resets to the main screen.

```js
var hud = Screen.createLayer(80, 20);
if (hud) {
  Screen.clearLayer(hud, 0x80000000);
  Screen.setLayer(hud);
  Draw.rect(0, 0, hud.width, hud.height, 0xff0000);
  Screen.setLayer(null);
  Screen.drawLayer(hud, 0, 0);
  Screen.freeLayer(hud);
}
```

### Font module

**Constants:**

*Faces:*  
* `Font.FACE_MONOSPACE`
* `Font.FACE_PROPORTIONAL`
* `Font.FACE_SYSTEM`  
  
*Styles (can be combined, excepting `STYLE_PLAIN` where appropriate):*  
* `Font.STYLE_PLAIN`
* `Font.STYLE_BOLD`
* `Font.STYLE_ITALIC`
* `Font.STYLE_UNDERLINED`  
  
*Sizes:*  
* `Font.SIZE_SMALL`
* `Font.SIZE_MEDIUM`
* `Font.SIZE_LARGE`  

*Alignment (same integer values as `javax.microedition.lcdui.Graphics` anchor bits; combine with `|` for corner/center behaviour):*  

On the **`Font` constructor** object:

* `Font.ALIGN_TOP` , `Font.ALIGN_BOTTOM` , `Font.ALIGN_VCENTER` — vertical component  
* `Font.ALIGN_LEFT` , `Font.ALIGN_RIGHT` , `Font.ALIGN_HCENTER` — horizontal component  
* `Font.ALIGN_NONE` — `(TOP | LEFT)`: **(x, y)** is the top-left of the string’s bounding box (default for new instances).  
* `Font.ALIGN_CENTER` — `(VCENTER | HCENTER)`: **(x, y)** is the center of the text.  

The global **`FontAlign`** object exposes the same numbers under short names: `FontAlign.TOP` ≡ `Font.ALIGN_TOP`, `FontAlign.NONE` ≡ `Font.ALIGN_NONE`, `FontAlign.CENTER` ≡ `Font.ALIGN_CENTER`, and so on. Use whichever style you prefer; assignments to `font.align` accept any of these constants.

**How alignment works in scripts**

* **`font.align`** is an integer bit mask. Set it to a single flag or **OR** horizontal and vertical flags, e.g. `font.align = Font.ALIGN_RIGHT | Font.ALIGN_VCENTER` to pin the string’s right edge and vertical center to **`(x, y)`** in `print(text, x, y)`.
* The runtime calls [`AthenaCanvas.drawFont`](src/AthenaCanvas.java) → `Graphics.drawString` with a **normalized** anchor: if the mask has no vertical bits, **TOP** is assumed; if it has no horizontal bits, **LEFT** is assumed. This matches common game code that sets “just” `RIGHT` or `HCENTER` and still get sensible placement.
* **`getTextSize(text)`** returns raw width/height of the string for the instance’s `javax.microedition.lcdui.Font`; it does **not** depend on `align` (alignment only affects where the box is drawn, not its size).

**Boot splash text** (`boot.ini` `textAlign` / `textAlign.N`) supports only **horizontal** keywords (`left` / `center` / `right`); the full in-game `Font.ALIGN_*` / `FontAlign` set applies only to `Font` in your scripts. See [Boot splash (`boot.ini`)](#boot-splash-bootini).

Construction:  

```js
var osdfnt = new Font("default");  //Load default font
var font = new Font(Font.FACE_MONOSPACE, Font.STYLE_ITALIC, Font.SIZE_MEDIUM); //Load a custom variant font. Arguments: face, style, size (style and size are optional)
``` 

Properties:
* `color` — Font tint; default `0x00ffffff` (opaque white) on the native side. Use `Color.new(…)` for API consistency with other modules.
* `align` — Bit mask described above; default `Font.ALIGN_NONE` (same as `FontAlign.NONE`).
  
Methods:
* `print(text, x, y)` — Draws on the **current** screen or layer using `font.color` and `font.align`. Example: `font.print("Hello world!", 10, 10);`
* `getTextSize(text)` — Returns `{ width, height }` in pixels using the same `javax.microedition.lcdui.Font` as drawing (`stringWidth` + `getHeight`). If the font failed to load, both are `0`.
* `free()` — Clears the native view; call when discarding the instance.

### Pad module

**Button masks (bit flags)** — combine with `|` for `pressed` / `justPressed` / `addListener` *mask*:

* `Pad.UP`, `Pad.DOWN`, `Pad.LEFT`, `Pad.RIGHT`, `Pad.FIRE`, `Pad.GAME_A`, `Pad.GAME_B`, `Pad.GAME_C`, `Pad.GAME_D`

**Polling**

* `Pad.update()` — Samples `GameCanvas.getKeyStates()` and updates internal *previous* / *current* state for edge detection. **When you use `os.startFrameLoop`**, the native loop performs the same sampling and listener pass **before** your frame function each frame, so you usually **omit** a manual `Pad.update()` in that mode. In a **manual** `while` loop, call `Pad.update()` **once per frame** before `Pad.pressed` / `Pad.justPressed` or any listeners you rely on.
* `Pad.pressed(mask)` — `true` while **any** bit in *mask* is down (sustained).
* `Pad.justPressed(mask)` — `true` on the **first frame** a transition from *no* masked key down to *some* masked key down (one-shot / edge, OR semantics across the mask).
* `Pad.NON_PRESSED` and edge helpers — the native canvas exposes `padNotPressed(mask)`: `true` when **none** of the bits in *mask* are currently held. This backs the listener kind below.

**Event listeners (recommended for menus and one-tap actions)**

* `var id = Pad.addListener(mask, kind, callback)` — Registers a callback for a **bitmask** *mask* (same flags as `Pad.UP` …) and a *kind*:
  * `Pad.PRESSED` (`0`) — Fires **every frame** while the condition holds: **any** masked button is down (`pressed`).
  * `Pad.JUST_PRESSED` (`1`) — Fires when **any** masked button was **not** down last frame **and** is down this frame (`justPressed` — one edge per key group per transition).
  * `Pad.NON_PRESSED` (`2`) — Fires while **no** bit in *mask* is down (`padNotPressed`), useful for “all clear” or idle detection on a set of keys.
* Returns a **positive integer id**, or **`-1`** on error (e.g. non-callable *callback*, *mask* `0`, or *kind* outside `0`…`2`).
* `Pad.clearListener(id)` — Removes the listener with that *id* (ignored if *id* ≤ 0 or unknown).

**Dispatch order and threading**

* Inside `Pad.update()` (and inside `os.startFrameLoop` each frame): the runtime takes a **snapshot** of the listener list, then invokes listeners whose *kind* and current key state match. Callbacks run **on the JS thread** with the global lock held (same as other native entry points), so keep them **short**.
* With **`os.startFrameLoop`**, per-frame order is: **key snapshot** → **Pad listener dispatch** → **Promise microtask drain** → your frame *fn* → **screen flush** (see [os module](#os-module) `os.startFrameLoop` bullet). That way input listeners run **before** the body of the frame, which matches the pattern documented in the demo `res/main.js` (snake: `JUST_PRESSED` for move / fire without duplicating `justPressed` checks in the frame body).

### Keyboard module
* `var c = Keyboard.get()` — Returns the last keypad key code (numeric), or `0` if none. Compare with `Keyboard.KEY_NUM0` … `Keyboard.KEY_NUM9`, `Keyboard.KEY_STAR`, `Keyboard.KEY_POUND` (see `Athena2ME.java` bindings).

### Timer module

* var timer = new Timer()  
  • get()  
  • set(value)  
  • free()  
  • pause()  
  • resume()  
  • reset()  
  • playing()  

### Sound module

**Stream** and **Sfx** split the work: one background `Player` per `Sound.Stream` instance vs short samples on a shared **channel** pool for `Sound.Sfx`. Audio uses `javax.microedition.media` (MMAPI) only; use **WAV (PCM)** and, for long BGM, **MIDI** where the device stack supports it.

| | **Sound.Stream** (BGM) | **Sound.Sfx** (one-shots) |
| --- | --- | --- |
| **Files** | **WAV (PCM)**, or **MIDI** (`.mid` / `.midi`) | **WAV (PCM)** short clips only |
| **Role** | Long track, one instance, `position` / `length` / `loop` | Clips, **8** simultaneous voices (`AthenaSound.MAX_CHANNELS`) |
| **Path** | Resource path in the JAR (e.g. `res/bgm.wav`) | Same (e.g. `res/sfx.wav`) |

**Formats:** a typical MIDP 2 build exposes **WAV** and, for streams, **MIDI (Stream only, `.mid` / `.midi`)** through MMAPI, without custom decoders. `position` / `rewind` on MIDI can be **best-effort** depending on the emulator or handset.

* **Sound.setVolume(*volume*)** — Master output **0..100** (`VolumeControl` on new playback).
* **Sound.findChannel()** — Returns the first free SFX **channel** index (0-based), or **undefined** if all eight are busy.
* **const bgm = Sound.Stream(*path*)**  
  **Methods:** `play()`, `pause()`, `free()`, `playing()` (0/1), `rewind()` (seek to 0; call `play()` again to hear from the start if needed).  
  **Properties (number):** `position` (ms), `length` (ms, read from the `Player` after load), `loop` (non-zero = loop, **best-effort** `setLoopCount` / fallback).
* **const hit = Sound.Sfx(*path*)**  
  **Methods:** `play()` (no arg: pick a free channel, return **channel index**), `play(*channel*)` (fixed slot, return **undefined**), `free()`, `playing(*channel*)` (0/1).  
  **Properties:** `volume` **0..100** (default 100; combined with master), `pan` and `pitch` **-100..100** — `pitch` is applied if `PitchControl` exists; **pan** is reserved (many MMAPI builds have no panned sample mix).

SFX is loaded into memory **once** per `Sfx` object; each `play()` creates a new `Player` for that **channel** and releases the slot when the clip **ends** (`END_OF_MEDIA`). Stopping and closing all SFX/Stream `Player` instances runs when the MIDlet is destroyed (`destroyApp`).

### Render3D (JSR-184)

**3D** — `Render3D` picks **`m3g`** (JSR-184) when `Graphics3D` is in the runtime; otherwise **`soft`** (CPU raster: triangle strips or indexed lists; **no** `.m3g` **Loader** in software — use immediate meshes or a separate art path). For M3G, enable JSR-184 in the WTK / MSA. **`Render3D.load`(*path.m3g*)** only applies when the backend is **`m3g`** (returns a string error in software mode).

**Software raster (implementation)** — **Untextured** triangles use **`Graphics.fillTriangle`** via [`AthenaCanvas.drawTriangle`](src/AthenaCanvas.java) (fast). **Textured** triangles rasterize per scanline into a buffer and call **`Graphics.drawRGB`** through [`AthenaCanvas.drawRgb`](src/AthenaCanvas.java) (fewer native calls than one `fillRect` per pixel). UVs use perspective-correct interpolation; sampling is **bilinear** by default on **soft** (**`setTextureFilter`** / **`setTextureWrap`** adjust sampling and wrap/clamp).

**Backend parity** — New `Render3D` APIs are implemented on **both** `m3g` and `soft` **unless** called out (e.g. **`setDepthBuffer`**, **`setMaxTriangles`**, and **`getCapabilities`** field **`depthBufferOption`** are **soft**-centric; **`load`**, **`worldAnimate`**, and **`m3g*`** are **M3G-only**). **Texture mapping** — call **`setTexture`(*jar path*)**, then **`setTexCoords`(*2 floats per vertex*)**, then **`setTriangleStripMesh` / `setIndexedMesh`** (same for both backends). Both backends use the JAR image resource path (e.g. `"/tex.png"`). If the image fails to load, drawing falls back to **flat / Gouraud** shading (no texture). **Texture alpha** — **soft** samples **ARGB** from `Image.getRGB` and blends with **`drawRGB(..., processAlpha true)`**; the software Z-buffer is updated only when texel alpha is **≥ 128** (approximate cut-out; overlapping transparent surfaces can still look wrong without **back-to-front** ordering). **M3G** uses **`CompositingMode.ALPHA`** on the immediate mesh when a texture is present, and tries **`Image2D.RGBA`** first when loading the image. The **`soft`** default triangle budget is **1024** (reallocate up to **4096** with `setMaxTriangles`); M3G has no per-frame triangle cap in this API. **`Render3D.setDepthBuffer(true)`** (software only) enables a per-pixel **depth buffer** and correct intersection for opaque geometry at the cost of **W×H** `int` plus extra fill work; M3G ignores it (hardware Z already). If depth is off, the software path uses **painter’s sort** (triangle centroid), which can be wrong for intersecting surfaces.

**`Float32Array` UVs** — `setTexCoords` accepts **2×N** floats (a `Float32Array` of UV pairs, or a JS array with an even length ≥ 2). Position arrays still require a multiple of 3 floats in `setTriangleStripMesh` / `setIndexedMesh`.

* **`Render3D.getBackend()`** — string `"m3g"` or `"soft"` (after `init` it matches the active backend; before `init`, the predicted value: M3G if the API is present, else software).
* **`Render3D.getCapabilities()`** — object: **`backend`** (string, active or predicted), **`m3gPresent`** (**1** / **0**, whether JSR-184 `Graphics3D` is available), **`maxTriangles`** (after `init` on **`soft`**: budget **32**..**4096**; on **`m3g`**: **-1**; if **`r3d`** is not created yet but the predicted backend is **`soft`**, **1024** is reported; otherwise **-1**), **`depthBufferOption`** (**1** when **`backend` === `"soft"`** so `setDepthBuffer` applies, else **0**).
* **`Render3D.setTextureFilter`("nearest" \| "linear")** — **both** backends: **nearest** vs **linear** sampling (software default **linear** / bilinear; M3G immediate mesh default was **nearest**, unchanged if you never call this).
* **`Render3D.setTextureWrap`("repeat" \| "clamp")** — **both** backends: **repeat** (wrap UVs) vs **clamp** to **[0,1]**.
* **`Render3D.setBackend`("m3g" \| "soft" \| "auto")** — choose the engine before (or during) the app: `"soft"` forces the raster path, `"m3g"` requires M3G in the runtime, `"auto"` / `"default"` restores auto-detect. Releases current 3D state (`end`); the next `init` / `begin` re-creates. Returns an error (string) if `m3g` is requested without the API.
* **`Render3D.init()`** — one-time state: default 55° FOV, `zNear` / `zFar` 0.1 / 200, clear color black, camera at (0, 0, **5**), default directional “global” light and material colours, `setMaxTriangles(1024)` and back-face culling on (both backends where applicable). **No default geometry** in Java: call **`Render3D.setTriangleStripMesh(...)`** or **`Render3D.setIndexedMesh(...)`** or **`Render3D.load`(*path.m3g*)** before the first `render`.
* **`Render3D.setPerspective(fov, near, far)`** — vertical FOV in **degrees**; `near` / `far` clip planes. Aspect uses the current draw target: main canvas or the active `Screen` layer.
* **`Render3D.setBackground(r, g, b)`** — each **0..255** (opaque clear).
* **`Render3D.setCamera(x, y, z)`** — eye position in world space; cancels a previous `setLookAt` on the same backend.
* **`Render3D.setLookAt(eyeX, eyeY, eyeZ, targetX, targetY, targetZ, upX, upY, upZ)`** — build a look-at view from `eye` toward `target` with `up` (same conventions as common graphics samples). M3G `Graphics3D` camera; software renderer uses the same basis for projection. Calling **`setCamera`** switches back to the simple eye-offset view (default forward **−Z** in world, **+X** / **+Y** right and up in software).
* **`Render3D.setMaxTriangles(n)`** — software backend only: reserve internal buffers (clamped **32**..**4096**); reallocation when the value changes. **No effect** on the M3G path.
* **`Render3D.setBackfaceCulling(true \| false)`** — **m3g:** `PolygonMode` cull back or none. **soft:** cull back-facing triangles in software before the painter sort. Default **true** after `init`.
* **`Render3D.setGlobalLight(dx, dy, dz)`** — one directional light in **world** space (not normalized; zero falls back to (0,1,0)). M3G updates the directional `Light` each frame. **soft:** N·L against vertex normals; combined with `setMaterialAmbient` / `setMaterialDiffuse`.
* **`Render3D.setMaterialAmbient(r, g, b)`** / **`setMaterialDiffuse(r, g, b)`** — **0..255** per channel; on **m3g** (immediate mesh) these feed `Material` colours; on **soft** they scale the same lighting model. Defaults match the previous M3G-like appearance after `init`.
* **`Render3D.setTexture`(*path*)** — JAR resource path for a **Texture2D** (M3G) or **`Image`/`getRGB`** (soft). Call **`setTexCoords`**, then **`setTriangleStripMesh` / `setIndexedMesh`** in that order. If loading fails, the mesh is still drawn without a texture.
* **`Render3D.setTexCoords`(*uvs*)** — per-vertex (u, v): **`Float32Array` with 2×N floats** (N = vertex count) or normal array with an **even** length **≥ 2** (must equal **2 ×** vertex count after the mesh is set). Paired with `setTexture` when texturing.
* **`Render3D.setDepthBuffer`(*true* \| *false*)** — **Software backend only** (M3G: no-op): enable a **Z-buffer** for the current draw target size (allocated per frame as needed). **Off** (default) uses back-face cull + **depth sort by triangle** (fast, not correct for all overlaps).
* **`Render3D.setTriangleStripMesh`(*positions*, *stripLens*[, *normals*])** — `positions` and optional `normals` are **arrays** of numbers (3 floats per vertex) or **`Float32Array`**. `stripLens` is **`Int32Array`** or array of strip lengths. Replaces the **immediate** mesh; clears a loaded `World` if any. Clears any **index** mode mesh.
* **`Render3D.setIndexedMesh`(*positions*, *indices*[, *normals*])** — `indices` is a list of **triangle** indices, length multiple of **3**; each index refers to a vertex in `positions` (3 floats per vertex). Optional `normals` per unique vertex, same float count as `positions`. The runtime expands to M3G strips and uses the same path in **soft** as a triangle list. Clears strip mesh mode and loaded world when applicable.
* **`Render3D.pushObjectMatrix()`** / **`popObjectMatrix()`** — save / restore the current object 4×4 (stack depth **8** on both backends). **Multiple `render` calls** between the same `begin` and `end` are supported: e.g. `setObjectMatrix` → `render` → change matrix or push/pop → `render` again.
* **`Render3D.clearMesh()`** — drop immediate strip/index mesh, pending UVs, and texture path; does not unload a **loaded** `World` from `load` (use a new `load` to replace the scene).
* **`Render3D.setMeshRotation(degrees)`** — Y-axis spin for the **immediate** mesh (with `setObjectMatrix`, rotation is applied after your matrix). Ignored when a **loaded** `World` is active.
* **`Render3D.setObjectMatrix`(*array16*)** — 16 `number`s, column-major 4×4, or 16 elements in a **`Float32Array`**; **`Render3D.setObjectMatrixIdentity()`** resets the mesh transform to identity.
* **`Render3D.load`(*path*)** — `Loader.load` on a JAR resource (e.g. `"/model.m3g"`). Resolves a **World** or wraps roots in a new **World** with a new **Camera**; on failure returns a **string** error, on success **null**. Clears the current **immediate** geometry.
* **`Render3D.getSceneInfo()`** — short one-line string (backend, loaded world, mesh/cull), for logging.
* **`Render3D.worldAnimate`(*timeMs*)** — when the backend is **m3g** and a scene was loaded with **`load`**, calls **`World.animate(timeMs)`** (JSR-184) if supported. **no-op** for immediate-only meshes or **soft** backend.
* **M3G loaded scene (all require `load` + `m3g` backend; return a string error or `null` on success)**  
  * **`Render3D.m3gNodeTranslate`(*userId*, *dx*, *dy*, *dz*)** — `Node.translate`.  
  * **`Render3D.m3gNodeSetTranslation`(*userId*, *x*, *y*, *z*)** — `Node.setTranslation`.  
  * **`Render3D.m3gNodeGetTranslation`(*userId*)** — `float[3]` or **`null`**.  
  * **`Render3D.m3gNodeSetOrientation`(*userId*, *angleDeg*, *ax*, *ay*, *az*)** — `Node.setOrientation`.  
  * **`Render3D.m3gAnimSetActiveInterval`(*userId*, *startMs*, *endMs*)** — `AnimationController.setActiveInterval`.  
  * **`Render3D.m3gAnimSetPosition`(*userId*, *sequence*, *timeMs*)** — `AnimationController.setPosition`.  
  * **`Render3D.m3gAnimSetSpeed`(*userId*, *speed*)** — `AnimationController.setSpeed` (sequence **0**).  
  * **`Render3D.m3gKeyframeDurationTrack0`(*userId*)** — duration ms of keyframe track **0** for a **`Node`**, or **-1**.
* **Frame (same thread as 2D / `os.startFrameLoop`):** `Render3D.begin()` (bind, viewport, clear, camera, lights) → `Render3D.render()` (one or more draws of the current mesh) → `Render3D.end()` (release) → e.g. **`Screen.update()`**. Draw 2D **after** `Render3D.end()` for a HUD, or keep 3D only and clear 2D first as needed.
* **Helpers** — [res/lib/mesh3d.js](res/lib/mesh3d.js) (optional): `indexBufferToStrips(vertices, faceIndices)`, `computeIndexedNormals(vertices, indices)`, and `uvsToExpandedIndexed(uvs, indices)` for indexed draw paths.

## CI and AthenaStudio API manifest

GitHub Actions workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

- Generates **`build/j2me-api.json`** from `src/Athena2ME.java` via **`node scripts/export-j2me-api.mjs`** (same schema as the AthenaStudio VS Code extension’s `targets/j2me-api.json`).
- Uploads the file as a **workflow artifact** on every run.
- On **tags `v*`** , attaches **`j2me-api.json`** to the **GitHub Release** so the AthenaStudio repo (or other tools) can pin a runtime version and download the manifest without cloning Java sources.

Local run: `node scripts/export-j2me-api.mjs` (output under `build/`, gitignored).

## Contributing

Contributions are what make the open source community such an amazing place to be learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AwesomeFeature`)
3. Commit your Changes (`git commit -m 'Add some AwesomeFeature'`)
4. Push to the Branch (`git push origin feature/AwesomeFeature`)
5. Open a Pull Request

## License

Distributed under MIT. See [LICENSE](LICENSE) for more information.

<!-- CONTACT -->
## Contact

Daniel Santos - [@danadsees](https://twitter.com/danadsees) - danielsantos346@gmail.com

Project Link: [https://github.com/DanielSant0s/Athena2ME](https://github.com/DanielSant0s/Athena2ME)





