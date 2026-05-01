// =============================================================================
// 3D — Render3D: M3G loads pogoroo.m3g + PogoRoo-style gameplay (Superscape demo),
// or software cube fallback. Uses Render3D.m3g* APIs for Node / AnimationController.
// =============================================================================

const W = Screen.width;
const H = Screen.height;

const fontTitle = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
const fontSub = new Font(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);

const COL_HUD0 = Color.new(200, 220, 255);
const COL_HUD1 = Color.new(120, 160, 220);
const COL_DIM = Color.new(60, 70, 100);
const COL_FALL = Color.new(40, 30, 50);

/** UserIDs from pogoroo.m3g (Superscape PogoRooMIDlet). */
const POGOROO_MOVE_GROUP_TRANSFORM_ID = 554921620;
const CAMERA_GROUP_TRANSFORM_ID = 769302310;
const POGOROO_TRANSFORM_ID = 347178853;
const ROO_BOUNCE_ID = 418071423;

const keyNone = 0;
const keyForward = 1;
const keyBackward = 2;
const keyLeft = 3;
const keyRight = 4;
const MaxHops = 10;
const GroundEdge = 9.0;
/** hopRoo sync: if KeyframeSequence duration is too small, animateRoo resets animTime every frame and hopCount stays 0 (Java uses 1000ms when ks is missing). */
const ANIM_HOP_MS_DEFAULT = 1000;
const ANIM_HOP_MS_MIN = 400;

const hopSteps = [0, 0, 0, 0, 0, 0.05, 0.1, 0.2, 0.1, 0.05, 0];

let frame = 0;
let usingM3GFile = false;
/** True when M3G file loaded and m3gNode* bindings exist. */
let m3gPogoOk = false;
let returnToMenu = null;

//Render3D.setBackend("soft");

let r3dBackend = Render3D.getBackend();

/**
 * World time for M3G World.animate / AnimationController (int32).
 * Truncated currentTimeMillis is often negative (2026+); uptime from first use
 * keeps a small monotonic clock and sane controller intervals.
 */
var _pogoClockBase = -1;
function pogoWorldMs() {
    if (os.uptimeMillis) {
        var u = os.uptimeMillis() | 0;
        if (_pogoClockBase < 0) {
            _pogoClockBase = u;
        }
        return (u - _pogoClockBase) | 0;
    }
    return os.currentTimeMillis() | 0;
}

// --- PogoRoo game state (Java demo parity) ---
let dirRoo = 0;
let dirCam = 0;
let keyMoveRoo = keyNone;
let keyTurnRoo = keyNone;
let hopCount = 0;
let animTime = 0;
let animLength = 1000;
let animLastTime = 0;
let okToHop = false;
let posRooLast = [0, 0, 0];
let edgeCount = 0;

function textSize(font, text) {
    return font.getTextSize(text);
}

function centerXForText(font, text) {
    return (W - textSize(font, text).width) / 2 | 0;
}

function updateKeysFromPad() {
    Pad.update();
    const up = Pad.pressed(Pad.UP);
    const down = Pad.pressed(Pad.DOWN);
    const left = Pad.pressed(Pad.LEFT);
    const right = Pad.pressed(Pad.RIGHT);

    if (up) {
        if (keyMoveRoo === keyNone) {
            keyMoveRoo = keyForward;
            hopCount = MaxHops;
        }
    } else {
        if (keyMoveRoo === keyForward) {
            keyMoveRoo = keyNone;
        }
    }
    if (down) {
        if (keyMoveRoo === keyNone) {
            keyMoveRoo = keyBackward;
            hopCount = MaxHops;
        }
    } else {
        if (keyMoveRoo === keyBackward) {
            keyMoveRoo = keyNone;
        }
    }

    if (left) {
        keyTurnRoo = keyLeft;
    } else if (right) {
        keyTurnRoo = keyRight;
    } else {
        keyTurnRoo = keyNone;
    }
}

/**
 * PogoRoo hop step using anim time tAnim (must match pre-animateRoo clock).
 * Returns [dx,dy] for translate after World.animate — M3G often reapplies skeletal
 * keyframes and would wipe a translate done earlier in the frame.
 */
function hopRooStep(tAnim) {
    if (tAnim === 0) {
        hopCount = 0;
        okToHop = true;
    }
    if (!okToHop) {
        return null;
    }
    if (keyMoveRoo === keyForward) {
    } else {
        if (keyMoveRoo === keyBackward) {
        } else {
            return null;
        }
    }

    var oldHopCount = hopCount;
    hopCount = (tAnim * 10) / animLength | 0;
    if (hopCount >= MaxHops) {
        okToHop = false;
        hopCount = MaxHops - 1;
    }

    var turnAngle = dirRoo * 3.14159 / 180.0;
    var h = 0;
    var i;
    for (i = oldHopCount + 1; i <= hopCount; i++) {
        h += hopSteps[i];
    }
    var x = h * Math.cos(turnAngle);
    var y = h * Math.sin(turnAngle);
    if (keyMoveRoo === keyForward) {
        return [-x, -y];
    }
    return [x, y];
}

function checkWorldEdge() {
    var pos = Render3D.m3gNodeGetTranslation(POGOROO_MOVE_GROUP_TRANSFORM_ID);
    if (!pos) {
        return;
    }
    if (pos.length < 3) {
        return;
    }
    if (edgeCount > 0) {
        edgeCount--;
    }
    try {
        var offEdge = false;
        if (Math.abs(pos[0]) > GroundEdge) {
            offEdge = true;
        }
        if (Math.abs(pos[1]) > GroundEdge) {
            offEdge = true;
        }
        if (offEdge) {
            edgeCount = 10;
            Render3D.m3gNodeSetTranslation(
                POGOROO_MOVE_GROUP_TRANSFORM_ID,
                posRooLast[0], posRooLast[1], posRooLast[2]
            );
        }
    } catch (e) { }
}

function turnRoo() {
    switch (keyTurnRoo) {
    case keyLeft:
        dirRoo += 5;
        dirCam -= 5;
        Render3D.m3gNodeSetOrientation(POGOROO_MOVE_GROUP_TRANSFORM_ID, dirRoo, 0, 0, 1);
        Render3D.m3gNodeSetOrientation(CAMERA_GROUP_TRANSFORM_ID, dirCam, 0, 0, 1);
        break;
    case keyRight:
        dirRoo -= 5;
        dirCam += 5;
        Render3D.m3gNodeSetOrientation(POGOROO_MOVE_GROUP_TRANSFORM_ID, dirRoo, 0, 0, 1);
        Render3D.m3gNodeSetOrientation(CAMERA_GROUP_TRANSFORM_ID, dirCam, 0, 0, 1);
        break;
    default:
        if (dirCam > 4.9) {
            dirCam -= 5.0;
        } else if (dirCam < -4.9) {
            dirCam += 5.0;
        } else {
            dirCam = 0.0;
        }
        Render3D.m3gNodeSetOrientation(CAMERA_GROUP_TRANSFORM_ID, dirCam, 0, 0, 1);
        break;
    }
}

function animateRoo(worldTime) {
    if (animLastTime === 0) {
        animLastTime = worldTime;
    }
    animTime += worldTime - animLastTime;
    if (animTime > animLength) {
        Render3D.m3gAnimSetActiveInterval(ROO_BOUNCE_ID, worldTime, worldTime + 2000);
        Render3D.m3gAnimSetPosition(ROO_BOUNCE_ID, 0, worldTime);
        animTime = 0;
    }
    animLastTime = worldTime;
}

function moveRoo(worldTime) {
    var tHop = animTime;
    var hopD = hopRooStep(tHop);
    turnRoo();
    animateRoo(worldTime);
    Render3D.worldAnimate(worldTime);
    if (hopD) {
        Render3D.m3gNodeTranslate(POGOROO_MOVE_GROUP_TRANSFORM_ID, hopD[0], hopD[1], 0);
    }
    checkWorldEdge();
    var last = Render3D.m3gNodeGetTranslation(POGOROO_MOVE_GROUP_TRANSFORM_ID);
    if (last) {
        if (last.length >= 3) {
            posRooLast[0] = last[0];
            posRooLast[1] = last[1];
            posRooLast[2] = last[2];
        }
    }
}

Render3D.init();
r3dBackend = Render3D.getBackend();

Render3D.setMaxTriangles(2048);

Render3D.setBackfaceCulling(true);

Render3D.setGlobalLight(0.3, 0.95, 0.2);

Render3D.setMaterialAmbient(55, 70, 110);

Render3D.setMaterialDiffuse(200, 210, 255);

Render3D.setPerspective(58, 0.1, 200);
Render3D.setBackground(12, 14, 28);
Render3D.setObjectMatrixIdentity();

if (r3dBackend === "m3g") {
    const loadErr = Render3D.load("/pogoroo.m3g");
    usingM3GFile = false;
    if (loadErr == null) {
        usingM3GFile = true;
    }
    if (loadErr === undefined) {
        usingM3GFile = true;
    }
}
m3gPogoOk = false;
if (usingM3GFile) {
    m3gPogoOk = true;
}
if (m3gPogoOk) {
    animLength = ANIM_HOP_MS_DEFAULT;
    var d = Render3D.m3gKeyframeDurationTrack0(POGOROO_TRANSFORM_ID) | 0;
    if (d >= ANIM_HOP_MS_MIN) {
        if (d <= 120000) {
            animLength = d;
        }
    }
    var tBoot = pogoWorldMs();
    Render3D.m3gAnimSetActiveInterval(ROO_BOUNCE_ID, tBoot, tBoot + 120000);
    Render3D.m3gAnimSetPosition(ROO_BOUNCE_ID, 0, tBoot);
    Render3D.m3gAnimSetSpeed(ROO_BOUNCE_ID, 1);
}
if (!usingM3GFile) {
    const cube = require("/lib/cube_strips.js");

    Render3D.setTexture("/cube_texture.png");

    Render3D.setTexCoords(cube.uvs);

    Render3D.setTriangleStripMesh(cube.positions, cube.stripLens, cube.normals);
}

exports.start = function (back) {
    returnToMenu = back;
    frame = 0;
};

exports.frame = function () {
    frame = frame + 1;

    if (Pad.justPressed(Pad.GAME_B)) {
        returnToMenu();
        return;
    }

    if (m3gPogoOk) {
        var nowMs = pogoWorldMs();
        updateKeysFromPad();
        moveRoo(nowMs);
    } else if (usingM3GFile) {
        if (Render3D.worldAnimate) {
            Render3D.worldAnimate(os.currentTimeMillis() | 0);
        }
    } else {
        const t = frame * 0.02;
        const r = 5.0;
        if (Render3D.setLookAt) {
            Render3D.setLookAt(
                r * Math.sin(t), 0.5, r * Math.cos(t),
                0, 0, 0,
                0, 1, 0
            );
        } else {
            Render3D.setCamera(0, 0, 5.2);
        }
        if (!usingM3GFile) {
            Render3D.setMeshRotation(frame * 0.8);
        }
    }

    Render3D.begin();
    Render3D.render();
    Render3D.end();

    fontTitle.color = COL_HUD0;
    fontTitle.print("Athena2ME", centerXForText(fontTitle, "Athena2ME"), 6);
    const titleH = textSize(fontTitle, "Athena2ME").height;

    var line2 = (r3dBackend === "m3g" ? "M3G" : "software");
    if (m3gPogoOk) {
        line2 += " / PogoRoo";
    } else if (usingM3GFile) {
        line2 += " / pogoroo.m3g";
    } else {
        line2 += " / lookAt+UV+light";
    }
    fontSub.color = COL_HUD1;
    fontSub.print(line2, centerXForText(fontSub, line2), 6 + titleH);

    if (Render3D.getSceneInfo) {
        const info = Render3D.getSceneInfo();
        if (info) {
            fontSub.color = COL_DIM;
            var s = "" + info;
            if (s.length > 42) {
                s = s.substring(0, 40) + "..";
            }
            fontSub.print(s, 4, 6 + titleH + textSize(fontSub, line2).height + 2);
        }
    }
    if (m3gPogoOk) {
        fontSub.color = COL_DIM;
        fontSub.print("D-pad move/turn", 4, H - 34);
    }
    const sub2 = "frame " + frame;
    fontSub.color = COL_DIM;
    fontSub.print(sub2, centerXForText(fontSub, sub2), H - 18);
};
