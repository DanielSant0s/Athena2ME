import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Boot splash UI: each slide is shown for {@code holdMs}, then the next (or done).
 * {@link java.util.Timer} only triggers repaints; timing runs inside {@link #paint} under a lock.
 */
public final class BootSplashCanvas extends Canvas {

    private final Athena2ME midlet;
    private final BootIniConfig cfg;
    private Timer timer;
    private volatile boolean disposed;
    volatile boolean coldStartReady;

    private int slideIndex;
    private long slideStartMs = -1L;
    private boolean sequenceDone;

    private int bufW;
    private int bufH;
    private Image slideImage;

    BootSplashCanvas(Athena2ME midlet0, BootIniConfig cfg0) {
        this.midlet = midlet0;
        this.cfg = cfg0;
    }

    void setColdStartReady(boolean v) {
        this.coldStartReady = v;
    }

    void startAnimating() {
        if (timer != null) {
            return;
        }
        timer = new Timer();
        long period = cfg.tickMs <= 0 ? 50L : (long) cfg.tickMs;
        timer.schedule(new TimerTask() {
            public void run() {
                if (disposed) {
                    return;
                }
                repaint();
            }
        }, 0L, period);
    }

    void dispose() {
        synchronized (this) {
            disposed = true;
            slideImage = null;
            bufW = 0;
            bufH = 0;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void invalidateSlideBitmap() {
        slideImage = null;
    }

    private void tickAnimation(long now) {
        if (cfg.slides == null || cfg.slides.length == 0) {
            if (coldStartReady) {
                midlet.scheduleBootHandoff();
            }
            return;
        }

        if (slideIndex < 0 || slideIndex >= cfg.slides.length) {
            slideIndex = 0;
        }

        if (sequenceDone) {
            if (coldStartReady) {
                midlet.scheduleBootHandoff();
            }
            return;
        }

        if (slideStartMs < 0L) {
            slideStartMs = now;
        }

        BootIniConfig.SplashSlide sl = cfg.slides[slideIndex];
        long elapsed = now - slideStartMs;
        if (elapsed < (long) sl.holdMs) {
            return;
        }

        if (coldStartReady) {
            midlet.scheduleBootHandoff();
            return;
        }

        if (slideIndex < cfg.slides.length - 1) {
            slideIndex++;
            slideStartMs = now;
            invalidateSlideBitmap();
        } else {
            sequenceDone = true;
        }
    }

    private void ensureSlideImage(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (slideImage != null && bufW == w && bufH == h) {
            return;
        }
        slideImage = null;
        bufW = w;
        bufH = h;
        BootIniConfig.SplashSlide sl = cfg.slides[slideIndex];
        Image off;
        try {
            off = Image.createImage(w, h);
        } catch (Throwable t) {
            return;
        }
        Graphics og = off.getGraphics();
        if (og == null) {
            return;
        }
        og.setColor(sl.backgroundRgb & 0xffffff);
        og.fillRect(0, 0, w, h);

        if (sl.imageItems != null) {
            for (int ii = 0; ii < sl.imageItems.length; ii++) {
                BootIniConfig.SplashImageItem im = sl.imageItems[ii];
                if (im == null || im.path == null || im.path.length() == 0) {
                    continue;
                }
                Image src = midlet.loadResourceImage(im.path);
                if (src != null) {
                    try {
                        int ix = resolveCoord(im.xSpec, im.x, w, h);
                        int iy = resolveCoord(im.ySpec, im.y, w, h);
                        og.drawImage(src, ix, iy, Graphics.TOP | Graphics.LEFT);
                    } catch (Throwable t) {
                    }
                }
            }
        }

        if (sl.textItems != null) {
            for (int ti = 0; ti < sl.textItems.length; ti++) {
                BootIniConfig.SplashTextItem tx = sl.textItems[ti];
                if (tx == null) {
                    continue;
                }
                Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, tx.fontSize);
                if (font == null) {
                    font = Font.getDefaultFont();
                }
                og.setFont(font);
                og.setColor(tx.colorRgb & 0xffffff);
                if (tx.text != null && tx.text.length() > 0) {
                    try {
                        String tdraw = expandScreenMacros(tx.text, w, h);
                        int px = resolveCoord(tx.xSpec, tx.x, w, h);
                        int py = resolveCoord(tx.ySpec, tx.y, w, h);
                        int anchor;
                        if (tx.align == 1) {
                            anchor = Graphics.TOP | Graphics.HCENTER;
                        } else if (tx.align == 2) {
                            anchor = Graphics.TOP | Graphics.RIGHT;
                        } else {
                            anchor = Graphics.TOP | Graphics.LEFT;
                        }
                        og.drawString(tdraw, px, py, anchor);
                    } catch (Throwable t) {
                    }
                }
            }
        }

        slideImage = off;
    }

    protected void paint(Graphics g) {
        synchronized (this) {
            if (disposed || g == null || midlet == null) {
                return;
            }

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            long now = System.currentTimeMillis();
            tickAnimation(now);

            if (cfg.slides == null || cfg.slides.length == 0) {
                g.setColor(0);
                g.fillRect(0, 0, w, h);
                return;
            }

            if (slideIndex < 0 || slideIndex >= cfg.slides.length) {
                slideIndex = 0;
            }

            ensureSlideImage(w, h);

            BootIniConfig.SplashSlide sl = cfg.slides[slideIndex];
            int bgRgb = sl.backgroundRgb;

            if (slideImage == null) {
                g.setColor(bgRgb & 0xffffff);
                g.fillRect(0, 0, w, h);
                return;
            }

            g.drawImage(slideImage, 0, 0, Graphics.TOP | Graphics.LEFT);
        }
    }

    /**
     * Expands screen-size placeholders for {@code boot.ini}:
     * {@code %W%} = width, {@code %H%} = height, {@code %W2%} / {@code %H2%} = half (integer / 2).
     * Longer tokens are replaced first so {@code %W2%} is not broken by a prior {@code %W%} pass.
     * <p>Coordinates ({@code textX}, {@code textY}, {@code imageX}, etc.) also accept sums/differences
     * after expansion, e.g. {@code %W2%+20} or {@code %H%-8} (see {@link #evalIntExpression}).
     */
    private static String expandScreenMacros(String s, int w, int h) {
        if (s == null) {
            return null;
        }
        int w2 = w / 2;
        int h2 = h / 2;
        String r = s;
        r = replaceLiteral(r, "%W2%", String.valueOf(w2));
        r = replaceLiteral(r, "%H2%", String.valueOf(h2));
        r = replaceLiteral(r, "%W%", String.valueOf(w));
        r = replaceLiteral(r, "%H%", String.valueOf(h));
        return r;
    }

    private static String replaceLiteral(String s, String from, String to) {
        if (s == null || from == null || from.length() == 0) {
            return s;
        }
        if (to == null) {
            to = "";
        }
        int fl = from.length();
        int sl = s.length();
        int i = 0;
        StringBuffer out = new StringBuffer(sl + 16);
        while (i < sl) {
            if (i <= sl - fl && substringEqualsAt(s, i, from)) {
                out.append(to);
                i += fl;
            } else {
                out.append(s.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** CLDC / old J2ME: avoid {@code String.regionMatches} (not all profiles expose the 4-arg form). */
    private static boolean substringEqualsAt(String s, int i, String from) {
        int n = from.length();
        for (int k = 0; k < n; k++) {
            if (s.charAt(i + k) != from.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static int resolveCoord(String spec, int fallback, int w, int h) {
        if (spec == null || spec.length() == 0) {
            return fallback;
        }
        String e = expandScreenMacros(spec.trim(), w, h);
        return evalIntExpression(e, fallback);
    }

    /**
     * After macro expansion, parses an integer or a chain {@code a+b-c+d} (spaces allowed).
     * Each term is an optional sign and decimal digits. On syntax error, returns {@code fallback}.
     */
    private static int evalIntExpression(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        String t = removeIniWhitespace(s);
        if (t.length() == 0) {
            return fallback;
        }
        int n = t.length();
        int[] end = new int[1];
        int acc = readSignedIntValue(t, 0, n, end);
        if (end[0] < 0) {
            return fallback;
        }
        int i = end[0];
        if (i >= n) {
            return acc;
        }
        while (i < n) {
            char op = t.charAt(i);
            if (op != '+' && op != '-') {
                return fallback;
            }
            i++;
            int v = readSignedIntValue(t, i, n, end);
            if (end[0] < 0) {
                return fallback;
            }
            if (op == '+') {
                acc += v;
            } else {
                acc -= v;
            }
            i = end[0];
        }
        return acc;
    }

    private static String removeIniWhitespace(String s) {
        if (s == null) {
            return s;
        }
        int l = s.length();
        StringBuffer b = new StringBuffer(l);
        for (int i = 0; i < l; i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }

    /**
     * Reads a signed integer from {@code t[start..n)}, sets {@code outEnd[0]} to the index after the
     * number or to {@code -1} on failure.
     */
    private static int readSignedIntValue(String t, int start, int n, int[] outEnd) {
        if (start > n) {
            outEnd[0] = -1;
            return 0;
        }
        int i = start;
        if (i < n) {
            char c0 = t.charAt(i);
            if (c0 == '+') {
                i++;
            } else if (c0 == '-') {
                i++;
            }
        }
        if (i >= n) {
            outEnd[0] = -1;
            return 0;
        }
        int v = 0;
        boolean any = false;
        while (i < n) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            any = true;
            v = v * 10 + (c - '0');
            i++;
        }
        if (!any) {
            outEnd[0] = -1;
            return 0;
        }
        outEnd[0] = i;
        boolean neg = (start < n && t.charAt(start) == '-');
        return neg ? -v : v;
    }

}
