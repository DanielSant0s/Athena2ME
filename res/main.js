// =============================================================================
//  NEON SNAKE  —  Athena2ME ES6+ fork demo
//
//  Showcases every feature the modernized interpreter now supports:
//    * class / extends / super / static       * const / let
//    * arrow functions                         * template literals
//    * destructuring (array, object, params)   * for...of
//    * default parameters / rest parameters    * Array.map / filter / some
//
//  Keep the visual language TIGHT and cheap: we only have ~1ms per frame on
//  an S40 mid-range. Every draw call matters.
// =============================================================================

// ---- canvas / layout --------------------------------------------------------

const W = Screen.width;
const H = Screen.height;

const CELL      = 8;
const HUD_H     = 20;
const BOARD_X0  = 3;
const BOARD_Y0  = HUD_H + 3;
const COLS      = Math.floor((W - BOARD_X0 * 2) / CELL);
const ROWS      = Math.floor((H - BOARD_Y0 - 3) / CELL);
const BOARD_W   = COLS * CELL;
const BOARD_H   = ROWS * CELL;
const BOARD_X1  = BOARD_X0 + BOARD_W;
const BOARD_Y1  = BOARD_Y0 + BOARD_H;

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

// ---- fonts ------------------------------------------------------------------

const font_big   = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD,  Font.SIZE_LARGE);
const font_med   = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD,  Font.SIZE_MEDIUM);
const font_small = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

// ---- neon primitives --------------------------------------------------------

// Three-layer faux-glow rect (outer, mid, core). Cheaper than real alpha
// blending — which the MIDP 2.0 canvas drops on setColor() anyway. We take
// the three colors as separate args to avoid allocating a wrapping array per
// call (this function runs ~30× per frame just for the snake body).
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

// ---- particles --------------------------------------------------------------

// Particles use sub-pixel coords (1/8 px). Velocities are sub-px/frame; sin/cos
// are real radians — scale with `mag` only (do not divide by 1000; that was for
// the old int-scaled trig).
const SUB = 8;

class Particle {
    constructor(sx, sy, svx, svy, life, col) {
        this.sx = sx; this.sy = sy;
        this.vx = svx; this.vy = svy;
        this.life = life;
        this.max  = life;
        this.col  = col;
    }
    step() {
        this.sx = this.sx + this.vx;
        this.sy = this.sy + this.vy;
        this.vy = this.vy + 1; // gravity (sub-px/frame^2)
        this.life = this.life - 1;
    }
    draw() {
        const s = this.life > this.max / 2 ? 3 : 2;
        Draw.rect(this.sx / SUB, this.sy / SUB, s, s, this.col);
    }
    alive() { return this.life > 0; }
}

const particles = [];

function spawnBurst(cx, cy, col, n = 10) {
    const TAU = Math.PI * 2;
    for (let i = 0; i < n; i++) {
        const ang = Math.random() * TAU;
        const mag = 16 + Math.random() * 16; // 16..32 sub-px/frame radial speed
        const vx  = Math.cos(ang) * mag;
        const vy  = Math.sin(ang) * mag - 8; // initial upward bias
        particles.push(new Particle(cx * SUB, cy * SUB, vx, vy,
                                    14 + Math.floor(Math.random() * 10), col));
    }
}

function stepParticles() {
    // In-place compaction. Cheaper than splice() and doesn't depend on it
    // (our RockScript fork doesn't implement Array.prototype.splice).
    let w = 0;
    const n = particles.length;
    for (let i = 0; i < n; i++) {
        const p = particles[i];
        p.step();
        if (p.alive()) {
            if (w !== i) particles[w] = p;
            w = w + 1;
        }
    }
    while (particles.length > w) particles.pop();
}

function drawParticles() {
    for (const p of particles) p.draw();
}

// ---- snake ------------------------------------------------------------------

class Snake {
    constructor() {
        const cx = (COLS / 2) | 0;
        const cy = (ROWS / 2) | 0;
        this.body = [
            { x: cx,     y: cy },
            { x: cx - 1, y: cy },
            { x: cx - 2, y: cy },
            { x: cx - 3, y: cy },
        ];
        this.dx = 1; this.dy = 0;
        this.qdx = 1; this.qdy = 0;
        this.grow = 0;
    }

    queue(dx, dy) {
        // forbid 180-degree reversal against the last *committed* direction
        if (this.dx + dx === 0 && this.dy + dy === 0) return;
        this.qdx = dx; this.qdy = dy;
    }

    step() {
        this.dx = this.qdx; this.dy = this.qdy;
        const head = this.body[0];
        const nx = head.x + this.dx;
        const ny = head.y + this.dy;

        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) return "wall";

        const bodyLen = this.body.length;
        // Ignore the tail: it will move out of the way this tick unless we grow.
        const limit = this.grow > 0 ? bodyLen : bodyLen - 1;
        for (let i = 0; i < limit; i++) {
            const s = this.body[i];
            if (s.x === nx && s.y === ny) return "self";
        }

        this.body.unshift({ x: nx, y: ny });
        if (this.grow > 0) this.grow = this.grow - 1;
        else this.body.pop();
        return "ok";
    }

    eatOn(apple) {
        const h = this.body[0];
        return h.x === apple.x && h.y === apple.y;
    }

    draw() {
        const n = this.body.length;
        for (let i = n - 1; i >= 0; i--) {
            const s = this.body[i];
            const px = BOARD_X0 + s.x * CELL + 1;
            const py = BOARD_Y0 + s.y * CELL + 1;
            const isHead = i === 0;
            if (isHead) glowRect(px, py, CELL - 2, CELL - 2, HEAD_0,  HEAD_1,  SNAKE_2);
            else        glowRect(px, py, CELL - 2, CELL - 2, SNAKE_0, SNAKE_1, SNAKE_2);
        }
        // little "eye" on the head to face the direction
        const h = this.body[0];
        const hx = BOARD_X0 + h.x * CELL;
        const hy = BOARD_Y0 + h.y * CELL;
        const cx = hx + CELL / 2 + this.dx * 2;
        const cy = hy + CELL / 2 + this.dy * 2;
        Draw.rect(cx - 1, cy - 1, 2, 2, BG_DEEP);
    }
}

// ---- apple ------------------------------------------------------------------

class Apple {
    constructor() { this.x = 0; this.y = 0; this.pulse = 0; this.respawn([]); }

    respawn(snake_body) {
        // Try random; if collides with snake, scan linearly for a free cell.
        for (let attempts = 0; attempts < 40; attempts++) {
            const rx = Math.floor(Math.random() * COLS);
            const ry = Math.floor(Math.random() * ROWS);
            if (!snake_body.some(s => s.x === rx && s.y === ry)) {
                this.x = rx; this.y = ry; return;
            }
        }
        for (let y = 0; y < ROWS; y++) {
            for (let x = 0; x < COLS; x++) {
                if (!snake_body.some(s => s.x === x && s.y === y)) {
                    this.x = x; this.y = y; return;
                }
            }
        }
    }

    draw(frame) {
        const pulse = (frame >> 2) & 3;       // 0..3, wraps every 16 frames
        const px = BOARD_X0 + this.x * CELL + 1;
        const py = BOARD_Y0 + this.y * CELL + 1;
        const fat = pulse === 1 || pulse === 3 ? 1 : 0;
        glowRect(px - fat, py - fat, CELL - 2 + fat * 2, CELL - 2 + fat * 2,
                 APPLE_0, APPLE_1, APPLE_2);
        // highlight dot
        Draw.rect(px + 1, py + 1, 2, 2, APPLE_HI);
    }
}

// ---- background -------------------------------------------------------------

function drawBoardFrame(frame) {
    // pulsating border
    const t = (frame >> 3) & 7;
    const pulse = t < 4 ? t : 7 - t;
    const pr = 70  + pulse * 25;
    const pg = 30  + pulse * 8;
    const pb = 140 + pulse * 20;
    const BORDER_PULSE = Color.new(pr, pg, pb);

    // outer glow
    Draw.rect(BOARD_X0 - 3, BOARD_Y0 - 3, BOARD_W + 6, BOARD_H + 6, BORDER_2);
    Draw.rect(BOARD_X0 - 2, BOARD_Y0 - 2, BOARD_W + 4, BOARD_H + 4, BORDER_1);
    Draw.rect(BOARD_X0 - 1, BOARD_Y0 - 1, BOARD_W + 2, BOARD_H + 2, BORDER_PULSE);
    Draw.rect(BOARD_X0,     BOARD_Y0,     BOARD_W,     BOARD_H,     BG_DEEP);

    // sparse grid dots (every 2 cells) — cheap, still reads as a grid
    for (let y = 0; y < ROWS; y = y + 2) {
        for (let x = 0; x < COLS; x = x + 2) {
            Draw.rect(BOARD_X0 + x * CELL, BOARD_Y0 + y * CELL, 1, 1, GRID);
        }
    }
}

function drawHUD(score, best, speedLevel) {
    // top bar
    Draw.rect(0, 0, W, HUD_H, BG_DEEP);
    hline(0, HUD_H, W, BORDER_2);
    hline(0, HUD_H - 1, W, BORDER_1);

    font_small.color = TEXT_DIM;
    font_small.print("SCORE", 4, 2);
    font_small.print("BEST",  W - 52, 2);
    glowText(font_med, "" + score, 4,       9, ACCENT_0, ACCENT_1);
    glowText(font_med, "" + best,  W - 52,  9, TEXT_0,   TEXT_1);

    // speed pips
    for (let i = 0; i < 5; i++) {
        const col = i < speedLevel ? ACCENT_0 : TEXT_DIM;
        Draw.rect(W / 2 - 12 + i * 5, 8, 3, 6, col);
    }
    font_small.color = TEXT_DIM;
    font_small.print("SPD", W / 2 - 12, 1);
}

// ---- menu / title stars -----------------------------------------------------

class Star {
    constructor() { this.reset(true); }
    reset(init) {
        this.x = Math.floor(Math.random() * W);
        this.y = init ? Math.floor(Math.random() * H) : -2;
        this.sp = 1 + Math.random() * 2;
        this.c  = Math.floor(Math.random() * 3);
    }
    step() {
        this.y = this.y + this.sp;
        if (this.y > H + 2) this.reset(false);
    }
    draw() {
        const cols = [TEXT_DIM, TEXT_1, ACCENT_1];
        Draw.rect(this.x | 0, this.y | 0, 1, 1, cols[this.c | 0]);
    }
}

const stars = [];
for (let i = 0; i < 28; i++) stars.push(new Star());

function drawStars() {
    for (const s of stars) { s.step(); s.draw(); }
}

// ---- state machine ----------------------------------------------------------

const STATE_MENU  = 0;
const STATE_READY = 1;   // snake visible but stationary, waiting for 1st input
const STATE_PLAY  = 2;
const STATE_PAUSE = 3;
const STATE_OVER  = 4;

const MENU_ITEMS = ["PLAY", "SPEED: ", "EXIT"];
const SPEED_LABELS = ["SLOW", "NORMAL", "FAST", "INSANE"];
const SPEED_TICKS  = [9, 6, 4, 2]; // frames per snake-step at 30 FPS

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
    game.snake = new Snake();
    game.apple = new Apple();
    game.apple.respawn(game.snake.body);
    game.score = 0;
    game.tick = 0;
    game.flashDeath = 0;
    game.state = STATE_READY;
    while (particles.length > 0) particles.pop();
}

// ---- drawing screens --------------------------------------------------------

function drawTitle(frame) {
    const t = (frame >> 1) % 24;
    const hop = t < 12 ? t : 24 - t;
    const y = 38 + (hop >> 3);

    glowText(font_big, "NEON",  W / 2 - 40, y,      ACCENT_0, ACCENT_1);
    glowText(font_big, "SNAKE", W / 2 - 8,  y + 2,  APPLE_0,  APPLE_1);

    // underline accent
    hline(W / 2 - 48, y + 22, 96, BORDER_1);
    hline(W / 2 - 48, y + 23, 96, BORDER_0);
}

function drawMenu() {
    Screen.clear(BG);
    drawStars();
    drawTitle(game.frame);

    const base_y = H / 2 + 12;
    for (let i = 0; i < MENU_ITEMS.length; i++) {
        const selected = i === game.menuSel;
        let label = MENU_ITEMS[i];
        if (i === 1) label = `${label}${SPEED_LABELS[game.speedIdx]}`;

        const y = base_y + i * 18;
        if (selected) {
            // selection bar
            Draw.rect(W / 2 - 54, y + 1, 108, 14, BORDER_2);
            Draw.rect(W / 2 - 53, y + 2, 106, 12, BORDER_1);
            // arrows that pulse
            const arrow = (game.frame >> 2) & 1 ? ">" : ">>";
            glowText(font_med, arrow, W / 2 - 58, y, ACCENT_0, ACCENT_1);
            glowText(font_med, label, W / 2 - 30, y, TEXT_0, ACCENT_1);
        } else {
            font_med.color = TEXT_1;
            font_med.print(label, W / 2 - 30, y);
        }
    }

    font_small.color = TEXT_DIM;
    font_small.print("ARROWS + FIRE", W / 2 - 36, H - 14);
}

function drawScene() {
    Screen.clear(game.flashDeath > 0 ? APPLE_2 : BG);
    drawHUD(game.score, game.best, game.speedIdx + 2);
    drawBoardFrame(game.frame);
    game.apple.draw(game.frame);
    game.snake.draw();
    drawParticles();
}

function drawPlay() {
    drawScene();
    if (game.state === STATE_PAUSE) {
        Draw.rect(0, H / 2 - 16, W, 32, BG_DEEP);
        hline(0, H / 2 - 17, W, BORDER_1);
        hline(0, H / 2 + 16, W, BORDER_1);
        glowText(font_big, "PAUSED", W / 2 - 28, H / 2 - 8, ACCENT_0, ACCENT_1);
    }
}

function drawReady() {
    drawScene();
    if ((game.frame >> 3) & 1) {
        font_med.color = ACCENT_0;
        font_med.print("PRESS A DIRECTION", W / 2 - 58, H / 2 - 6);
    }
}

function drawOver() {
    // Cheaper than calling drawScene() again: just render the last snake/apple
    // frame dimly and focus the eye on the game-over box.
    Screen.clear(BG_DEEP);
    drawHUD(game.score, game.best, game.speedIdx + 2);
    drawBoardFrame(game.frame);
    game.snake.draw();
    drawParticles();

    const boxY = H / 2 - 34;
    Draw.rect(8, boxY,     W - 16, 68, BG_DEEP);
    hline(8, boxY,     W - 16, BORDER_0);
    hline(8, boxY + 67, W - 16, BORDER_0);
    vline(8,     boxY, 68, BORDER_1);
    vline(W - 9, boxY, 68, BORDER_1);

    glowText(font_big, "GAME OVER", W / 2 - 46, boxY + 6, APPLE_0, APPLE_1);
    font_med.color = TEXT_1;
    font_med.print("score",    W / 2 - 44, boxY + 28);
    font_med.print("best",     W / 2 - 44, boxY + 42);
    glowText(font_med, "" + game.score, W / 2 + 10, boxY + 28, ACCENT_0, ACCENT_1);
    glowText(font_med, "" + game.best,  W / 2 + 10, boxY + 42, TEXT_0,   TEXT_1);

    if ((game.frame >> 3) & 1) {
        font_small.color = ACCENT_0;
        font_small.print("FIRE TO RESTART", W / 2 - 40, boxY + 56);
    }
}

// ---- input ------------------------------------------------------------------

function readDir() {
    if      (Pad.justPressed(Pad.UP))    return [0, -1];
    else if (Pad.justPressed(Pad.DOWN))  return [0,  1];
    else if (Pad.justPressed(Pad.LEFT))  return [-1, 0];
    else if (Pad.justPressed(Pad.RIGHT)) return [ 1, 0];
    return null;
}

function handleMenu() {
    if (Pad.justPressed(Pad.UP))   game.menuSel = (game.menuSel + 2) % 3;
    if (Pad.justPressed(Pad.DOWN)) game.menuSel = (game.menuSel + 1) % 3;
    if (game.menuSel === 1) {
        if (Pad.justPressed(Pad.LEFT))  game.speedIdx = (game.speedIdx + 3) % 4;
        if (Pad.justPressed(Pad.RIGHT)) game.speedIdx = (game.speedIdx + 1) % 4;
    }
    if (Pad.justPressed(Pad.FIRE)) {
        if      (game.menuSel === 0) startGame();
        else if (game.menuSel === 2) os.stopFrameLoop();
    }
}

function handleReady() {
    // wait for first direction press, then kick off actual play
    const dir = readDir();
    if (dir !== null) {
        const [dx, dy] = dir;
        game.snake.dx  = dx; game.snake.dy  = dy;
        game.snake.qdx = dx; game.snake.qdy = dy;
        game.state = STATE_PLAY;
        game.tick  = 0;
    }
}

function handlePlay() {
    if (Pad.justPressed(Pad.GAME_A) || Pad.justPressed(Pad.FIRE)) {
        game.state = STATE_PAUSE;
        return;
    }
    const dir = readDir();
    if (dir !== null) {
        const [dx, dy] = dir;
        game.snake.queue(dx, dy);
    }

    const tickEvery = SPEED_TICKS[game.speedIdx];
    game.tick = game.tick + 1;
    if (game.tick < tickEvery) return;
    game.tick = 0;

    const r = game.snake.step();
    if (r === "wall" || r === "self") {
        game.state = STATE_OVER;
        game.flashDeath = 4;
        if (game.score > game.best) game.best = game.score;
        const h = game.snake.body[0];
        const bx = BOARD_X0 + h.x * CELL + CELL / 2;
        const by = BOARD_Y0 + h.y * CELL + CELL / 2;
        spawnBurst(bx, by, SPARK_0, 24);
        spawnBurst(bx, by, SNAKE_0, 12);
        return;
    }

    if (game.snake.eatOn(game.apple)) {
        game.score = game.score + 1;
        game.snake.grow = game.snake.grow + 1;
        const ax = BOARD_X0 + game.apple.x * CELL + CELL / 2;
        const ay = BOARD_Y0 + game.apple.y * CELL + CELL / 2;
        spawnBurst(ax, ay, APPLE_0, 8);
        spawnBurst(ax, ay, SPARK_0, 6);
        game.apple.respawn(game.snake.body);
    }
}

function handlePause() {
    if (Pad.justPressed(Pad.FIRE) || Pad.justPressed(Pad.GAME_A)) {
        game.state = STATE_PLAY;
    }
    if (Pad.justPressed(Pad.GAME_B)) {
        game.state = STATE_MENU;
    }
}

function handleOver() {
    if (Pad.justPressed(Pad.FIRE)) startGame();
    else if (Pad.justPressed(Pad.GAME_B)) game.state = STATE_MENU;
}

// ---- main loop --------------------------------------------------------------

os.setExitHandler(() => os.stopFrameLoop());

os.startFrameLoop(() => {
    game.frame = game.frame + 1;
    if (game.flashDeath > 0) game.flashDeath = game.flashDeath - 1;
    stepParticles();

    const s = game.state;
    if      (s === STATE_MENU)  { handleMenu();  drawMenu(); }
    else if (s === STATE_READY) { handleReady(); drawReady(); }
    else if (s === STATE_PLAY)  { handlePlay();  drawPlay(); }
    else if (s === STATE_PAUSE) { handlePause(); drawPlay(); }
    else if (s === STATE_OVER)  { handleOver();  drawOver(); }
}, 60);
