import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Hashtable;

/**
 * Loads {@code /boot.ini} (UTF-8) for the configurable boot splash sequence.
 */
public final class BootIniConfig {

    public static final int HANDOFF_IMMEDIATE = 0;
    public static final int HANDOFF_AFTER_SLIDE = 1;

    /** Timer tick for {@link BootSplashCanvas} repaints. */
    public int tickMs = 50;

    public int handoffPolicy = HANDOFF_AFTER_SLIDE;

    /**
     * When false, the ES6 preprocessor is not run (faster cold start; JS must be legacy syntax only).
     * Default is true. Set in {@code [boot]} with {@code es6=false} (or 0, no, off, legacy).
     */
    public boolean es6 = true;

    public final SplashSlide[] slides;

    private BootIniConfig(SplashSlide[] slides0, int tick0, int handoff0, boolean es60) {
        slides = slides0;
        if (tick0 > 0) {
            tickMs = tick0;
        }
        handoffPolicy = handoff0;
        es6 = es60;
    }

    /** One line of text on a splash slide (from {@code text.N} or legacy single {@code text}). */
    public static final class SplashTextItem {
        public String text = "";
        public int x;
        public int y;
        /**
         * Raw X/Y from {@code boot.ini} when present, so {@link BootSplashCanvas} can expand
         * screen macros at paint time. If {@code null}, use {@link #x} / {@link #y}.
         */
        public String xSpec;
        public String ySpec;
        /**
         * Horizontal alignment: 0 = left, 1 = center, 2 = right (see {@code textalign} in ini).
         */
        public int align;
        /** {@link javax.microedition.lcdui.Font} size constant (e.g. 0 = SIZE_MEDIUM). */
        public int fontSize;
        public int colorRgb = 0xffffff;
    }

    /** One image on a splash slide (from {@code image.N} or legacy single {@code image}). */
    public static final class SplashImageItem {
        public String path = "";
        public int x;
        public int y;
        public String xSpec;
        public String ySpec;
    }

    /** One splash screen step. */
    public static final class SplashSlide {
        public int backgroundRgb = 0x000000;
        public SplashTextItem[] textItems = new SplashTextItem[0];
        public SplashImageItem[] imageItems = new SplashImageItem[0];
        /** How long this slide stays visible (ms). */
        public int holdMs = 800;
    }

    /**
     * Load from classpath {@code /boot.ini}. If missing or invalid, returns a minimal config
     * (no slides, black screen) so cold start can still run asynchronously.
     */
    public static BootIniConfig loadFromClasspath(Class anchor) {
        InputStream is = null;
        try {
            is = anchor.getResourceAsStream("/boot.ini");
            if (is == null) {
                return new BootIniConfig(new SplashSlide[0], 50, HANDOFF_AFTER_SLIDE, true);
            }
            byte[] raw = readAll(is);
            is.close();
            is = null;
            String s = decodeUtf8(raw);
            return parseIni(s);
        } catch (Throwable t) {
            t.printStackTrace();
            return new BootIniConfig(new SplashSlide[0], 50, HANDOFF_AFTER_SLIDE, true);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable t2) {
                }
            }
        }
    }

    private static byte[] readAll(InputStream is) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) >= 0) {
            bo.write(buf, 0, n);
        }
        return bo.toByteArray();
    }

    private static String decodeUtf8(byte[] raw) {
        if (raw.length >= 3 && (raw[0] & 0xff) == 0xef && (raw[1] & 0xff) == 0xbb && (raw[2] & 0xff) == 0xbf) {
            byte[] t = new byte[raw.length - 3];
            System.arraycopy(raw, 3, t, 0, t.length);
            raw = t;
        }
        try {
            return new String(raw, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(raw);
        }
    }

    private static BootIniConfig parseIni(String text) {
        Hashtable sections = new Hashtable();
        String cur = "";
        int len = text.length();
        int i = 0;
        while (i < len) {
            int lineEnd = text.indexOf('\n', i);
            if (lineEnd < 0) {
                lineEnd = len;
            }
            String line = text.substring(i, lineEnd);
            if (line.length() > 0 && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }
            i = lineEnd + 1;

            line = trim(line);
            if (line.length() == 0) {
                continue;
            }
            if (line.charAt(0) == '#') {
                continue;
            }
            if (line.charAt(0) == '[') {
                int end = line.indexOf(']');
                if (end > 1) {
                    cur = lc(line.substring(1, end));
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trim(line.substring(0, eq));
            String val = trim(line.substring(eq + 1));
            if (key.length() == 0) {
                continue;
            }
            Hashtable sec = (Hashtable) sections.get(cur);
            if (sec == null) {
                sec = new Hashtable();
                sections.put(cur, sec);
            }
            sec.put(lc(key), val);
        }

        int tick = 50;
        Hashtable tickSec = (Hashtable) sections.get("tick");
        if (tickSec != null) {
            tick = parseInt((String) tickSec.get("ms"), 50);
        }

        int handoff = HANDOFF_AFTER_SLIDE;
        boolean es6 = true;
        Hashtable bootSec = (Hashtable) sections.get("boot");
        if (bootSec != null) {
            String h = (String) bootSec.get("handoff");
            if (h != null) {
                if ("immediate".equals(lc(h))) {
                    handoff = HANDOFF_IMMEDIATE;
                } else {
                    handoff = HANDOFF_AFTER_SLIDE;
                }
            }
            String es6s = (String) bootSec.get("es6");
            if (es6s != null) {
                String u = lc(trim(es6s));
                if ("0".equals(u) || "false".equals(u) || "no".equals(u) || "off".equals(u) || "legacy".equals(u)) {
                    es6 = false;
                } else {
                    es6 = true;
                }
            }
        }

        int maxIdx = -1;
        for (java.util.Enumeration e = sections.keys(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            if (name.startsWith("splash.")) {
                try {
                    int idx = Integer.parseInt(name.substring("splash.".length()));
                    if (idx > maxIdx) {
                        maxIdx = idx;
                    }
                } catch (NumberFormatException ex) {
                }
            }
        }

        int count = maxIdx + 1;
        if (bootSec != null) {
            int declared = parseInt((String) bootSec.get("slides"), -1);
            if (declared >= 0) {
                count = declared;
            }
        }

        if (count <= 0) {
            return new BootIniConfig(new SplashSlide[0], tick, handoff, es6);
        }

        SplashSlide[] slides = new SplashSlide[count];
        for (int k = 0; k < count; k++) {
            slides[k] = new SplashSlide();
            Hashtable sec = (Hashtable) sections.get("splash." + k);
            if (sec != null) {
                applySlideKeys(slides[k], sec);
            }
        }
        return new BootIniConfig(slides, tick, handoff, es6);
    }

    private static void applySlideKeys(SplashSlide sl, Hashtable sec) {
        sl.backgroundRgb = parseColor((String) sec.get("background"), sl.backgroundRgb);
        sl.holdMs = Math.max(0, parseInt((String) sec.get("holdms"), sl.holdMs));

        int legX = parseInt((String) sec.get("textx"), 0);
        int legY = parseInt((String) sec.get("texty"), 0);
        int legColor = parseColor((String) sec.get("textcolor"), 0xffffff);
        int legFont = 0;
        String lts = (String) sec.get("textsize");
        if (lts != null) {
            legFont = fontSizeFromString(lts);
        }
        int legAlign = parseTextAlign((String) sec.get("textalign"), 0);
        int legIX = parseInt((String) sec.get("imagex"), 0);
        int legIY = parseInt((String) sec.get("imagey"), 0);
        String legIXs = (String) sec.get("imagex");
        if (legIXs != null) {
            legIXs = legIXs.trim();
        } else {
            legIXs = null;
        }
        String legIYs = (String) sec.get("imagey");
        if (legIYs != null) {
            legIYs = legIYs.trim();
        } else {
            legIYs = null;
        }

        int maxT = maxIndexForPrefixGroup(sec, new String[] {
            "text.", "textx.", "texty.", "textsize.", "textcolor."
        });
        if (maxT < 0) {
            SplashTextItem one = new SplashTextItem();
            String t0 = (String) sec.get("text");
            if (t0 != null) {
                one.text = t0;
            }
            one.x = legX;
            one.y = legY;
            one.align = legAlign;
            one.colorRgb = legColor;
            one.fontSize = legFont;
            String txs = (String) sec.get("textx");
            if (txs != null) {
                one.xSpec = txs.trim();
                one.x = parseInt(txs, legX);
            }
            String tys = (String) sec.get("texty");
            if (tys != null) {
                one.ySpec = tys.trim();
                one.y = parseInt(tys, legY);
            }
            sl.textItems = new SplashTextItem[] {one};
        } else {
            SplashTextItem[] arr = new SplashTextItem[maxT + 1];
            for (int i = 0; i <= maxT; i++) {
                arr[i] = new SplashTextItem();
                String ti = (String) sec.get("text." + i);
                if (ti == null && i == 0) {
                    ti = (String) sec.get("text");
                }
                if (ti != null) {
                    arr[i].text = ti;
                }
                String xis = (String) sec.get("textx." + i);
                if (xis != null) {
                    arr[i].xSpec = xis.trim();
                    arr[i].x = parseInt(xis, legX);
                } else {
                    arr[i].x = legX;
                }
                String yis = (String) sec.get("texty." + i);
                if (yis != null) {
                    arr[i].ySpec = yis.trim();
                    arr[i].y = parseInt(yis, legY);
                } else {
                    arr[i].y = legY;
                }
                String tsi = (String) sec.get("textsize." + i);
                if (tsi != null) {
                    arr[i].fontSize = fontSizeFromString(tsi);
                } else {
                    arr[i].fontSize = legFont;
                }
                String tci = (String) sec.get("textcolor." + i);
                if (tci != null) {
                    arr[i].colorRgb = parseColor(tci, legColor);
                } else {
                    arr[i].colorRgb = legColor;
                }
                String tai = (String) sec.get("textalign." + i);
                if (tai != null) {
                    arr[i].align = parseTextAlign(tai, legAlign);
                } else {
                    arr[i].align = legAlign;
                }
            }
            sl.textItems = arr;
        }

        int maxI = maxIndexForPrefixGroup(sec, new String[] {"image.", "imagex.", "imagey."});
        if (maxI < 0) {
            SplashImageItem im = new SplashImageItem();
            String ip = (String) sec.get("image");
            if (ip != null) {
                im.path = ip.trim();
            }
            im.x = legIX;
            im.y = legIY;
            if (legIXs != null) {
                im.xSpec = legIXs;
            }
            if (legIYs != null) {
                im.ySpec = legIYs;
            }
            sl.imageItems = new SplashImageItem[] {im};
        } else {
            SplashImageItem[] iarr = new SplashImageItem[maxI + 1];
            for (int i = 0; i <= maxI; i++) {
                iarr[i] = new SplashImageItem();
                String pi = (String) sec.get("image." + i);
                if (pi == null && i == 0) {
                    pi = (String) sec.get("image");
                }
                if (pi != null) {
                    iarr[i].path = pi.trim();
                }
                String ixs = (String) sec.get("imagex." + i);
                if (ixs != null) {
                    iarr[i].xSpec = ixs.trim();
                    iarr[i].x = parseInt(ixs, legIX);
                } else {
                    iarr[i].x = legIX;
                }
                String iys = (String) sec.get("imagey." + i);
                if (iys != null) {
                    iarr[i].ySpec = iys.trim();
                    iarr[i].y = parseInt(iys, legIY);
                } else {
                    iarr[i].y = legIY;
                }
            }
            sl.imageItems = iarr;
        }
    }

    private static int maxIndexForPrefixGroup(Hashtable sec, String[] prefixes) {
        int m = -1;
        for (java.util.Enumeration e = sec.keys(); e.hasMoreElements();) {
            String k = (String) e.nextElement();
            for (int p = 0; p < prefixes.length; p++) {
                String pre = prefixes[p];
                if (k.length() > pre.length() && k.startsWith(pre)) {
                    int idx = parseDigitsInt(k, pre.length());
                    if (idx >= 0 && idx > m) {
                        m = idx;
                    }
                }
            }
        }
        return m;
    }

    /** -1 if rest is not all digits, else the non-negative number. */
    private static int parseDigitsInt(String key, int start) {
        int n = key.length() - start;
        if (n <= 0) {
            return -1;
        }
        for (int i = 0; i < n; i++) {
            char c = key.charAt(start + i);
            if (c < '0' || c > '9') {
                return -1;
            }
        }
        try {
            return Integer.parseInt(key.substring(start));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static int fontSizeFromString(String s) {
        String u = lc(s);
        if ("small".equals(u)) {
            return 8;
        }
        if ("large".equals(u)) {
            return 16;
        }
        return 0;
    }

    /** 0 = left, 1 = center, 2 = right. */
    private static int parseTextAlign(String s, int def) {
        if (s == null) {
            return def;
        }
        String u = lc(trim(s));
        if ("center".equals(u) || "centre".equals(u) || "middle".equals(u)) {
            return 1;
        }
        if ("right".equals(u)) {
            return 2;
        }
        if ("left".equals(u)) {
            return 0;
        }
        return def;
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseColor(String s, int defRgb) {
        if (s == null || s.length() == 0) {
            return defRgb;
        }
        String x = s.trim();
        if (x.charAt(0) == '#') {
            x = x.substring(1);
        }
        try {
            if (x.length() == 6) {
                return Integer.parseInt(x, 16);
            }
            if (x.length() == 8) {
                return Integer.parseInt(x, 16) & 0xffffff;
            }
        } catch (NumberFormatException e) {
        }
        return defRgb;
    }

    private static String trim(String s) {
        int a = 0;
        int b = s.length();
        while (a < b && s.charAt(a) <= ' ') {
            a++;
        }
        while (b > a && s.charAt(b - 1) <= ' ') {
            b--;
        }
        return s.substring(a, b);
    }

    private static String lc(String s) {
        int n = s.length();
        StringBuffer sb = new StringBuffer(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + ('a' - 'A'));
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
