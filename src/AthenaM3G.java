import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.AnimationController;
import javax.microedition.m3g.AnimationTrack;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Loader;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Node;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.m3g.World;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.KeyframeSequence;
import javax.microedition.m3g.Texture2D;
import javax.microedition.lcdui.Image;

/**
 * JSR-184 (M3G): immediate mode with a mesh from {@link #setTriangleStripMesh} in JS,
 * or a {@link Loader} scene (String). Concrete geometry is not hard-coded in this class.
 */
public final class AthenaM3G {

    public static final boolean M3G_CLASS_PRESENT;

    static {
        boolean ok = false;
        try {
            Class.forName("javax.microedition.m3g.Graphics3D");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        M3G_CLASS_PRESENT = ok;
    }

    public static boolean isApiAvailable() {
        if (!M3G_CLASS_PRESENT) {
            return false;
        }
        try {
            return Graphics3D.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private Graphics3D g3d;
    private final Background background = new Background();
    private final Camera camera = new Camera();
    private final Light headLight = new Light();
    private final Light ambient = new Light();
    private final Transform camTransform = new Transform();
    private final Transform objTransform = new Transform();
    private final Transform drawTransform = new Transform();
    private final Transform tmpLight = new Transform();
    /** Rotation only: local +Z axis maps to world (lx,ly,lz) so directional rays align with -L. */
    private final Transform headLightTrans = new Transform();
    private final Transform[] oStack = new Transform[8];
    private int oStackDep;
    /** M3G {@link Transform} uses row-major 4x4; JS passes column-major for {@link #setObjectTransformFromColumnMajor}. */
    private final float[] m3gFromColTmp = new float[16];
    /** Reused row-major basis for directional head light (see {@link #setHeadLightTransformForDirection}). */
    private final float[] headLightMatReuse = new float[16];
    /** Reused view matrix for {@link #buildLookAtCameraTransform()}. */
    private final float[] lookAtMatReuse = new float[16];
    private VertexBuffer meshVb;
    private TriangleStripArray meshIb;
    private Appearance meshAppearance;
    private boolean inited;
    private boolean targetBound;
    private boolean useLoadedWorld;
    private World loadedWorld;
    private float camX, camY, camZ = 5.0f;
    private boolean useLookAt;
    private float laTx, laTy, laTz, laUx, laUy, laUz;
    private float fovY = 55.0f;
    private float zNear = 0.1f;
    private float zFar = 200.0f;
    private float objectAngle;
    private float lDirX, lDirY = 1.0f, lDirZ;
    private int matAr = 0x88, matAg = 0x88, matAb = 0xcc, matDr = 0xff, matDg = 0xff, matDb = 0xff;
    private boolean cullBack = true;
    private float[] meshUvPending;
    private String texturePathPending;
    /** Default nearest + repeat matches previous immediate-mesh behaviour. */
    private boolean texFilterNearest = true;
    private boolean texWrapRepeat = true;
    private static final int BIND_HINTS = Graphics3D.DITHER | Graphics3D.TRUE_COLOR;

    public AthenaM3G() {
        for (int i = 0; i < 8; i++) {
            oStack[i] = new Transform();
        }
    }

    private void syncImmediateAppearanceShading() {
        if (meshAppearance == null) {
            return;
        }
        try {
            Material mat = meshAppearance.getMaterial();
            if (mat != null) {
                int amb = 0xff000000 | (matAr << 16) | (matAg << 8) | matAb;
                int dif = 0xff000000 | (matDr << 16) | (matDg << 8) | matDb;
                mat.setColor(Material.AMBIENT, amb);
                mat.setColor(Material.DIFFUSE, dif);
            }
            PolygonMode poly = meshAppearance.getPolygonMode();
            if (poly != null) {
                poly.setCulling(cullBack ? PolygonMode.CULL_BACK : PolygonMode.CULL_NONE);
            }
        } catch (Throwable t) {
        }
    }

    private void applyTextureSamplerState(Texture2D tex) {
        if (tex == null) {
            return;
        }
        try {
            int filt = texFilterNearest ? Texture2D.FILTER_NEAREST : Texture2D.FILTER_LINEAR;
            tex.setFiltering(Texture2D.FILTER_BASE_LEVEL, filt);
            int wrapS = texWrapRepeat ? Texture2D.WRAP_REPEAT : Texture2D.WRAP_CLAMP;
            int wrapT = texWrapRepeat ? Texture2D.WRAP_REPEAT : Texture2D.WRAP_CLAMP;
            tex.setWrapping(wrapS, wrapT);
            tex.setBlending(Texture2D.FUNC_MODULATE);
        } catch (Throwable t) {
        }
    }

    private Appearance createMeshAppearance(Texture2D tex) {
        int amb = 0xff000000 | (matAr << 16) | (matAg << 8) | matAb;
        int dif = 0xff000000 | (matDr << 16) | (matDg << 8) | matDb;
        Material mat = new Material();
        mat.setColor(Material.AMBIENT, amb);
        mat.setColor(Material.DIFFUSE, dif);
        mat.setColor(Material.EMISSIVE, 0);
        mat.setColor(Material.SPECULAR, 0);
        mat.setShininess(0.0f);
        CompositingMode comp = new CompositingMode();
        comp.setDepthTestEnable(true);
        comp.setDepthWriteEnable(true);
        try {
            if (tex != null) {
                comp.setBlending(CompositingMode.ALPHA);
            }
        } catch (Throwable t) {
        }
        PolygonMode poly = new PolygonMode();
        poly.setCulling(cullBack ? PolygonMode.CULL_BACK : PolygonMode.CULL_NONE);
        poly.setShading(PolygonMode.SHADE_SMOOTH);
        Appearance app = new Appearance();
        app.setMaterial(mat);
        app.setCompositingMode(comp);
        app.setPolygonMode(poly);
        if (tex != null) {
            applyTextureSamplerState(tex);
            app.setTexture(0, tex);
        }
        return app;
    }

    public int getEffectiveMaxTriangles() {
        return -1;
    }

    public void setTextureFilterNearest(boolean nearest) {
        texFilterNearest = nearest;
        try {
            if (meshAppearance != null) {
                Texture2D t = meshAppearance.getTexture(0);
                applyTextureSamplerState(t);
            }
        } catch (Throwable t) {
        }
    }

    public void setTextureWrapRepeat(boolean repeat) {
        texWrapRepeat = repeat;
        try {
            if (meshAppearance != null) {
                Texture2D t = meshAppearance.getTexture(0);
                applyTextureSamplerState(t);
            }
        } catch (Throwable t) {
        }
    }

    public void init() {
        if (inited) {
            return;
        }
        g3d = Graphics3D.getInstance();
        headLight.setMode(Light.DIRECTIONAL);
        headLight.setColor(0xffffff);
        // Match software raster: (mat_amb + mat_dif * max(0, N·L)) per channel; exponents 1.0, not 0.35/1.2.
        headLight.setIntensity(1.0f);
        ambient.setMode(Light.AMBIENT);
        ambient.setColor(0xffffff);
        ambient.setIntensity(1.0f);
        background.setColorClearEnable(true);
        background.setDepthClearEnable(true);
        inited = true;
    }

    public boolean isInitialized() {
        return inited;
    }

    public void setPerspective(float aspect, float fovYDeg, float near, float far) {
        this.fovY = fovYDeg;
        this.zNear = near;
        this.zFar = far;
        camera.setPerspective(fovY, aspect, zNear, zFar);
    }

    public void setBackgroundColor(int r, int g, int b) {
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        int argb = 0xff000000 | (r << 16) | (g << 8) | b;
        background.setColor(argb);
    }

    public void setMaxTriangles(int max) {
    }

    public void setCameraPosition(float x, float y, float z) {
        useLookAt = false;
        camX = x;
        camY = y;
        camZ = z;
    }

    public void setLookAt(float ex, float ey, float ez, float tx, float ty, float tz, float ux, float uy, float uz) {
        useLookAt = true;
        camX = ex;
        camY = ey;
        camZ = ez;
        laTx = tx;
        laTy = ty;
        laTz = tz;
        laUx = ux;
        laUy = uy;
        laUz = uz;
    }

    public void setObjectRotationY(float degrees) {
        objectAngle = degrees;
    }

    public void setObjectTransform(Transform t) {
        if (t != null) {
            objTransform.set(t);
        } else {
            objTransform.setIdentity();
        }
    }

    /**
     * 4x4 in <strong>column-major</strong> (16 {@code float}), same as OpenGL. Converted
     * to M3G row-major in {@code Transform.set}. Keeps M3G types off {@link Athena2ME}
     * until the runtime is known to expose JSR-184.
     */
    public void setObjectTransformFromColumnMajor(float[] m) {
        if (m == null || m.length < 16) {
            objTransform.setIdentity();
            return;
        }
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                m3gFromColTmp[r * 4 + c] = m[c * 4 + r];
            }
        }
        objTransform.set(m3gFromColTmp);
    }

    public void setObjectTransformIdentity() {
        objTransform.setIdentity();
    }

    public void pushObjectMatrix() {
        if (oStackDep >= 8) {
            return;
        }
        oStack[oStackDep].set(objTransform);
        oStackDep++;
    }

    public void popObjectMatrix() {
        if (oStackDep <= 0) {
            return;
        }
        oStackDep--;
        objTransform.set(oStack[oStackDep]);
    }

    public void setBackfaceCulling(boolean on) {
        cullBack = on;
        syncImmediateAppearanceShading();
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
        syncImmediateAppearanceShading();
    }

    public void setMaterialDiffuse(int r, int g, int b) {
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        matDr = r;
        matDg = g;
        matDb = b;
        syncImmediateAppearanceShading();
    }

    public void setTexture2DPath(String p) {
        texturePathPending = p;
    }

    public void setTexCoords(float[] uvs) {
        meshUvPending = uvs;
    }

    public void worldAnimate(int timeMs) {
        if (!useLoadedWorld || loadedWorld == null) {
            return;
        }
        try {
            // World time is int (JSR-184). Truncated wall ms is often negative in 2026+;
            // skipping on timeMs < 0 prevented animate() from ever running.
            loadedWorld.animate(timeMs);
        } catch (Throwable t) {
        }
    }

    public String getSceneInfo() {
        return "M3G world=" + useLoadedWorld
                + " imm=" + (meshVb != null)
                + " cull=" + cullBack;
    }

    private Object3D m3gFindObject(int userId) {
        if (!useLoadedWorld || loadedWorld == null) {
            return null;
        }
        try {
            return loadedWorld.find(userId);
        } catch (Throwable t) {
            return null;
        }
    }

    public String m3gNodeTranslate(int userId, float dx, float dy, float dz) {
        Object3D o = m3gFindObject(userId);
        if (o == null) {
            return "m3g: find failed or no world";
        }
        if (!(o instanceof Node)) {
            return "m3g: not a Node";
        }
        try {
            ((Node) o).translate(dx, dy, dz);
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : "translate failed";
        }
    }

    public String m3gNodeSetTranslation(int userId, float x, float y, float z) {
        Object3D o = m3gFindObject(userId);
        if (o == null) {
            return "m3g: find failed or no world";
        }
        if (!(o instanceof Node)) {
            return "m3g: not a Node";
        }
        try {
            ((Node) o).setTranslation(x, y, z);
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : "setTranslation failed";
        }
    }

    public float[] m3gNodeGetTranslation(int userId) {
        Object3D o = m3gFindObject(userId);
        if (!(o instanceof Node)) {
            return null;
        }
        float[] t3 = new float[3];
        try {
            ((Node) o).getTranslation(t3);
            return t3;
        } catch (Throwable t) {
            return null;
        }
    }

    public String m3gNodeSetOrientation(int userId, float angleDeg, float ax, float ay, float az) {
        Object3D o = m3gFindObject(userId);
        if (o == null) {
            return "m3g: find failed or no world";
        }
        if (!(o instanceof Node)) {
            return "m3g: not a Node";
        }
        try {
            ((Node) o).setOrientation(angleDeg, ax, ay, az);
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : "setOrientation failed";
        }
    }

    public String m3gAnimSetActiveInterval(int userId, int startMs, int endMs) {
        Object3D o = m3gFindObject(userId);
        if (o == null) {
            return "m3g: find failed or no world";
        }
        if (!(o instanceof AnimationController)) {
            return "m3g: not an AnimationController";
        }
        try {
            ((AnimationController) o).setActiveInterval(startMs, endMs);
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : "setActiveInterval failed";
        }
    }

    public String m3gAnimSetPosition(int userId, int sequence, int timeMs) {
        Object3D o = m3gFindObject(userId);
        if (o == null) {
            return "m3g: find failed or no world";
        }
        if (!(o instanceof AnimationController)) {
            return "m3g: not an AnimationController";
        }
        try {
            ((AnimationController) o).setPosition(sequence, timeMs);
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : "setPosition failed";
        }
    }

    public String m3gAnimSetSpeed(int userId, float speed) {
        Object3D o = m3gFindObject(userId);
        if (o == null) {
            return "m3g: find failed or no world";
        }
        if (!(o instanceof AnimationController)) {
            return "m3g: not an AnimationController";
        }
        try {
            // WTK / some JSR-184 builds: setSpeed(float speed, int sequenceIndex)
            ((AnimationController) o).setSpeed(speed, 0);
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : "setSpeed failed";
        }
    }

    public int m3gKeyframeDurationTrack0(int userId) {
        Object3D o = m3gFindObject(userId);
        if (!(o instanceof Node)) {
            return -1;
        }
        try {
            AnimationTrack tr = ((Node) o).getAnimationTrack(0);
            if (tr == null) {
                return -1;
            }
            KeyframeSequence ks = tr.getKeyframeSequence();
            if (ks == null) {
                return -1;
            }
            return ks.getDuration();
        } catch (Throwable t) {
            return -1;
        }
    }

    public Transform getObjectTransform() {
        return objTransform;
    }

    private static float maxAbsElements(float[] a) {
        float m = 0.0f;
        for (int i = 0; i < a.length; i++) {
            float x = a[i];
            if (x < 0.0f) {
                x = -x;
            }
            if (x > m) {
                m = x;
            }
        }
        return m;
    }

    /**
     * Unit-tile U/V in [0,1] for M3G vertex array encoding. The software rasterizer wraps per
     * fragment at sample time, so 1.0 and 0.0 stay distinct for interpolation. Per-vertex here we
     * must not map 1.0f to fractional 0, or both edges of a unit quad (0 and 1) quantize to 0 and
     * the pipeline interpolates a constant — no visible texture. Integer UVs: repeat {0,1} by parity
     * when the fractional part is ~0.
     */
    private static float vertexTexCoord01(float t) {
        if (t != t) {
            return 0.0f;
        }
        double x = t - Math.floor(t);
        if (x < 0.0) {
            x += 1.0;
        }
        if (x < 1.0e-5) {
            long k = (long) Math.floor((double) t + 1.0e-6);
            if (k < 0L) {
                return 0.0f;
            }
            return ((k & 1L) == 1L) ? 1.0f : 0.0f;
        }
        if (x > 0.999999) {
            return 1.0f;
        }
        return (float) x;
    }

    /**
     * M3G CLDC: {@link VertexArray#set(int, int, float[])} is often missing; use
     * {@code short[]} with 2 bytes per component. {@link VertexBuffer#setPositions} applies
     * per-component: {@code float = (short) * scale}.
     */
    private static void quantizeToShorts(float[] src, short[] dst, float scale) {
        for (int i = 0; i < src.length; i++) {
            int v = (int) (src[i] / scale);
            if (v > 32767) {
                v = 32767;
            } else if (v < -32768) {
                v = -32768;
            }
            dst[i] = (short) v;
        }
    }

    /**
     * Replaces the immediate draw mesh. {@code pos.length} = 3 * vertex count;
     * {@code normals} optional, same float count as {@code pos} if present.
     * Optional: call {@link #setTexture2DPath} and {@link #setTexCoords} (length 2 * vertex count) before.
     */
    public void setTriangleStripMesh(float[] pos, int[] stripLen, float[] normals) {
        useLoadedWorld = false;
        loadedWorld = null;
        buildImmediateMeshFromStrip(pos, stripLen, normals, meshUvPending);
    }

    public void setIndexedTriangleMesh(float[] pos, int[] idx, float[] normals) {
        useLoadedWorld = false;
        loadedWorld = null;
        if (pos == null || idx == null) {
            return;
        }
        if (pos.length < 9 || (pos.length % 3) != 0) {
            return;
        }
        if (idx.length < 3 || (idx.length % 3) != 0) {
            return;
        }
        int ntri = idx.length / 3;
        int[] strip = new int[ntri];
        for (int i = 0; i < ntri; i++) {
            strip[i] = 3;
        }
        int nv3 = 3 * ntri;
        float[] exp = new float[nv3 * 3];
        float[] nexp = null;
        if (normals != null && normals.length == pos.length) {
            nexp = new float[nv3 * 3];
        }
        for (int t = 0; t < ntri; t++) {
            for (int k = 0; k < 3; k++) {
                int vix = idx[t * 3 + k];
                if (vix < 0 || (vix * 3) + 2 >= pos.length) {
                    return;
                }
                int o = (t * 3 + k) * 3;
                exp[o] = pos[vix * 3];
                exp[o + 1] = pos[vix * 3 + 1];
                exp[o + 2] = pos[vix * 3 + 2];
                if (nexp != null) {
                    nexp[o] = normals[vix * 3];
                    nexp[o + 1] = normals[vix * 3 + 1];
                    nexp[o + 2] = normals[vix * 3 + 2];
                }
            }
        }
        float[] uvUse = null;
        if (meshUvPending != null && meshUvPending.length == (pos.length / 3) * 2) {
            uvUse = new float[nv3 * 2];
            for (int t = 0; t < ntri; t++) {
                for (int k = 0; k < 3; k++) {
                    int vix = idx[t * 3 + k];
                    int o = (t * 3 + k) * 2;
                    uvUse[o] = meshUvPending[vix * 2];
                    uvUse[o + 1] = meshUvPending[vix * 2 + 1];
                }
            }
        }
        buildImmediateMeshFromStrip(exp, strip, nexp, uvUse);
    }

    private void buildImmediateMeshFromStrip(float[] pos, int[] stripLen, float[] normals, float[] uvs) {
        meshVb = null;
        meshIb = null;
        meshAppearance = null;
        if (pos == null || stripLen == null) {
            return;
        }
        if (pos.length < 9 || (pos.length % 3) != 0) {
            return;
        }
        if (stripLen.length == 0) {
            return;
        }
        int nv = pos.length / 3;
        if (normals != null && normals.length != pos.length) {
            return;
        }
        if (uvs != null && uvs.length != nv * 2) {
            uvs = null;
        }
        Texture2D t2d = null;
        if (uvs != null && texturePathPending != null) {
            t2d = tryLoadTexture2D();
        }
        float posMax = maxAbsElements(pos);
        if (posMax < 1.0e-8f) {
            posMax = 1.0f;
        }
        float posScale = posMax / 32767.0f;
        short[] pShort = new short[pos.length];
        quantizeToShorts(pos, pShort, posScale);
        VertexArray va = new VertexArray(nv, 3, 2);
        va.set(0, nv, pShort);
        meshVb = new VertexBuffer();
        meshVb.setPositions(va, posScale, null);
        if (normals != null) {
            float nMax = maxAbsElements(normals);
            if (nMax < 1.0e-8f) {
                nMax = 1.0f;
            }
            float nScale = nMax / 32767.0f;
            short[] nShort = new short[normals.length];
            quantizeToShorts(normals, nShort, nScale);
            VertexArray na = new VertexArray(nv, 3, 2);
            na.set(0, nv, nShort);
            meshVb.setNormals(na);
        }
        if (uvs != null && t2d != null) {
            short[] tShort = new short[uvs.length];
            for (int i = 0; i < uvs.length; i++) {
                float c = vertexTexCoord01(uvs[i]);
                int v = (int) (c * 32767.0f);
                if (v > 32767) {
                    v = 32767;
                } else if (v < 0) {
                    v = 0;
                }
                tShort[i] = (short) v;
            }
            VertexArray tva = new VertexArray(nv, 2, 2);
            tva.set(0, nv, tShort);
            meshVb.setTexCoords(0, tva, 1.0f / 32767.0f, null);
        }
        meshIb = new TriangleStripArray(0, stripLen);
        meshAppearance = createMeshAppearance((t2d != null && uvs != null) ? t2d : null);
    }

    private Texture2D tryLoadTexture2D() {
        if (texturePathPending == null) {
            return null;
        }
        String p0 = texturePathPending;
        if (p0 == null || p0.length() == 0) {
            return null;
        }
        String pWithLead = p0.charAt(0) == '/' ? p0 : ("/" + p0);
        String pNoLead = p0.charAt(0) == '/' ? p0.substring(1) : p0;
        String[] tryNames = { p0, pWithLead, pNoLead };
        Image im = null;
        for (int t = 0; t < tryNames.length; t++) {
            if (tryNames[t] == null) {
                continue;
            }
            try {
                im = Image.createImage(tryNames[t]);
            } catch (Throwable e) {
                im = null;
            }
            if (im != null) {
                break;
            }
        }
        if (im == null) {
            return null;
        }
        try {
            Image2D i2d;
            try {
                i2d = new Image2D(Image2D.RGBA, im);
            } catch (Throwable tRgb) {
                i2d = new Image2D(Image2D.RGB, im);
            }
            return new Texture2D(i2d);
        } catch (Throwable t) {
            return null;
        }
    }

    public void clearImmediateMesh() {
        meshVb = null;
        meshIb = null;
        meshAppearance = null;
        meshUvPending = null;
        texturePathPending = null;
    }

    public String loadM3G(String resPath) {
        useLoadedWorld = false;
        loadedWorld = null;
        clearImmediateMesh();
        try {
            if (resPath == null) {
                return "null path";
            }
            String p = resPath;
            if (p.length() == 0) {
                return "empty path";
            }
            String pNoLead = p.charAt(0) == '/' ? p.substring(1) : p;
            String pWithLead = p.charAt(0) == '/' ? p : ("/" + p);

            Object3D[] roots = null;
            String[] tryNames = { pWithLead, pNoLead, p };
            for (int t = 0; t < tryNames.length; t++) {
                if (tryNames[t] == null) {
                    continue;
                }
                try {
                    roots = Loader.load(tryNames[t]);
                } catch (Throwable ex) {
                    roots = null;
                }
                if (roots != null && roots.length > 0) {
                    break;
                }
            }
            if (roots == null || roots.length == 0) {
                return "Loader failed or empty: " + resPath;
            }
            for (int i = 0; i < roots.length; i++) {
                if (roots[i] instanceof World) {
                    loadedWorld = (World) roots[i];
                    useLoadedWorld = true;
                    return null;
                }
            }
            World w = new World();
            Group g = new Group();
            for (int i = 0; i < roots.length; i++) {
                if (roots[i] instanceof Node) {
                    g.addChild((Node) roots[i]);
                }
            }
            w.addChild(g);
            Camera c = new Camera();
            c.setPerspective(fovY, 1.0f, zNear, zFar);
            w.addChild(c);
            w.setActiveCamera(c);
            w.setBackground(background);
            loadedWorld = w;
            useLoadedWorld = true;
            return null;
        } catch (Throwable t) {
            return t.getMessage() != null ? t.getMessage() : "load failed";
        }
    }

    public void begin(AthenaCanvas canvas) {
        if (!inited) {
            init();
        }
        if (g3d == null) {
            g3d = Graphics3D.getInstance();
        }
        Graphics g = canvas.getBindGraphicsFor3D();
        int w = canvas.getTargetWidth3D();
        int h = canvas.getTargetHeight3D();
        g3d.bindTarget(g, true, BIND_HINTS);
        g3d.setViewport(0, 0, w, h);
        targetBound = true;
    }

    public void beginFrame(AthenaCanvas canvas) {
        begin(canvas);
        setupFrame();
    }

    public void setupFrame() {
        if (!targetBound) {
            return;
        }
        g3d.clear(background);
        if (useLoadedWorld && loadedWorld != null) {
            return;
        }
        camTransform.setIdentity();
        if (!useLookAt) {
            camTransform.postTranslate(camX, camY, camZ);
        } else {
            buildLookAtCameraTransform();
        }
        g3d.setCamera(camera, camTransform);
        g3d.resetLights();
        tmpLight.setIdentity();
        g3d.addLight(ambient, tmpLight);
        float llen = (float) Math.sqrt((double) (lDirX * lDirX + lDirY * lDirY + lDirZ * lDirZ));
        if (llen < 1.0e-8f) {
            llen = 1.0f;
        }
        setHeadLightTransformForDirection(lDirX / llen, lDirY / llen, lDirZ / llen, headLightTrans);
        g3d.addLight(headLight, headLightTrans);
    }

    /**
     * Maps local -Z to world -L (direction toward light = L) so the fixed directional
     * in {@link #headLight} matches {@code setGlobalLightDirection} (WTK has no {@code Light#setDirection}).
     */
    private void setHeadLightTransformForDirection(float lx, float ly, float lz, Transform out) {
        out.setIdentity();
        float uxx = 0.0f, uyy = 1.0f, uzz = 0.0f;
        float rx = uyy * lz - uzz * ly;
        float ry = uzz * lx - uxx * lz;
        float rz = uxx * ly - uyy * lx;
        float rl = (float) Math.sqrt((double) (rx * rx + ry * ry + rz * rz));
        if (rl < 1.0e-8f) {
            uxx = 1.0f;
            uyy = 0.0f;
            uzz = 0.0f;
            rx = uyy * lz - uzz * ly;
            ry = uzz * lx - uxx * lz;
            rz = uxx * ly - uyy * lx;
            rl = (float) Math.sqrt((double) (rx * rx + ry * ry + rz * rz));
        }
        if (rl < 1.0e-8f) {
            out.setIdentity();
            return;
        }
        rx /= rl;
        ry /= rl;
        rz /= rl;
        float uux = ry * lz - rz * ly;
        float uuy = rz * lx - rx * lz;
        float uuz = rx * ly - ry * lx;
        float uul = (float) Math.sqrt((double) (uux * uux + uuy * uuy + uuz * uuz));
        if (uul < 1.0e-8f) {
            out.setIdentity();
            return;
        }
        uux /= uul;
        uuy /= uul;
        uuz /= uul;
        float[] m = headLightMatReuse;
        m[0] = rx;
        m[1] = uux;
        m[2] = lx;
        m[3] = 0.0f;
        m[4] = ry;
        m[5] = uuy;
        m[6] = ly;
        m[7] = 0.0f;
        m[8] = rz;
        m[9] = uuz;
        m[10] = lz;
        m[11] = 0.0f;
        m[12] = 0.0f;
        m[13] = 0.0f;
        m[14] = 0.0f;
        m[15] = 1.0f;
        out.set(m);
    }

    private void buildLookAtCameraTransform() {
        float fx = laTx - camX;
        float fy = laTy - camY;
        float fz = laTz - camZ;
        float fl = (float) Math.sqrt((double) (fx * fx + fy * fy + fz * fz));
        if (fl < 1.0e-8f) {
            camTransform.postTranslate(camX, camY, camZ);
            return;
        }
        fx /= fl;
        fy /= fl;
        fz /= fl;
        float uxi = laUx;
        float uyi = laUy;
        float uzi = laUz;
        float rx = fy * uzi - fz * uyi;
        float ry = fz * uxi - fx * uzi;
        float rz = fx * uyi - fy * uxi;
        float rl = (float) Math.sqrt((double) (rx * rx + ry * ry + rz * rz));
        if (rl < 1.0e-8f) {
            camTransform.postTranslate(camX, camY, camZ);
            return;
        }
        rx /= rl;
        ry /= rl;
        rz /= rl;
        float uux = ry * fz - rz * fy;
        float uuy = rz * fx - rx * fz;
        float uuz = rx * fy - ry * fx;
        float uul = (float) Math.sqrt((double) (uux * uux + uuy * uuy + uuz * uuz));
        if (uul < 1.0e-8f) {
            camTransform.postTranslate(camX, camY, camZ);
            return;
        }
        uux /= uul;
        uuy /= uul;
        uuz /= uul;
        float[] m = lookAtMatReuse;
        m[0] = rx;
        m[1] = uux;
        m[2] = -fx;
        m[3] = camX;
        m[4] = ry;
        m[5] = uuy;
        m[6] = -fy;
        m[7] = camY;
        m[8] = rz;
        m[9] = uuz;
        m[10] = -fz;
        m[11] = camZ;
        m[12] = 0.0f;
        m[13] = 0.0f;
        m[14] = 0.0f;
        m[15] = 1.0f;
        camTransform.set(m);
    }

    public void renderImmediate() {
        if (!targetBound) {
            return;
        }
        if (useLoadedWorld && loadedWorld != null) {
            g3d.render(loadedWorld);
            return;
        }
        if (meshVb == null || meshIb == null || meshAppearance == null) {
            return;
        }
        drawTransform.set(objTransform);
        if (objectAngle != 0.0f) {
            drawTransform.postRotate(objectAngle, 0.0f, 1.0f, 0.0f);
        }
        g3d.render(meshVb, meshIb, meshAppearance, drawTransform);
    }

    public void end() {
        if (g3d != null && targetBound) {
            g3d.releaseTarget();
        }
        targetBound = false;
    }

    public void dispose() {
        end();
        inited = false;
        g3d = null;
    }
}
