// Asteroids showcase: vector ship + asteroids with screen wrap and bullets.

const W = Screen.width;
const H = Screen.height;

const fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
const fontSmall = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
const fontBig = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);

const BG = Color.new(2, 4, 12);
const STAR = Color.new(60, 75, 110);
const SHIP = Color.new(220, 235, 255);
const SHIP_THRUST = Color.new(255, 160, 60);
const BULLET = Color.new(255, 240, 120);
const ROCK = Color.new(180, 195, 220);
const ROCK_HI = Color.new(230, 240, 255);
const TEXT = Color.new(235, 245, 255);
const DIM = Color.new(120, 145, 185);
const ACCENT = Color.new(0, 220, 255);
const DANGER = Color.new(255, 80, 100);

const DEG_TO_RAD = Math.PI / 180.0;

const SHIP_RADIUS = 7;
const SHIP_TURN = 8;
const SHIP_THRUST_ACC = 0.25;
const SHIP_FRICTION = 0.985;
const SHIP_MAX_SPEED = 4.5;

const BULLET_CAP = 12;
const BULLET_SPEED = 6.0;
const BULLET_LIFE = 60;

const ROCK_CAP = 18;
const ROCK_BIG = 0;
const ROCK_MED = 1;
const ROCK_SMALL = 2;
const ROCK_RADIUS = [16, 10, 6];
const ROCK_SCORE = [20, 50, 100];
const ROCK_SPEED_BASE = [1.1, 1.6, 2.2];

const STAR_COUNT = 24;
const starX = new Int32Array(STAR_COUNT);
const starY = new Int32Array(STAR_COUNT);

const bx = new Float32Array(BULLET_CAP);
const by = new Float32Array(BULLET_CAP);
const bvx = new Float32Array(BULLET_CAP);
const bvy = new Float32Array(BULLET_CAP);
const blife = new Int32Array(BULLET_CAP);

const rx = new Float32Array(ROCK_CAP);
const ry = new Float32Array(ROCK_CAP);
const rvx = new Float32Array(ROCK_CAP);
const rvy = new Float32Array(ROCK_CAP);
const rsize = new Int32Array(ROCK_CAP);
const ralive = new Int32Array(ROCK_CAP);

let shipX = 0;
let shipY = 0;
let shipVX = 0;
let shipVY = 0;
let shipAngle = 0;
let shipCos = 1;
let shipSin = 0;

let score = 0;
let lives = 3;
let level = 1;
let fireCooldown = 0;
let invuln = 0;
let gameOver = false;
let frame = 0;
let returnToMenu = null;

function refreshShipDirection() {
    let rad = shipAngle * DEG_TO_RAD;
    shipCos = Math.cos(rad);
    shipSin = Math.sin(rad);
}

function spawnRock(size, atX, atY, vx, vy) {
    let k;
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
    let k;
    for (k = 0; k < n; k++) {
        let ang = Math.random() * Math.PI * 2.0;
        let sp = ROCK_SPEED_BASE[ROCK_BIG] + Math.random() * 0.6;
        let atEdge = Math.random();
        let sx;
        let sy;
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
    let k;
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
    let k;
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
    let nvx = shipVX + shipCos * SHIP_THRUST_ACC;
    let nvy = shipVY + shipSin * SHIP_THRUST_ACC;
    let sp = Math.sqrt(nvx * nvx + nvy * nvy);
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
    let thrusting = Pad.pressed(Pad.UP);
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
    let k;
    for (k = 0; k < BULLET_CAP; k++) {
        if (blife[k] === 0) continue;
        bx[k] = wrap(bx[k] + bvx[k], W);
        by[k] = wrap(by[k] + bvy[k], H);
        blife[k] = blife[k] - 1;
    }
}

function updateRocks() {
    let k;
    for (k = 0; k < ROCK_CAP; k++) {
        if (ralive[k] === 0) continue;
        rx[k] = wrap(rx[k] + rvx[k], W);
        ry[k] = wrap(ry[k] + rvy[k], H);
    }
}

function destroyRock(k) {
    let size = rsize[k];
    score += ROCK_SCORE[size];
    ralive[k] = 0;
    if (size >= ROCK_SMALL) return;
    let nx = rx[k];
    let ny = ry[k];
    let nextSize = size + 1;
    let s;
    for (s = 0; s < 2; s++) {
        let ang = Math.random() * Math.PI * 2.0;
        let sp = ROCK_SPEED_BASE[nextSize] + Math.random() * 0.7;
        spawnRock(nextSize, nx, ny, Math.cos(ang) * sp, Math.sin(ang) * sp);
    }
}

function rocksAlive() {
    let n = 0;
    let k;
    for (k = 0; k < ROCK_CAP; k++) {
        if (ralive[k] !== 0) n++;
    }
    return n;
}

function checkBulletHits() {
    let k;
    let j;
    for (k = 0; k < BULLET_CAP; k++) {
        if (blife[k] === 0) continue;
        let bxp = bx[k];
        let byp = by[k];
        for (j = 0; j < ROCK_CAP; j++) {
            if (ralive[j] === 0) continue;
            let dxp = bxp - rx[j];
            let dyp = byp - ry[j];
            let rad = ROCK_RADIUS[rsize[j]];
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
    let j;
    for (j = 0; j < ROCK_CAP; j++) {
        if (ralive[j] === 0) continue;
        let dxp = shipX - rx[j];
        let dyp = shipY - ry[j];
        let rad = ROCK_RADIUS[rsize[j]] + SHIP_RADIUS - 2;
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
    let k;
    for (k = 0; k < STAR_COUNT; k++) {
        Draw.rect(starX[k], starY[k], 1, 1, STAR);
    }
}

function drawShip(thrusting) {
    if (gameOver) return;
    if (invuln > 0 && (frame & 3) < 2) return;
    let px = shipX | 0;
    let py = shipY | 0;
    let nx = -shipSin;
    let ny = shipCos;
    let tipX = (px + shipCos * SHIP_RADIUS) | 0;
    let tipY = (py + shipSin * SHIP_RADIUS) | 0;
    let leftX = (px - shipCos * (SHIP_RADIUS - 1) + nx * (SHIP_RADIUS - 2)) | 0;
    let leftY = (py - shipSin * (SHIP_RADIUS - 1) + ny * (SHIP_RADIUS - 2)) | 0;
    let rightX = (px - shipCos * (SHIP_RADIUS - 1) - nx * (SHIP_RADIUS - 2)) | 0;
    let rightY = (py - shipSin * (SHIP_RADIUS - 1) - ny * (SHIP_RADIUS - 2)) | 0;
    Draw.line(tipX, tipY, leftX, leftY, SHIP);
    Draw.line(tipX, tipY, rightX, rightY, SHIP);
    Draw.line(leftX, leftY, rightX, rightY, SHIP);
    if (thrusting && (frame & 1) === 0) {
        let flameX = (px - shipCos * (SHIP_RADIUS + 4)) | 0;
        let flameY = (py - shipSin * (SHIP_RADIUS + 4)) | 0;
        Draw.line(leftX, leftY, flameX, flameY, SHIP_THRUST);
        Draw.line(rightX, rightY, flameX, flameY, SHIP_THRUST);
    }
}

function drawBullets() {
    let k;
    for (k = 0; k < BULLET_CAP; k++) {
        if (blife[k] === 0) continue;
        let px = bx[k] | 0;
        let py = by[k] | 0;
        Draw.rect(px - 1, py - 1, 2, 2, BULLET);
    }
}

function drawRock(px, py, rad, hi) {
    let s = (rad * 0.7) | 0;
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
    let k;
    for (k = 0; k < ROCK_CAP; k++) {
        if (ralive[k] === 0) continue;
        let px = rx[k] | 0;
        let py = ry[k] | 0;
        let rad = ROCK_RADIUS[rsize[k]];
        drawRock(px, py, rad, rsize[k] === ROCK_BIG ? ROCK : ROCK_HI);
    }
}

function drawHud() {
    fontTitle.color = TEXT;
    fontTitle.print("Asteroids", 5, 4);
    fontSmall.color = ACCENT;
    fontSmall.print("Score " + score, 5, 22);
    fontSmall.color = DIM;
    let rightLbl = "Lvl " + level + "  Lives " + lives;
    fontSmall.print(rightLbl, W - fontSmall.getTextSize(rightLbl).width - 4, 22);
    fontSmall.color = DIM;
    fontSmall.print("UP thrust  L/R turn  FIRE shoot  GAME_B menu", 5, H - 16);
    if (gameOver) {
        let msg = "GAME OVER";
        let hint = "FIRE: restart";
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

    let thrusting = false;
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
