import javax.microedition.lcdui.Image;

/**
 * Software immediate 3D: triangle strips or indexed lists, 4x4 math, look-at, optional back-face
 * culling, matrix stack, shell-sorted draw order, flat or textured (UV + classpath {@link Image} path).
 */
public final class Render3DSoftBackend implements Render3DBackend {
    private static final float DEG2RAD = 3.14159265f / 180.0f;
    private static final int MAX_TRIS_CAP = 4096;
    private static final int MIN_TRIS = 32;

    private int maxTris = 1024;
    private int[] tOrder;
    private float[] tDepth;
    private int[] tIA, tIB, tIC, tC;
    /** Per-vertex Gouraud colors when texturing. */
    private int[] tColG0, tColG1, tColG2;
    private static final int MAT_STACK = 8;
    private final float[][] mStack = new float[MAT_STACK][16];
    private int mStackDep;

    private final float[] mUser = new float[16];
    private final float[] mRot = new float[16];
    private final float[] mWorld = new float[16];
    private final float[] w0 = new float[3];
    private final float[] w1 = new float[3];
    private final float[] w2 = new float[3];
    private final float[] n0 = new float[3];
    private final float[] n1 = new float[3];
    private final float[] n2 = new float[3];
    private final float[] vFwd = new float[3];
    private final float[] vRight = new float[3];
    private final float[] vUp = new float[3];

    private float fovY = 55.0f;
    private float zNear = 0.1f;
    private float zFar = 200.0f;
    private float aspect = 1.0f;
    private float eyeX, eyeY, eyeZ = 5.0f;
    private float objectAngle;
    private int bgArgb = 0xff000000;
    private boolean inited;
    private AthenaCanvas lastCanvas;
    private float[] meshPos;
    private int[] meshStrip;
    private int[] meshIndex;
    private int meshMode;
    private float[] meshNrm;
    private boolean useLookAt;
    private boolean cullBack = true;
    private float lDirX, lDirY = 1.0f, lDirZ;
    private int matAr = 0x88, matAg = 0x88, matAb = 0xcc, matDr = 0xff, matDg = 0xff, matDb = 0xff;
    private String texture2dPath;
    private String textureLoadKey;
    private boolean prepForTex;
    private float[] meshUv;
    private int[] texArgb;
    private int texW;
    private int texH;
    private final float[] ezv3 = new float[3];
    private final float[] wTmp = new float[3];
    private final float[] pScr = new float[6];
    private final float[] fN = new float[3];
    private final float[] evView = new float[3];
    /** Eye-space Z per pixel (as float bits); enabled by {@link #setDepthBuffer}. */
    private boolean depthBufferEnabled;
    private int[] zBits;
    private int zBufW;
    private int zBufH;
    /** {@code false} = bilinear (default); {@code true} = nearest. */
    private boolean texFilterNearest;
    /** {@code true} = wrap (default); {@code false} = clamp UV to [0,1]. */
    private boolean texWrapRepeat = true;
    /** Reused row buffer for {@link AthenaCanvas#drawRgb} texturing. */
    private int[] texScanline;
    /** Reused edge buffer for triangle scanlines (avoids per-triangle alloc). */
    private final float[] edgeXsBuf = new float[8];

    public Render3DSoftBackend() {
        setIdentity4(mUser);
        reallocateTris(1024);
    }

    private void reallocateTris(int n) {
        if (n < MIN_TRIS) {
            n = MIN_TRIS;
        }
        if (n > MAX_TRIS_CAP) {
            n = MAX_TRIS_CAP;
        }
        maxTris = n;
        tOrder = new int[maxTris];
        tDepth = new float[maxTris];
        tIA = new int[maxTris];
        tIB = new int[maxTris];
        tIC = new int[maxTris];
        tC = new int[maxTris];
        tColG0 = new int[maxTris];
        tColG1 = new int[maxTris];
        tColG2 = new int[maxTris];
    }

    public void setMaxTriangles(int max) {
        if (max <= 0) {
            return;
        }
        reallocateTris(max);
    }

    public void setDepthBuffer(boolean on) {
        depthBufferEnabled = on;
        if (!on) {
            zBits = null;
            zBufW = 0;
            zBufH = 0;
        }
    }

    public String getId() {
        return "soft";
    }

    public void init(AthenaCanvas canvas) {
        inited = true;
    }

    public void setPerspectiveFromViewport(float a, float fovYDeg, float n, float f) {
        this.aspect = a > 0.0001f ? a : 1.0f;
        this.fovY = fovYDeg;
        this.zNear = n > 0.0001f ? n : 0.1f;
        this.zFar = f > this.zNear ? f : 200.0f;
    }

    public void setBackgroundColor(int r, int g, int b) {
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        bgArgb = 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public void setCameraPosition(float x, float y, float z) {
        useLookAt = false;
        eyeX = x;
        eyeY = y;
        eyeZ = z;
    }

    public void setLookAt(float ex, float ey, float ez, float tx, float ty, float tz, float ux, float uy, float uz) {
        useLookAt = true;
        eyeX = ex;
        eyeY = ey;
        eyeZ = ez;
        vFwd[0] = tx - ex;
        vFwd[1] = ty - ey;
        vFwd[2] = tz - ez;
        float fl = (float) Math.sqrt((double) (vFwd[0] * vFwd[0] + vFwd[1] * vFwd[1] + vFwd[2] * vFwd[2]));
        if (fl < 1.0e-8f) {
            useLookAt = false;
            return;
        }
        vFwd[0] /= fl;
        vFwd[1] /= fl;
        vFwd[2] /= fl;
        vRight[0] = vFwd[1] * uz - vFwd[2] * uy;
        vRight[1] = vFwd[2] * ux - vFwd[0] * uz;
        vRight[2] = vFwd[0] * uy - vFwd[1] * ux;
        float rl = (float) Math.sqrt((double) (vRight[0] * vRight[0] + vRight[1] * vRight[1] + vRight[2] * vRight[2]));
        if (rl < 1.0e-8f) {
            useLookAt = false;
            return;
        }
        vRight[0] /= rl;
        vRight[1] /= rl;
        vRight[2] /= rl;
        vUp[0] = vRight[1] * vFwd[2] - vRight[2] * vFwd[1];
        vUp[1] = vRight[2] * vFwd[0] - vRight[0] * vFwd[2];
        vUp[2] = vRight[0] * vFwd[1] - vRight[1] * vFwd[0];
        float ul = (float) Math.sqrt((double) (vUp[0] * vUp[0] + vUp[1] * vUp[1] + vUp[2] * vUp[2]));
        if (ul < 1.0e-8f) {
            useLookAt = false;
            return;
        }
        vUp[0] /= ul;
        vUp[1] /= ul;
        vUp[2] /= ul;
    }

    public void setObjectRotationY(float degrees) {
        objectAngle = degrees;
    }

    public void setObjectTransformFromColumnMajor(float[] m) {
        if (m == null || m.length < 16) {
            setIdentity4(mUser);
            return;
        }
        for (int i = 0; i < 16; i++) {
            mUser[i] = m[i];
        }
    }

    public void setObjectTransformIdentity() {
        setIdentity4(mUser);
    }

    public void pushObjectMatrix() {
        if (mStackDep >= MAT_STACK) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            mStack[mStackDep][i] = mUser[i];
        }
        mStackDep++;
    }

    public void popObjectMatrix() {
        if (mStackDep <= 0) {
            return;
        }
        mStackDep--;
        for (int i = 0; i < 16; i++) {
            mUser[i] = mStack[mStackDep][i];
        }
    }

    public void setTriangleStripMesh(float[] pos, int[] stripLen, float[] normals) {
        meshMode = 0;
        meshIndex = null;
        meshPos = pos;
        meshStrip = stripLen;
        meshNrm = normals;
    }

    public void setIndexedTriangleMesh(float[] pos, int[] indices, float[] normals) {
        meshMode = 1;
        meshStrip = null;
        meshPos = pos;
        meshIndex = indices;
        meshNrm = normals;
    }

    public void setBackfaceCulling(boolean on) {
        cullBack = on;
    }

    public void setGlobalLightDirection(float x, float y, float z) {
        lDirX = x;
        lDirY = y;
        lDirZ = z;
    }

    public void setMaterialAmbient(int r, int g, int b) {
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        matAr = r;
        matAg = g;
        matAb = b;
    }

    public void setMaterialDiffuse(int r, int g, int b) {
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        matDr = r;
        matDg = g;
        matDb = b;
    }

    public void setTexture2DPath(String path) {
        texture2dPath = path;
        textureLoadKey = null;
        texArgb = null;
    }

    public void setTexCoords(float[] uvs) {
        meshUv = uvs;
    }

    public void clearImmediateMesh() {
        meshPos = null;
        meshStrip = null;
        meshIndex = null;
        meshNrm = null;
        meshMode = 0;
        texture2dPath = null;
        textureLoadKey = null;
        meshUv = null;
        texArgb = null;
        texW = 0;
        texH = 0;
    }

    public void beginFrame(AthenaCanvas canvas) {
        if (canvas == null) {
            return;
        }
        lastCanvas = canvas;
        if (!inited) {
            inited = true;
        }
        if (!useLookAt) {
            vFwd[0] = 0.0f;
            vFwd[1] = 0.0f;
            vFwd[2] = -1.0f;
            vRight[0] = 1.0f;
            vRight[1] = 0.0f;
            vRight[2] = 0.0f;
            vUp[0] = 0.0f;
            vUp[1] = 1.0f;
            vUp[2] = 0.0f;
        }
        canvas.clearScreen(bgArgb);
    }

    public void renderImmediate(AthenaCanvas canvas) {
        AthenaCanvas c = canvas != null ? canvas : lastCanvas;
        if (c == null) {
            return;
        }
        if (meshPos == null) {
            return;
        }
        if (meshMode == 0) {
            if (meshStrip == null) {
                return;
            }
        } else {
            if (meshIndex == null) {
                return;
            }
        }
        int nFloat = meshPos.length;
        if (nFloat < 9) {
            return;
        }
        int nv = nFloat / 3;
        prepForTex = texture2dPath != null && texture2dPath.length() > 0 && meshUv != null && meshUv.length == nv * 2;
        if (prepForTex) {
            ensureTextureLoaded(c);
        } else {
            texArgb = null;
        }
        boolean useTex = prepForTex && texArgb != null;
        buildWorldMatrix();
        int tw = c.getTargetWidth3D();
        int th = c.getTargetHeight3D();
        if (tw <= 0 || th <= 0) {
            return;
        }
        float tHalf = (float) Math.tan((double) (fovY * 0.5f * DEG2RAD));
        if (tHalf < 0.0001f) {
            tHalf = 0.0001f;
        }
        float a = aspect > 0.0001f ? aspect : 1.0f;
        int nTri = 0;
        if (meshMode == 0) {
            nTri = buildTriangleListFromStrips(nv, maxTris);
        } else {
            nTri = buildTriangleListFromIndex(nv, maxTris);
        }
        if (nTri <= 0) {
            return;
        }
        if (depthBufferEnabled) {
            prepareDepthBuffer(tw, th);
            for (int i = 0; i < nTri; i++) {
                tOrder[i] = i;
            }
        } else {
            sortOrderDescDepth(tOrder, tDepth, nTri);
        }
        for (int k = 0; k < nTri; k++) {
            int o = tOrder[k];
            int i0 = tIA[o];
            int i1 = tIB[o];
            int i2 = tIC[o];
            mulModel(meshPos, i0, w0);
            mulModel(meshPos, i1, w1);
            mulModel(meshPos, i2, w2);
            if (useTex) {
                if (!toScreenWithEzv(tHalf, a, tw, th, w0, pScr, 0, ezv3, 0)
                        || !toScreenWithEzv(tHalf, a, tw, th, w1, pScr, 2, ezv3, 1)
                        || !toScreenWithEzv(tHalf, a, tw, th, w2, pScr, 4, ezv3, 2)) {
                    continue;
                }
                float u0 = meshUv[i0 * 2], v0u = meshUv[i0 * 2 + 1];
                float u1 = meshUv[i1 * 2], v1u = meshUv[i1 * 2 + 1];
                float u2 = meshUv[i2 * 2], v2u = meshUv[i2 * 2 + 1];
                drawTexturedTriangle(
                        c, tw, th,
                        (int) pScr[0], (int) pScr[1], (int) pScr[2], (int) pScr[3], (int) pScr[4], (int) pScr[5],
                        u0, v0u, u1, v1u, u2, v2u,
                        ezv3[0], ezv3[1], ezv3[2],
                        tColG0[o], tColG1[o], tColG2[o],
                        depthBufferEnabled, zBits);
            } else {
                if (depthBufferEnabled) {
                    if (!toScreenWithEzv(tHalf, a, tw, th, w0, pScr, 0, ezv3, 0)
                            || !toScreenWithEzv(tHalf, a, tw, th, w1, pScr, 2, ezv3, 1)
                            || !toScreenWithEzv(tHalf, a, tw, th, w2, pScr, 4, ezv3, 2)) {
                        continue;
                    }
                    drawSolidTriangleDepth(
                            c, tw, th,
                            (int) pScr[0], (int) pScr[1], (int) pScr[2], (int) pScr[3], (int) pScr[4], (int) pScr[5],
                            ezv3[0], ezv3[1], ezv3[2],
                            tC[o], zBits);
                } else {
                    if (!toScreen(tHalf, a, tw, th, w0, pScr, 0) || !toScreen(tHalf, a, tw, th, w1, pScr, 2)
                            || !toScreen(tHalf, a, tw, th, w2, pScr, 4)) {
                        continue;
                    }
                    c.drawTriangle(
                            (int) pScr[0], (int) pScr[1],
                            (int) pScr[2], (int) pScr[3],
                            (int) pScr[4], (int) pScr[5],
                            tC[o]);
                }
            }
        }
    }

    private void prepareDepthBuffer(int tw, int th) {
        int n = tw * th;
        if (zBits == null || zBufW != tw || zBufH != th) {
            zBits = new int[n];
            zBufW = tw;
            zBufH = th;
        }
        int far = Float.floatToIntBits(Float.POSITIVE_INFINITY);
        for (int i = 0; i < n; i++) {
            zBits[i] = far;
        }
    }

    private int buildTriangleListFromStrips(int nv, int cap) {
        int nTri = 0;
        int base = 0;
        for (int s = 0; s < meshStrip.length; s++) {
            int sl = meshStrip[s];
            if (sl < 3) {
                if (sl > 0) {
                    base += sl;
                }
                continue;
            }
            for (int t = 0; t < sl - 2; t++) {
                if (nTri >= cap) {
                    return nTri;
                }
                int i0 = base + t;
                int i1 = base + t + 1;
                int i2 = base + t + 2;
                if ((t & 1) == 1) {
                    int q = i1;
                    i1 = i2;
                    i2 = q;
                }
                if (i0 >= nv || i1 >= nv || i2 >= nv) {
                    break;
                }
                nTri = addTriangle(nTri, i0, i1, i2, cap);
            }
            if (nTri >= cap) {
                return nTri;
            }
            base += sl;
        }
        return nTri;
    }

    private int buildTriangleListFromIndex(int nv, int cap) {
        int nTri = 0;
        int nt = meshIndex.length / 3;
        for (int t = 0; t < nt; t++) {
            if (nTri >= cap) {
                return nTri;
            }
            int i0 = meshIndex[t * 3];
            int i1 = meshIndex[t * 3 + 1];
            int i2 = meshIndex[t * 3 + 2];
            if (i0 < 0 || i0 >= nv || i1 < 0 || i1 >= nv || i2 < 0 || i2 >= nv) {
                return nTri;
            }
            nTri = addTriangle(nTri, i0, i1, i2, cap);
        }
        return nTri;
    }

    private int addTriangle(int nTri, int i0, int i1, int i2, int cap) {
        mulModel(meshPos, i0, w0);
        mulModel(meshPos, i1, w1);
        mulModel(meshPos, i2, w2);
        if (cullBack) {
            float ax = w1[0] - w0[0], ay = w1[1] - w0[1], az = w1[2] - w0[2];
            float bx = w2[0] - w0[0], byy = w2[1] - w0[1], bz = w2[2] - w0[2];
            fN[0] = ay * bz - az * byy;
            fN[1] = az * bx - ax * bz;
            fN[2] = ax * byy - ay * bx;
            float cxl = fN[0] * fN[0] + fN[1] * fN[1] + fN[2] * fN[2];
            if (cxl < 1.0e-20f) {
                return nTri;
            }
            evView[0] = eyeX - (w0[0] + w1[0] + w2[0]) * 0.3333333f;
            evView[1] = eyeY - (w0[1] + w1[1] + w2[1]) * 0.3333333f;
            evView[2] = eyeZ - (w0[2] + w1[2] + w2[2]) * 0.3333333f;
            if (fN[0] * evView[0] + fN[1] * evView[1] + fN[2] * evView[2] <= 0.0f) {
                return nTri;
            }
        }
        tIA[nTri] = i0;
        tIB[nTri] = i1;
        tIC[nTri] = i2;
        if (prepForTex) {
            tColG0[nTri] = shadeOneVertex(i0);
            tColG1[nTri] = shadeOneVertex(i1);
            tColG2[nTri] = shadeOneVertex(i2);
            tC[nTri] = argbAverage3(tColG0[nTri], tColG1[nTri], tColG2[nTri]);
        } else {
            tC[nTri] = shadeAtVertices(i0, i1, i2);
        }
        float cx = (w0[0] + w1[0] + w2[0]) * 0.3333333f;
        float cyy = (w0[1] + w1[1] + w2[1]) * 0.3333333f;
        float cz = (w0[2] + w1[2] + w2[2]) * 0.3333333f;
        float ex = cx - eyeX, eyy = cyy - eyeY, ez = cz - eyeZ;
        tDepth[nTri] = ex * ex + eyy * eyy + ez * ez;
        tOrder[nTri] = nTri;
        return nTri + 1;
    }

    private int shadeAtVertices(int i0, int i1, int i2) {
        float ll = (float) Math.sqrt((double) (lDirX * lDirX + lDirY * lDirY + lDirZ * lDirZ));
        float lnx = lDirX, lny = lDirY, lnz = lDirZ;
        if (ll > 1.0e-8f) {
            lnx /= ll;
            lny /= ll;
            lnz /= ll;
        }
        float aR = (float) matAr / 255.0f, aG = (float) matAg / 255.0f, aB = (float) matAb / 255.0f;
        float dR = (float) matDr / 255.0f, dG = (float) matDg / 255.0f, dB = (float) matDb / 255.0f;
        if (meshNrm != null) {
            readN3(meshNrm, i0, wTmp);
            nrm3(mWorld, wTmp, n0);
            readN3(meshNrm, i1, wTmp);
            nrm3(mWorld, wTmp, n1);
            readN3(meshNrm, i2, wTmp);
            nrm3(mWorld, wTmp, n2);
            float mx = n0[0] + n1[0] + n2[0];
            float my = n0[1] + n1[1] + n2[1];
            float mz = n0[2] + n1[2] + n2[2];
            float len = (float) Math.sqrt((double) (mx * mx + my * my + mz * mz));
            if (len < 0.0001f) {
                int cr = (int) (255.0f * aR);
                int cg = (int) (255.0f * aG);
                int cb = (int) (255.0f * aB);
                if (cr > 255) {
                    cr = 255;
                }
                if (cg > 255) {
                    cg = 255;
                }
                if (cb > 255) {
                    cb = 255;
                }
                return 0xFF000000 | (cr << 16) | (cg << 8) | cb;
            }
            mx /= len;
            my /= len;
            mz /= len;
            float nd = mx * lnx + my * lny + mz * lnz;
            if (nd < 0.0f) {
                nd = 0.0f;
            }
            if (nd > 1.0f) {
                nd = 1.0f;
            }
            int cr = (int) (255.0f * (aR + dR * nd));
            int cg = (int) (255.0f * (aG + dG * nd));
            int cb = (int) (255.0f * (aB + dB * nd));
            if (cr > 255) {
                cr = 255;
            }
            if (cg > 255) {
                cg = 255;
            }
            if (cb > 255) {
                cb = 255;
            }
            return 0xFF000000 | (cr << 16) | (cg << 8) | cb;
        }
        return flatShadeNoNormals();
    }

    private int shadeOneVertex(int vi) {
        float ll = (float) Math.sqrt((double) (lDirX * lDirX + lDirY * lDirY + lDirZ * lDirZ));
        float lnx = lDirX, lny = lDirY, lnz = lDirZ;
        if (ll > 1.0e-8f) {
            lnx /= ll;
            lny /= ll;
            lnz /= ll;
        }
        float aR = (float) matAr / 255.0f, aG = (float) matAg / 255.0f, aB = (float) matAb / 255.0f;
        float dR = (float) matDr / 255.0f, dG = (float) matDg / 255.0f, dB = (float) matDb / 255.0f;
        if (meshNrm != null) {
            readN3(meshNrm, vi, wTmp);
            nrm3(mWorld, wTmp, wTmp);
            float nd = wTmp[0] * lnx + wTmp[1] * lny + wTmp[2] * lnz;
            if (nd < 0.0f) {
                nd = 0.0f;
            }
            if (nd > 1.0f) {
                nd = 1.0f;
            }
            int cr = (int) (255.0f * (aR + dR * nd));
            int cg = (int) (255.0f * (aG + dG * nd));
            int cb = (int) (255.0f * (aB + dB * nd));
            if (cr > 255) {
                cr = 255;
            }
            if (cg > 255) {
                cg = 255;
            }
            if (cb > 255) {
                cb = 255;
            }
            return 0xFF000000 | (cr << 16) | (cg << 8) | cb;
        }
        return flatShadeNoNormals();
    }

    private int flatShadeNoNormals() {
        float nd = 0.5f;
        float aR = (float) matAr / 255.0f, aG = (float) matAg / 255.0f, aB = (float) matAb / 255.0f;
        float dR = (float) matDr / 255.0f, dG = (float) matDg / 255.0f, dB = (float) matDb / 255.0f;
        int cr = (int) (255.0f * (aR + dR * nd));
        int cg = (int) (255.0f * (aG + dG * nd));
        int cb = (int) (255.0f * (aB + dB * nd));
        if (cr > 255) {
            cr = 255;
        }
        if (cg > 255) {
            cg = 255;
        }
        if (cb > 255) {
            cb = 255;
        }
        return 0xFF000000 | (cr << 16) | (cg << 8) | cb;
    }

    private static int argbAverage3(int c0, int c1, int c2) {
        int r = ((c0 >> 16) & 0xff) + ((c1 >> 16) & 0xff) + ((c2 >> 16) & 0xff);
        int g = ((c0 >> 8) & 0xff) + ((c1 >> 8) & 0xff) + ((c2 >> 8) & 0xff);
        int b = (c0 & 0xff) + (c1 & 0xff) + (c2 & 0xff);
        return 0xFF000000 | ((r / 3) << 16) | ((g / 3) << 8) | (b / 3);
    }

    private void ensureTextureLoaded(AthenaCanvas c) {
        if (c == null || texture2dPath == null) {
            return;
        }
        if (texture2dPath.equals(textureLoadKey) && texArgb != null) {
            return;
        }
        textureLoadKey = texture2dPath;
        texArgb = null;
        texW = 0;
        texH = 0;
        String p0 = texture2dPath;
        String pWithLead = p0 != null && p0.length() > 0 && p0.charAt(0) != '/' ? ("/" + p0) : p0;
        String pNoLead = p0 != null && p0.length() > 0 && p0.charAt(0) == '/' ? p0.substring(1) : p0;
        String[] tryNames = p0 == null ? new String[0] : new String[] { p0, pWithLead, pNoLead };
        Image im = null;
        for (int t = 0; t < tryNames.length; t++) {
            if (tryNames[t] == null) {
                continue;
            }
            im = c.loadImage(tryNames[t]);
            if (im != null) {
                break;
            }
        }
        if (im == null) {
            return;
        }
        int w = im.getWidth();
        int h = im.getHeight();
        if (w < 1 || h < 1) {
            return;
        }
        texW = w;
        texH = h;
        texArgb = new int[texW * texH];
        try {
            im.getRGB(texArgb, 0, texW, 0, 0, texW, texH);
        } catch (Throwable t) {
            texArgb = null;
            texW = 0;
            texH = 0;
        }
    }

    private void drawTexturedTriangle(
            AthenaCanvas c, int scw, int sch,
            int x0, int y0, int x1, int y1, int x2, int y2,
            float u0, float v0f, float u1, float v1f, float u2, float v2f,
            float e0, float e1, float e2, int c0, int c1, int c2,
            boolean useDepth, int[] zb) {
        if (texArgb == null || e0 < zNear * 0.5f || e1 < zNear * 0.5f || e2 < zNear * 0.5f) {
            return;
        }
        float in0 = 1.0f / e0, in1 = 1.0f / e1, in2 = 1.0f / e2;
        int xmin = x0;
        if (x1 < xmin) {
            xmin = x1;
        }
        if (x2 < xmin) {
            xmin = x2;
        }
        int xmax = x0;
        if (x1 > xmax) {
            xmax = x1;
        }
        if (x2 > xmax) {
            xmax = x2;
        }
        int ymin = y0;
        if (y1 < ymin) {
            ymin = y1;
        }
        if (y2 < ymin) {
            ymin = y2;
        }
        int ymax = y0;
        if (y1 > ymax) {
            ymax = y1;
        }
        if (y2 > ymax) {
            ymax = y2;
        }
        if (xmin < 0) {
            xmin = 0;
        }
        if (ymin < 0) {
            ymin = 0;
        }
        if (xmax >= scw) {
            xmax = scw - 1;
        }
        if (ymax >= sch) {
            ymax = sch - 1;
        }
        if (xmin > xmax || ymin > ymax) {
            return;
        }
        float tden = triSignArea2((float) x0, (float) y0, (float) x1, (float) y1, (float) x2, (float) y2);
        if (tden * tden < 0.1f) {
            return;
        }
        float[] edgeXs = edgeXsBuf;
        for (int y = ymin; y <= ymax; y++) {
            float py = y + 0.5f;
            int en = collectTriangleScanlineXs(py, x0, y0, x1, y1, x2, y2, edgeXs);
            if (en < 2) {
                continue;
            }
            sortFloats(edgeXs, en);
            int xl = floorSigned(edgeXs[0]);
            int xr = ceilSigned(edgeXs[en - 1]);
            if (xl < xmin) {
                xl = xmin;
            }
            if (xr > xmax) {
                xr = xmax;
            }
            if (xl < 0) {
                xl = 0;
            }
            if (xr >= scw) {
                xr = scw - 1;
            }
            if (xl > xr) {
                continue;
            }
            int span = xr - xl + 1;
            if (texScanline == null || texScanline.length < span) {
                int cap = span < 256 ? 256 : span;
                texScanline = new int[cap];
            }
            for (int xi = 0; xi < span; xi++) {
                int x = xl + xi;
                float px = x + 0.5f;
                float bba = triSignArea2(px, py, (float) x1, (float) y1, (float) x2, (float) y2) / tden;
                float bbb = triSignArea2((float) x0, (float) y0, px, py, (float) x2, (float) y2) / tden;
                float bbc = 1.0f - bba - bbb;
                if (bba <= 0.0f && bbb <= 0.0f && bbc <= 0.0f) {
                    bba = -bba;
                    bbb = -bbb;
                    bbc = -bbc;
                } else if (!(bba >= 0.0f && bbb >= 0.0f && bbc >= 0.0f)) {
                    texScanline[xi] = 0;
                    continue;
                }
                float den = bba * in0 + bbb * in1 + bbc * in2;
                if (den < 1.0e-20f) {
                    texScanline[xi] = 0;
                    continue;
                }
                float ezW = 1.0f / den;
                if (useDepth) {
                    int zi = y * scw + x;
                    if (Float.intBitsToFloat(zb[zi]) <= ezW) {
                        texScanline[xi] = 0;
                        continue;
                    }
                }
                float tu = (bba * u0 * in0 + bbb * u1 * in1 + bbc * u2 * in2) / den;
                float tv = (bba * v0f * in0 + bbb * v1f * in1 + bbc * v2f * in2) / den;
                int ti = sampleTexel(tu, tv);
                int ta = (ti >>> 24) & 0xff;
                if (ta == 0) {
                    texScanline[xi] = 0;
                    continue;
                }
                int r0 = (c0 >> 16) & 0xff, g0 = (c0 >> 8) & 0xff, b0a = c0 & 0xff;
                int r1 = (c1 >> 16) & 0xff, g1 = (c1 >> 8) & 0xff, b1a = c1 & 0xff;
                int r2 = (c2 >> 16) & 0xff, g2 = (c2 >> 8) & 0xff, b2a = c2 & 0xff;
                int r = (int) (bba * r0 + bbb * r1 + bbc * r2);
                int gg = (int) (bba * g0 + bbb * g1 + bbc * g2);
                int b = (int) (bba * b0a + bbb * b1a + bbc * b2a);
                if (r > 255) {
                    r = 255;
                }
                if (gg > 255) {
                    gg = 255;
                }
                if (b > 255) {
                    b = 255;
                }
                int trp = (ti >> 16) & 0xff, tgp = (ti >> 8) & 0xff, tbp = ti & 0xff;
                int outA = ta;
                int outR = (trp * r) / 255;
                int outG = (tgp * gg) / 255;
                int outB = (tbp * b) / 255;
                texScanline[xi] = (outA << 24) | (outR << 16) | (outG << 8) | outB;
                if (useDepth && ta >= 128) {
                    zb[y * scw + x] = Float.floatToIntBits(ezW);
                }
            }
            c.drawRgb(texScanline, 0, span, xl, y, span, 1, true);
        }
    }

    private static int collectTriangleScanlineXs(float py, int x0, int y0, int x1, int y1, int x2, int y2, float[] xs) {
        int n = 0;
        n = edgeIntersectScanline(py, x0, y0, x1, y1, xs, n);
        n = edgeIntersectScanline(py, x1, y1, x2, y2, xs, n);
        n = edgeIntersectScanline(py, x2, y2, x0, y0, xs, n);
        return n;
    }

    private static int edgeIntersectScanline(float py, int xa, int ya, int xb, int yb, float[] xs, int n) {
        float fax = xa, fay = ya, fbx = xb, fby = yb;
        float dy = fby - fay;
        if (Math.abs(dy) < 1.0e-5f) {
            if (Math.abs(py - fay) <= 0.5001f) {
                float lo = fax < fbx ? fax : fbx;
                float hi = fax < fbx ? fbx : fax;
                if (n < xs.length) {
                    xs[n++] = lo;
                }
                if (n < xs.length) {
                    xs[n++] = hi;
                }
            }
            return n;
        }
        float t = (py - fay) / dy;
        if (t >= 0.0f && t <= 1.0f && n < xs.length) {
            xs[n++] = fax + t * (fbx - fax);
        }
        return n;
    }

    private static void sortFloats(float[] a, int n) {
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (a[i] > a[j]) {
                    float t = a[i];
                    a[i] = a[j];
                    a[j] = t;
                }
            }
        }
    }

    private void drawSolidTriangleDepth(
            AthenaCanvas c, int scw, int sch,
            int x0, int y0, int x1, int y1, int x2, int y2,
            float e0, float e1, float e2, int col, int[] zb) {
        if (e0 < zNear * 0.5f || e1 < zNear * 0.5f || e2 < zNear * 0.5f) {
            return;
        }
        float in0 = 1.0f / e0, in1 = 1.0f / e1, in2 = 1.0f / e2;
        int xmin = x0;
        if (x1 < xmin) {
            xmin = x1;
        }
        if (x2 < xmin) {
            xmin = x2;
        }
        int xmax = x0;
        if (x1 > xmax) {
            xmax = x1;
        }
        if (x2 > xmax) {
            xmax = x2;
        }
        int ymin = y0;
        if (y1 < ymin) {
            ymin = y1;
        }
        if (y2 < ymin) {
            ymin = y2;
        }
        int ymax = y0;
        if (y1 > ymax) {
            ymax = y1;
        }
        if (y2 > ymax) {
            ymax = y2;
        }
        if (xmin < 0) {
            xmin = 0;
        }
        if (ymin < 0) {
            ymin = 0;
        }
        if (xmax >= scw) {
            xmax = scw - 1;
        }
        if (ymax >= sch) {
            ymax = sch - 1;
        }
        if (xmin > xmax || ymin > ymax) {
            return;
        }
        float tden = triSignArea2((float) x0, (float) y0, (float) x1, (float) y1, (float) x2, (float) y2);
        if (tden * tden < 0.1f) {
            return;
        }
        float[] edgeXs = edgeXsBuf;
        for (int y = ymin; y <= ymax; y++) {
            float py = y + 0.5f;
            int en = collectTriangleScanlineXs(py, x0, y0, x1, y1, x2, y2, edgeXs);
            if (en < 2) {
                continue;
            }
            sortFloats(edgeXs, en);
            int xl = floorSigned(edgeXs[0]);
            int xr = ceilSigned(edgeXs[en - 1]);
            if (xl < xmin) {
                xl = xmin;
            }
            if (xr > xmax) {
                xr = xmax;
            }
            if (xl < 0) {
                xl = 0;
            }
            if (xr >= scw) {
                xr = scw - 1;
            }
            if (xl > xr) {
                continue;
            }
            int span = xr - xl + 1;
            if (texScanline == null || texScanline.length < span) {
                int cap = span < 256 ? 256 : span;
                texScanline = new int[cap];
            }
            for (int xi = 0; xi < span; xi++) {
                int x = xl + xi;
                float px = x + 0.5f;
                float bba = triSignArea2(px, py, (float) x1, (float) y1, (float) x2, (float) y2) / tden;
                float bbb = triSignArea2((float) x0, (float) y0, px, py, (float) x2, (float) y2) / tden;
                float bbc = 1.0f - bba - bbb;
                if (bba <= 0.0f && bbb <= 0.0f && bbc <= 0.0f) {
                    bba = -bba;
                    bbb = -bbb;
                    bbc = -bbc;
                } else if (!(bba >= 0.0f && bbb >= 0.0f && bbc >= 0.0f)) {
                    texScanline[xi] = 0;
                    continue;
                }
                float den = bba * in0 + bbb * in1 + bbc * in2;
                if (den < 1.0e-20f) {
                    texScanline[xi] = 0;
                    continue;
                }
                float ezW = 1.0f / den;
                int zi = y * scw + x;
                if (Float.intBitsToFloat(zb[zi]) <= ezW) {
                    texScanline[xi] = 0;
                    continue;
                }
                zb[zi] = Float.floatToIntBits(ezW);
                texScanline[xi] = col;
            }
            c.drawRgb(texScanline, 0, span, xl, y, span, 1, true);
        }
    }

    private static int floorSigned(float f) {
        int i = (int) f;
        if (f < 0.0f && (float) i != f) {
            return i - 1;
        }
        return i;
    }

    private static int ceilSigned(float f) {
        int i = (int) f;
        if (f > 0.0f && (float) i != f) {
            return i + 1;
        }
        return i;
    }

    private float texUForSample(float u) {
        return texWrapRepeat ? wrapU(u) : clamp01f(u);
    }

    private static float clamp01f(float t) {
        if (t != t) {
            return 0.0f;
        }
        if (t < 0.0f) {
            return 0.0f;
        }
        if (t > 1.0f) {
            return 1.0f;
        }
        return t;
    }

    private int sampleTexel(float u, float v) {
        if (texFilterNearest) {
            return sampleTexNearest(u, v);
        }
        return sampleTexBilinear(u, v);
    }

    private int sampleTexNearest(float u, float v) {
        if (texArgb == null || texW < 1 || texH < 1) {
            return 0xff808080;
        }
        float uf = texUForSample(u);
        float vf = texUForSample(v);
        int xi = (int) (uf * (float) texW);
        int yi = (int) (vf * (float) texH);
        if (xi < 0) {
            xi = 0;
        }
        if (yi < 0) {
            yi = 0;
        }
        if (xi >= texW) {
            xi = texW - 1;
        }
        if (yi >= texH) {
            yi = texH - 1;
        }
        return texArgb[xi + yi * texW];
    }

    private int sampleTexBilinear(float u, float v) {
        if (texArgb == null || texW < 1 || texH < 1) {
            return 0xff808080;
        }
        float uf = texUForSample(u);
        float vf = texUForSample(v);
        float uu = uf * (float) texW - 0.5f;
        float vv = vf * (float) texH - 0.5f;
        int x0 = (int) uu;
        int y0 = (int) vv;
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x0 >= texW) {
            x0 = texW - 1;
        }
        if (y0 >= texH) {
            y0 = texH - 1;
        }
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        if (texWrapRepeat) {
            if (x1 >= texW) {
                x1 = 0;
            }
            if (y1 >= texH) {
                y1 = 0;
            }
        } else {
            if (x1 >= texW) {
                x1 = texW - 1;
            }
            if (y1 >= texH) {
                y1 = texH - 1;
            }
        }
        float ax = uu - (float) x0;
        if (ax < 0.0f) {
            ax = 0.0f;
        }
        if (ax > 1.0f) {
            ax = 1.0f;
        }
        float ay = vv - (float) y0;
        if (ay < 0.0f) {
            ay = 0.0f;
        }
        if (ay > 1.0f) {
            ay = 1.0f;
        }
        int a00 = texArgb[x0 + y0 * texW];
        int a10 = texArgb[x1 + y0 * texW];
        int a01 = texArgb[x0 + y1 * texW];
        int a11 = texArgb[x1 + y1 * texW];
        float w00 = (1.0f - ax) * (1.0f - ay), w10 = ax * (1.0f - ay), w01 = (1.0f - ax) * ay, w11 = ax * ay;
        int al = (int) (w00 * ((a00 >>> 24) & 0xff) + w10 * ((a10 >>> 24) & 0xff) + w01 * ((a01 >>> 24) & 0xff) + w11 * ((a11 >>> 24) & 0xff));
        int r = (int) (w00 * ((a00 >> 16) & 0xff) + w10 * ((a10 >> 16) & 0xff) + w01 * ((a01 >> 16) & 0xff) + w11 * ((a11 >> 16) & 0xff));
        int g = (int) (w00 * ((a00 >> 8) & 0xff) + w10 * ((a10 >> 8) & 0xff) + w01 * ((a01 >> 8) & 0xff) + w11 * ((a11 >> 8) & 0xff));
        int b = (int) (w00 * (a00 & 0xff) + w10 * (a10 & 0xff) + w01 * (a01 & 0xff) + w11 * (a11 & 0xff));
        if (al > 255) {
            al = 255;
        }
        if (r > 255) {
            r = 255;
        }
        if (g > 255) {
            g = 255;
        }
        if (b > 255) {
            b = 255;
        }
        if (al < 0) {
            al = 0;
        }
        return (al << 24) | (r << 16) | (g << 8) | b;
    }

    private static float triSignArea2(float x0, float y0, float x1, float y1, float x2, float y2) {
        return (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
    }

    private static float wrapU(float t) {
        if (t != t) {
            return 0.0f;
        }
        float x = t - (float) (int) t;
        if (x < 0.0f) {
            x += 1.0f;
        }
        if (x >= 0.999999f) {
            return 0.0f;
        }
        return x;
    }

    private boolean toScreenWithEzv(
            float tHalf, float a, int tw, int th, float[] w, float[] scr, int off, float[] ezOut, int ezi) {
        float dx = w[0] - eyeX;
        float dy = w[1] - eyeY;
        float dz = w[2] - eyeZ;
        float exv = dx * vRight[0] + dy * vRight[1] + dz * vRight[2];
        float eyv = dx * vUp[0] + dy * vUp[1] + dz * vUp[2];
        float ezv = dx * vFwd[0] + dy * vFwd[1] + dz * vFwd[2];
        if (ezv < zNear) {
            return false;
        }
        scr[off] = 0.5f * (float) tw + 0.5f * (float) tw * (exv / (ezv * tHalf * a));
        scr[off + 1] = 0.5f * (float) th - 0.5f * (float) th * (eyv / (ezv * tHalf));
        ezOut[ezi] = ezv;
        return true;
    }

    /** Sort {@code order[0..n-1]} so that {@code depth[order[0]]} is largest (painter, far to near). */
    private static void sortOrderDescDepth(int[] order, float[] depth, int n) {
        if (n < 2) {
            return;
        }
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                if (depth[order[i]] < depth[order[j]]) {
                    int s = order[i];
                    order[i] = order[j];
                    order[j] = s;
                }
            }
        }
    }

    public void end() {
    }

    public String loadM3G(String resPath) {
        return "Loading .m3g requires M3G; check Render3D.getBackend() === \"m3g\".";
    }

    public void worldAnimate(int timeMs) {
    }

    public String m3gNodeTranslate(int userId, float dx, float dy, float dz) {
        return "m3gNodeTranslate requires M3G backend";
    }

    public String m3gNodeSetTranslation(int userId, float x, float y, float z) {
        return "m3gNodeSetTranslation requires M3G backend";
    }

    public float[] m3gNodeGetTranslation(int userId) {
        return null;
    }

    public String m3gNodeSetOrientation(int userId, float angleDeg, float ax, float ay, float az) {
        return "m3gNodeSetOrientation requires M3G backend";
    }

    public String m3gAnimSetActiveInterval(int userId, int startMs, int endMs) {
        return "m3gAnimSetActiveInterval requires M3G backend";
    }

    public String m3gAnimSetPosition(int userId, int sequence, int timeMs) {
        return "m3gAnimSetPosition requires M3G backend";
    }

    public String m3gAnimSetSpeed(int userId, float speed) {
        return "m3gAnimSetSpeed requires M3G backend";
    }

    public int m3gKeyframeDurationTrack0(int userId) {
        return -1;
    }

    public String getSceneInfo() {
        return "soft im=" + (meshPos != null) + " cull=" + cullBack + " tris<=" + maxTris
                + (depthBufferEnabled ? " zbuf=1" : " zbuf=0")
                + (texW > 0 ? (" tex=" + texW + "x" + texH) : "")
                + (texFilterNearest ? " samp=near" : " samp=linear")
                + (texWrapRepeat ? " wrap=R" : " wrap=C");
    }

    public int getEffectiveMaxTriangles() {
        return maxTris;
    }

    public void setTextureFilterNearest(boolean nearest) {
        texFilterNearest = nearest;
    }

    public void setTextureWrapRepeat(boolean repeat) {
        texWrapRepeat = repeat;
    }

    public static void setIdentity4(float[] o) {
        for (int i = 0; i < 16; i++) {
            o[i] = 0.0f;
        }
        o[0] = o[5] = o[10] = o[15] = 1.0f;
    }

    private void buildWorldMatrix() {
        setIdentity4(mRot);
        float rad = objectAngle * DEG2RAD;
        float co = (float) Math.cos((double) rad);
        float sn = (float) Math.sin((double) rad);
        mRot[0] = co;
        mRot[1] = 0.0f;
        mRot[2] = -sn;
        mRot[3] = 0.0f;
        mRot[4] = 0.0f;
        mRot[5] = 1.0f;
        mRot[6] = 0.0f;
        mRot[7] = 0.0f;
        mRot[8] = sn;
        mRot[9] = 0.0f;
        mRot[10] = co;
        mRot[11] = 0.0f;
        mRot[12] = 0.0f;
        mRot[13] = 0.0f;
        mRot[14] = 0.0f;
        mRot[15] = 1.0f;
        mul4(mRot, mUser, mWorld);
    }

    private void mulModel(float[] pos, int vi, float[] out) {
        int p = vi * 3;
        float x = pos[p];
        float y = pos[p + 1];
        float z = pos[p + 2];
        out[0] = mWorld[0] * x + mWorld[4] * y + mWorld[8] * z + mWorld[12];
        out[1] = mWorld[1] * x + mWorld[5] * y + mWorld[9] * z + mWorld[13];
        out[2] = mWorld[2] * x + mWorld[6] * y + mWorld[10] * z + mWorld[14];
    }

    private static void readN3(float[] nbuf, int vi, float[] o) {
        int p = vi * 3;
        if (nbuf == null || p + 2 >= nbuf.length) {
            o[0] = 0.0f;
            o[1] = 1.0f;
            o[2] = 0.0f;
            return;
        }
        o[0] = nbuf[p];
        o[1] = nbuf[p + 1];
        o[2] = nbuf[p + 2];
    }

    private static void nrm3(float[] m, float[] in, float[] o) {
        float x = in[0], y = in[1], z = in[2];
        o[0] = m[0] * x + m[4] * y + m[8] * z;
        o[1] = m[1] * x + m[5] * y + m[9] * z;
        o[2] = m[2] * x + m[6] * y + m[10] * z;
        float l2 = o[0] * o[0] + o[1] * o[1] + o[2] * o[2];
        if (l2 < 0.0000001f) {
            o[0] = 0.0f;
            o[1] = 1.0f;
            o[2] = 0.0f;
        } else {
            float L = 1.0f / (float) Math.sqrt((double) l2);
            o[0] *= L;
            o[1] *= L;
            o[2] *= L;
        }
    }

    private static void mul4(float[] a, float[] b, float[] out) {
        for (int j = 0; j < 4; j++) {
            for (int r = 0; r < 4; r++) {
                float s = 0.0f;
                for (int k = 0; k < 4; k++) {
                    s += a[k * 4 + r] * b[j * 4 + k];
                }
                out[j * 4 + r] = s;
            }
        }
    }

    private boolean toScreen(float tHalf, float a, int tw, int th, float[] w, float[] scr, int off) {
        float dx = w[0] - eyeX;
        float dy = w[1] - eyeY;
        float dz = w[2] - eyeZ;
        float exv = dx * vRight[0] + dy * vRight[1] + dz * vRight[2];
        float eyv = dx * vUp[0] + dy * vUp[1] + dz * vUp[2];
        float ezv = dx * vFwd[0] + dy * vFwd[1] + dz * vFwd[2];
        if (ezv < zNear) {
            return false;
        }
        scr[off] = 0.5f * (float) tw + 0.5f * (float) tw * (exv / (ezv * tHalf * a));
        scr[off + 1] = 0.5f * (float) th - 0.5f * (float) th * (eyv / (ezv * tHalf));
        return true;
    }
}
