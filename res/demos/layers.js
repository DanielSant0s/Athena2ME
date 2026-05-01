// Offscreen layer showcase: cached background + moving foreground/HUD.

var W = Screen.width;
var H = Screen.height;

var fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
var fontSmall = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

var BG = Color.new(4, 6, 18);
var TILE_A = Color.new(12, 20, 42);
var TILE_B = Color.new(18, 30, 62);
var LINE = Color.new(42, 75, 125);
var TEXT = Color.new(235, 245, 255);
var DIM = Color.new(120, 145, 185);
var ORANGE = Color.new(255, 155, 45);
var BLUE = Color.new(0, 185, 255);
var GREEN = Color.new(65, 240, 145);

var bgLayer = null;
var frame = 0;
var shipX = (W / 2) | 0;
var shipY = (H / 2) | 0;
var returnToMenu = null;
var HUD_BG = Color.new(5, 8, 20);

function buildBackground() {
    if (bgLayer) {
        return;
    }
    bgLayer = Screen.createLayer(W, H);
    if (!bgLayer) {
        return;
    }
    Screen.setLayer(bgLayer);
    Screen.clear(BG);
    var tile = 16;
    var y;
    var x;
    for (y = 0; y < H; y += tile) {
        for (x = 0; x < W; x += tile) {
            Draw.rect(x, y, tile, tile, ((x + y) / tile & 1) ? TILE_A : TILE_B);
        }
    }
    for (x = 0; x < W; x += 8) {
        Draw.line(x, 0, x, H, LINE);
    }
    for (y = 0; y < H; y += 8) {
        Draw.line(0, y, W, y, LINE);
    }
    Screen.setLayer(null);
}

function drawBackgroundFallback() {
    Screen.clear(BG);
    var y;
    var x;
    for (y = 0; y < H; y += 16) {
        for (x = 0; x < W; x += 16) {
            Draw.rect(x, y, 16, 16, ((x + y) / 16 & 1) ? TILE_A : TILE_B);
        }
    }
}

function drawShip(x, y) {
    Draw.triangle(x, y - 10, x - 8, y + 8, x + 8, y + 8, ORANGE);
    Draw.triangle(x, y - 5, x - 5, y + 6, x + 5, y + 6, BLUE);
    Draw.rect(x - 2, y + 8, 4, 5, GREEN);
}

exports.start = function (back) {
    returnToMenu = back;
    buildBackground();
};

exports.frame = function () {
    frame++;

    if (Pad.justPressed(Pad.GAME_B)) {
        returnToMenu();
        return;
    }
    if (Pad.pressed(Pad.LEFT)) shipX -= 2;
    if (Pad.pressed(Pad.RIGHT)) shipX += 2;
    if (Pad.pressed(Pad.UP)) shipY -= 2;
    if (Pad.pressed(Pad.DOWN)) shipY += 2;
    if (shipX < 10) shipX = 10;
    if (shipX > W - 10) shipX = W - 10;
    if (shipY < 28) shipY = 28;
    if (shipY > H - 15) shipY = H - 15;

    if (bgLayer) {
        Screen.clear(BG);
        Screen.drawLayer(bgLayer, -((frame / 2) & 15), 0);
        Screen.drawLayer(bgLayer, 16 - ((frame / 2) & 15), 0);
    } else {
        drawBackgroundFallback();
    }

    drawShip(shipX, shipY);

    Draw.rect(0, 0, W, 22, HUD_BG);
    fontTitle.color = TEXT;
    fontTitle.print("Layers + HUD", 5, 3);
    fontSmall.color = DIM;
    fontSmall.print("cached bg=" + (bgLayer ? "yes" : "no"), W - 84, 6);
    fontSmall.print("D-pad move, GAME_B menu", 5, H - 16);
};
