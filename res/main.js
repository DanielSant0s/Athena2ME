// Athena2ME demo launcher.
// D-pad: choose demo. Fire: launch. In demos, GAME_B returns to this menu.

var W = Screen.width;
var H = Screen.height;

var fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
var fontMenu = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
var fontSmall = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

var C_BG = Color.new(5, 8, 18);
var C_PANEL = Color.new(16, 24, 48);
var C_PANEL_2 = Color.new(8, 14, 30);
var C_TEXT = Color.new(235, 245, 255);
var C_DIM = Color.new(120, 145, 185);
var C_ACCENT = Color.new(0, 220, 255);
var C_HILITE = Color.new(255, 180, 60);

var demos = [
    {
        title: "Particles + Input",
        subtitle: "Pool-style typed arrays, sparks, D-pad emitter",
        path: "/demos/particles.js"
    },
    {
        title: "Layers + HUD",
        subtitle: "Offscreen layer cache, scrolling tiles, text HUD",
        path: "/demos/layers.js"
    },
    {
        title: "Render3D Cube",
        subtitle: "M3G/soft backend, light, UV mesh, moving camera",
        path: "/demos/render3d_cube.js"
    },
    {
        title: "PogoRoo 3D",
        subtitle: "Original M3G pogoroo.m3g gameplay demo",
        path: "/demos/pogoroo.js"
    },
    {
        title: "Asteroids",
        subtitle: "Vector ship, screen wrap, splitting rocks",
        path: "/demos/asteroids.js"
    }
];

var selected = 0;
var frame = 0;
var activeDemo = null;

function textWidth(font, s) {
    return font.getTextSize(s).width;
}

function centerText(font, s, y, color) {
    font.color = color;
    font.print(s, ((W - textWidth(font, s)) / 2) | 0, y);
}

function drawMenuItem(i, y) {
    var active = i === selected;
    var x = 8;
    var w = W - 16;
    var h = 32;
    Draw.rect(x, y, w, h, active ? C_PANEL : C_PANEL_2);
    Draw.rect(x, y, w, 1, active ? C_ACCENT : C_PANEL);
    Draw.rect(x, y + h - 1, w, 1, active ? C_HILITE : C_PANEL);

    fontMenu.color = active ? C_TEXT : C_DIM;
    fontMenu.print(demos[i].title, x + 8, y + 3);
    fontSmall.color = active ? C_ACCENT : C_DIM;
    fontSmall.print(demos[i].subtitle, x + 8, y + 19);
}

function startMenu() {
    activeDemo = null;
}

function launchSelected() {
    activeDemo = require(demos[selected].path);
    if (activeDemo.start) {
        activeDemo.start(startMenu);
    }
}

function drawMenuFrame() {
    frame++;

    if (Pad.justPressed(Pad.UP)) {
        selected--;
        if (selected < 0) {
            selected = demos.length - 1;
        }
    }
    if (Pad.justPressed(Pad.DOWN)) {
        selected++;
        if (selected >= demos.length) {
            selected = 0;
        }
    }
    if (Pad.justPressed(Pad.FIRE)) {
        launchSelected();
        return;
    }

    Screen.clear(C_BG);
    centerText(fontTitle, "Athena2ME Showcases", 8, C_TEXT);
    centerText(fontSmall, "Select a demo and press FIRE", 27, C_DIM);

    var baseY = 48;
    for (var i = 0; i < demos.length; i++) {
        drawMenuItem(i, baseY + i * 38);
    }

    var footer = "UP/DOWN choose  |  FIRE launch";
    centerText(fontSmall, footer, H - 17, C_DIM);
}

os.setExitHandler(function () {
    os.stopFrameLoop();
});

startMenu();

os.startFrameLoop(function () {
    if (activeDemo && activeDemo.frame) {
        activeDemo.frame();
    } else {
        drawMenuFrame();
    }
}, 30);
