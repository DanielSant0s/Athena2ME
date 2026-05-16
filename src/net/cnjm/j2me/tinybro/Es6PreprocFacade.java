package net.cnjm.j2me.tinybro;

/**
 * Single entry for ES6 preprocessing. Today this delegates to {@link Es6Preproc};
 * heavier passes can be split into separate classes and loaded lazily from here
 * without touching {@link RocksInterpreter}.
 *
 * <p>System properties (best-effort on CLDC):</p>
 * <ul>
 *   <li>{@code feature.es6preproc.literalFold} — {@code false} disables
 *       {@link Es6Preproc#ENABLE_LITERAL_FOLD} for this call only.</li>
 *   <li>{@link RocksInterpreter#escapeOptForLiterals} — copied into
 *       {@link Es6Preproc#ESCAPE_OPT_FOR_LITERALS} for this call only.</li>
 *   <li>Build-time prebake: sources that begin with {@code // @a2m:prebaked v1} (after optional BOM /
 *       whitespace) skip {@link Es6Preproc#process(String)} — see {@code tools/preproc} in the repo.</li>
 * </ul>
 */
public final class Es6PreprocFacade {

    private static boolean optionalHooksTried;

    private Es6PreprocFacade() {
    }

    public static boolean isPrebaked(String src) {
        if (src == null || src.length() == 0) {
            return false;
        }
        int i = 0;
        int n = src.length();
        if (i < n && src.charAt(i) == '\uFEFF') {
            i++;
        }
        while (i < n) {
            char c = src.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
                continue;
            }
            break;
        }
        final String marker = "// @a2m:prebaked v1";
        return i + marker.length() <= n && src.regionMatches(false, i, marker, 0, marker.length());
    }

    public static String process(String src) {
        if (src == null || src.length() == 0) {
            return src;
        }
        if (isPrebaked(src)) {
            return src;
        }
        if (!optionalHooksTried) {
            optionalHooksTried = true;
            try {
                Class.forName("net.cnjm.j2me.tinybro.Es6PreprocOptionalHooks");
            } catch (Throwable ignored) {
            }
        }
        boolean prevFold = Es6Preproc.ENABLE_LITERAL_FOLD;
        boolean prevEsc = Es6Preproc.ESCAPE_OPT_FOR_LITERALS;
        try {
            // CLDC: System.getProperty(String) only — missing key => null => fold stays enabled.
            Es6Preproc.ENABLE_LITERAL_FOLD = !"false".equals(System.getProperty("feature.es6preproc.literalFold"));
            Es6Preproc.ESCAPE_OPT_FOR_LITERALS = RocksInterpreter.escapeOptForLiterals;
            return Es6Preproc.process(src);
        } finally {
            Es6Preproc.ENABLE_LITERAL_FOLD = prevFold;
            Es6Preproc.ESCAPE_OPT_FOR_LITERALS = prevEsc;
        }
    }
}
