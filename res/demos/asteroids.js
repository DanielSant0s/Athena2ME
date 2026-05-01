// Asteroids showcase: vector ship + asteroids with screen wrap and bullets.

var W = Screen.width;
var H = Screen.height;

var fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
var fontSmall = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
var fontBig = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);

var BG = Color.new(2, 4, 12);
var STAR = Color.new(60, 75, 110);
var SHIP = Color.new(220, 235, 255);
var SHIP_THRUST = Color.new(255, 160, 60);
var BULLET = Color.new(255, 240, 120);
var ROCK = Color.new(180, 195, 220);
var ROCK_HI = Color.new(230, 240, 255);
var TEXT = Color.new(235, 245, 255);
var DIM = Color.new(120, 145, 185);
var ACCENT = Color.new(0, 220, 255);
var DANGER = Color.new(255, 80, 100);

var DEG_TO_RAD = Math.PI / 180.0;

var SHIP_RADIUS = 7;
var SHIP_TURN = 8;
var SHIP_THRUST_ACC = 0.25;
var SHIP_FRICTION = 0.985;
var SHIP_MAX_SPEED = 4.5;

var BULLET_CAP = 12;
var BULLET_SPEED = 6.0;
var BULLET_LIFE = 60;

var ROCK_CAP = 18;
var ROCK_BIG = 0;
var ROCK_MED = 1;
var ROCK_SMALL = 2;
var ROCK_RADIUS = [16, 10, 6];
var ROCK_SCORE = [20, 50, 100];
var ROCK_SPEED_BASE = [1.1, 1.6, 2.2];

var STAR_COUNT = 24;
var starX = new Int32Array(STAR_COUNT);
var starY = new Int32Array(STAR_COUNT);

var bx = new Float32Array(BULLET_CAP);
var by = new Float32Array(BULLET_CAP);
var bvx = new Float32Array(BULLET_CAP);
var bvy = new Float32Array(BULLET_CAP);
var blife = new Int32Array(BULLET_CAP);

var rx = new Float32Array(ROCK_CAP);
var ry = new Float32Array(ROCK_CAP);
var rvx = new Float32Array(ROCK_CAP);
var rvy = new Float32Array(ROCK_CAP);
var rsize = new Int32Array(ROCK_CAP);
var ralive = new Int32Array(ROCK_CAP);

var shipX = 0;
var shipY = 0;
var shipVX = 0;
var shipVY = 0;
var shipAngle = 0;
var shipCos = 1;
var shipSin = 0;

var score = 0;
var lives = 3;
var level = 1;
var fireCooldown = 0;
var invuln = 0;
var gameOver = false;
var frame = 0;
var returnToMenu = null;

function refreshShipDirection() {
    var rad = shipAngle * DEG_TO_RAD;
    shipCos = Math.cos(rad);
    shipSin = Math.sin(rad);
}

function spawnRock(size, atX, atY, vx, vy) {
    var k;
    for (k = 0; k < ROCK_CAP; k++) {
        if (ralive[k] === 0) {
            rsize[k] = size;
            rx[k] = atX;
            ry[k] = atY;
            rvx[k] = vx;
            rvy[k] = vy;
            ralive[k] = 1;
            return k;
        }
    }
    return -1;
}

function spawnLevel(n) {
    var k;
    for (k = 0; k < n; k++) {
        var ang = Math.random() * Math.PI * 2.0;
        var sp = ROCK_SPEED_BASE[ROCK_BIG] + Math.random() * 0.6;
        var atEdge = Math.random();
        var sx;
        var sy;
        if (atEdge < 0.25) {
            sx = 4;
            sy = Math.random() * H;
        } else if (atEdge < 0.5) {
            sx = W - 4;
            sy = Math.random() * H;
        } else if (atEdge < 0.75) {
            sx = Math.random() * W;
            sy = 4;
        } else {
            sx = Math.random() * W;
            sy = H - 4;
        }
        spawnRock(ROCK_BIG, sx, sy, Math.cos(ang) * sp, Math.sin(ang) * sp);
    }
}

function resetShip() {
    shipX = W / 2;
    shipY = H / 2;
    shipVX = 0;
    shipVY = 0;
    shipAngle = -90;
    refreshShipDirection();
    invuln = 60;
}

function resetGame() {
    score = 0;
    lives = 3;
    level = 1;
    gameOver = false;
    fireCooldown = 0;
    var k;
    for (k = 0; k < BULLET_CAP; k++) blife[k] = 0;
    for (k = 0; k < ROCK_CAP; k++) ralive[k] = 0;
    for (k = 0; k < STAR_COUNT; k++) {
        starX[k] = (Math.random() * W) | 0;
        starY[k] = (Math.random() * H) | 0;
    }
    resetShip();
    spawnLevel(3);
}

function wrap(value, max) {
    while (value < 0) value += max;
    while (value >= max) value -= max;
    return value;
}

function fire() {
    if (fireCooldown > 0 || gameOver) {
        return;
    }
    var k;
    for (k = 0; k < BULLET_CAP; k++) {
        if (blife[k] === 0) {
            bx[k] = shipX + shipCos * SHIP_RADIUS;
            by[k] = shipY + shipSin * SHIP_RADIUS;
            bvx[k] = shipCos * BULLET_SPEED;
            bvy[k] = shipSin * BULLET_SPEED;
            blife[k] = BULLET_LIFE;
            fireCooldown = 6;
            return;
        }
    }
}

function thrust() {
    var nvx = shipVX + shipCos * SHIP_THRUST_ACC;
    var nvy = shipVY + shipSin * SHIP_THRUST_ACC;
    var sp = Math.sqrt(nvx * nvx + nvy * nvy);
    if (sp > SHIP_MAX_SPEED) {
        nvx = nvx * SHIP_MAX_SPEED / sp;
        nvy = nvy * SHIP_MAX_SPEED / sp;
    }
    shipVX = nvx;
    shipVY = nvy;
}

function updateShip() {
    if (Pad.pressed(Pad.LEFT)) {
        shipAngle -= SHIP_TURN;
        refreshShipDirection();
    }
    if (Pad.pressed(Pad.RIGHT)) {
        shipAngle += SHIP_TURN;
        refreshShipDirection();
    }
    var thrusting = Pad.pressed(Pad.UP);
    if (thrusting) {
        thrust();
    } else {
        shipVX = shipVX * SHIP_FRICTION;
        shipVY = shipVY * SHIP_FRICTION;
    }
    shipX = wrap(shipX + shipVX, W);
    shipY = wrap(shipY + shipVY, H);
    if (Pad.justPressed(Pad.FIRE)) {
        fire();
    }
    if (fireCooldown > 0) fireCooldown--;
    if (invuln > 0) invuln--;
    return thrusting;
}

function updateBullets() {
    var k;
    for (k = 0; k < BULLET_CAP; k++) {
        if (blife[k] === 0) continue;
        bx[k] = wrap(bx[k] + bvx[k], W);
        by[k] = wrap(by[k] + bvy[k], H);
        blife[k] = blife[k] - 1;
    }
}

function updateRocks() {
    var k;
    for (k = 0; k < ROCK_CAP; k++) {
        if (ralive[k] === 0) continue;
        rx[k] = wrap(rx[k] + rvx[k], W);
        ry[k] = wrap(ry[k] + rvy[k], H);
    }
}

function destroyRock(k) {
    var size = rsize[k];
    score += ROCK_SCORE[size];
    ralive[k] = 0;
    if (size >= ROCK_SMALL) return;
    var nx = rx[k];
    var ny = ry[k];
    var nextSize = size + 1;
    var s;
    for (s = 0; s < 2; s++) {
        var ang = Math.random() * Math.PI * 2.0;
        var sp = ROCK_SPEED_BASE[nextSize] + Math.random() * 0.7;
        spawnRock(nextSize, nx, ny, Math.cos(ang) * sp, Math.sin(ang) * sp);
    }
}

function rocksAlive() {
    var n = 0;
    var k;
    for (k = 0; k < ROCK_CAP; k++) {
        if (ralive[k] !== 0) n++;
    }
    return n;
}

function checkBulletHits() {
    var k;
    var j;
    for (k = 0; k < BULLET_CAP; k++) {
        if (blife[k] === 0) continue;
        var bxp = bx[k];
        var byp = by[k];
        for (j = 0; j < ROCK_CAP; j++) {
            if (ralive[j] === 0) continue;
            var dxp = bxp - rx[j];
            var dyp = byp - ry[j];
            var rad = ROCK_RADIUS[rsize[j]];
            if (dxp * dxp + dyp * dyp <= rad * rad) {
                blife[k] = 0;
                destroyRock(j);
                break;
            }
        }
    }
}

function checkShipHit() {
    if (invuln > 0 || gameOver) return;
    var j;
    for (j = 0; j < ROCK_CAP; j++) {
        if (ralive[j] === 0) continue;
        var dxp = shipX - rx[j];
        var dyp = shipY - ry[j];
        var rad = ROCK_RADIUS[rsize[j]] + SHIP_RADIUS - 2;
        if (dxp * dxp + dyp * dyp <= rad * rad) {
            lives--;
            if (lives <= 0) {
                gameOver = true;
            } else {
                resetShip();
            }
            return;
        }
    }
}

function maybeAdvanceLevel() {
    if (rocksAlive() > 0) return;
    level++;
    spawnLevel(2 + level);
}

function drawStars() {
    var k;
    for (k = 0; k < STAR_COUNT; k++) {
        Draw.rect(starX[k], starY[k], 1, 1, STAR);
    }
}

function drawShip(thrusting) {
    if (gameOver) return;
    if (invuln > 0 && (frame & 3) < 2) return;
    var px = shipX | 0;
    var py = shipY | 0;
    var nx = -shipSin;
    var ny = shipCos;
    var tipX = (px + shipCos * SHIP_RADIUS) | 0;
    var tipY = (py + shipSin * SHIP_RADIUS) | 0;
    var leftX = (px - shipCos * (SHIP_RADIUS - 1) + nx * (SHIP_RADIUS - 2)) | 0;
    var leftY = (py - shipSin * (SHIP_RADIUS - 1) + ny * (SHIP_RADIUS - 2)) | 0;
    var rightX = (px - shipCos * (SHIP_RADIUS - 1) - nx * (SHIP_RADIUS - 2)) | 0;
    var rightY = (py - shipSin * (SHIP_RADIUS - 1) - ny * (SHIP_RADIUS - 2)) | 0;
    Draw.line(tipX, tipY, leftX, leftY, SHIP);
    Draw.line(tipX, tipY, rightX, rightY, SHIP);
    Draw.line(leftX, leftY, rightX, rightY, SHIP);
    if (thrusting && (frame & 1) === 0) {
        var flameX = (px - shipCos * (SHIP_RADIUS + 4)) | 0;
        var flameY = (py - shipSin * (SHIP_RADIUS + 4)) | 0;
        Draw.line(leftX, leftY, flameX, flameY, SHIP_THRUST);
        Draw.line(rightX, rightY, flameX, flameY, SHIP_THRUST);
    }
}

function drawBullets() {
    var k;
    for (k = 0; k < BULLET_CAP; k++) {
        if (blife[k] === 0) continue;
        var px = bx[k] | 0;
        var py = by[k] | 0;
        Draw.rect(px - 1, py - 1, 2, 2, BULLET);
    }
}

function drawRock(px, py, rad, hi) {
    var s = (rad * 0.7) | 0;
    Draw.line(px - rad, py, px - s, py - s, hi);
    Draw.line(px - s, py - s, px, py - rad, hi);
    Draw.line(px, py - rad, px + s, py - s, hi);
    Draw.line(px + s, py - s, px + rad, py, hi);
    Draw.line(px + rad, py, px + s, py + s, hi);
    Draw.line(px + s, py + s, px, py + rad, hi);
    Draw.line(px, py + rad, px - s, py + s, hi);
    Draw.line(px - s, py + s, px - rad, py, hi);
}

function drawRocks() {
    var k;
    for (k = 0; k < ROCK_CAP; k++) {
        if (ralive[k] === 0) continue;
        var px = rx[k] | 0;
        var py = ry[k] | 0;
        var rad = ROCK_RADIUS[rsize[k]];
        drawRock(px, py, rad, rsize[k] === ROCK_BIG ? ROCK : ROCK_HI);
    }
}

function drawHud() {
    fontTitle.color = TEXT;
    fontTitle.print("Asteroids", 5, 4);
    fontSmall.color = ACCENT;
    fontSmall.print("Score " + score, 5, 22);
    fontSmall.color = DIM;
    var rightLbl = "Lvl " + level + "  Lives " + lives;
    fontSmall.print(rightLbl, W - fontSmall.getTextSize(rightLbl).width - 4, 22);
    fontSmall.color = DIM;
    fontSmall.print("UP thrust  L/R turn  FIRE shoot  GAME_B menu", 5, H - 16);
    if (gameOver) {
        var msg = "GAME OVER";
        var hint = "FIRE: restart";
        fontBig.color = DANGER;
        fontBig.print(msg, ((W - fontBig.getTextSize(msg).width) / 2) | 0, ((H / 2) - 18) | 0);
        fontSmall.color = TEXT;
        fontSmall.print(hint, ((W - fontSmall.getTextSize(hint).width) / 2) | 0, ((H / 2) + 6) | 0);
    }
}

exports.start = function (back) {
    returnToMenu = back;
    resetGame();
};

exports.frame = function () {
    frame++;

    if (Pad.justPressed(Pad.GAME_B)) {
        returnToMenu();
        return;
    }

    var thrusting = false;
    if (gameOver) {
        if (Pad.justPressed(Pad.FIRE)) {
            resetGame();
        }
    } else {
        thrusting = updateShip();
        updateBullets();
        updateRocks();
        checkBulletHits();
        checkShipHit();
        maybeAdvanceLevel();
    }

    Screen.clear(BG);
    drawStars();
    drawRocks();
    drawBullets();
    drawShip(thrusting);
    drawHud();
};
