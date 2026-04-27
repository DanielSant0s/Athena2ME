// Cube geometry (triangle strips) — defined in this script, not in Java.
// Edge length ~0.9: vertices at ±0.45 (e.g. short*0.045 from a reference M3G model).

var h = 0.45;

// 24 vertices (4 per face x 6 faces) + normals — Float32Array avoids slow reads after setTriangleStripMesh
exports.positions = new Float32Array([
    h, h, h,  -h, h, h,  h, -h, h,  -h, -h, h,
    -h, h, -h,  h, h, -h,  -h, -h, -h,  h, -h, -h,
    -h, h, h,  -h, h, -h,  -h, -h, h,  -h, -h, -h,
    h, h, -h,  h, h, h,  h, -h, -h,  h, -h, h,
    h, h, -h,  -h, h, -h,  h, h, h,  -h, h, h,
    h, -h, h,  -h, -h, h,  h, -h, -h,  -h, -h, -h
]);

exports.normals = new Float32Array([
    0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
    0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1,
    -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0,
    1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0,
    0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0,
    0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0
]);

exports.stripLens = new Int32Array([4, 4, 4, 4, 4, 4]);

// One UV per position (0–1) — repeat 4 corners per face, consistent with the strip quads
exports.uvs = new Float32Array([
    0, 0, 1, 0, 0, 1, 1, 1,
    0, 0, 1, 0, 0, 1, 1, 1,
    0, 0, 1, 0, 0, 1, 1, 1,
    0, 0, 1, 0, 0, 1, 1, 1,
    0, 0, 1, 0, 0, 1, 1, 1,
    0, 0, 1, 0, 0, 1, 1, 1
]);
