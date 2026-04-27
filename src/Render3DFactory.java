/**
 * Picks the M3G backend when JSR-184 is available at runtime, otherwise the software backend.
 */
public final class Render3DFactory {
    private Render3DFactory() {
    }

    public static Render3DBackend create() {
        if (AthenaM3G.isApiAvailable()) {
            return new Render3DM3GBackend(new AthenaM3G());
        }
        return new Render3DSoftBackend();
    }

    /**
     * @param id {@code null} or {@code "auto"} = same as {@link #create()}; {@code "soft"}; {@code "m3g"} (only if API is present)
     * @return {@code null} if {@code m3g} was requested but M3G is unavailable
     */
    public static Render3DBackend createForId(String id) {
        if (id == null) {
            return create();
        }
        String t = id.trim();
        if (t.length() == 0) {
            return create();
        }
        if (t.equalsIgnoreCase("soft") || t.equalsIgnoreCase("software")) {
            return new Render3DSoftBackend();
        }
        if (t.equalsIgnoreCase("m3g") || t.equalsIgnoreCase("hw")) {
            if (AthenaM3G.isApiAvailable()) {
                return new Render3DM3GBackend(new AthenaM3G());
            }
            return null;
        }
        if (t.equalsIgnoreCase("auto") || t.equalsIgnoreCase("default")) {
            return create();
        }
        return null;
    }
}
