// Helpers to build data for Render3D.setIndexedMesh and setTriangleStripMesh.
// Winding: counter-clockwise from outside for correct lighting / culling.

/** @param {ArrayLike<number>} flatNine xyz xyz xyz or pass [ax,ay,az, ...] */
function trianglePositions3(flat) {
  return new Float32Array(flat);
}

/**
 * @param {number} nV number of unique vertices
 * @param {Array|Int32Array} faceIndices triples (i0,i1,i2) per face
 * @returns {{ positions: Float32Array, stripLens: Int32Array } | null} strip mesh, or null if count mismatch
 */
function indexBufferToStrips(vertices, faceIndices) {
  var n = faceIndices.length;
  if (n % 3 !== 0) {
    return null;
  }
  var nt = n / 3;
  var p = new Float32Array(nt * 9);
  var i;
  for (i = 0; i < nt; i++) {
    var t0 = faceIndices[i * 3] * 3, t1 = faceIndices[i * 3 + 1] * 3, t2 = faceIndices[i * 3 + 2] * 3;
    var o = i * 9;
    p[o] = vertices[t0];
    p[o + 1] = vertices[t0 + 1];
    p[o + 2] = vertices[t0 + 2];
    p[o + 3] = vertices[t1];
    p[o + 4] = vertices[t1 + 1];
    p[o + 5] = vertices[t1 + 2];
    p[o + 6] = vertices[t2];
    p[o + 7] = vertices[t2 + 1];
    p[o + 8] = vertices[t2 + 2];
  }
  var sl = new Int32Array(nt);
  for (i = 0; i < nt; i++) {
    sl[i] = 3;
  }
  return { positions: p, stripLens: sl };
}

/**
 * Smooth normals: face normals summed per unique vertex, then unit length.
 * @param {ArrayLike<number>} vertices 3 * vertexCount
 * @param {ArrayLike<number>} indices triangle list (3 * triangleCount)
 * @returns {Float32Array} same length as vertices
 */
function computeIndexedNormals(vertices, indices) {
  var nv = (vertices.length / 3) | 0;
  if (nv < 1) {
    return new Float32Array(0);
  }
  var acc = new Float32Array(nv * 3);
  var t, ntri = (indices.length / 3) | 0;
  for (t = 0; t < ntri; t++) {
    var i0 = indices[t * 3] | 0, i1 = indices[t * 3 + 1] | 0, i2 = indices[t * 3 + 2] | 0;
    if (i0 < 0 || i1 < 0 || i2 < 0 || i0 >= nv || i1 >= nv || i2 >= nv) {
      continue;
    }
    var ax = vertices[i1 * 3] - vertices[i0 * 3], ay = vertices[i1 * 3 + 1] - vertices[i0 * 3 + 1], az = vertices[i1 * 3 + 2] - vertices[i0 * 3 + 2];
    var bx = vertices[i2 * 3] - vertices[i0 * 3], by = vertices[i2 * 3 + 1] - vertices[i0 * 3 + 1], bz = vertices[i2 * 3 + 2] - vertices[i0 * 3 + 2];
    var nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
    acc[i0 * 3] += nx; acc[i0 * 3 + 1] += ny; acc[i0 * 3 + 2] += nz;
    acc[i1 * 3] += nx; acc[i1 * 3 + 1] += ny; acc[i1 * 3 + 2] += nz;
    acc[i2 * 3] += nx; acc[i2 * 3 + 1] += ny; acc[i2 * 3 + 2] += nz;
  }
  var out = new Float32Array(nv * 3);
  for (var i = 0; i < nv; i++) {
    var x = acc[i * 3], y = acc[i * 3 + 1], z = acc[i * 3 + 2];
    var L = x * x + y * y + z * z;
    if (L < 1e-20) {
      out[i * 3] = 0; out[i * 3 + 1] = 1; out[i * 3 + 2] = 0;
    } else {
      L = 1.0 / Math.sqrt(L);
      out[i * 3] = x * L; out[i * 3 + 1] = y * L; out[i * 3 + 2] = z * L;
    }
  }
  return out;
}

/**
 * One (u,v) per corner in expanded-triangle order (matches indexBufferToStrips / duplicated vertices).
 * @param {ArrayLike<number>} uvs 2 * uniqueVertexCount
 * @param {ArrayLike<number>} indices same as for the indexed mesh
 */
function uvsToExpandedIndexed(uvs, indices) {
  var ntri = (indices.length / 3) | 0;
  var out = new Float32Array(ntri * 3 * 2);
  for (var t = 0; t < ntri; t++) {
    for (var k = 0; k < 3; k++) {
      var vix = indices[t * 3 + k] | 0;
      var o = (t * 3 + k) * 2;
      out[o] = uvs[vix * 2];
      out[o + 1] = uvs[vix * 2 + 1];
    }
  }
  return out;
}

exports.trianglePositions3 = trianglePositions3;
exports.indexBufferToStrips = indexBufferToStrips;
exports.computeIndexedNormals = computeIndexedNormals;
exports.uvsToExpandedIndexed = uvsToExpandedIndexed;
