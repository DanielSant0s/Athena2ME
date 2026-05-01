// Render3D showcase: textured cube when assets are present, software fallback otherwise.

var W = Screen.width;
var H = Screen.height;

var fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
var fontSmall = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

var TEXT = Color.new(235, 245, 255);
var DIM = Color.new(120, 145, 185);
var WARN = Color.new(255, 180, 70);

var frame = 0;
var backend = "unknown";
var meshReady = false;
var useLookAt = true;
var returnToMenu = null;

function initScene() {
    Render3D.init();
    backend = Render3D.getBackend();
    Render3D.setMaxTriangles(2048);
    Render3D.setBackfaceCulling(true);
    Render3D.setGlobalLight(0.25, 0.95, 0.35);
    Render3D.setMaterialAmbient(55, 70, 110);
    Render3D.setMaterialDiffuse(210, 220, 255);
    Render3D.setPerspective(58, 0.1, 200);
    Render3D.setBackground(10, 12, 25);
    Render3D.setObjectMatrixIdentity();

    try {
        var cube = require("/lib/cube_strips.js");
        Render3D.setTexture("/cube_texture.png");
        Render3D.setTexCoords(cube.uvs);
        Render3D.setTriangleStripMesh(cube.positions, cube.stripLens, cube.normals);
        meshReady = true;
    } catch (e) {
        meshReady = false;
    }
}

function updateCamera() {
    var t = frame * 0.025;
    var r = 5.2;
    if (useLookAt && Render3D.setLookAt) {
        Render3D.setLookAt(
            r * Math.sin(t), 1.0 + Math.sin(t * 0.7) * 0.35, r * Math.cos(t),
            0, 0, 0,
            0, 1, 0
        );
    } else {
        Render3D.setCamera(0, 0, 5.5);
    }
}

exports.start = function (back) {
    returnToMenu = back;
    initScene();
};

exports.frame = function () {
    frame++;

    if (Pad.justPressed(Pad.GAME_B)) {
        returnToMenu();
        return;
    }
    if (Pad.justPressed(Pad.FIRE)) {
        useLookAt = !useLookAt;
    }

    updateCamera();
    Render3D.setMeshRotation(frame * 0.9);

    Render3D.begin();
    Render3D.render();
    Render3D.end();

    fontTitle.color = TEXT;
    fontTitle.print("Render3D Cube", 5, 5);
    fontSmall.color = DIM;
    fontSmall.print("backend: " + backend + "  lookAt: " + (useLookAt ? "on" : "off"), 5, 22);
    if (!meshReady) {
        fontSmall.color = WARN;
        fontSmall.print("mesh asset unavailable", 5, 38);
    }
    fontSmall.color = DIM;
    fontSmall.print("FIRE toggle camera, GAME_B menu", 5, H - 16);
};
