// Particle/input showcase: fixed-size typed arrays, no per-frame objects.

var W = Screen.width;
var H = Screen.height;

var fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
var fontSmall = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

var BG = Color.new(2, 4, 12);
var TEXT = Color.new(230, 245, 255);
var DIM = Color.new(105, 135, 180);
var CYAN = Color.new(0, 220, 255);
var MAGENTA = Color.new(255, 60, 170);
var GOLD = Color.new(255, 190, 45);
var GREEN = Color.new(60, 255, 150);

var MAX = 96;
var SCALE = 16;
var px = new Int32Array(MAX);
var py = new Int32Array(MAX);
var vx = new Int32Array(MAX);
var vy = new Int32Array(MAX);
var life = new Int32Array(MAX);
var tone = new Int32Array(MAX);
var cursorX = (W / 2) | 0;
var cursorY = (H / 2) | 0;
var nextSlot = 0;
var frame = 0;
var returnToMenu = null;

function particleColor(t, l) {
    if (l < 10) {
        return DIM;
    }
    if (t === 0) {
        return CYAN;
    }
    if (t === 1) {
        return MAGENTA;
    }
    if (t === 2) {
        return GOLD;
    }
    return GREEN;
}

function spawnOne(x, y, ax, ay, t) {
    var i = nextSlot;
    nextSlot++;
    if (nextSlot >= MAX) {
        nextSlot = 0;
    }
    px[i] = x * SCALE;
    py[i] = y * SCALE;
    vx[i] = ax;
    vy[i] = ay;
    life[i] = 32 + ((frame + i) & 15);
    tone[i] = t;
}

function burst(x, y) {
    var i;
    for (i = 0; i < 20; i++) {
        var a = (frame * 7 + i * 31) & 255;
        var sx = ((a % 17) - 8) * 2;
        var sy = (((a / 17) | 0) - 7) * 2;
        if (sx === 0 && sy === 0) {
            sy = -12;
        }
        spawnOne(x, y, sx, sy, i & 3);
    }
}

function updateParticles() {
    var i;
    for (i = 0; i < MAX; i++) {
        if (life[i] <= 0) {
            continue;
        }
        life[i]--;
        px[i] += vx[i];
        py[i] += vy[i];
        vy[i] += 1;
        if (py[i] > (H - 3) * SCALE) {
            py[i] = (H - 3) * SCALE;
            vy[i] = -((vy[i] * 3) / 5 | 0);
        }
    }
}

function drawParticles() {
    var i;
    for (i = 0; i < MAX; i++) {
        if (life[i] <= 0) {
            continue;
        }
        var x = (px[i] / SCALE) | 0;
        var y = (py[i] / SCALE) | 0;
        var s = life[i] > 24 ? 3 : 2;
        Draw.rect(x, y, s, s, particleColor(tone[i], life[i]));
    }
}

exports.start = function (back) {
    returnToMenu = back;
    burst(cursorX, cursorY);
};

exports.frame = function () {
    frame++;

    if (Pad.justPressed(Pad.GAME_B)) {
        returnToMenu();
        return;
    }
    if (Pad.pressed(Pad.LEFT)) {
        cursorX -= 3;
    }
    if (Pad.pressed(Pad.RIGHT)) {
        cursorX += 3;
    }
    if (Pad.pressed(Pad.UP)) {
        cursorY -= 3;
    }
    if (Pad.pressed(Pad.DOWN)) {
        cursorY += 3;
    }
    if (cursorX < 4) cursorX = 4;
    if (cursorX > W - 8) cursorX = W - 8;
    if (cursorY < 24) cursorY = 24;
    if (cursorY > H - 8) cursorY = H - 8;

    if (Pad.justPressed(Pad.FIRE) || (frame % 9) === 0) {
        burst(cursorX, cursorY);
    }

    updateParticles();

    Screen.clear(BG);
    drawParticles();
    Draw.rect(cursorX - 3, cursorY - 3, 7, 7, TEXT);
    Draw.rect(cursorX - 2, cursorY - 2, 5, 5, CYAN);

    fontTitle.color = TEXT;
    fontTitle.print("Particles + Input", 5, 5);
    fontSmall.color = DIM;
    fontSmall.print("D-pad move, FIRE burst, GAME_B menu", 5, H - 16);
};
