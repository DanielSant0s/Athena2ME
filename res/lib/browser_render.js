/**
 * Paint layout ops from {@code Browser.getOp(i)} using Draw / Font / Image / Color.
 * Y positions are in document space; subtract {@code Browser.scrollY()} and add a
 * vertical offset for headers.
 */

var C_BG = Color.new(5, 8, 16);
var C_PANEL = Color.new(16, 24, 48);
var C_ACCENT = Color.new(0, 220, 255);
var C_HILITE = Color.new(255, 180, 60);
var C_TEXT = Color.new(235, 245, 255);

var fontCache = {};
var imageCache = {};

/** CSS rgb in low 24 bits → draw color; 0 falls back to readable text. */
function layoutColor(c) {
    if (c == null) {
        return C_TEXT;
    }
    c = c & 0xffffff;
    if (c === 0) {
        return C_TEXT;
    }
    return Color.new((c >> 16) & 255, (c >> 8) & 255, c & 255);
}

/** Background fill color; 0 means transparent (do not draw). */
function layoutFillColor(c) {
    if (c == null) {
        return null;
    }
    c = c & 0xffffff;
    if (c === 0) {
        return null;
    }
    return Color.new((c >> 16) & 255, (c >> 8) & 255, c & 255);
}

function pageBackgroundColor() {
    var n = Browser.opCount();
    var i;
    for (i = 0; i < n; i++) {
        var op = Browser.getOp(i);
        if (op.kind === "rect" && op.x === 0 && op.y === 0 && op.w > 0 && op.h > 0) {
            var fill = layoutFillColor(op.color);
            if (fill != null) {
                return fill;
            }
        }
    }
    return C_BG;
}

/** Clip a screen-space rect to the content viewport; null if fully outside. */
function clipToViewport(x, y, w, h, contentTop, bottom) {
    if (w <= 0 || h <= 0) {
        return null;
    }
    if (y + h <= contentTop || y > bottom) {
        return null;
    }
    var cy = y;
    var ch = h;
    if (cy < contentTop) {
        ch -= contentTop - cy;
        cy = contentTop;
    }
    if (cy + ch > bottom + 1) {
        ch = bottom + 1 - cy;
    }
    if (ch <= 0) {
        return null;
    }
    return { x: x, y: cy, w: w, h: ch };
}

function visibleInViewport(y, h, contentTop, bottom) {
    var clipH = h > 0 ? h : 0;
    return clipH > 0 && y + clipH >= contentTop && y <= bottom;
}

function normFace(face) {
    if (face === 64) {
        return Font.FACE_PROPORTIONAL;
    }
    if (face === 32) {
        return Font.FACE_MONOSPACE;
    }
    return Font.FACE_SYSTEM;
}

function normSize(size) {
    if (size === 8) {
        return Font.SIZE_SMALL;
    }
    if (size === 16) {
        return Font.SIZE_LARGE;
    }
    return Font.SIZE_MEDIUM;
}

function getFont(face, style, size) {
    face = normFace(face);
    size = normSize(size);
    var k = face + "," + style + "," + size;
    var f = fontCache[k];
    if (!f) {
        f = new Font(face, style, size);
        fontCache[k] = f;
    }
    return f;
}

function getImage(src) {
    if (!src || src.length === 0) {
        return null;
    }
    var im = imageCache[src];
    if (im) {
        return im;
    }
    try {
        im = new Image(src);
        imageCache[src] = im;
        return im;
    } catch (e) {
        return null;
    }
}

/** Decode UTF-8 Uint8Array / byte-like sequence to a string. */
function utf8FromBytes(u8) {
    if (u8 == null) {
        return "";
    }
    var n = u8.length;
    var out = "";
    var i = 0;
    while (i < n) {
        var c0 = u8[i] & 0xff;
        if (c0 < 0x80) {
            out += String.fromCharCode(c0);
            i++;
        } else if ((c0 & 0xe0) === 0xc0 && i + 1 < n) {
            var c1 = u8[i + 1] & 0xff;
            out += String.fromCharCode(((c0 & 0x1f) << 6) | (c1 & 0x3f));
            i += 2;
        } else if ((c0 & 0xf0) === 0xe0 && i + 2 < n) {
            var c1b = u8[i + 1] & 0xff;
            var c2 = u8[i + 2] & 0xff;
            out += String.fromCharCode(((c0 & 0x0f) << 12) | ((c1b & 0x3f) << 6) | (c2 & 0x3f));
            i += 3;
        } else {
            out += String.fromCharCode(c0);
            i++;
        }
    }
    return out;
}

function responseBodyToString(res) {
    if (res == null || res.body == null) {
        return "";
    }
    return utf8FromBytes(res.body);
}

/** Non-null while a {@code Request} navigation is in flight. */
var httpLoading = null;

exports.isHttpLoading = function () {
    return httpLoading != null;
};

function escapeHtml(s) {
    if (s == null) {
        return "";
    }
    s = s.toString();
    var out = "";
    var i;
    for (i = 0; i < s.length; i++) {
        var c = s.charAt(i);
        if (c === "<") {
            out += "&lt;";
        } else if (c === ">") {
            out += "&gt;";
        } else if (c === "&") {
            out += "&amp;";
        } else if (c === "\"") {
            out += "&quot;";
        } else {
            out += c;
        }
    }
    return out;
}

/** Prefer {@code <body>} for real pages; wrap so remote content is readable. */
function wrapRemoteHtml(html) {
    var low = html.toLowerCase();
    var bi = low.indexOf("<body");
    if (bi >= 0) {
        var gt = html.indexOf(">", bi);
        var end = low.indexOf("</body>", bi);
        if (gt >= 0 && end > gt) {
            html = html.substring(gt + 1, end);
        }
    }
    return "<div style=\"background:#f4f4f4;color:#333333;padding:12px;width:100%\">" + html + "</div>";
}

function showHttpErrorPage(url, message) {
    Browser.loadHtml(
        "<div style=\"padding:8px;background:#201018;color:#ffd0d0;width:100%\">" +
        "<p style=\"font-weight:bold\">Could not load page</p>" +
        "<p>" + escapeHtml(message) + "</p>" +
        "<p style=\"font-size:small\">" + escapeHtml(url) + "</p>" +
        "<p><a href=\"app://page-a\">Back to Page A</a></p>" +
        "</div>"
    );
}

function finishHttpLoad(url, doPush) {
    return function (res) {
        httpLoading = null;
        var code = res.responseCode;
        console.log("HTTP " + code + " " + url + " bytes=" + res.contentLength);
        if (code < 200 || code >= 300) {
            showHttpErrorPage(url, "HTTP status " + code);
            return;
        }
        var html = responseBodyToString(res);
        if (html.length === 0) {
            showHttpErrorPage(url, "Empty response body");
            return;
        }
        Browser.loadHtml(wrapRemoteHtml(html));
        if (doPush) {
            Browser.historyPush(url);
        }
    };
}

function failHttpLoad(url) {
    return function (err) {
        httpLoading = null;
        var msg = "Network error";
        if (err != null) {
            if (err.message != null && err.message.toString().length > 0) {
                msg = err.message.toString();
            } else if (typeof err === "string") {
                msg = err;
            }
        }
        console.log("HTTP failed " + url + ": " + msg);
        showHttpErrorPage(url, msg);
    };
}

function drawLoadingBanner(headerH) {
    if (httpLoading == null) {
        return;
    }
    var y = headerH + 8;
    var w = Screen.width - 16;
    Draw.rect(8, y, w, 28, C_PANEL);
    exports.drawBorder(8, y, w, 28, C_ACCENT);
    var lf = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    lf.color = C_TEXT;
    var label = httpLoading.toString();
    if (label.length > 42) {
        label = label.substring(0, 39) + "...";
    }
    lf.print("Loading " + label, 12, y + 4);
}

function focusBoundsDoc() {
    var fid = Browser.focusNodeId();
    if (fid < 0) {
        return null;
    }
    var n = Browser.opCount();
    var minX = 99999;
    var minY = 99999;
    var maxX = -99999;
    var maxY = -99999;
    var found = false;
    var i;
    for (i = 0; i < n; i++) {
        var op = Browser.getOp(i);
        if (op.nodeId !== fid) {
            continue;
        }
        found = true;
        var x1 = op.x;
        var y1 = op.y;
        var ow = op.w;
        var oh = op.h;
        if (op.kind === "img") {
            if (ow <= 0) {
                ow = 48;
            }
            if (oh <= 0) {
                oh = 48;
            }
        }
        var x2 = op.x + ow;
        var y2 = op.y + oh;
        if (x1 < minX) {
            minX = x1;
        }
        if (y1 < minY) {
            minY = y1;
        }
        if (x2 > maxX) {
            maxX = x2;
        }
        if (y2 > maxY) {
            maxY = y2;
        }
    }
    if (!found) {
        return null;
    }
    var w = maxX - minX;
    var h = maxY - minY;
    if (w < 4) {
        w = 4;
    }
    if (h < 4) {
        h = 14;
    }
    return { x: minX, y: minY, w: w, h: h };
}

function paintFocusRing(contentTop, bottom, sy, fid) {
    if (fid < 0) {
        return;
    }
    var b = focusBoundsDoc();
    if (b == null) {
        return;
    }
    var x = b.x;
    var y = b.y - sy + contentTop;
    var w = b.w;
    var h = b.h;
    if (!visibleInViewport(y, h, contentTop, bottom)) {
        return;
    }
    exports.drawBorder(x - 2, y - 1, w + 4, h + 2, C_ACCENT);
}

function clampScroll(viewportH, contentBottom) {
    var maxScroll = contentBottom > viewportH ? contentBottom - viewportH : 0;
    var sy = Browser.scrollY();
    if (sy < 0) {
        sy = 0;
    }
    if (sy > maxScroll) {
        sy = maxScroll;
    }
    Browser.scrollY(sy);
    return maxScroll;
}

exports.ensureFocusVisible = function (viewportH) {
    var b = focusBoundsDoc();
    if (b == null) {
        return;
    }
    var sy = Browser.scrollY();
    var top = b.y;
    var bot = b.y + b.h;
    if (top < sy) {
        Browser.scrollY(top);
    } else if (bot > sy + viewportH) {
        Browser.scrollY(bot - viewportH);
    }
    clampScroll(viewportH, Browser.contentBottom());
};

exports.drawBorder = function (x, y, w, h, color) {
    if (w <= 0 || h <= 0) {
        return;
    }
    Draw.line(x, y, x + w - 1, y, color);
    Draw.line(x + w - 1, y, x + w - 1, y + h - 1, color);
    Draw.line(x + w - 1, y + h - 1, x, y + h - 1, color);
    Draw.line(x, y + h - 1, x, y, color);
};

/** Paint all ops shifted by {@code contentTop} on screen (after scroll). */
exports.paintOps = function (contentTop) {
    var H = Screen.height;
    var footerH = 16;
    var bottom = H - footerH;
    var viewportH = bottom - contentTop + 1;
    if (viewportH < 1) {
        viewportH = 1;
    }
    var sy = Browser.scrollY();
    var fid = Browser.focusNodeId();
    var n = Browser.opCount();
    var pageBg = pageBackgroundColor();

    // Always fill the visible content area (scroll can move document bg off-screen).
    Draw.rect(0, contentTop, Screen.width, viewportH, pageBg);

    var rects = [];
    var texts = [];
    var imgs = [];
    var i;
    for (i = 0; i < n; i++) {
        var op = Browser.getOp(i);
        var k = op.kind;
        if (k === "rect" || k === "border") {
            rects.push(op);
        } else if (k === "text") {
            texts.push(op);
        } else if (k === "img") {
            imgs.push(op);
        }
    }
    var groups = [rects, texts, imgs];
    var g;
    for (g = 0; g < groups.length; g++) {
        var ops = groups[g];
        for (i = 0; i < ops.length; i++) {
            var op = ops[i];
            var k = op.kind;
            var x = op.x;
            var y = op.y - sy + contentTop;
            var w = op.w;
            var h = op.h;
            var clipH = h > 0 ? h : (k === "img" ? 48 : 0);
            if (!visibleInViewport(y, clipH, contentTop, bottom)) {
                continue;
            }

            if (k === "rect") {
                if (op.x === 0 && op.y === 0 && op.h >= Browser.contentBottom()) {
                    continue;
                }
                var fill = layoutFillColor(op.color);
                if (fill == null) {
                    continue;
                }
                var cr = clipToViewport(x, y, w, h, contentTop, bottom);
                if (cr == null) {
                    continue;
                }
                Draw.rect(cr.x, cr.y, cr.w, cr.h, fill);
            } else if (k === "border") {
                var bc = layoutFillColor(op.color);
                if (bc == null) {
                    bc = Color.new(96, 128, 160);
                }
                exports.drawBorder(x, y, w, h, bc);
            } else if (k === "text") {
                var font = getFont(op.fontFace, op.fontStyle, op.fontSize);
                font.color = layoutColor(op.color);
                var text = op.text == null ? "" : op.text.toString();
                if (text.length === 0) {
                    continue;
                }
                font.print(text, x, y);
            } else if (k === "img") {
                var img = getImage(op.imgSrc);
                var dw = w > 0 ? w : 48;
                var dh = h > 0 ? h : 48;
                if (img) {
                    img.draw(x, y);
                } else {
                    var ir = clipToViewport(x, y, dw, dh, contentTop, bottom);
                    if (ir != null) {
                        Draw.rect(ir.x, ir.y, ir.w, ir.h, C_PANEL);
                        exports.drawBorder(ir.x, ir.y, ir.w, ir.h, Color.new(100, 120, 160));
                    }
                }
            }
        }
    }

    paintFocusRing(contentTop, bottom, sy, fid);
};

/**
 * @param {object} nav - { loadAppHtml, loadHttp, allowUrl }
 */
exports.handleActivate = function (nav, act) {
    if (act == null || act.url == null) {
        return;
    }
    var url = act.url.toString();
    if (!nav.allowUrl(url)) {
        return;
    }
    if (url.indexOf("http://") === 0 || url.indexOf("https://") === 0) {
        nav.loadHttp(url, act.method, act.body);
        return;
    }
    if (url.indexOf("app://") === 0) {
        if (nav.loadAppHtml) {
            nav.loadAppHtml(url);
        }
    }
};

exports.loadHttpDocument = function (url, method, bodyStr, doPush) {
    if (doPush === undefined) {
        doPush = true;
    }
    httpLoading = url;
    console.log("HTTP " + (method != null ? method : "GET") + " " + url);
    var req = new Request();
    var onOk = finishHttpLoad(url, doPush);
    var onErr = failHttpLoad(url);
    if (method === "POST") {
        req.post(url, bodyStr != null ? bodyStr : "").then(onOk, onErr);
    } else {
        req.get(url).then(onOk, onErr);
    }
};

/**
 * One frame: Pad input, navigation, scroll clamp, draw.
 * @param {object} nav - callbacks and security; see browser.js
 * @returns {boolean} true if the caller should exit (e.g. GAME_B back)
 */
exports.frame = function (nav) {
    var H = Screen.height;
    var headerH = 22;
    var footerH = 16;
    var viewportH = H - headerH - footerH;
    if (viewportH < 24) {
        viewportH = 24;
    }

    if (Pad.justPressed(Pad.GAME_B)) {
        if (nav && nav.onBack) {
            nav.onBack();
        }
        return true;
    }
    if (Pad.justPressed(Pad.LEFT)) {
        var uBack = Browser.historyBack();
        if (uBack != null && nav && nav.goHistoryUrl) {
            nav.goHistoryUrl(uBack.toString());
        }
    }
    if (Pad.justPressed(Pad.RIGHT)) {
        var uFwd = Browser.historyForward();
        if (uFwd != null && nav && nav.goHistoryUrl) {
            nav.goHistoryUrl(uFwd.toString());
        }
    }
    if (Pad.justPressed(Pad.UP)) {
        Browser.scrollBy(-24, viewportH);
    }
    if (Pad.justPressed(Pad.DOWN)) {
        Browser.scrollBy(24, viewportH);
    }
    if (Pad.justPressed(Pad.GAME_C)) {
        Browser.moveFocus(-1);
        exports.ensureFocusVisible(viewportH);
    }
    if (Pad.justPressed(Pad.GAME_D)) {
        Browser.moveFocus(1);
        exports.ensureFocusVisible(viewportH);
    }
    if (Pad.justPressed(Pad.FIRE)) {
        var act = Browser.activate();
        exports.handleActivate(nav, act);
    }

    clampScroll(viewportH, Browser.contentBottom());

    Screen.clear(C_BG);
    Draw.rect(0, 0, Screen.width, headerH, C_PANEL);
    var hdr = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_SMALL);
    hdr.color = C_TEXT;
    hdr.print("HTML browser (layout in Java)", 4, 4);

    exports.paintOps(headerH);

    drawLoadingBanner(headerH);

    var foot = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    foot.color = Color.new(140, 160, 200);
    foot.print("B back  LR hist  UD scroll  CD focus  FIRE go", 2, H - 14);

    Screen.update();
    return false;
};
