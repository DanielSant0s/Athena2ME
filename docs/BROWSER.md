# HTML mini-browser (Java layout, JS paint)

Athena2ME can treat a subset of HTML and CSS as a **layout engine** implemented in Java (`Parser`, `Page`, `Node`, `Host`). Painting, scrolling, and game-key handling stay in **JavaScript** using the same APIs as the rest of the runtime (`Draw`, `Font`, `Image`, `Screen`, `Pad`, `Request`, `os.startFrameLoop`).

This avoids a second paint stack in Java (`Page.paint` / `Node.paint` are not the primary path for the browser demo).

## Build feature flag

`Browser` natives are wrapped in `// @feature browser` … `// @end` in `Athena2ME.java`. To **strip** them from sources when producing a smaller JAR, run:

```bash
ATHENA_FEATURES=es6preproc,debug node scripts/strip-features.mjs
```

Include `browser` in `ATHENA_FEATURES` if you need the module after stripping other regions. With an empty `ATHENA_FEATURES`, the strip script does nothing (all regions kept).

## Java side

| Piece | Role |
|--------|------|
| `AthenaPageHost` | `Host` implementation: classpath images (`.png` / `.jpg` / …), text bodies for `.css` and other non-binary resources, `rgbImage@…` placeholders. `http:` / `https:` returns `null` so layout never blocks on network. |
| `Parser` | Parses HTML; applies `<style>` blocks and `<link rel="stylesheet">` via `Host.getResource`. |
| `Page` | `calcStyle` / rows / wrap; geometry used to build the draw list. |
| `BrowserSession` | `loadHtml`, flat op vector, focus list, `activateFocused` (links + form GET/POST URL building), `historyPush` / `historyBack` / `historyForward`, `hitTest(docX, docY)`. |

### CSS subset (layout)

Common properties include `font` (shorthand), **`font-size`** (`small` / `medium` / `large` / `default`, or `px` mapped to MIDP sizes), **`font-weight`** (`bold`, `normal`, numeric weights where `≥600` → bold), `color`, `background`, margins, padding, borders, `width` / `height`, and `display`. Unknown property names are ignored with a console line from the parser.

## `Browser` module (JS)

Bindings are created only when the `browser` feature region is present.

| API | Description |
|-----|-------------|
| `Browser.loadHtml(html)` | Parse HTML, run layout at current canvas size, rebuild ops and focus. |
| `Browser.reload()` | Re-layout last HTML string. |
| `Browser.clear()` | Drop page and ops. |
| `Browser.opCount()` | Number of draw records. |
| `Browser.getOp(i)` | Object: `kind`, `x`, `y`, `w`, `h`, `color`, `fontFace`, `fontStyle`, `fontSize`, `flags`, `nodeTag`, `nodeId`, `text`, `href`, `imgSrc`. |
| `Browser.scrollY()` / `Browser.scrollY(y)` | Vertical scroll (pixels). |
| `Browser.contentBottom()` | Document height (max bottom Y). |
| `Browser.focusCount()` | Focusable nodes (links, inputs, buttons, …). |
| `Browser.focusIndex()` / `Browser.focusIndex(i)` | Focus index. |
| `Browser.focusNodeId()` | Opaque id matching `nodeId` on ops for the focused node. |
| `Browser.moveFocus(delta)` | Move focus by ±1 (wraps). |
| `Browser.activate()` | Returns `{ url, method, body }` for navigation or form submit (`method` is `"GET"` or `"POST"`; `body` is url-encoded for POST). |
| `Browser.historyPush(url)` | Push URL after a successful navigation (JS decides when). |
| `Browser.historyBack()` / `Browser.historyForward()` | Return previous/next URL or `null`. |
| `Browser.hitTest(x, y)` | Hit test in **document** coordinates (add `scrollY` to a screen Y yourself). Returns `nodeId` or `-1`. |

### Op `kind` values

| `kind` | Meaning |
|--------|---------|
| `rect` | Filled rectangle (background). |
| `border` | Border box (drawn as lines in JS). |
| `text` | Text fragment; use `Font` with `fontFace` / `fontStyle` / `fontSize` (MIDP-compatible integers). |
| `img` | Image placement; `imgSrc` is the resource path for `new Image(imgSrc)`. |

## JS libraries and demo

- [`res/lib/browser_render.js`](../res/lib/browser_render.js) — maps ops to `Draw` / `Font` / `Image`, focus ring, `ensureFocusVisible`, `frame()` (Pad + history). `loadHttpDocument(url, method, bodyStr, doPush)` runs `Request` and optionally `historyPush` (set `doPush` false when restoring from `historyBack` / `historyForward`).
- [`res/demos/browser.js`](../res/demos/browser.js) — showcase: in-app `app://` pages, GET form to `app://echo`, optional `https://example.com` (allowlisted). Like other Athena2ME demos it exports **`start`** and **`frame`** so `res/main.js` drives one `os.startFrameLoop`; do not call `os.startFrameLoop` again inside the demo (that would leave the menu loop running on top).

## HTTP and security

- Prefer **`Request.get` / `Request.post`** from JS for documents and form posts (async, fits the frame loop).
- **`Host.getResource`** is for layout-time assets (images for sizing, linked CSS on the classpath). Do not rely on it for arbitrary remote HTML.
- Real products should enforce an **allowlist** of URL schemes and hosts before calling `Request` (the demo uses a small `allowUrl` filter).

## Regression test

`res/tests.js` includes `testBrowserLayoutSnapshot()` when `typeof Browser !== "undefined"`: it loads a tiny HTML fragment, asserts `opCount() > 0`, and checks `hitTest` against a painted box.

## Future work

- Native `TextBox` for editable `<input>` / `<textarea>` when those fields need real text entry on device.
- Pointer routing through `Browser.hitTest` when a pointer API exists.
