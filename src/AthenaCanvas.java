import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.InterruptedException;
import java.lang.Runnable;
import java.lang.System;

import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public class AthenaCanvas extends GameCanvas {
    static final int TEXT_COLOR = 0x00ffffff;
    static final int CLEAR_COLOR = 0x00000000;

    /** Offscreen buffer + Graphics; exposed to JS via {@code Screen.createLayer}. */
    public static final class Layer {
        Image image;
        Graphics graphics;
        int width;
        int height;
        boolean freed;
    }

    private int pressed_buttons = 0;
    private int old_pressed_buttons = 0;

    private int key = 0;

    /** Buffer graphics for this {@link GameCanvas} (front buffer). */
    private final Graphics screenGraphics;
    /** Target for {@link Draw}, {@link Font}, and immediate / batched {@link Image} draws. */
    private Graphics currentGraphics;
    /** Non-null when drawing to an offscreen layer. */
    private Layer currentLayer;

    private static final int SPRITE_BATCH_INIT = 64;
    private boolean spriteBatchActive;
    private Image[] spriteBatchImg = new Image[SPRITE_BATCH_INIT];
    private int[] spriteBatchX = new int[SPRITE_BATCH_INIT];
    private int[] spriteBatchY = new int[SPRITE_BATCH_INIT];
    private int[] spriteBatchSx = new int[SPRITE_BATCH_INIT];
    private int[] spriteBatchSy = new int[SPRITE_BATCH_INIT];
    private int[] spriteBatchW = new int[SPRITE_BATCH_INIT];
    private int[] spriteBatchH = new int[SPRITE_BATCH_INIT];
    private int spriteBatchCount;

    /** Per-{@link Graphics} color cache + coalesced {@code fillRect} for consecutive same-color rects. */
    private static final int COLOR_UNSET = 0x80000000;
    private int lastDrawColor = COLOR_UNSET;
    private static final int RECT_BATCH_INIT = 256;
    private int[] rectBatch = new int[RECT_BATCH_INIT * 4];
    private int rectBatchCount;
    private int rectBatchColor = COLOR_UNSET;

    private void invalidateDrawColor() {
        lastDrawColor = COLOR_UNSET;
    }

    /** Apply {@code color} to {@link #currentGraphics} only if it changed (per target buffer). */
    private void applyColor(int color) {
        if (color != lastDrawColor) {
            currentGraphics.setColor(color);
            lastDrawColor = color;
        }
    }

    private void flushRectBatch() {
        if (rectBatchCount == 0) {
            return;
        }
        applyColor(rectBatchColor);
        Graphics g = currentGraphics;
        for (int i = 0, j = 0; i < rectBatchCount; i++, j += 4) {
            g.fillRect(rectBatch[j], rectBatch[j + 1], rectBatch[j + 2], rectBatch[j + 3]);
        }
        rectBatchCount = 0;
        rectBatchColor = COLOR_UNSET;
    }

    private void enqueueFillRect(int x, int y, int w, int h, int color) {
        if (rectBatchCount > 0 && color != rectBatchColor) {
            flushRectBatch();
        }
        if ((rectBatchCount + 1) * 4 > rectBatch.length) {
            flushRectBatch();
        }
        if (rectBatchCount == 0) {
            rectBatchColor = color;
        }
        int o = rectBatchCount * 4;
        rectBatch[o] = x;
        rectBatch[o + 1] = y;
        rectBatch[o + 2] = w;
        rectBatch[o + 3] = h;
        rectBatchCount++;
    }

    private static int readInt32LE(byte[] data, int pos) {
        int b0 = data[pos] & 0xff;
        int b1 = data[pos + 1] & 0xff;
        int b2 = data[pos + 2] & 0xff;
        int b3 = data[pos + 3] & 0xff;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    /** Draws a batch of rectangles from an interleaved Int32Array slab. */
    public void drawRects(byte[] data, int byteOffset, int elementCount,
            int count, int stride, int xOff, int yOff, int wOff, int hOff, int colorOff) {
        if (data == null || count <= 0 || stride <= 0) {
            return;
        }
        int maxOff = xOff;
        if (yOff > maxOff) maxOff = yOff;
        if (wOff > maxOff) maxOff = wOff;
        if (hOff > maxOff) maxOff = hOff;
        if (colorOff > maxOff) maxOff = colorOff;
        if (xOff < 0 || yOff < 0 || wOff < 0 || hOff < 0 || colorOff < 0
                || elementCount <= maxOff) {
            return;
        }
        int maxCount = (elementCount - maxOff + stride - 1) / stride;
        if (count > maxCount) {
            count = maxCount;
        }
        for (int i = 0; i < count; i++) {
            int base = i * stride;
            int w = readInt32LE(data, byteOffset + ((base + wOff) << 2));
            int h = readInt32LE(data, byteOffset + ((base + hOff) << 2));
            if (w <= 0 || h <= 0) {
                continue;
            }
            int x = readInt32LE(data, byteOffset + ((base + xOff) << 2));
            int y = readInt32LE(data, byteOffset + ((base + yOff) << 2));
            int color = readInt32LE(data, byteOffset + ((base + colorOff) << 2));
            enqueueFillRect(x, y, w, h, color);
        }
    }

    public AthenaCanvas(boolean suppressKeyEvents) {
        super(suppressKeyEvents);

        screenGraphics = getGraphics();
        currentGraphics = screenGraphics;
        currentLayer = null;
    }

    public final int ALIGN_TOP = Graphics.TOP;
    public final int ALIGN_BOTTOM = Graphics.BOTTOM;
    public final int ALIGN_VCENTER = Graphics.VCENTER;
    public final int ALIGN_LEFT = Graphics.LEFT;
    public final int ALIGN_RIGHT = Graphics.RIGHT;
    public final int ALIGN_HCENTER = Graphics.HCENTER;
    public final int ALIGN_NONE = Graphics.TOP | Graphics.LEFT;
    public final int ALIGN_CENTER = Graphics.VCENTER | Graphics.HCENTER;

    private void flushPendingSpriteBatch() {
        flushRectBatch();
        if (!spriteBatchActive || spriteBatchCount == 0) {
            return;
        }
        Graphics g = currentGraphics;
        for (int i = 0; i < spriteBatchCount; i++) {
            Image img = spriteBatchImg[i];
            if (img != null) {
                g.drawRegion(img,
                    spriteBatchSx[i], spriteBatchSy[i],
                    spriteBatchW[i], spriteBatchH[i],
                    Sprite.TRANS_NONE,
                    spriteBatchX[i], spriteBatchY[i],
                    Graphics.TOP | Graphics.LEFT);
            }
        }
        spriteBatchCount = 0;
    }

    private void growSpriteBatch(int need) {
        int len = spriteBatchImg.length;
        int n = len;
        while (n < need) {
            n *= 2;
        }
        spriteBatchImg = growImageArray(spriteBatchImg, n);
        spriteBatchX = growIntArray(spriteBatchX, n);
        spriteBatchY = growIntArray(spriteBatchY, n);
        spriteBatchSx = growIntArray(spriteBatchSx, n);
        spriteBatchSy = growIntArray(spriteBatchSy, n);
        spriteBatchW = growIntArray(spriteBatchW, n);
        spriteBatchH = growIntArray(spriteBatchH, n);
    }

    private static Image[] growImageArray(Image[] a, int n) {
        Image[] b = new Image[n];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    private static int[] growIntArray(int[] a, int n) {
        int[] b = new int[n];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    /** Starts accumulating {@link Image} regions on the current target; flushes any prior batch first. */
    public void beginSpriteBatch() {
        flushRectBatch();
        if (spriteBatchActive && spriteBatchCount > 0) {
            flushPendingSpriteBatch();
        }
        spriteBatchActive = true;
    }

    /** Emits queued sprite draws to the current {@link Graphics} target. */
    public void flushSpriteBatch() {
        if (spriteBatchActive) {
            flushPendingSpriteBatch();
        }
    }

    /** Flush and stop batching (safe if batching was inactive). */
    public void endSpriteBatch() {
        flushPendingSpriteBatch();
        spriteBatchActive = false;
    }

    public boolean isSpriteBatchActive() {
        return spriteBatchActive;
    }

    /** Create an offscreen RGB buffer; returns {@code null} on failure (e.g. OOM). */
    public Layer createLayer(int w, int h) {
        if (w <= 0 || h <= 0) {
            return null;
        }
        try {
            Image img = Image.createImage(w, h);
            Graphics gr = img.getGraphics();
            Layer L = new Layer();
            L.image = img;
            L.graphics = gr;
            L.width = w;
            L.height = h;
            L.freed = false;
            return L;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Direct draws and batch flushes go to this layer's {@link Graphics}, or to the main canvas if {@code null}. */
    public void setDrawLayer(Layer L) {
        flushRectBatch();
        flushPendingSpriteBatch();
        invalidateDrawColor();
        if (L != null && L.freed) {
            currentLayer = null;
            currentGraphics = screenGraphics;
            return;
        }
        currentLayer = L;
        currentGraphics = L != null ? L.graphics : screenGraphics;
    }

    public Layer getCurrentLayer() {
        return currentLayer;
    }

    /** Fill a layer without changing the current draw target. */
    public void clearLayer(Layer L, int color) {
        if (L == null || L.freed || L.graphics == null) {
            return;
        }
        flushRectBatch();
        flushPendingSpriteBatch();
        L.graphics.setColor(color);
        L.graphics.fillRect(0, 0, L.width, L.height);
    }

    /** Blit a full layer onto the current target. */
    public void drawLayer(Layer L, int x, int y) {
        if (L == null || L.freed || L.image == null) {
            return;
        }
        flushRectBatch();
        flushPendingSpriteBatch();
        currentGraphics.drawImage(L.image, x, y, Graphics.TOP | Graphics.LEFT);
    }

    public void freeLayer(Layer L) {
        if (L == null || L.freed) {
            return;
        }
        flushRectBatch();
        flushPendingSpriteBatch();
        if (currentLayer == L) {
            currentLayer = null;
            currentGraphics = screenGraphics;
        }
        L.freed = true;
        L.image = null;
        L.graphics = null;
    }

    /**
     * {@link Graphics#drawString} requires one horizontal and one vertical anchor. If the mask is
     * incomplete (e.g. only {@link Graphics#RIGHT}), default the missing axis to top-left, matching
     * AthenaEnv font alignment usage where a single {@code ALIGN_*} can be set.
     */
    private static int normalizeTextAnchor(int anchor) {
        int h = anchor & (Graphics.LEFT | Graphics.RIGHT | Graphics.HCENTER);
        int v = anchor & (Graphics.TOP | Graphics.BOTTOM | Graphics.VCENTER | Graphics.BASELINE);
        if (h == 0) {
            h = Graphics.LEFT;
        }
        if (v == 0) {
            v = Graphics.TOP;
        }
        return h | v;
    }

    /**
     * Draws a string with the given {@link Font}, {@code color}, and anchor. Restores the previous
     * {@link Graphics} font so other drawing is unaffected.
     */
    public void drawFont(final Font font, final String text, final int x, final int y, final int anchor, final int color) {
        flushRectBatch();
        applyColor(color);
        Font f = font != null ? font : Font.getDefaultFont();
        Graphics g = currentGraphics;
        Font prev = g.getFont();
        g.setFont(f);
        try {
            g.drawString(text, x, y, normalizeTextAnchor(anchor));
        } finally {
            g.setFont(prev);
        }
    }

    public void clearScreen(final int color) {
        flushRectBatch();
        flushPendingSpriteBatch();
        applyColor(color);
        if (currentLayer != null) {
            currentGraphics.fillRect(0, 0, currentLayer.width, currentLayer.height);
        } else {
            currentGraphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    public void drawRect(int x, int y, int w, int h, int color) {
        enqueueFillRect(x, y, w, h, color);
    }

    public void drawLine(int x1, int y1, int x2, int y2, int color) {
        flushRectBatch();
        applyColor(color);
        currentGraphics.drawLine(x1, y1, x2, y2);
    }

    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        flushRectBatch();
        applyColor(color);
        currentGraphics.fillTriangle(x1, y1, x2, y2, x3, y3);
    }

    /**
     * Flushes 2D batches then {@link Graphics#drawRGB(int[], int, int, int, int, int, int, boolean)}.
     * Used by the software 3D path for scanline texturing (fewer native calls than per-pixel {@link #drawRect}).
     */
    public void drawRgb(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        if (rgbData == null || width <= 0 || height <= 0) {
            return;
        }
        flushRectBatch();
        flushPendingSpriteBatch();
        invalidateDrawColor();
        currentGraphics.drawRGB(rgbData, offset, scanlength, x, y, width, height, processAlpha);
    }

    public void _drawImage(Image img, int x, int y) {
        flushRectBatch();
        flushPendingSpriteBatch();
        currentGraphics.drawImage(img, x, y, Graphics.TOP | Graphics.LEFT);
    }

    /**
     * Draws an image region on the current target, or enqueues when sprite batching is active.
     * {@code endx}/{@code endy} are exclusive rectangle corners (same convention as {@link Image} wrapper fields).
     */
    public void drawImageRegion(Image img, int x, int y, int startx, int starty, int endx, int endy) {
        if (img == null) {
            return;
        }
        int rw = endx - startx;
        int rh = endy - starty;
        if (rw <= 0 || rh <= 0) {
            return;
        }
        flushRectBatch();
        if (spriteBatchActive) {
            if (spriteBatchCount >= spriteBatchImg.length) {
                growSpriteBatch(spriteBatchCount + 1);
            }
            int i = spriteBatchCount++;
            spriteBatchImg[i] = img;
            spriteBatchX[i] = x;
            spriteBatchY[i] = y;
            spriteBatchSx[i] = startx;
            spriteBatchSy[i] = starty;
            spriteBatchW[i] = rw;
            spriteBatchH[i] = rh;
        } else {
            currentGraphics.drawRegion(img,
                startx, starty, rw, rh,
                Sprite.TRANS_NONE,
                x, y,
                Graphics.TOP | Graphics.LEFT);
        }
    }

    public void _drawImageRegion(Image img, int x, int y, int startx, int starty, int endx, int endy) {
        drawImageRegion(img, x, y, startx, starty, endx, endy);
    }

    // Cache decoded Images by resource name. Loading a PNG on a feature phone can
    // easily take tens of milliseconds, so per-frame `new Image(...)` calls in JS
    // become a major stall. The cache is keyed by the exact name string passed in.
    private final Hashtable imageCache = new Hashtable();

    public Image loadImage(String name) {
        if (name == null) return null;
        Image ret = (Image) imageCache.get(name);
        if (ret != null) return ret;
        try {
            ret = Image.createImage(name);
            if (ret != null) imageCache.put(name, ret);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return ret;
    }

    public void screenUpdate() {
        flushRectBatch();
        flushPendingSpriteBatch();
        flushGraphics();
        invalidateDrawColor();
    }

    public void padUpdate() {
        old_pressed_buttons = pressed_buttons;
        pressed_buttons = getKeyStates();
    }

    public boolean padPressed(int buttons) {
        return (pressed_buttons & buttons) != 0;
    }

    public boolean padJustPressed(int buttons) {
        return (((old_pressed_buttons & buttons) == 0) && ((pressed_buttons & buttons) != 0));
    }

    /** None of the bits in {@code buttons} are down (mask test; use with {@code Pad} constants). */
    public boolean padNotPressed(int buttons) {
        if (buttons == 0) {
            return false;
        }
        return (pressed_buttons & buttons) == 0;
    }

    public void keyPressed(int keyCode) {
        key = keyCode;
    }

    public void keyReleased(int keyCode) {
        if (key == keyCode) {
            key = 0;
        }
    }

    public int getKeypad() {
        return key;
    }

    /**
     * Flushes 2D batches and returns the {@link Graphics} target for M3G
     * {@code bindTarget} (main buffer or current layer).
     */
    public Graphics getBindGraphicsFor3D() {
        flushRectBatch();
        flushPendingSpriteBatch();
        return currentGraphics;
    }

    public int getTargetWidth3D() {
        if (currentLayer != null) {
            return currentLayer.width;
        }
        return getWidth();
    }

    public int getTargetHeight3D() {
        if (currentLayer != null) {
            return currentLayer.height;
        }
        return getHeight();
    }
}
