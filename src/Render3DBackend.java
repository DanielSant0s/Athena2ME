/**
 * Pluggable 3D immediate-mode API: hardware M3G (JSR-184) or software rasterization.
 * Default (no) package for access to {@link AthenaCanvas} in this MIDlet.
 * New methods should be implemented in both {@link Render3DM3GBackend} and {@link Render3DSoftBackend}
 * unless a capability is M3G-only and documented (rare; UV texturing is supported on both backends).
 */
public interface Render3DBackend {

    /** {@code m3g} (JSR-184) or {@code soft} (CPU raster). */
    String getId();

    void init(AthenaCanvas canvas);

    void setMaxTriangles(int max);

    /**
     * Software: enable an optional W×H depth buffer (correct occlusion for opaque tris, extra RAM/CPU). M3G: no-op.
     */
    void setDepthBuffer(boolean on);

    void setPerspectiveFromViewport(float aspect, float fovYDeg, float near, float far);

    void setBackgroundColor(int r, int g, int b);

    void setCameraPosition(float x, float y, float z);

    void setLookAt(float ex, float ey, float ez, float tx, float ty, float tz, float ux, float uy, float uz);

    void setObjectRotationY(float degrees);

    void setObjectTransformFromColumnMajor(float[] m);

    void setObjectTransformIdentity();

    void pushObjectMatrix();

    void popObjectMatrix();

    void setTriangleStripMesh(float[] pos, int[] stripLen, float[] normals);

    void setIndexedTriangleMesh(float[] pos, int[] indices, float[] normals);

    void setBackfaceCulling(boolean on);

    void setGlobalLightDirection(float x, float y, float z);

    void setMaterialAmbient(int r, int g, int b);

    void setMaterialDiffuse(int r, int g, int b);

    void setTexture2DPath(String path);

    void setTexCoords(float[] uvs);

    void clearImmediateMesh();

    void beginFrame(AthenaCanvas canvas);

    void renderImmediate(AthenaCanvas canvas);

    void end();

    /**
     * @return {@code null} on success; otherwise a short error string for the script layer.
     */
    String loadM3G(String resPath);

    /** M3G: optional World animation; no-op on soft. */
    void worldAnimate(int timeMs);

    /** M3G loaded world: {@link Node#translate}; soft: error. */
    String m3gNodeTranslate(int userId, float dx, float dy, float dz);

    /** M3G: {@link Node#setTranslation}; soft: error. */
    String m3gNodeSetTranslation(int userId, float x, float y, float z);

    /** M3G: {@link Node#getTranslation}; soft: {@code null}. */
    float[] m3gNodeGetTranslation(int userId);

    /** M3G: {@link Node#setOrientation} (angle in degrees); soft: error. */
    String m3gNodeSetOrientation(int userId, float angleDeg, float ax, float ay, float az);

    /** M3G: {@code AnimationController#setActiveInterval}; soft: error. */
    String m3gAnimSetActiveInterval(int userId, int startMs, int endMs);

    /** M3G: {@code AnimationController#setPosition}; soft: error. */
    String m3gAnimSetPosition(int userId, int sequence, int timeMs);

    /** M3G: {@code AnimationController#setSpeed}; soft: error. */
    String m3gAnimSetSpeed(int userId, float speed);

    /** M3G: duration ms of track 0 keyframes for a {@link Node}, or {@code -1}. */
    int m3gKeyframeDurationTrack0(int userId);

    /** One-line summary of current M3G scene / mesh state (optional for HUD). */
    String getSceneInfo();

    /** Software: current triangle budget (32..4096). M3G: {@code -1} (no cap in this API). */
    int getEffectiveMaxTriangles();

    /** {@code true} = point sample; {@code false} = bilinear (software) / linear (M3G) where supported. */
    void setTextureFilterNearest(boolean nearest);

    /** {@code true} = repeat/wrap UVs; {@code false} = clamp to [0,1]. */
    void setTextureWrapRepeat(boolean repeat);
}
