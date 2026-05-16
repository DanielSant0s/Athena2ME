// Athena2ME demo launcher.
// D-pad: choose demo. Fire: launch. In demos, GAME_B returns here.
// Adapts to small screens by scrolling vertically when rows do not all fit.

let W = Screen.width;
let H = Screen.height;

const fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
const fontMenu = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
const fontSmall = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

const C_BG = Color.new(5, 8, 18);
const C_PANEL = Color.new(16, 24, 48);
const C_PANEL_2 = Color.new(8, 14, 30);
const C_TEXT = Color.new(235, 245, 255);
const C_DIM = Color.new(120, 145, 185);
const C_ACCENT = Color.new(0, 220, 255);
const C_HILITE = Color.new(255, 180, 60);

// Layout constants (hand-tuned to typical J2ME font metrics so we don't depend
// on Font.getTextSize().height, which under-reports leading on many runtimes).
const ROW_H = 32;
const ROW_GAP = 6;
const TITLE_Y_IN_ROW = 3;
const SUB_Y_IN_ROW = 19;
const SIDE_PAD = 8;
const TEXT_INSET = 8;

const HDR_Y = 4;
const TITLE_LH = 20;
const SMALL_LH = 12;
const HDR_GAP = 2;
const FOOTER_GAP = 4;

const demos = [
    {
        title: "Particles + Input",
        subtitle: "Pool typed arrays, sparks, D-pad",
        path: "/demos/particles.js"
    },
    {
        title: "Layers + HUD",
        subtitle: "Offscreen layer cache, scrolling",
        path: "/demos/layers.js"
    },
    {
        title: "Render3D Cube",
        subtitle: "M3G/soft backend, light, UV mesh",
        path: "/demos/render3d_cube.js"
    },
    {
        title: "PogoRoo 3D",
        subtitle: "Original M3G pogoroo gameplay",
        path: "/demos/pogoroo.js"
    },
    {
        title: "Asteroids",
        subtitle: "Vector ship, wrap, splitting rocks",
        path: "/demos/asteroids.js"
    }
];

const N_DEMOS = demos.length;

let selected = 0;
let activeDemo = null;
let menuScrollY = 0;

const textWidth = (font, s) => font.getTextSize(s).width;

// Returns s truncated with "..." so it fits in maxW pixels for the given font.
const ellipsize = (font, s, maxW) => {
    if (s == null) {
        return "";
    }
    if (maxW < 8) {
        return "";
    }
    if (textWidth(font, s) <= maxW) {
        return s;
    }
    const dots = "...";
    const dotsW = textWidth(font, dots);
    if (dotsW >= maxW) {
        return "";
    }
    let lo = 0;
    let hi = s.length;
    while (lo < hi) {
        const mid = (lo + hi + 1) >> 1;
        if (textWidth(font, s.substring(0, mid)) + dotsW <= maxW) {
            lo = mid;
        } else {
            hi = mid - 1;
        }
    }
    if (lo <= 0) {
        return "";
    }
    return `${s.substring(0, lo)}${dots}`;
};

const drawCenter = (font, s, y, color) => {
    font.color = color;
    const cx = ((W - textWidth(font, s)) / 2) | 0;
    font.print(s, cx, y);
};

const drawMenuItem = (demo, i, y, textW) => {
    const { title, subtitle } = demo;
    const active = i === selected;
    const x = SIDE_PAD;
    const boxW = W - 2 * SIDE_PAD;
    Draw.rect(x, y, boxW, ROW_H, active ? C_PANEL : C_PANEL_2);
    Draw.rect(x, y, boxW, 1, active ? C_ACCENT : C_PANEL);
    Draw.rect(x, y + ROW_H - 1, boxW, 1, active ? C_HILITE : C_PANEL);

    const tx = x + TEXT_INSET;
    fontMenu.color = active ? C_TEXT : C_DIM;
    fontMenu.print(ellipsize(fontMenu, title, textW), tx, y + TITLE_Y_IN_ROW);
    fontSmall.color = active ? C_ACCENT : C_DIM;
    fontSmall.print(ellipsize(fontSmall, subtitle, textW), tx, y + SUB_Y_IN_ROW);
};

const startMenu = () => {
    activeDemo = null;
};

const launchSelected = () => {
    const mod = require(demos[selected].path);
    activeDemo = mod;
    const { start } = mod;
    if (start) {
        start(startMenu);
    }
};

const clampScroll = (maxScroll) => {
    menuScrollY = Math.max(0, Math.min(maxScroll, menuScrollY));
};

const scrollToSelection = (viewportH, maxScroll) => {
    const stride = ROW_H + ROW_GAP;
    const selTop = selected * stride;
    const selBot = selTop + ROW_H;
    if (selTop < menuScrollY) {
        menuScrollY = selTop;
    }
    if (selBot > menuScrollY + viewportH) {
        menuScrollY = selBot - viewportH;
    }
    clampScroll(maxScroll);
};

const drawMenuFrame = () => {
    W = Screen.width;
    H = Screen.height;

    const subY = HDR_Y + TITLE_LH + HDR_GAP;
    const listTop = subY + SMALL_LH + FOOTER_GAP;
    const footerY = H - SMALL_LH - 2;
    let listViewportH = footerY - FOOTER_GAP - listTop;
    if (listViewportH < ROW_H) {
        listViewportH = ROW_H;
    }

    const stride = ROW_H + ROW_GAP;
    const totalH = N_DEMOS * stride - ROW_GAP;
    const maxScroll = totalH > listViewportH ? totalH - listViewportH : 0;

    let textW = W - 2 * (SIDE_PAD + TEXT_INSET);
    if (textW < 16) {
        textW = 16;
    }

    if (Pad.justPressed(Pad.UP)) {
        selected = (selected - 1 + N_DEMOS) % N_DEMOS;
        scrollToSelection(listViewportH, maxScroll);
    }
    if (Pad.justPressed(Pad.DOWN)) {
        selected = (selected + 1) % N_DEMOS;
        scrollToSelection(listViewportH, maxScroll);
    }
    if (Pad.justPressed(Pad.FIRE)) {
        launchSelected();
        return;
    }
    clampScroll(maxScroll);

    Screen.clear(C_BG);

    // Rows first; header/footer redrawn after so they cleanly cover any partial
    // row that pokes into their reserved bands (acts as our "clip rect").
    let firstVisible = (menuScrollY / stride) | 0;
    if (firstVisible < 0) {
        firstVisible = 0;
    }
    const viewBot = menuScrollY + listViewportH;
    let i = 0;
    for (const demo of demos) {
        if (i < firstVisible) {
            i++;
            continue;
        }
        const rowTop = i * stride;
        if (rowTop >= viewBot) {
            break;
        }
        drawMenuItem(demo, i, listTop + rowTop - menuScrollY, textW);
        i++;
    }

    // Cover any row pixels that bled past viewport top/bottom.
    Draw.rect(0, 0, W, listTop, C_BG);
    Draw.rect(0, listTop + listViewportH, W, H - (listTop + listViewportH), C_BG);

    drawCenter(fontTitle, "Athena2ME Showcases", HDR_Y, C_TEXT);
    drawCenter(fontSmall, "Select a demo and press FIRE", subY, C_DIM);

    if (maxScroll > 0) {
        if (menuScrollY > 0) {
            Draw.rect(SIDE_PAD / 2, listTop, W - SIDE_PAD, 2, C_ACCENT);
        }
        if (menuScrollY < maxScroll) {
            Draw.rect(SIDE_PAD / 2, listTop + listViewportH - 2, W - SIDE_PAD, 2, C_HILITE);
        }
    }

    drawCenter(fontSmall, "UP/DOWN choose  |  FIRE launch", footerY, C_DIM);
};

os.setExitHandler(() => {
    os.stopFrameLoop();
});

startMenu();

os.startFrameLoop(() => {
    const frame = activeDemo && activeDemo.frame;
    if (frame) {
        frame();
    } else {
        drawMenuFrame();
    }
}, 30);
