# Athena2ME scripting best practices (performance and memory)

This guide targets **runtime JavaScript** (`main.js`, demos, modules loaded with `require`). For interpreter internals, native caches, and benchmarking, see **[PERFORMANCE.md](PERFORMANCE.md)** and the README (*Performance and size*).

Typical devices are J2ME handsets with a **small heap (on the order of a few MB)** and modest CPUs. Aim for **fewer allocations per frame**, **less work on hot paths**, and a **controlled footprint**.

---

## 1. Execution model and main loop

- Use **`os.startFrameLoop(callback, fps)`** for games or animated UIs. Avoid `while (true)` or other blocking loops in the interpreter—they trigger ANRs on real hardware and prevent proper Promise microtask draining and input sampling.
- Keep each frame callback **predictable**: avoid heavy logic and large synchronous I/O mid-frame when you have alternatives.
- **`os.sleep(ms)`** is for cooperative sleeps when you are not using the native frame loop.

---

## 2. Loading code and data (`require`, `loadScript`)

- **Never use `eval`** to load whole files or modules: it can corrupt lexer state and is expensive. Prefer **`require(path)`** or **`loadScript`**.
- **`require`** runs a file **once** and returns cached **`exports`**. Split code into modules and **avoid** redundant `require` inside hot loops (first-load cost includes preprocess/cache work, not just lookup).
- ES6 preprocessing keeps a **bounded RAM cache** (see PERFORMANCE.md) and may use **RMS (`A2MjsMod`)** for preprocessed blobs. Large modules increase storage and RAM pressure; **lazy-load** anything not needed at startup.
- **Circular `require` graphs** are unsupported—structure dependencies as a tree or a single entry point.

---

## 3. Allocations and object reuse

- In the game loop, **avoid allocating every frame** (object literals `{}`, arrays `[]`, fresh closures, excessive string concatenation).
- **Hoist** constants to module scope: fonts, colors (`Color.new`), static configuration arrays, resolved `require` references.
- For entities (bullets, particles), use **parallel arrays** or **`Float32Array` / `Int32Array`** with free-slot indices instead of creating and discarding thousands of `Object` wrappers.
- Reuse **scratch buffers**: one temporary `Float32Array` for repeated math beats `new Float32Array` inside an inner loop.

---

## 4. Typed arrays and 3D geometry

- On hot paths (vertices, UVs, indices), prefer **`Float32Array`** / **`Int32Array`** over plain `Array` of numbers: less interpreter boxing, and native APIs (`Render3D`, etc.) can consume **views without extra copies** on the **software** backend.
- On **`soft`**, **`Float32Array` views** for positions, normals, and UVs can be **zero-copy**. On **`m3g`**, data is still materialized for M3G—**prefer `soft`** if buffer upload is your bottleneck.
- **`uploadStaticMesh` / `useUploadedMesh` / `freeUploadedMesh`**: do not mutate backing buffers while a handle is live; **free** handles when a mesh is retired so associated native memory can be reclaimed.

---

## 5. Indexed reads `arr[i]` on typed arrays

- The runtime **does not** apply the same property inline cache (`LvalueInlineCache`) to **indexed reads** on typed arrays (coherence with backing `byte[]` data). In very tight loops over **`Uint8Array` / `Int32Array` / `Float32Array`**, per-access cost can exceed plain objects—**reduce** redundant rereads of the same index and keep hot values in **scalar locals** when possible (`var x = buf[i];` then reuse `x`).

---

## 6. Object literals and ES6

- Repeated object literals at the same bytecode site can benefit from **shape caching** when **`os.setLiteralShapeCacheEnabled(true)`** is on (profile before/after).
- Long, mostly static template literals: **`os.setEscapeOptForLiterals(true)`** speeds preprocessing when spans are safe (no problematic special characters); see PERFORMANCE.md §2.

---

## 7. 2D drawing and `AthenaCanvas`

- Sprite batching caps at **4096** entries; exceeding that flushes mid-frame. Call **`Screen.reserveBatch(n)`** when you know ahead of time how many sprites you will draw to **pre-grow** storage and cut internal reallocations.
- Cut redundant drawing calls: batch similar work and avoid unnecessary mutable state between calls.

---

## 8. Sound

- **`AthenaSound.playSfx`** reuses the same MMAPI **`Player`** when replaying the same **`byte[]` identity** on a channel. For frequent repeats, **load once** and keep one in-memory buffer reference instead of recreating identical arrays.

---

## 9. Storage and networking

- **`localStorage`** persists **`setItem` / `removeItem` / `clear`** **synchronously** to disk. **Never** write **every frame**; batch changes, debounce, or save only at events/menus.
- **`Socket.recv`** / pooled network buffers: honor the pooled **`byte[]`** contract—release in **`finally`** where required; if a read fills the buffer, ownership may transfer to **`newUint8Array`** (zero-copy handoff). Do not retain references to buffers returned to the pool.

---

## 10. Promises and async work

- **`PromiseRuntime`** uses a **256-slot** ring microtask queue; on overflow, oldest jobs are dropped with a **single** log line. Avoid enqueueing huge bursts of `.then` work per frame without need.

---

## 10a. Generators (`function*`, `yield`)

- Calling a generator function returns an **iterator object** (inherit `next` from the runtime prototype). Drive it with **`it.next()`** until **`done`** is true; **`for…of`** over generators is not implemented yet (preprocessor `for…of` still targets `.length` collections).
- Each **live** suspended generator keeps its **activation chain** and interpreter stack snapshot on the heap until it finishes. Prefer **few** concurrent generators (cutscenes, scripted sequences), not thousands of tiny coroutines per frame.

---

## 11. Natives and internal pooling

- **`NativeFunctionFast`** plus argument pooling (`os.setFastNativeArgPooling`) matter for frequent native calls—keep callbacks stable when the runtime can cache references.
- **`os.setCtorPoolEnabled`** may reduce constructor `this` churn **after you measure**; it defaults off.

---

## 12. Measurement and release builds

- Use **`os.getPerfStats()`** and **`os.getMemoryStats()`** for lightweight regressions; **`res/tests.js`** includes microbenchmarks and named scenarios (`bench scene *`) when enabled.
- **`ant release`** may run **`scripts/strip-features.mjs`** via **`ATHENA_FEATURES`** to strip unused Java paths—a smaller JAR and less indirectly loadable code.

---

## 13. Quick checklist

| Area | Avoid | Prefer |
|------|--------|--------|
| Main loop | Blocking `while` | `os.startFrameLoop` |
| Modules | `eval` for scripts | `require` / `loadScript` |
| Hot frame | `new Object`, `{}`, fresh closures | Flat data, pools, scalar locals |
| 3D meshes (soft) | Large mutable `Array` without views | `Float32Array` + static handles when possible |
| Sprites | Accidental flush from overload | `Screen.reserveBatch`, stay under batch cap |
| Sound | New identical `byte[]` each play | Same reused `byte[]` reference |
| Persistence | `localStorage.setItem` every frame | Save at controlled points |

---

*For VM-level detail, see [PERFORMANCE.md](PERFORMANCE.md).*
