
// =============================================================================
//  NEON SNAKE  —  Athena2ME ES6+ fork demo
//
//  Showcases every feature the modernized interpreter now supports:
//    * class / extends / super / static       * const / let
//    * arrow functions                         * template literals
//    * destructuring (array, object, params)   * for...of
//    * default parameters / rest parameters    * Array.map / filter / some
//    * Uint8Array / Int32Array (board occupancy + snake/particle buffers)
//
//  Performance targets (~200 MHz): static board in an offscreen layer (one
//  drawLayer per frame instead of hundreds of fillRects), cached HUD text,
//  snake as Int32Array ring. Particles: two Int32Array slabs (double-buffer step),
//  fixed-point subpixels (SUB); trig only at spawn, velocities via Math.round.
//
//  Input: Pad.addListener(…, Pad.JUST_PRESSED) — despacho após Pad.update, antes
//  do corpo de cada frame. Callbacks anónimos/arrow preservam escopo capturado.
// =============================================================================

// ---- canvas / layout --------------------------------------------------------

const W = Screen.width;
const H = Screen.height;

const CELL      = 8;
const HUD_H     = 20;
const BOARD_X0  = 3;
const BOARD_Y0  = HUD_H + 3;
const COLS      = Math.max(1, Math.floor((W - BOARD_X0 * 2) / CELL));
const ROWS      = Math.max(1, Math.floor((H - BOARD_Y0 - 3) / CELL));
const BOARD_W   = COLS * CELL;
const BOARD_H   = ROWS * CELL;
const BOARD_X1  = BOARD_X0 + BOARD_W;
const BOARD_Y1  = BOARD_Y0 + BOARD_H;

const BGW       = BOARD_W + 6;
const BGH       = BOARD_H + 6;

const SNAKE_CAP = COLS * ROWS;

// 0 = free, 1 = snake segment. Maintained incrementally by Snake.step (O(1) self-hit).
const boardOcc = new Uint8Array(COLS * ROWS);

// ---- palette (neon) ---------------------------------------------------------

const BG        = Color.new(6,  6,  14);
const BG_DEEP   = Color.new(2,  2,  8);
const GRID      = Color.new(24, 20, 48);
const BORDER_0  = Color.new(180, 60, 255);
const BORDER_1  = Color.new(90,  30, 140);
const BORDER_2  = Color.new(40,  14, 70);

const SNAKE_0   = Color.new(0,   255, 180);
const SNAKE_1   = Color.new(0,   150, 110);
const SNAKE_2   = Color.new(0,   70,  50);
const HEAD_0    = Color.new(180, 255, 220);
const HEAD_1    = Color.new(80,  200, 160);

const APPLE_0   = Color.new(255, 60,  150);
const APPLE_1   = Color.new(170, 25,  100);
const APPLE_2   = Color.new(70,  10,  40);
const APPLE_HI  = Color.new(255, 200, 230);

const SPARK_0   = Color.new(255, 220, 80);
const SPARK_1   = Color.new(255, 120, 30);

const TEXT_0    = Color.new(230, 240, 255);
const TEXT_1    = Color.new(130, 150, 200);
const TEXT_DIM  = Color.new(80,  90,  130);
const ACCENT_0  = Color.new(0,   240, 255);
const ACCENT_1  = Color.new(0,   120, 180);

// Hoisted: avoid per-star array allocation in Star.draw
const STAR_COLS = [TEXT_DIM, TEXT_1, ACCENT_1];

// Unpack packed cell to globals (avoid {x,y} alloc)
var unpackX = 0;
var unpackY = 0;
function unpackCellToGlobals(p) {
    unpackX = p % COLS;
    unpackY = (p / COLS) | 0;
}

// ---- fonts ------------------------------------------------------------------

const font_big   = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD,  Font.SIZE_LARGE);
const font_med   = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD,  Font.SIZE_MEDIUM);
const font_small = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

/** Pixel size of a string in this font (`javax.microedition.lcdui.Font` stringWidth + getHeight). */
function textSize(font, text) {
    return font.getTextSize(text);
}

/** X so *text* is horizontally centered on the full canvas width. */
function centerXForText(font, text) {
    return (W - textSize(font, text).width) / 2 | 0;
}

// ---- neon primitives --------------------------------------------------------

function glowRect(x, y, w, h, c0, c1, c2) {
    Draw.rect(x - 2, y - 2, w + 4, h + 4, c2);
    Draw.rect(x - 1, y - 1, w + 2, h + 2, c1);
    Draw.rect(x,     y,     w,     h,     c0);
}

function glowText(font, text, x, y, c0, c1) {
    font.color = c1;
    font.print(text, x + 1, y);
    font.print(text, x - 1, y);
    font.print(text, x, y + 1);
    font.print(text, x, y - 1);
    font.color = c0;
    font.print(text, x, y);
}

function hline(x, y, w, c) { Draw.rect(x, y, w, 1, c); }
function vline(x, y, h, c) { Draw.rect(x, y, 1, h, c); }

// ---- static board (offscreen) ----------------------------------------------

let boardBgLayer = null;

/** One-time grid + border; avoids per-frame fillRect storm on the board. */
function buildBoardBgLayer() {
    if (boardBgLayer) return;
    boardBgLayer = Screen.createLayer(BGW, BGH);
    if (!boardBgLayer) return;
    Screen.setLayer(boardBgLayer);
    Draw.rect(0, 0, BGW, BGH, BORDER_2);
    Draw.rect(1, 1, BGW - 2, BGH - 2, BORDER_1);
    Draw.rect(2, 2, BGW - 4, BGH - 4, BORDER_0);
    Draw.rect(3, 3, BGW - 6, BGH - 6, BG_DEEP);
    let y;
    let x;
    for (y = 0; y < ROWS; y = y + 2) {
        for (x = 0; x < COLS; x = x + 2) {
            Draw.rect(3 + x * CELL, 3 + y * CELL, 1, 1, GRID);
        }
    }
    Screen.setLayer(null);
}

/** Fallback if createLayer fails (low heap). */
function drawBoardFrameFallback() {
    Draw.rect(BOARD_X0 - 3, BOARD_Y0 - 3, BOARD_W + 6, BOARD_H + 6, BORDER_2);
    Draw.rect(BOARD_X0 - 2, BOARD_Y0 - 2, BOARD_W + 4, BOARD_H + 4, BORDER_1);
    Draw.rect(BOARD_X0 - 1, BOARD_Y0 - 1, BOARD_W + 2, BOARD_H + 2, BORDER_0);
    Draw.rect(BOARD_X0,     BOARD_Y0,     BOARD_W,     BOARD_H,     BG_DEEP);
    let y;
    let x;
    for (y = 0; y < ROWS; y = y + 2) {
        for (x = 0; x < COLS; x = x + 2) {
            Draw.rect(BOARD_X0 + x * CELL, BOARD_Y0 + y * CELL, 1, 1, GRID);
        }
    }
}

function blitBoardBg() {
    if (!boardBgLayer) buildBoardBgLayer();
    if (boardBgLayer) {
        Screen.drawLayer(boardBgLayer, BOARD_X0 - 3, BOARD_Y0 - 3);
    } else {
        drawBoardFrameFallback();
    }
}

// ---- HUD cache (text only when values change) --------------------------------

let hudLayer = null;
const hudSnap = { score: -1, best: -1, speed: -1 };

function rebuildHudLayer(score, best, speedLevel) {
    if (!hudLayer) {
        hudLayer = Screen.createLayer(W, HUD_H);
        if (!hudLayer) return false;
    }
    Screen.setLayer(hudLayer);
    Draw.rect(0, 0, W, HUD_H, BG_DEEP);
    hline(0, HUD_H, W, BORDER_2);
    hline(0, HUD_H - 1, W, BORDER_1);

    font_small.color = TEXT_DIM;
    font_small.print("SCORE", 4, 2);
    const bestX = W - 4 - textSize(font_small, "BEST").width;
    font_small.print("BEST",  bestX, 2);
    glowText(font_med, "" + score, 4,       9, ACCENT_0, ACCENT_1);
    const bestStr = "" + best;
    glowText(font_med, bestStr,  W - 4 - textSize(font_med, bestStr).width,  9, TEXT_0,   TEXT_1);

    let i;
    for (i = 0; i < 5; i++) {
        const col = i < speedLevel ? ACCENT_0 : TEXT_DIM;
        Draw.rect(W / 2 - 12 + i * 5, 8, 3, 6, col);
    }
    font_small.color = TEXT_DIM;
    const spdW = textSize(font_small, "SPD").width;
    font_small.print("SPD", (W - spdW) / 2 | 0, 1);
    Screen.setLayer(null);
    hudSnap.score = score;
    hudSnap.best = best;
    hudSnap.speed = speedLevel;
    return true;
}

function drawHudCached(score, best, speedLevel) {
    if (hudLayer &&
        hudSnap.score === score &&
        hudSnap.best === best &&
        hudSnap.speed === speedLevel) {
        Screen.drawLayer(hudLayer, 0, 0);
        return;
    }
    if (!rebuildHudLayer(score, best, speedLevel)) {
        Draw.rect(0, 0, W, HUD_H, BG_DEEP);
        hline(0, HUD_H, W, BORDER_2);
        hline(0, HUD_H - 1, W, BORDER_1);
        font_small.color = TEXT_DIM;
        font_small.print("SCORE", 4, 2);
        const bestX0 = W - 4 - textSize(font_small, "BEST").width;
        font_small.print("BEST",  bestX0, 2);
        glowText(font_med, "" + score, 4,       9, ACCENT_0, ACCENT_1);
        const bestStr0 = "" + best;
        glowText(font_med, bestStr0,  W - 4 - textSize(font_med, bestStr0).width,  9, TEXT_0,   TEXT_1);
        let i;
        for (i = 0; i < 5; i++) {
            const col = i < speedLevel ? ACCENT_0 : TEXT_DIM;
            Draw.rect(W / 2 - 12 + i * 5, 8, 3, 6, col);
        }
        font_small.color = TEXT_DIM;
        const spdW0 = textSize(font_small, "SPD").width;
        font_small.print("SPD", (W - spdW0) / 2 | 0, 1);
    } else {
        Screen.drawLayer(hudLayer, 0, 0);
    }
}

// ---- particles (interleaved Int32Array ×2, hard cap) --------------------------

const SUB = 8;
const PM_MAX = 24;
/** One slab: [sx,sy,vx,vy,life,maxLife,color] per particle */
const PM_STRIDE = 7;
const PM_SX = 0;
const PM_SY = 1;
const PM_VX = 2;
const PM_VY = 3;
const PM_LIFE = 4;
const PM_MAXL = 5;
const PM_COL = 6;

const RECT_STRIDE = 5;
const RECT_X = 0;
const RECT_Y = 1;
const RECT_W = 2;
const RECT_H = 3;
const RECT_COL = 4;

const pm0 = new Int32Array(PM_MAX * PM_STRIDE);
const pm1 = new Int32Array(PM_MAX * PM_STRIDE);
const pmRects = new Int32Array(PM_MAX * RECT_STRIDE);
let pm = pm0;
let pmOut = pm1;
let pmCount = 0;
let pmRectCount = 0;

function spawnBurst(cx, cy, col, n) {
    const TAU = Math.PI * 2;
    let i;
    for (i = 0; i < n && pmCount < PM_MAX; i++) {
        const ang = Math.random() * TAU;
        const mag = 16 + Math.random() * 16;
        const life = 14 + ((Math.random() * 10) | 0);
        const k = pmCount;
        pmCount = pmCount + 1;
        const b = k * PM_STRIDE;
        pm[b + PM_SX] = (cx * SUB) | 0;
        pm[b + PM_SY] = (cy * SUB) | 0;
        // Arredonda no spawn: |0 truncava ~0 e “congelava”; float no step custa no interpretador.
        pm[b + PM_VX] = Math.round(Math.cos(ang) * mag);
        pm[b + PM_VY] = Math.round(Math.sin(ang) * mag - 8);
        pm[b + PM_LIFE] = life;
        pm[b + PM_MAXL] = life;
        pm[b + PM_COL] = col | 0;
        if (pmRectCount < PM_MAX) {
            const rb = pmRectCount * RECT_STRIDE;
            pmRects[rb + RECT_X] = cx | 0;
            pmRects[rb + RECT_Y] = cy | 0;
            pmRects[rb + RECT_W] = 3;
            pmRects[rb + RECT_H] = 3;
            pmRects[rb + RECT_COL] = col | 0;
            pmRectCount = pmRectCount + 1;
        }
    }
}

function stepParticles() {
    const src = pm;
    const dst = pmOut;
    const rects = pmRects;
    let w = 0;
    let rw = 0;
    let i;
    for (i = 0; i < pmCount; i++) {
        const b = i * PM_STRIDE;
        const sx = src[b + PM_SX];
        const sy = src[b + PM_SY];
        const vx = src[b + PM_VX];
        const vy = src[b + PM_VY];
        const life = src[b + PM_LIFE];
        const maxl = src[b + PM_MAXL];
        const col = src[b + PM_COL];
        const nsx = sx + vx;
        const nsy = sy + vy;
        const nvy = vy + 1;
        const nl = life - 1;
        if (nl > 0) {
            const wb = w * PM_STRIDE;
            dst[wb + PM_SX] = nsx;
            dst[wb + PM_SY] = nsy;
            dst[wb + PM_VX] = vx;
            dst[wb + PM_VY] = nvy;
            dst[wb + PM_LIFE] = nl;
            dst[wb + PM_MAXL] = maxl;
            dst[wb + PM_COL] = col;
            const s = nl > (maxl >> 1) ? 3 : 2;
            const rb = rw * RECT_STRIDE;
            rects[rb + RECT_X] = (nsx / SUB) | 0;
            rects[rb + RECT_Y] = (nsy / SUB) | 0;
            rects[rb + RECT_W] = s;
            rects[rb + RECT_H] = s;
            rects[rb + RECT_COL] = col;
            w = w + 1;
            rw = rw + 1;
        }
    }
    pmCount = w;
    pmRectCount = rw;
    pmOut = src;
    pm = dst;
}

function drawParticles() {
    const buf = pm;
    if (Draw.rects) {
        Draw.rects(pmRects, pmRectCount);
        return;
    }
    let i;
    for (i = 0; i < pmCount; i++) {
        const b = i * PM_STRIDE;
        const life = buf[b + PM_LIFE];
        if (life <= 0) continue;
        const maxl = buf[b + PM_MAXL];
        const s = life > (maxl >> 1) ? 3 : 2;
        Draw.rect((buf[b + PM_SX] / SUB) | 0, (buf[b + PM_SY] / SUB) | 0, s, s, buf[b + PM_COL]);
    }
}

function clearParticles() {
    pmCount = 0;
    pmRectCount = 0;
}

// ---- snake (ring buffer, no segment objects) -------------------------------

function packCell(x, y) {
    return (y * COLS + x) | 0;
}

class Snake {
    constructor() {
        this.buf = new Int32Array(SNAKE_CAP);
        this.tail = 0;
        this.len = 4;
        const cx = (COLS / 2) | 0;
        const cy = (ROWS / 2) | 0;
        this.buf[0] = packCell(cx - 3, cy);
        this.buf[1] = packCell(cx - 2, cy);
        this.buf[2] = packCell(cx - 1, cy);
        this.buf[3] = packCell(cx, cy);
        this.tail = 0;
        this.dx = 1;
        this.dy = 0;
        this.qdx = 1;
        this.qdy = 0;
        this.grow = 0;
        let i;
        for (i = 0; i < boardOcc.length; i++) boardOcc[i] = 0;
        for (i = 0; i < this.len; i++) {
            boardOcc[this.buf[(this.tail + i) % SNAKE_CAP]] = 1;
        }
    }

    headPacked() {
        return this.buf[(this.tail + this.len - 1) % SNAKE_CAP];
    }

    queue(dx, dy) {
        if (this.dx + dx === 0 && this.dy + dy === 0) return;
        this.qdx = dx;
        this.qdy = dy;
    }

    step() {
        this.dx = this.qdx;
        this.dy = this.qdy;
        const headP = this.headPacked();
        const hx = headP % COLS;
        const hy = (headP / COLS) | 0;
        const nx = hx + this.dx;
        const ny = hy + this.dy;

        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) return "wall";

        const np = packCell(nx, ny);
        if (this.grow > 0) {
            if (boardOcc[np]) return "self";
        } else {
            const tailP = this.buf[this.tail];
            if (np !== tailP && boardOcc[np]) return "self";
        }

        if (this.grow > 0) {
            this.grow = this.grow - 1;
            this.len = this.len + 1;
        } else {
            boardOcc[this.buf[this.tail]] = 0;
            this.tail = (this.tail + 1) % SNAKE_CAP;
        }

        const hi = (this.tail + this.len - 1) % SNAKE_CAP;
        this.buf[hi] = np;
        boardOcc[np] = 1;
        return "ok";
    }

    eatOn(apple) {
        const h = this.headPacked();
        return h === packCell(apple.x, apple.y);
    }

    draw() {
        let i;
        let idx = this.tail;
        for (i = this.len - 1; i >= 0; i = i - 1) {
            const p = this.buf[idx];
            idx = idx + 1;
            if (idx === SNAKE_CAP) idx = 0;
            const x = p % COLS;
            const y = (p / COLS) | 0;
            const px = BOARD_X0 + x * CELL + 1;
            const py = BOARD_Y0 + y * CELL + 1;
            const isHead = i === this.len - 1;
            if (isHead) glowRect(px, py, CELL - 2, CELL - 2, HEAD_0,  HEAD_1,  SNAKE_2);
            else        glowRect(px, py, CELL - 2, CELL - 2, SNAKE_0, SNAKE_1, SNAKE_2);
        }
        const hp = this.headPacked();
        const hx = hp % COLS;
        const hy = (hp / COLS) | 0;
        const hpx = BOARD_X0 + hx * CELL;
        const hpy = BOARD_Y0 + hy * CELL;
        const cx = hpx + CELL / 2 + this.dx * 2;
        const cy = hpy + CELL / 2 + this.dy * 2;
        Draw.rect(cx - 1, cy - 1, 2, 2, BG_DEEP);
    }
}

// ---- apple ------------------------------------------------------------------

class Apple {
    constructor() { this.x = 0; this.y = 0; this.respawn(); }

    respawn() {
        let attempts;
        for (attempts = 0; attempts < 40; attempts++) {
            const rx = (Math.random() * COLS) | 0;
            const ry = (Math.random() * ROWS) | 0;
            if (boardOcc[ry * COLS + rx] === 0) {
                this.x = rx;
                this.y = ry;
                return;
            }
        }
        let y;
        let x;
        for (y = 0; y < ROWS; y++) {
            for (x = 0; x < COLS; x++) {
                if (boardOcc[y * COLS + x] === 0) {
                    this.x = x;
                    this.y = y;
                    return;
                }
            }
        }
    }

    drawBaked(appleBaked, frame) {
        const pulse = (frame >> 2) & 3;
        const fat = pulse === 1 || pulse === 3 ? 1 : 0;
        const L = appleBaked[fat];
        if (L) {
            const sx = BOARD_X0 + this.x * CELL - 1;
            const sy = BOARD_Y0 + this.y * CELL - 1;
            Screen.drawLayer(L, sx, sy);
        } else {
            this.drawProcedural(frame);
        }
    }

    drawProcedural(frame) {
        const pulse = (frame >> 2) & 3;
        const px = BOARD_X0 + this.x * CELL + 1;
        const py = BOARD_Y0 + this.y * CELL + 1;
        const fat = pulse === 1 || pulse === 3 ? 1 : 0;
        glowRect(px - fat, py - fat, CELL - 2 + fat * 2, CELL - 2 + fat * 2,
                 APPLE_0, APPLE_1, APPLE_2);
        Draw.rect(px + 1, py + 1, 2, 2, APPLE_HI);
    }
}

// ---- menu / title stars -----------------------------------------------------

class Star {
    constructor() { this.reset(true); }
    reset(init) {
        this.x = (Math.random() * W) | 0;
        this.y = init ? (Math.random() * H) | 0 : -2;
        this.sp = 1 + Math.random() * 2;
        this.c  = (Math.random() * 3) | 0;
    }
    step() {
        this.y = this.y + this.sp;
        if (this.y > H + 2) this.reset(false);
    }
    draw() {
        Draw.rect(this.x | 0, this.y | 0, 1, 1, STAR_COLS[this.c | 0]);
    }
}

const stars = [];
for (let i = 0; i < 28; i++) stars.push(new Star());

function drawStars() {
    let i;
    for (i = 0; i < stars.length; i++) {
        stars[i].step();
        stars[i].draw();
    }
}

// ---- state machine ----------------------------------------------------------

const STATE_MENU  = 0;
const STATE_READY = 1;
const STATE_PLAY  = 2;
const STATE_PAUSE = 3;
const STATE_OVER  = 4;

const MENU_ITEMS = ["PLAY", "SPEED: ", "EXIT"];
const SPEED_LABELS = ["SLOW", "NORMAL", "FAST", "INSANE"];
const SPEED_TICKS  = [9, 6, 4, 2];

/** Pre-baked apple sprite (fat 0 / 1); fallback if createLayer fails */
let appleBaked = [null, null];

function buildAppleBakedLayers() {
    let fat;
    for (fat = 0; fat < 2; fat = fat + 1) {
        if (appleBaked[fat]) continue;
        const L = Screen.createLayer(CELL + 4, CELL + 4);
        if (!L) return;
        appleBaked[fat] = L;
        Screen.setLayer(L);
        // Offscreen buffer defaults to white on MIDP; fill with the cell background
        // (same as the board interior) or drawLayer shows a light halo.
        Draw.rect(0, 0, CELL + 4, CELL + 4, BG_DEEP);
        const px = 2;
        const py = 2;
        glowRect(px - fat, py - fat, CELL - 2 + fat * 2, CELL - 2 + fat * 2, APPLE_0, APPLE_1, APPLE_2);
        Draw.rect(px + 1, py + 1, 2, 2, APPLE_HI);
        Screen.setLayer(null);
    }
}

const game = {
    state: STATE_MENU,
    frame: 0,
    menuSel: 0,
    speedIdx: 1,
    score: 0,
    best: 0,
    tick: 0,
    snake: null,
    apple: null,
    flashDeath: 0,
};

function startGame() {
    buildBoardBgLayer();
    buildAppleBakedLayers();
    game.snake = new Snake();
    game.apple = new Apple();
    game.apple.respawn();
    game.score = 0;
    game.tick = 0;
    game.flashDeath = 0;
    game.state = STATE_READY;
    clearParticles();
    hudSnap.score = -1;
}

// ---- drawing screens --------------------------------------------------------

function drawTitle(frame) {
    const t = (frame >> 1) % 24;
    const hop = t < 12 ? t : 24 - t;
    const y = 38 + (hop >> 3);

    glowText(font_big, "NEON",  centerXForText(font_big, "NEON"), y,      ACCENT_0, ACCENT_1);
    glowText(font_big, "SNAKE", centerXForText(font_big, "SNAKE"), y + 2,  APPLE_0,  APPLE_1);

    const lineW = 96;
    const lineX = (W - lineW) / 2 | 0;
    hline(lineX, y + 22, lineW, BORDER_1);
    hline(lineX, y + 23, lineW, BORDER_0);
}

function drawMenu() {
    Screen.clear(BG);
    drawStars();
    drawTitle(game.frame);

    const base_y = H / 2 + 12;
    let i;
    for (i = 0; i < MENU_ITEMS.length; i++) {
        const selected = i === game.menuSel;
        let label = MENU_ITEMS[i];
        if (i === 1) label = `${label}${SPEED_LABELS[game.speedIdx]}`;

        const y = base_y + i * 18;
        if (selected) {
            Draw.rect(W / 2 - 54, y + 1, 108, 14, BORDER_2);
            Draw.rect(W / 2 - 53, y + 2, 106, 12, BORDER_1);
            const arrow = (game.frame >> 2) & 1 ? ">" : ">>";
            glowText(font_med, arrow, W / 2 - 58, y, ACCENT_0, ACCENT_1);
            glowText(font_med, label, W / 2 - 30, y, TEXT_0, ACCENT_1);
        } else {
            font_med.color = TEXT_1;
            font_med.print(label, W / 2 - 30, y);
        }
    }

    font_small.color = TEXT_DIM;
    font_small.print("ARROWS + FIRE", centerXForText(font_small, "ARROWS + FIRE"), H - 14);
}

function drawScenePlay() {
    const g = game;
    const flash = g.flashDeath > 0;
    if (flash) {
        Screen.clear(APPLE_2);
    } else {
        Draw.rect(0, HUD_H, W, H - HUD_H, BG);
    }
    drawHudCached(g.score, g.best, g.speedIdx + 2);
    blitBoardBg();
    g.apple.drawBaked(appleBaked, g.frame);
    g.snake.draw();
    drawParticles();
}

function drawPlay() {
    drawScenePlay();
    if (game.state === STATE_PAUSE) {
        Draw.rect(0, H / 2 - 16, W, 32, BG_DEEP);
        hline(0, H / 2 - 17, W, BORDER_1);
        hline(0, H / 2 + 16, W, BORDER_1);
        glowText(font_big, "PAUSED", centerXForText(font_big, "PAUSED"), H / 2 - 8, ACCENT_0, ACCENT_1);
    }
}

function drawReady() {
    drawScenePlay();
    if ((game.frame >> 3) & 1) {
        font_med.color = ACCENT_0;
        font_med.print("PRESS A DIRECTION", centerXForText(font_med, "PRESS A DIRECTION"), H / 2 - 6);
    }
}

function drawOver() {
    Screen.clear(BG_DEEP);
    drawHudCached(game.score, game.best, game.speedIdx + 2);
    blitBoardBg();
    game.snake.draw();
    drawParticles();

    const boxY = H / 2 - 34;
    Draw.rect(8, boxY,     W - 16, 68, BG_DEEP);
    hline(8, boxY,     W - 16, BORDER_0);
    hline(8, boxY + 67, W - 16, BORDER_0);
    vline(8,     boxY, 68, BORDER_1);
    vline(W - 9, boxY, 68, BORDER_1);

    glowText(font_big, "GAME OVER", centerXForText(font_big, "GAME OVER"), boxY + 6, APPLE_0, APPLE_1);
    font_med.color = TEXT_1;
    font_med.print("score",    W / 2 - 44, boxY + 28);
    font_med.print("best",     W / 2 - 44, boxY + 42);
    glowText(font_med, "" + game.score, W / 2 + 10, boxY + 28, ACCENT_0, ACCENT_1);
    glowText(font_med, "" + game.best,  W / 2 + 10, boxY + 42, TEXT_0,   TEXT_1);

    if ((game.frame >> 3) & 1) {
        font_small.color = ACCENT_0;
        font_small.print("FIRE TO RESTART", centerXForText(font_small, "FIRE TO RESTART"), boxY + 56);
    }
}

// ---- input (Pad.addListener, JUST_PRESSED; runs before the frame body) ------

var padEventIds = [];

function clearPadInputListeners() {
    let k;
    for (k = 0; k < padEventIds.length; k++) {
        Pad.clearListener(padEventIds[k]);
    }
    padEventIds = [];
}

function registerPadListener(mask, kind, fn) {
    const id = Pad.addListener(mask, kind, fn);
    if (id > 0) {
        padEventIds.push(id);
    }
}

function onDirectionJust(dx, dy) {
    const s = game.state;
    if (s === STATE_MENU) {
        if (dy !== 0) {
            if (dy < 0) game.menuSel = (game.menuSel + 2) % 3;
            else        game.menuSel = (game.menuSel + 1) % 3;
        } else if (dx !== 0 && game.menuSel === 1) {
            if (dx < 0) game.speedIdx = (game.speedIdx + 3) % 4;
            else        game.speedIdx = (game.speedIdx + 1) % 4;
        }
        return;
    }
    if (s === STATE_READY) {
        const sn = game.snake;
        sn.dx = dx; sn.dy = dy; sn.qdx = dx; sn.qdy = dy;
        game.state = STATE_PLAY;
        game.tick = 0;
        return;
    }
    if (s === STATE_PLAY) {
        game.snake.queue(dx, dy);
    }
}

function onFireJust() {
    if (game.state === STATE_MENU) {
        if (game.menuSel === 0) {
            startGame();
        } else if (game.menuSel === 2) {
            clearPadInputListeners();
            os.stopFrameLoop();
        }
        return;
    }
    if (game.state === STATE_PLAY) {
        game.state = STATE_PAUSE;
    } else if (game.state === STATE_PAUSE) {
        game.state = STATE_PLAY;
    } else if (game.state === STATE_OVER) {
        startGame();
    }
}

function onGameAJust() {
    if (game.state === STATE_PLAY) {
        game.state = STATE_PAUSE;
    } else if (game.state === STATE_PAUSE) {
        game.state = STATE_PLAY;
    }
}

function onGameBJust() {
    if (game.state === STATE_PAUSE || game.state === STATE_OVER) {
        game.state = STATE_MENU;
    }
}

function installPadInput() {
    const J = Pad.JUST_PRESSED;
    registerPadListener(Pad.UP,    J, () => { onDirectionJust(0, -1); });
    registerPadListener(Pad.DOWN,  J, () => { onDirectionJust(0, 1);  });
    registerPadListener(Pad.LEFT,  J, () => { onDirectionJust(-1, 0); });
    registerPadListener(Pad.RIGHT, J, () => { onDirectionJust(1, 0);  });
    registerPadListener(Pad.FIRE,  J, onFireJust);
    registerPadListener(Pad.GAME_A, J, onGameAJust);
    registerPadListener(Pad.GAME_B, J, onGameBJust);
}

function handlePlay() {
    const g = game;
    const sn = g.snake;

    const tickEvery = SPEED_TICKS[g.speedIdx];
    g.tick = g.tick + 1;
    if (g.tick < tickEvery) return;
    g.tick = 0;

    const r = sn.step();
    if (r === "wall" || r === "self") {
        g.state = STATE_OVER;
        g.flashDeath = 4;
        if (g.score > g.best) g.best = g.score;
        const hp = sn.headPacked();
        unpackCellToGlobals(hp);
        const bx = BOARD_X0 + unpackX * CELL + CELL / 2;
        const by = BOARD_Y0 + unpackY * CELL + CELL / 2;
        spawnBurst(bx, by, SPARK_0, 24);
        spawnBurst(bx, by, SNAKE_0, 12);
        return;
    }

    if (sn.eatOn(g.apple)) {
        g.score = g.score + 1;
        sn.grow = sn.grow + 1;
        const ap = g.apple;
        const ax = BOARD_X0 + ap.x * CELL + CELL / 2;
        const ay = BOARD_Y0 + ap.y * CELL + CELL / 2;
        spawnBurst(ax, ay, APPLE_0, 8);
        spawnBurst(ax, ay, SPARK_0, 6);
        ap.respawn();
    }
}

// ---- main loop --------------------------------------------------------------

os.setExitHandler(() => {
    clearPadInputListeners();
    os.stopFrameLoop();
});

// FPS meter (~60-frame average) — remove or comment out console in release builds if needed
const _fps = { t0: 0, acc: 0, st: STATE_MENU };
const _logFps = () => {
    const t = os.uptimeMillis();
    if (_fps.t0 === 0) { _fps.t0 = t; return; }
    _fps.acc = _fps.acc + 1;
    if (_fps.acc < 60) return;
    const dt = t - _fps.t0;
    if (dt > 0 && typeof console !== "undefined" && console.log) {
        console.log("FPS~" + ((60000 + (dt >> 1)) / dt | 0) + " st=" + _fps.st);
    }
    _fps.t0 = t;
    _fps.acc = 0;
};

// Do not create layers during script load (Graphics may not be ready); only in startGame / blitBoardBg.
installPadInput();
os.startFrameLoop(() => {
    const g = game;
    g.frame = g.frame + 1;
    if (g.flashDeath > 0) g.flashDeath = g.flashDeath - 1;
    stepParticles();

    const s = g.state;
    if (s !== _fps.st) { _fps.st = s; _fps.t0 = 0; _fps.acc = 0; }
    _logFps();

    if      (s === STATE_MENU)  { drawMenu(); }
    else if (s === STATE_READY) { drawReady(); }
    else if (s === STATE_PLAY)  { handlePlay();  drawPlay(); }
    else if (s === STATE_PAUSE) { drawPlay(); }
    else if (s === STATE_OVER)  { drawOver(); }
}, 30);
