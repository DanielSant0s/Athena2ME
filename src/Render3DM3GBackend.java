/**
 * Thin wrapper over {@link AthenaM3G}; use only when {@link AthenaM3G#isApiAvailable()} is true.
 */
public final class Render3DM3GBackend implements Render3DBackend {
    private final AthenaM3G core;

    public Render3DM3GBackend(AthenaM3G core) {
        this.core = core;
    }

    public String getId() {
        return "m3g";
    }

    public void setMaxTriangles(int max) {
        core.setMaxTriangles(max);
    }

    public void setDepthBuffer(boolean on) {
    }

    public void init(AthenaCanvas canvas) {
        core.init();
    }

    public void setPerspectiveFromViewport(float aspect, float fovYDeg, float near, float far) {
        core.setPerspective(aspect, fovYDeg, near, far);
    }

    public void setBackgroundColor(int r, int g, int b) {
        core.setBackgroundColor(r, g, b);
    }

    public void setCameraPosition(float x, float y, float z) {
        core.setCameraPosition(x, y, z);
    }

    public void setLookAt(float ex, float ey, float ez, float tx, float ty, float tz, float ux, float uy, float uz) {
        core.setLookAt(ex, ey, ez, tx, ty, tz, ux, uy, uz);
    }

    public void setObjectRotationY(float degrees) {
        core.setObjectRotationY(degrees);
    }

    public void setObjectTransformFromColumnMajor(float[] m) {
        core.setObjectTransformFromColumnMajor(m);
    }

    public void setObjectTransformIdentity() {
        core.setObjectTransformIdentity();
    }

    public void pushObjectMatrix() {
        core.pushObjectMatrix();
    }

    public void popObjectMatrix() {
        core.popObjectMatrix();
    }

    public void setTriangleStripMesh(float[] pos, int[] stripLen, float[] normals) {
        core.setTriangleStripMesh(pos, stripLen, normals);
    }

    public void setIndexedTriangleMesh(float[] pos, int[] indices, float[] normals) {
        core.setIndexedTriangleMesh(pos, indices, normals);
    }

    public void setBackfaceCulling(boolean on) {
        core.setBackfaceCulling(on);
    }

    public void setGlobalLightDirection(float x, float y, float z) {
        core.setGlobalLightDirection(x, y, z);
    }

    public void setMaterialAmbient(int r, int g, int b) {
        core.setMaterialAmbient(r, g, b);
    }

    public void setMaterialDiffuse(int r, int g, int b) {
        core.setMaterialDiffuse(r, g, b);
    }

    public void setTexture2DPath(String path) {
        core.setTexture2DPath(path);
    }

    public void setTexCoords(float[] uvs) {
        core.setTexCoords(uvs);
    }

    public void clearImmediateMesh() {
        core.clearImmediateMesh();
    }

    public void beginFrame(AthenaCanvas canvas) {
        core.beginFrame(canvas);
    }

    public void renderImmediate(AthenaCanvas canvas) {
        core.renderImmediate();
    }

    public void end() {
        core.end();
    }

    public String loadM3G(String resPath) {
        return core.loadM3G(resPath);
    }

    public void worldAnimate(int timeMs) {
        core.worldAnimate(timeMs);
    }

    public String m3gNodeTranslate(int userId, float dx, float dy, float dz) {
        return core.m3gNodeTranslate(userId, dx, dy, dz);
    }

    public String m3gNodeSetTranslation(int userId, float x, float y, float z) {
        return core.m3gNodeSetTranslation(userId, x, y, z);
    }

    public float[] m3gNodeGetTranslation(int userId) {
        return core.m3gNodeGetTranslation(userId);
    }

    public String m3gNodeSetOrientation(int userId, float angleDeg, float ax, float ay, float az) {
        return core.m3gNodeSetOrientation(userId, angleDeg, ax, ay, az);
    }

    public String m3gAnimSetActiveInterval(int userId, int startMs, int endMs) {
        return core.m3gAnimSetActiveInterval(userId, startMs, endMs);
    }

    public String m3gAnimSetPosition(int userId, int sequence, int timeMs) {
        return core.m3gAnimSetPosition(userId, sequence, timeMs);
    }

    public String m3gAnimSetSpeed(int userId, float speed) {
        return core.m3gAnimSetSpeed(userId, speed);
    }

    public int m3gKeyframeDurationTrack0(int userId) {
        return core.m3gKeyframeDurationTrack0(userId);
    }

    public String getSceneInfo() {
        return core.getSceneInfo();
    }

    public int getEffectiveMaxTriangles() {
        return core.getEffectiveMaxTriangles();
    }

    public void setTextureFilterNearest(boolean nearest) {
        core.setTextureFilterNearest(nearest);
    }

    public void setTextureWrapRepeat(boolean repeat) {
        core.setTextureWrapRepeat(repeat);
    }
}
