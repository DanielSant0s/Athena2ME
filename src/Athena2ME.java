import java.io.*;
import java.util.Hashtable;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;

import net.cnjm.j2me.tinybro.*;
import net.cnjm.j2me.util.*;
import net.cnjm.j2me.sync.AtomicInt;
import net.cnjm.j2me.sync.Mutex;
import net.cnjm.j2me.sync.Semaphore;

public class Athena2ME extends MIDlet implements CommandListener {
    RocksInterpreter ri;
    Rv jsThis = null;
    Rv jsExitHandler = null;
    private AthenaCanvas canvas;
    private Command exitCmd = new Command("Exit", Command.EXIT, 1);
    private Thread jsThread = null;
    volatile boolean jsRunning = false;
    private Thread frameThread = null;
    volatile boolean frameRunning = false;

    private BootSplashCanvas bootCanvas;
    private Thread coldStartThread;
    private boolean bootHandoffScheduled;

    /** Cache for {@code require()}: canonical resource path → module {@code exports} object. */
    private final Hashtable moduleCache = new Hashtable();

    private Render3DBackend r3d;
    /** {@code null} = auto-detect; {@code "soft"} / {@code "m3g"} = forced (see {@code Render3D.setBackend} in JS). */
    private String r3dMode;
    /** Column-major 4x4 transform from JS; no {@code javax.microedition.m3g.Transform} on the MIDlet class
     * (avoids loading M3G types on devices without JSR-184 at startup). */
    private final float[] m3gUserMatrix16 = new float[16];

    /** Monotonic id for {@code Pad.addListener}. */
    private int padListenerNextId = 1;
    private PadListener[] padListeners = new PadListener[4];
    private int padListenerCount;
    /** Grow-only snapshot for {@link #dispatchPadListeners}; avoids per-frame allocation. */
    private PadListener[] padListenerSnap;
    /** Canonical resource path → decoded {@link Image} (shared with boot splash). */
    private final Rhash imageResourceCache = new Rhash(16);

    private static long perfFramesRendered;
    private static long perfNsPadDispatch;
    private static long perfNsJsCallback;
    private static long perfNsScreenUpdate;

    private static final class PadListener {
        int id;
        int buttons;
        int kind;
        Rv fn;
        PadListener(int id, int buttons, int kind, Rv fn) {
            this.id = id;
            this.buttons = buttons;
            this.kind = kind;
            this.fn = fn;
        }
    }

    /** 0 = PRESSED, 1 = JUST_PRESSED, 2 = NON_PRESSED (must match {@code Pad.*} binding values). */
    private static boolean padListenerShouldFire(PadListener L, AthenaCanvas cv) {
        if (L.buttons == 0 || L.fn == null) {
            return false;
        }
        if (L.kind == 0) {
            return cv.padPressed(L.buttons);
        }
        if (L.kind == 1) {
            return cv.padJustPressed(L.buttons);
        }
        return cv.padNotPressed(L.buttons);
    }

    private void ensurePadListenerCapacity(int need) {
        if (need <= padListeners.length) {
            return;
        }
        int n = padListeners.length;
        while (n < need) {
            n *= 2;
        }
        PadListener[] neu = new PadListener[n];
        System.arraycopy(padListeners, 0, neu, 0, padListenerCount);
        padListeners = neu;
    }

    private void addPadListener(PadListener L) {
        ensurePadListenerCapacity(padListenerCount + 1);
        padListeners[padListenerCount++] = L;
    }

    private void removePadListenerById(int id) {
        for (int i = 0; i < padListenerCount; i++) {
            if (padListeners[i].id == id) {
                padListeners[i] = padListeners[--padListenerCount];
                padListeners[padListenerCount] = null;
                return;
            }
        }
    }

    private void dispatchPadListeners(RocksInterpreter ri, Rv thisRef) {
        int n = padListenerCount;
        if (n == 0) {
            return;
        }
        if (padListenerSnap == null || padListenerSnap.length < n) {
            padListenerSnap = new PadListener[n + 4];
        }
        PadListener[] snap = padListenerSnap;
        for (int i = 0; i < n; i++) {
            snap[i] = padListeners[i];
        }
        for (int i = 0; i < n; i++) {
            PadListener L = snap[i];
            snap[i] = null;
            if (L == null) {
                continue;
            }
            if (padListenerShouldFire(L, canvas) && L.fn != null && L.fn.isCallable()) {
                try {
                    ri.invokeJS(L.fn, thisRef, null, 0, 0);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    /**
     * Loads and caches a classpath image (same keying as {@link AthenaCanvas#loadImage}).
     * Used by the main canvas and {@link BootSplashCanvas} so PNGs are decoded once.
     */
    public Image loadResourceImage(String name) {
        if (name == null) {
            return null;
        }
        String path = name.trim();
        if (path.length() == 0) {
            return null;
        }
        if (path.charAt(0) != '/') {
            path = "/" + path;
        }
        Object hit = imageResourceCache.getNativeRef(path);
        if (hit instanceof Image) {
            return (Image) hit;
        }
        Image ret = null;
        try {
            ret = Image.createImage(path);
        } catch (Throwable ignored) {
        }
        if (ret != null) {
            imageResourceCache.putNativeRef(path, ret);
        }
        return ret;
    }

    /**
     * Serializes {@link RocksInterpreter#call}, {@link RocksInterpreter#runInGlobalScope},
     * and {@link PromiseRuntime#drain} for this MIDlet's single interpreter.
     */
    private final Object jsRuntimeLock = new Object();

    /** Truncate a JS number toward zero for integer-only MIDP APIs. */
    private static int jsInt(Rv v) {
        if (v == null || v == Rv._undefined) return 0;
        Rv n = v.toNum();
        if (n == Rv._NaN) return 0;
        return (int) Rv.numValue(n);
    }

    private static float jsFloat(Rv v) {
        if (v == null || v == Rv._undefined) return 0.0f;
        Rv n = v.toNum();
        if (n == Rv._NaN) return 0.0f;
        return (float) Rv.numValue(n);
    }

    /** Mirrors package-private {@code Rv.ARRAY} for {@code type} checks. */
    private static final int RV_T_ARRAY = Rv.OBJECT + 0x0A;

    private static boolean m3gMatrix16FromArray(Rv arr, float[] out16) {
        if (arr == null || out16 == null || out16.length < 16) {
            return false;
        }
        if (arr.type == Rv.FLOAT32_ARRAY && arr.opaque instanceof Rv.Float32View) {
            Rv.Float32View v = (Rv.Float32View) arr.opaque;
            if ((v.byteLength >> 2) != 16) {
                return false;
            }
            try {
                for (int i = 0, p = v.offset; i < 16; i++, p += 4) {
                    out16[i] = Float.intBitsToFloat(int32le(v.data, p));
                }
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
        if (arr.type != RV_T_ARRAY || arr.num < 16) {
            return false;
        }
        try {
            for (int i = 0; i < 16; i++) {
                Rv el = arr.get(Rv.intStr(i));
                out16[i] = (float) Rv.numValue(el.toNum());
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static int int32le(byte[] b, int o) {
        if (b == null || o < 0 || o + 4 > b.length) {
            return 0;
        }
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | (b[o + 3] << 24);
    }

    private void ensureR3D() {
        if (r3d != null) {
            return;
        }
        if (r3dMode == null) {
            r3d = Render3DFactory.create();
        } else {
            r3d = Render3DFactory.createForId(r3dMode);
        }
        if (r3d == null) {
            r3d = new Render3DSoftBackend();
        }
    }

    private String render3dPredictedId() {
        if (r3dMode != null) {
            if ("soft".equals(r3dMode)) {
                return "soft";
            }
            if ("m3g".equals(r3dMode)) {
                return AthenaM3G.isApiAvailable() ? "m3g" : "soft";
            }
        }
        return AthenaM3G.isApiAvailable() ? "m3g" : "soft";
    }

    /** Float positions; length must be a multiple of 3 (xyz triples). */
    private static float[] floatsFromRArray(Rv a) {
        if (a == null) {
            return null;
        }
        if (a.type == Rv.FLOAT32_ARRAY && a.opaque instanceof Rv.Float32View) {
            Rv.Float32View v = (Rv.Float32View) a.opaque;
            int n = v.byteLength >> 2;
            if (n < 9 || (n % 3) != 0) {
                return null;
            }
            float[] o = new float[n];
            for (int i = 0, p = v.offset; i < n; i++, p += 4) {
                o[i] = Float.intBitsToFloat(int32le(v.data, p));
            }
            return o;
        }
        if (a.type != RV_T_ARRAY) {
            return null;
        }
        int n = a.num;
        if (n < 9 || (n % 3) != 0) {
            return null;
        }
        float[] o = new float[n];
        for (int i = 0; i < n; i++) {
            Rv e = a.get(Rv.intStr(i));
            o[i] = (float) Rv.numValue((e == null) ? Rv._NaN : e.toNum());
        }
        return o;
    }

    /** Per-vertex (u,v) pairs: length {@code 2N} for N vertices, even and at least 2. */
    private static float[] uvFloatsFromRArray(Rv a) {
        if (a == null) {
            return null;
        }
        if (a.type == Rv.FLOAT32_ARRAY && a.opaque instanceof Rv.Float32View) {
            Rv.Float32View v = (Rv.Float32View) a.opaque;
            int n = v.byteLength >> 2;
            if (n < 2 || (n & 1) != 0) {
                return null;
            }
            float[] o = new float[n];
            for (int i = 0, p = v.offset; i < n; i++, p += 4) {
                o[i] = Float.intBitsToFloat(int32le(v.data, p));
            }
            return o;
        }
        if (a.type != RV_T_ARRAY) {
            return null;
        }
        int n = a.num;
        if (n < 2 || (n & 1) != 0) {
            return null;
        }
        float[] o = new float[n];
        for (int i = 0; i < n; i++) {
            Rv e = a.get(Rv.intStr(i));
            o[i] = (float) Rv.numValue((e == null) ? Rv._NaN : e.toNum());
        }
        return o;
    }

    /** Strip lengths or other integers; accepts a JS array or Int32Array. */
    private static int[] intsFromRArray(Rv a) {
        if (a == null) {
            return null;
        }
        if (a.type == Rv.INT32_ARRAY && a.opaque instanceof Rv.Int32View) {
            Rv.Int32View v = (Rv.Int32View) a.opaque;
            int w = v.byteLength >> 2;
            if (w <= 0) {
                return null;
            }
            int[] o = new int[w];
            for (int i = 0, p = v.offset; i < w; i++, p += 4) {
                o[i] = int32le(v.data, p);
            }
            return o;
        }
        if (a.type != RV_T_ARRAY) {
            return null;
        }
        if (a.num <= 0) {
            return null;
        }
        int[] o = new int[a.num];
        for (int i = 0; i < a.num; i++) {
            o[i] = jsInt(a.get(Rv.intStr(i)));
        }
        return o;
    }

    /** {@link AthenaCanvas.Layer} wrapper exposed as JS object ({@code opaque}). */
    private static AthenaCanvas.Layer layerFromRv(Rv v) {
        if (v == null || v == Rv._undefined || v == Rv._null) {
            return null;
        }
        Object o = v.opaque;
        if (o instanceof AthenaCanvas.Layer) {
            return (AthenaCanvas.Layer) o;
        }
        return null;
    }

    /** Build a Uint8Array view backed by a fresh ArrayBuffer (same pattern as StdLib). */
    private static Rv newUint8Array(RocksInterpreter ri, byte[] data) {
        return PromiseRuntime.newUint8Array(ri, data);
    }

    private static byte[] bytesFromBufferArg(Rv arg) {
        if (arg == null || arg == Rv._undefined) {
            return new byte[0];
        }
        if (arg.type == Rv.UINT8_ARRAY && arg.opaque instanceof Rv.Uint8View) {
            Rv.Uint8View uv = (Rv.Uint8View) arg.opaque;
            byte[] out = new byte[uv.byteLength];
            System.arraycopy(uv.data, uv.offset, out, 0, uv.byteLength);
            return out;
        }
        try {
            return arg.toStr().str.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return arg.toStr().str.getBytes();
        }
    }

    private static Rv j2mePropertyOrNull(String name) {
        try {
            String s = System.getProperty(name);
            if (s == null) {
                return Rv._null;
            }
            return new Rv(s);
        } catch (Throwable t) {
            return Rv._null;
        }
    }

    /** Fast-path for {@code Image} draw: mirrors JS property writes without extra {@code Rhash} reads. */
    private static final class ImageView implements OpaquePropertySink {
        final Image image;
        int startx, starty, endx, endy, w, h;
        ImageView(Image img) {
            this.image = img;
            int iw = img.getWidth();
            int ih = img.getHeight();
            this.startx = 0;
            this.starty = 0;
            this.endx = iw;
            this.endy = ih;
            this.w = iw;
            this.h = ih;
        }
        public void onPropertyPut(String key, Rv value) {
            int n = jsInt(value);
            if ("startx".equals(key)) { startx = n; return; }
            if ("starty".equals(key)) { starty = n; return; }
            if ("endx".equals(key)) { endx = n; return; }
            if ("endy".equals(key)) { endy = n; return; }
            if ("width".equals(key)) { w = n; return; }
            if ("height".equals(key)) { h = n; return; }
        }
    }

    private static final class FontView implements OpaquePropertySink {
        final javax.microedition.lcdui.Font font;
        int color;
        int align;
        Rv textSizeModule;
        Rv textSizeW;
        Rv textSizeH;
        FontView(javax.microedition.lcdui.Font font, int align0, int color0) {
            this.font = font;
            this.align = align0;
            this.color = color0;
        }
        public void onPropertyPut(String key, Rv value) {
            if ("align".equals(key)) { align = jsInt(value); return; }
            if ("color".equals(key)) { color = jsInt(value); return; }
        }
    }

    private static final class StreamView implements OpaquePropertySink {
        final AthenaSound.StreamHandle h;
        int position;
        int length;
        int loop;
        StreamView(AthenaSound.StreamHandle h, int len0) {
            this.h = h;
            this.position = 0;
            this.length = len0;
            this.loop = 0;
        }
        public void onPropertyPut(String key, Rv value) {
            if ("position".equals(key)) { position = jsInt(value); return; }
            if ("length".equals(key)) { length = jsInt(value); return; }
            if ("loop".equals(key)) { loop = jsInt(value); return; }
        }
    }

    private static final class SfxView implements OpaquePropertySink {
        AthenaSound.SfxData data;
        int volume;
        int pan;
        int pitch;
        SfxView(AthenaSound.SfxData d) {
            this.data = d;
            this.volume = 100;
            this.pan = 0;
            this.pitch = 0;
        }
        public void onPropertyPut(String key, Rv value) {
            if ("volume".equals(key)) { volume = jsInt(value); return; }
            if ("pan".equals(key)) { pan = jsInt(value); return; }
            if ("pitch".equals(key)) { pitch = jsInt(value); return; }
        }
    }

    public Athena2ME() {
        canvas = new AthenaCanvas(false, this);
        canvas.addCommand(exitCmd);
        canvas.setCommandListener(this);
    }

    protected void destroyApp(boolean unconditional) {
        frameRunning = false;
        if (bootCanvas != null) {
            bootCanvas.dispose();
            bootCanvas = null;
        }
        if (coldStartThread != null) {
            coldStartThread.interrupt();
            coldStartThread = null;
        }
        if (frameThread != null) {
            frameThread.interrupt();
            frameThread = null;
        }
        jsRunning = false;
        if (jsThread != null) {
            jsThread.interrupt();
            jsThread = null;
        }
        AthenaSound.shutdown();
        Display.getDisplay(this).setCurrent((Displayable)null);

    }

    protected void pauseApp() {
        // TODO Auto-generated method stub

    }

    protected void startApp() throws MIDletStateChangeException {
        moduleCache.clear();
        bootHandoffScheduled = false;
        final BootIniConfig bootCfg = BootIniConfig.loadFromClasspath(getClass());
        bootCanvas = new BootSplashCanvas(this, bootCfg);
        Display.getDisplay(this).setCurrent(bootCanvas);
        bootCanvas.startAnimating();

        coldStartThread = new Thread(new Runnable() {
            public void run() {
                try {
                    performColdStartPrepareJsThread(bootCfg);
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    notifyColdStartThreadFinished(bootCfg.handoffPolicy);
                }
            }
        });
        coldStartThread.start();
    }

    private void notifyColdStartThreadFinished(int handoffPolicy) {
        if (handoffPolicy == BootIniConfig.HANDOFF_IMMEDIATE) {
            scheduleBootHandoff();
        } else {
            if (bootCanvas != null) {
                bootCanvas.setColdStartReady(true);
            } else {
                scheduleBootHandoff();
            }
        }
    }

    synchronized void scheduleBootHandoff() {
        if (bootHandoffScheduled) {
            return;
        }
        bootHandoffScheduled = true;
        Display.getDisplay(this).callSerially(new Runnable() {
            public void run() {
                BootSplashCanvas b = bootCanvas;
                bootCanvas = null;
                if (b != null) {
                    b.dispose();
                }
                coldStartThread = null;
                Display.getDisplay(Athena2ME.this).setCurrent(canvas);
                if (jsThread != null) {
                    jsThread.start();
                }
            }
        });
    }

    private void performColdStartPrepareJsThread(BootIniConfig bootCfg) {
        InputStream is = "".getClass().getResourceAsStream("/main.js");
        String src = "";

        try {
            src = readUTF(readData(is));
            is.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (ri == null) {
            ri = new RocksInterpreter();
        }
        ri.es6PreprocessEnabled = bootCfg.es6;
        int srcHash = src.hashCode();
        String cachedPp;
        boolean cacheHit = false;
        if ((cachedPp = tryLoadPreprocessedSourceFromRms(srcHash, bootCfg.es6)) != null) {
            cacheHit = true;
            ri.skipEs6PreprocessForNextReset = true;
            ri.reset(cachedPp, null, 0, cachedPp.length());
        } else {
            ri.reset(src, null, 0, src.length());
        }
        ri.evalString = true;
        ri.DEBUG = false;
        if (!cacheHit) {
            try {
                savePreprocessedSourceToRms(srcHash, ri.src, bootCfg.es6);
            } catch (Throwable t) { }
        }

        Node func = ri.astNode(null, '{', 0, 0);
        ri.astNode(func, '{', 0, ri.endpos);
        func.referencesArguments = RocksInterpreter.stmtBlockReferencesArguments(func);
        Rv rv = new Rv(false, func, 0);
        rv.co = ri.initGlobalObject();
        final Rv callObj = rv.co;
        final Athena2ME selfMidlet = this;

        Rv ls = ri.newModule();
        ri.addToObject(callObj, "localStorage", ls);
        
        ri.addToObject(ls, "setItem", ri.addNativeFunction(new NativeFunctionListEntry("localStorage.setItem", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                String k = num > 0 ? ((Rv) args.oArray[start]).toStr().str : "undefined";
                String v = num > 1 ? ((Rv) args.oArray[start + 1]).toStr().str : "undefined";
                AthenaStorage.setItem(k, v);
                return Rv._undefined;
            }
        })));
        ri.addToObject(ls, "getItem", ri.addNativeFunction(new NativeFunctionListEntry("localStorage.getItem", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                String k = num > 0 ? ((Rv) args.oArray[start]).toStr().str : "undefined";
                String val = AthenaStorage.getItem(k);
                return val == null ? Rv._null : new Rv(val);
            }
        })));
        ri.addToObject(ls, "removeItem", ri.addNativeFunction(new NativeFunctionListEntry("localStorage.removeItem", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                String k = num > 0 ? ((Rv) args.oArray[start]).toStr().str : "undefined";
                AthenaStorage.removeItem(k);
                return Rv._undefined;
            }
        })));
        ri.addToObject(ls, "clear", ri.addNativeFunction(new NativeFunctionListEntry("localStorage.clear", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                AthenaStorage.clear();
                return Rv._undefined;
            }
        })));

        Rv lz4 = ri.newModule();
        ri.addToObject(callObj, "LZ4", lz4);
        
        ri.addToObject(lz4, "compress", ri.addNativeFunction(new NativeFunctionListEntry("LZ4.compress", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                if (num < 1) return Rv._undefined;
                byte[] src = bytesFromBufferArg((Rv) args.oArray[start]);
                byte[] comp = AthenaLZ4.compressRaw(src, 0, src.length);
                return newUint8Array(ri, comp);
            }
        })));
        
        ri.addToObject(lz4, "decompress", ri.addNativeFunction(new NativeFunctionListEntry("LZ4.decompress", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                if (num < 2) return Rv._undefined;
                byte[] src = bytesFromBufferArg((Rv) args.oArray[start]);
                int size = jsInt((Rv) args.oArray[start + 1]);
                byte[] dest = new byte[size];
                try {
                    AthenaLZ4.decompressRaw(src, 0, src.length, dest, 0, size);
                } catch (Exception e) {
                    return Rv._undefined;
                }
                return newUint8Array(ri, dest);
            }
        })));

        Rv deflateMod = ri.newModule();
        ri.addToObject(callObj, "DEFLATE", deflateMod);
        ri.addToObject(deflateMod, "inflate", ri.addNativeFunction(new NativeFunctionListEntry("DEFLATE.inflate", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                if (num < 2) return Rv._undefined;
                byte[] src = bytesFromBufferArg((Rv) args.oArray[start]);
                int size = jsInt((Rv) args.oArray[start + 1]);
                try {
                    byte[] dest = new net.cnjm.j2me.util.ZipMe(null).inflate(src, 0, size);
                    return newUint8Array(ri, dest);
                } catch (Exception e) {
                    return Rv._undefined;
                }
            }
        })));

        Rv zipMod = ri.newModule();
        ri.addToObject(callObj, "ZIP", zipMod);
        ri.addToObject(zipMod, "open", ri.addNativeFunction(new NativeFunctionListEntry("ZIP.open", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, final RocksInterpreter ri) {
                if (num < 1) return Rv._undefined;
                byte[] src = bytesFromBufferArg((Rv) args.oArray[start]);
                final net.cnjm.j2me.util.ZipMe zip = new net.cnjm.j2me.util.ZipMe(src);
                
                Rv zobj = ri.newModule();
                ri.addToObject(zobj, "list", ri.addNativeFunction(new NativeFunctionListEntry("ZIP_list", new NativeFunctionFast() {
                    public final int length = 0;
                    public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter r) {
                        try {
                            Pack list = zip.list();
                            Rv arr = r.newEmptyArray();
                            arr.num = list.oSize;
                            for (int i = 0; i < list.oSize; i++) {
                                ri.addToObject(arr, String.valueOf(i), new Rv((String) list.oArray[i]));
                            }
                            return arr;
                        } catch (Exception e) {
                            return r.newEmptyArray();
                        }
                    }
                })));
                
                ri.addToObject(zobj, "get", ri.addNativeFunction(new NativeFunctionListEntry("ZIP_get", new NativeFunctionFast() {
                    public final int length = 1;
                    public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter r) {
                        if (num < 1) return Rv._undefined;
                        String name = ((Rv) args.oArray[start]).toStr().str;
                        try {
                            byte[] data = zip.get(name);
                            if (data == null) return Rv._null;
                            return newUint8Array(r, data);
                        } catch (Exception e) {
                            return Rv._null;
                        }
                    }
                })));
                return zobj;
            }
        })));

        Rv _os = ri.newModule();
        ri.addToObject(_os, "platform", new Rv("j2me"));
        ri.addToObject(_os, "O_RDONLY", new Rv(AthenaFile.O_RDONLY));
        ri.addToObject(_os, "O_WRONLY", new Rv(AthenaFile.O_WRONLY));
        ri.addToObject(_os, "O_RDWR", new Rv(AthenaFile.O_RDWR));
        ri.addToObject(_os, "O_NDELAY", new Rv(AthenaFile.O_NDELAY));
        ri.addToObject(_os, "O_APPEND", new Rv(AthenaFile.O_APPEND));
        ri.addToObject(_os, "O_CREAT", new Rv(AthenaFile.O_CREAT));
        ri.addToObject(_os, "O_TRUNC", new Rv(AthenaFile.O_TRUNC));
        ri.addToObject(_os, "O_EXCL", new Rv(AthenaFile.O_EXCL));
        ri.addToObject(_os, "SEEK_SET", new Rv(AthenaFile.SEEK_SET));
        ri.addToObject(_os, "SEEK_CUR", new Rv(AthenaFile.SEEK_CUR));
        ri.addToObject(_os, "SEEK_END", new Rv(AthenaFile.SEEK_END));

        ri.addToObject(_os, "vibrate", ri.addNativeFunction(new NativeFunctionListEntry("os.vibrate", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter r) {
                if (num > 0) {
                    int duration = jsInt((Rv) args.oArray[start]);
                    if (duration > 0) {
                        try {
                            javax.microedition.lcdui.Display.getDisplay(selfMidlet).vibrate(duration);
                        } catch (Exception e) {}
                    }
                }
                return Rv._undefined;
            }
        })));

        Rv _camera = ri.newModule();
        ri.addToObject(_os, "camera", _camera);
        ri.addToObject(_camera, "takeSnapshot", ri.addNativeFunction(new NativeFunctionListEntry("os.camera.takeSnapshot", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, final RocksInterpreter r) {
                int w = 320, h = 240;
                String enc = "jpeg";
                if (num > 0 && args.oArray[start] != null) {
                    Rv opts = (Rv) args.oArray[start];
                    Rv rw = opts.get("width");
                    if (rw != null && rw != Rv._undefined) w = jsInt(rw);
                    Rv rh = opts.get("height");
                    if (rh != null && rh != Rv._undefined) h = jsInt(rh);
                    Rv re = opts.get("encoding");
                    if (re != null && re != Rv._undefined) enc = re.toStr().str;
                }
                final int width = w;
                final int height = h;
                final String encoding = enc;
                final Rv promise = net.cnjm.j2me.tinybro.PromiseRuntime.createPending(r);
                
                new Thread(new Runnable() {
                    public void run() {
                        javax.microedition.media.Player player = null;
                        try {
                            player = javax.microedition.media.Manager.createPlayer("capture://video");
                            player.realize();
                            player.start();
                            javax.microedition.media.control.VideoControl vc = (javax.microedition.media.control.VideoControl) player.getControl("VideoControl");
                            if (vc == null) {
                                net.cnjm.j2me.tinybro.PromiseRuntime.reject(r, promise, Rv.error("No VideoControl"));
                            } else {
                                byte[] raw = vc.getSnapshot("encoding=" + encoding + "&width=" + width + "&height=" + height);
                                if (raw == null) {
                                    net.cnjm.j2me.tinybro.PromiseRuntime.reject(r, promise, Rv.error("Snapshot failed"));
                                } else {
                                    net.cnjm.j2me.tinybro.PromiseRuntime.resolveViaCapability(r, promise, newUint8Array(r, raw));
                                }
                            }
                        } catch (Exception e) {
                            net.cnjm.j2me.tinybro.PromiseRuntime.reject(r, promise, Rv.error(e.toString()));
                        } finally {
                            if (player != null) {
                                try {
                                    player.close();
                                } catch (Exception e) {}
                            }
                        }
                    }
                }).start();
                
                return promise;
            }
        })));

        ri.addToObject(_os, "setExitHandler", 
            ri.addNativeFunction(new NativeFunctionListEntry("os.setExitHandler", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                jsExitHandler = args.get("0");
            
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "open", 
            ri.addNativeFunction(new NativeFunctionListEntry("os.open", new NativeFunction() {
            public final int length = 2;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                String path = args.get("0").toStr().str;
                int flags = jsInt(args.get("1"));
            
                return new Rv(AthenaFile.open(path, flags));
            }
        })));

        ri.addToObject(_os, "close", 
            ri.addNativeFunction(new NativeFunctionListEntry("os.close", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int fd = jsInt(args.get("0"));

                AthenaFile.close(fd);
            
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "seek", 
            ri.addNativeFunction(new NativeFunctionListEntry("os.seek", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int fd = jsInt(args.get("0"));
                int offset = jsInt(args.get("1"));
                int whence = jsInt(args.get("2"));
            
                return new Rv(AthenaFile.seek(fd, offset, whence));
            }
        })));

        ri.addToObject(_os, "read",
            ri.addNativeFunction(new NativeFunctionListEntry("os.read", new NativeFunction() {
            public final int length = 2;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int fd = jsInt(args.get("0"));
                int size = jsInt(args.get("1"));
                if (size < 1) {
                    size = 1024;
                }
                if (size > 1048576) {
                    size = 1048576;
                }
                byte[] buf = new byte[size];
                int n = AthenaFile.read(fd, buf, size);
                if (n <= 0) {
                    return newUint8Array(ri, new byte[0]);
                }
                if (n == size) {
                    return newUint8Array(ri, buf);
                }
                byte[] t = new byte[n];
                System.arraycopy(buf, 0, t, 0, n);
                return newUint8Array(ri, t);
            }
        })));

        ri.addToObject(_os, "write",
            ri.addNativeFunction(new NativeFunctionListEntry("os.write", new NativeFunction() {
            public final int length = 2;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int fd = jsInt(args.get("0"));
                byte[] data = bytesFromBufferArg(args.get("1"));
                int n = AthenaFile.write(fd, data, data.length);
                return new Rv(n);
            }
        })));

        ri.addToObject(_os, "fstat",
            ri.addNativeFunction(new NativeFunctionListEntry("os.fstat", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int fd = jsInt(args.get("0"));
                long[] st = AthenaFile.stat(fd);
                Rv o = ri.newModule();
                if (st == null) {
                    ri.addToObject(o, "error", new Rv("bad fd or stat failed"));
                    return o;
                }
                ri.addToObject(o, "size", new Rv((double) st[0]));
                ri.addToObject(o, "isDirectory", new Rv((double) st[1]));
                ri.addToObject(o, "lastModified", new Rv((double) st[2]));
                return o;
            }
        })));

        ri.addToObject(_os, "sleep",
            ri.addNativeFunction(new NativeFunctionListEntry("os.sleep", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                int ms = jsInt(Rv.argAt(args, start, num, 0));
                if (ms < 0) ms = 0;
                synchronized (jsRuntimeLock) {
                    PromiseRuntime.drain(ri);
                }
                try { Thread.sleep(ms); } catch (InterruptedException e) {}
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "flushPromises",
            ri.addNativeFunction(new NativeFunctionListEntry("os.flushPromises", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                synchronized (jsRuntimeLock) {
                    PromiseRuntime.drain(ri);
                }
                return Rv._undefined;
            }
        })));

        // os.startFrameLoop(fn, fps) — opt-in native game loop. The loop runs in a
        // dedicated Thread and does three things per frame: pad update, invoke the
        // user callback, flush graphics. Eliminates the overhead of an
        // interpreted `while(running){ ... }` driving the game.
        final Athena2ME self = this;
        final Rv _this = callObj;
        ri.addToObject(_os, "startFrameLoop",
            ri.addNativeFunction(new NativeFunctionListEntry("os.startFrameLoop", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (self.frameRunning) return Rv._undefined;
                final Rv fn = Rv.argAt(args, start, num, 0);
                if (!fn.isCallable()) return Rv._undefined;
                int fps = num > 1 ? jsInt(Rv.argAt(args, start, num, 1)) : 30;
                final int frameMs = fps > 0 ? 1000 / fps : 0;
                final RocksInterpreter interp = ri;
                final Rv thisRef = _this;
                self.frameRunning = true;
                final AthenaCanvas cv = self.canvas;
                final Object frameJsLock = self.jsRuntimeLock;
                self.frameThread = new Thread(new Runnable() {
                    public void run() {
                        while (self.frameRunning) {
                            long deadline = System.currentTimeMillis() + frameMs;
                            try {
                                long t0;
                                cv.padUpdate();
                                synchronized (frameJsLock) {
                                    t0 = System.currentTimeMillis();
                                    cv.beginFrameAutoBatch();
                                    self.dispatchPadListeners(interp, thisRef);
                                    perfNsPadDispatch += System.currentTimeMillis() - t0;
                                    PromiseRuntime.drain(interp);
                                    t0 = System.currentTimeMillis();
                                    interp.call(false, fn, fn.co, thisRef, null, 0, 0);
                                    perfNsJsCallback += System.currentTimeMillis() - t0;
                                }
                                t0 = System.currentTimeMillis();
                                cv.screenUpdate();
                                perfNsScreenUpdate += System.currentTimeMillis() - t0;
                                perfFramesRendered++;
                            } catch (Throwable t) {
                                t.printStackTrace();
                                break;
                            }
                            long wait = deadline - System.currentTimeMillis();
                            if (wait > 0) {
                                try { Thread.sleep(wait); } catch (InterruptedException e) { break; }
                            } else {
                                Thread.yield();
                            }
                        }
                        self.frameRunning = false;
                    }
                });
                self.frameThread.start();
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "stopFrameLoop",
            ri.addNativeFunction(new NativeFunctionListEntry("os.stopFrameLoop", new NativeFunctionFast() {
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                self.frameRunning = false;
                if (self.frameThread != null) {
                    self.frameThread.interrupt();
                    self.frameThread = null;
                }
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "getSystemInfo",
            ri.addNativeFunction(new NativeFunctionListEntry("os.getSystemInfo", new NativeFunctionFast() {
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv o = ri.newModule();
                ri.addToObject(o, "microedition.platform", j2mePropertyOrNull("microedition.platform"));
                ri.addToObject(o, "microedition.configuration", j2mePropertyOrNull("microedition.configuration"));
                ri.addToObject(o, "microedition.profiles", j2mePropertyOrNull("microedition.profiles"));
                ri.addToObject(o, "microedition.locale", j2mePropertyOrNull("microedition.locale"));
                ri.addToObject(o, "microedition.encoding", j2mePropertyOrNull("microedition.encoding"));
                return o;
            }
        })));

        ri.addToObject(_os, "getMemoryStats",
            ri.addNativeFunction(new NativeFunctionListEntry("os.getMemoryStats", new NativeFunctionFast() {
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (num > 0) {
                    Rv a0 = Rv.argAt(args, start, num, 0);
                    if (a0 != Rv._undefined && a0 != Rv._null && a0.asBool()) {
                        Runtime.getRuntime().gc();
                    }
                }
                if (num > 1) {
                    Rv a1 = Rv.argAt(args, start, num, 1);
                    if (a1 != Rv._undefined && a1 != Rv._null && a1.asBool()) {
                        ri.trimInternalPools();
                    }
                }
                long total = Runtime.getRuntime().totalMemory();
                long free = Runtime.getRuntime().freeMemory();
                long used = total - free;
                Rv o = ri.newModule();
                ri.addToObject(o, "heapTotal", new Rv((double) total));
                ri.addToObject(o, "heapFree", new Rv((double) free));
                ri.addToObject(o, "heapUsed", new Rv((double) used));
                ri.addToObject(o, "imageCacheEntries", new Rv((double) selfMidlet.imageResourceCache.size));
                ri.addToObject(o, "moduleCacheEntries", new Rv((double) selfMidlet.moduleCache.size()));
                ri.addToObject(o, "padListenerCount", new Rv((double) selfMidlet.padListenerCount));
                ri.addToObject(o, "rhashSlabRecycleDepth", new Rv((double) Rv.rhashEntryRecycleDepth()));
                ri.addToObject(o, "rhashSlabCapacity", new Rv((double) Rv.rhashEntrySlabCapacity()));
                ri.addToObject(o, "nativeBindings", new Rv((double) ri.getNativeFunctionCount()));
                return o;
            }
        })));

        ri.addToObject(_os, "getPerfStats",
            ri.addNativeFunction(new NativeFunctionListEntry("os.getPerfStats", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                Rv o = useRi.newModule();
                long hint = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                useRi.addToObject(o, "framesRendered", new Rv((double) perfFramesRendered));
                useRi.addToObject(o, "msPadDispatch", new Rv((double) perfNsPadDispatch));
                useRi.addToObject(o, "msJsCallback", new Rv((double) perfNsJsCallback));
                useRi.addToObject(o, "msScreenUpdate", new Rv((double) perfNsScreenUpdate));
                useRi.addToObject(o, "heapUsedHint", new Rv((double) hint));
                return o;
            }
        })));

        ri.addToObject(_os, "trimPools",
            ri.addNativeFunction(new NativeFunctionListEntry("os.trimPools", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                useRi.trimInternalPools();
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "getStorageStats",
            ri.addNativeFunction(new NativeFunctionListEntry("os.getStorageStats", new NativeFunctionFast() {
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv o = ri.newModule();
                if (num < 1) {
                    ri.addToObject(o, "error", new Rv("fileUrl required"));
                    return o;
                }
                Rv a0 = Rv.argAt(args, start, num, 0);
                if (a0 == Rv._undefined || a0 == Rv._null) {
                    ri.addToObject(o, "error", new Rv("fileUrl required"));
                    return o;
                }
                String fileUrl = a0.toStr().str;
                if (fileUrl == null || fileUrl.length() == 0) {
                    ri.addToObject(o, "error", new Rv("fileUrl required"));
                    return o;
                }
                FileConnection fc = null;
                try {
                    fc = (FileConnection) Connector.open(fileUrl, Connector.READ);
                    ri.addToObject(o, "total", new Rv((double) fc.totalSize()));
                    ri.addToObject(o, "free", new Rv((double) fc.availableSize()));
                } catch (Throwable t) {
                    String msg = t.getMessage();
                    ri.addToObject(o, "error", new Rv(msg != null && msg.length() > 0 ? msg : t.toString()));
                } finally {
                    if (fc != null) {
                        try {
                            fc.close();
                        } catch (IOException e) {
                        }
                    }
                }
                return o;
            }
        })));

        ri.addToObject(_os, "getProperty",
            ri.addNativeFunction(new NativeFunctionListEntry("os.getProperty", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                if (num < 1) {
                    return Rv._null;
                }
                Rv a0 = Rv.argAt(args, start, num, 0);
                if (a0 == Rv._undefined || a0 == Rv._null) {
                    return Rv._null;
                }
                return j2mePropertyOrNull(a0.toStr().str);
            }
        })));

        ri.addToObject(_os, "bluetoothGetCapabilities",
            ri.addNativeFunction(new NativeFunctionListEntry("os.bluetoothGetCapabilities", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                return AthenaBluetooth.getCapabilities(useRi);
            }
        })));

        ri.addToObject(_os, "bluetoothInquiry",
            ri.addNativeFunction(new NativeFunctionListEntry("os.bluetoothInquiry", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                int ms = num > 0 ? jsInt(Rv.argAt(args, start, num, 0)) : 0;
                return AthenaBluetooth.inquiryPromise(useRi, ms);
            }
        })));

        ri.addToObject(_os, "currentTimeMillis",
            ri.addNativeFunction(new NativeFunctionListEntry("os.currentTimeMillis", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                return new Rv((double) System.currentTimeMillis());
            }
        })));

        ri.addToObject(_os, "uptimeMillis",
            ri.addNativeFunction(new NativeFunctionListEntry("os.uptimeMillis", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                return new Rv((double) (System.currentTimeMillis() - RocksInterpreter.bootTime));
            }
        })));

        ri.addToObject(_os, "gc",
            ri.addNativeFunction(new NativeFunctionListEntry("os.gc", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                Runtime.getRuntime().gc();
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "threadYield",
            ri.addNativeFunction(new NativeFunctionListEntry("os.threadYield", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter useRi) {
                Thread.yield();
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "spawn",
            ri.addNativeFunction(new NativeFunctionListEntry("os.spawn", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv fn = args.get("0");
                if (fn == null || !fn.isCallable()) {
                    return PromiseRuntime.rejected(ri, Rv.error("spawn: function required"));
                }
                final Rv fnCap = fn;
                final RocksInterpreter riCap = ri;
                final Rv promise = PromiseRuntime.createPending(ri);
                new Thread(new Runnable() {
                    public void run() {
                        PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                            public void run(RocksInterpreter ri2) {
                                RocksInterpreter use = ri2 != null ? ri2 : riCap;
                                try {
                                    Rv out = use.invokeJS(fnCap, Rv._undefined, RocksInterpreter.EMPTY_ARGS_PACK, 0, 0);
                                    PromiseRuntime.resolveViaCapability(use, promise, out != null ? out : Rv._undefined);
                                } catch (Throwable t) {
                                    PromiseRuntime.reject(use, promise, Rv.error(t.getMessage() != null ? t.getMessage() : "spawn failed"));
                                }
                            }
                        });
                    }
                }).start();
                return promise;
            }
        })));

        final Rv[] mutexCtorBox = new Rv[1];
        mutexCtorBox[0] = ri.addNativeFunction(new NativeFunctionListEntry("os.Mutex.ctor", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv ret = new Rv(Rv.OBJECT, mutexCtorBox[0]);
                ret.opaque = new Mutex();
                return ret;
            }
        }));
        mutexCtorBox[0].nativeCtor("Mutex", callObj);
        final Rv mutexCtor = mutexCtorBox[0];
        ri.addToObject(mutexCtor.ctorOrProt, "lock",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Mutex.lock", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (!(o instanceof Mutex)) {
                    return Rv._undefined;
                }
                try {
                    ((Mutex) o).lock();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(mutexCtor.ctorOrProt, "tryLock",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Mutex.tryLock", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (!(o instanceof Mutex)) {
                    return Rv._false;
                }
                return ((Mutex) o).tryLock() ? Rv._true : Rv._false;
            }
        })));
        ri.addToObject(mutexCtor.ctorOrProt, "unlock",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Mutex.unlock", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (o instanceof Mutex) {
                    ((Mutex) o).unlock();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(_os, "Mutex",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Mutex", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv ret = new Rv(Rv.OBJECT, mutexCtor);
                ret.opaque = new Mutex();
                return ret;
            }
        })));

        final Rv[] semCtorBox = new Rv[1];
        semCtorBox[0] = ri.addNativeFunction(new NativeFunctionListEntry("os.Semaphore.ctor", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int initial = args != null && args.get("0") != null ? jsInt(args.get("0")) : 1;
                int max = args != null && args.get("1") != null ? jsInt(args.get("1")) : initial;
                if (initial < 0) {
                    initial = 0;
                }
                if (max < 1) {
                    max = initial > 0 ? initial : 1;
                }
                Rv ret = new Rv(Rv.OBJECT, semCtorBox[0]);
                ret.opaque = new Semaphore(initial, max);
                return ret;
            }
        }));
        semCtorBox[0].nativeCtor("Semaphore", callObj);
        final Rv semCtor = semCtorBox[0];
        ri.addToObject(semCtor.ctorOrProt, "acquire",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Semaphore.acquire", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (!(o instanceof Semaphore)) {
                    return Rv._undefined;
                }
                try {
                    ((Semaphore) o).acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(semCtor.ctorOrProt, "tryAcquire",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Semaphore.tryAcquire", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (!(o instanceof Semaphore)) {
                    return Rv._false;
                }
                return ((Semaphore) o).tryAcquire() ? Rv._true : Rv._false;
            }
        })));
        ri.addToObject(semCtor.ctorOrProt, "release",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Semaphore.release", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (o instanceof Semaphore) {
                    ((Semaphore) o).release();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(semCtor.ctorOrProt, "availablePermits",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Semaphore.availablePermits", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (!(o instanceof Semaphore)) {
                    return Rv.smallInt(0);
                }
                return Rv.smallInt(((Semaphore) o).availablePermits());
            }
        })));
        ri.addToObject(_os, "Semaphore",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Semaphore", new NativeFunction() {
            public final int length = 2;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int initial = args != null && args.get("0") != null ? jsInt(args.get("0")) : 1;
                int max = args != null && args.get("1") != null ? jsInt(args.get("1")) : initial;
                if (initial < 0) {
                    initial = 0;
                }
                if (max < 1) {
                    max = initial > 0 ? initial : 1;
                }
                Rv ret = new Rv(Rv.OBJECT, semCtor);
                ret.opaque = new Semaphore(initial, max);
                return ret;
            }
        })));

        final Rv[] atomicCtorBox = new Rv[1];
        atomicCtorBox[0] = ri.addNativeFunction(new NativeFunctionListEntry("os.AtomicInt.ctor", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int v = args != null && args.get("0") != null ? jsInt(args.get("0")) : 0;
                Rv ret = new Rv(Rv.OBJECT, atomicCtorBox[0]);
                ret.opaque = new AtomicInt(v);
                return ret;
            }
        }));
        atomicCtorBox[0].nativeCtor("AtomicInt", callObj);
        final Rv atomicCtor = atomicCtorBox[0];
        ri.addToObject(atomicCtor.ctorOrProt, "get",
            ri.addNativeFunction(new NativeFunctionListEntry("os.AtomicInt.get", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (!(o instanceof AtomicInt)) {
                    return Rv.smallInt(0);
                }
                return Rv.smallInt(((AtomicInt) o).get());
            }
        })));
        ri.addToObject(atomicCtor.ctorOrProt, "set",
            ri.addNativeFunction(new NativeFunctionListEntry("os.AtomicInt.set", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (o instanceof AtomicInt && args != null) {
                    ((AtomicInt) o).set(jsInt(args.get("0")));
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(atomicCtor.ctorOrProt, "addAndGet",
            ri.addNativeFunction(new NativeFunctionListEntry("os.AtomicInt.addAndGet", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Object o = _this.opaque;
                if (!(o instanceof AtomicInt) || args == null) {
                    return Rv.smallInt(0);
                }
                return Rv.smallInt(((AtomicInt) o).addAndGet(jsInt(args.get("0"))));
            }
        })));
        ri.addToObject(_os, "AtomicInt",
            ri.addNativeFunction(new NativeFunctionListEntry("os.AtomicInt", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int v = args != null && args.get("0") != null ? jsInt(args.get("0")) : 0;
                Rv ret = new Rv(Rv.OBJECT, atomicCtor);
                ret.opaque = new AtomicInt(v);
                return ret;
            }
        })));

        /** Max pooled objects per {@code os.pool} (heap safety on constrained devices). */
        final int poolMaxCapacity = 8192;

        final Rv[] poolCtorBox = new Rv[1];
        poolCtorBox[0] = ri.addNativeFunction(new NativeFunctionListEntry("os.Pool.ctor", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                return new Rv(Rv.OBJECT, poolCtorBox[0]);
            }
        }));
        poolCtorBox[0].nativeCtor("Pool", callObj);
        final Rv poolCtor = poolCtorBox[0];

        ri.addToObject(poolCtor.ctorOrProt, "acquire",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Pool.acquire", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Object po = thiz != null ? thiz.opaque : null;
                if (!(po instanceof JsObjectPool)) {
                    return Rv._null;
                }
                return ((JsObjectPool) po).acquire(ri, args, start, num);
            }
        })));
        ri.addToObject(poolCtor.ctorOrProt, "release",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Pool.release", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Object po = thiz != null ? thiz.opaque : null;
                if (!(po instanceof JsObjectPool)) {
                    return Rv._undefined;
                }
                Rv obj = num > 0 ? (Rv) args.getObject(start) : Rv._undefined;
                ((JsObjectPool) po).release(obj);
                return Rv._undefined;
            }
        })));
        ri.addToObject(poolCtor.ctorOrProt, "free",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Pool.free", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Object po = thiz != null ? thiz.opaque : null;
                if (!(po instanceof JsObjectPool)) {
                    return Rv.smallInt(0);
                }
                return Rv.smallInt(((JsObjectPool) po).available());
            }
        })));
        ri.addToObject(poolCtor.ctorOrProt, "capacity",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Pool.capacity", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Object po = thiz != null ? thiz.opaque : null;
                if (!(po instanceof JsObjectPool)) {
                    return Rv.smallInt(0);
                }
                return Rv.smallInt(((JsObjectPool) po).capacity());
            }
        })));
        ri.addToObject(poolCtor.ctorOrProt, "inUse",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Pool.inUse", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Object po = thiz != null ? thiz.opaque : null;
                if (!(po instanceof JsObjectPool)) {
                    return Rv.smallInt(0);
                }
                return Rv.smallInt(((JsObjectPool) po).inUse());
            }
        })));

        ri.addToObject(_os, "pool",
            ri.addNativeFunction(new NativeFunctionListEntry("os.pool", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv ctor = Rv.argAt(args, start, num, 0);
                Rv sizeRv = Rv.argAt(args, start, num, 1);
                if (ctor == null || !ctor.isCallable()) {
                    return Rv.error("os.pool: constructor required");
                }
                int cap = jsInt(sizeRv);
                if (cap < 0) {
                    cap = 0;
                }
                if (cap > poolMaxCapacity) {
                    cap = poolMaxCapacity;
                }
                JsObjectPool state;
                try {
                    state = new JsObjectPool(ri, ctor, cap);
                } catch (Throwable t) {
                    String msg = t.getMessage();
                    return Rv.error("os.pool: " + (msg != null ? msg : "failed"));
                }
                Rv ret = new Rv(Rv.OBJECT, poolCtor);
                ret.opaque = state;
                return ret;
            }
        })));

        Rv _ThreadMod = ri.newModule();
        ri.addToObject(_ThreadMod, "start",
            ri.addNativeFunction(new NativeFunctionListEntry("os.Thread.start", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv fn = args.get("0");
                if (fn == null || !fn.isCallable()) {
                    return PromiseRuntime.rejected(ri, Rv.error("Thread.start: function required"));
                }
                final Rv fnCap = fn;
                final RocksInterpreter riCap = ri;
                final Rv promise = PromiseRuntime.createPending(ri);
                new Thread(new Runnable() {
                    public void run() {
                        PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                            public void run(RocksInterpreter ri2) {
                                RocksInterpreter use = ri2 != null ? ri2 : riCap;
                                try {
                                    Rv out = use.invokeJS(fnCap, Rv._undefined, RocksInterpreter.EMPTY_ARGS_PACK, 0, 0);
                                    PromiseRuntime.resolveViaCapability(use, promise, out != null ? out : Rv._undefined);
                                } catch (Throwable t) {
                                    PromiseRuntime.reject(use, promise, Rv.error(t.getMessage() != null ? t.getMessage() : "Thread.start failed"));
                                }
                            }
                        });
                    }
                }).start();
                return promise;
            }
        })));
        ri.addToObject(_os, "Thread", _ThreadMod);

        ri.addToObject(callObj, "os", _os);

        Rv _Screen = ri.newModule();
        ri.addToObject(_Screen, "width", new Rv(canvas.getWidth()));
        ri.addToObject(_Screen, "height", new Rv(canvas.getHeight()));

        final Rv[] screenLayerCtorBox = new Rv[1];
        screenLayerCtorBox[0] = ri.addNativeFunction(new NativeFunctionListEntry("Screen.Layer.ctor", new NativeFunction() {
            public final int length = 0;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                return new Rv(Rv.OBJECT, screenLayerCtorBox[0]);
            }
        }));
        screenLayerCtorBox[0].nativeCtor("ScreenLayer", callObj);
        final Rv screenLayerCtor = screenLayerCtorBox[0];

        ri.addToObject(_Screen, "clear", 
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.clear", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                int color = num > 0 ? jsInt(Rv.argAt(args, start, num, 0)) : AthenaCanvas.CLEAR_COLOR;

                canvas.clearScreen(color);

                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "update", 
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.update", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    canvas.screenUpdate();
                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Screen, "beginBatch",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.beginBatch", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                canvas.beginSpriteBatch();
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "flushBatch",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.flushBatch", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                canvas.flushSpriteBatch();
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "endBatch",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.endBatch", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                canvas.endSpriteBatch();
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "setAutoBatch",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.setAutoBatch", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                boolean on = num > 0 && Rv.argAt(args, start, num, 0).asBool();
                canvas.setAutoSpriteBatchPerFrame(on);
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "createLayer",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.createLayer", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                int w = jsInt(Rv.argAt(args, start, num, 0));
                int h = jsInt(Rv.argAt(args, start, num, 1));
                AthenaCanvas.Layer L = canvas.createLayer(w, h);
                if (L == null) {
                    return Rv._null;
                }
                Rv ret = new Rv(Rv.OBJECT, screenLayerCtor);
                ret.opaque = L;
                ri.addToObject(ret, "width", new Rv(L.width));
                ri.addToObject(ret, "height", new Rv(L.height));
                return ret;
            }
        })));

        ri.addToObject(_Screen, "setLayer",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.setLayer", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                AthenaCanvas.Layer L = num > 0 ? layerFromRv(Rv.argAt(args, start, num, 0)) : null;
                canvas.setDrawLayer(L);
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "clearLayer",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.clearLayer", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                AthenaCanvas.Layer L = layerFromRv(Rv.argAt(args, start, num, 0));
                int color = jsInt(Rv.argAt(args, start, num, 1));
                canvas.clearLayer(L, color);
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "drawLayer",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.drawLayer", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                AthenaCanvas.Layer L = layerFromRv(Rv.argAt(args, start, num, 0));
                int x = jsInt(Rv.argAt(args, start, num, 1));
                int y = jsInt(Rv.argAt(args, start, num, 2));
                canvas.drawLayer(L, x, y);
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "freeLayer",
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.freeLayer", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                AthenaCanvas.Layer L = layerFromRv(Rv.argAt(args, start, num, 0));
                canvas.freeLayer(L);
                return Rv._undefined;
            }
        })));

        ri.addToObject(callObj, "Screen", _Screen);

        Rv _Render3D = ri.newModule();
        ri.addToObject(_Render3D, "getBackend",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.getBackend", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                if (r3d != null) {
                    return new Rv(r3d.getId());
                }
                return new Rv(render3dPredictedId());
            }
        })));
        ri.addToObject(_Render3D, "getCapabilities",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.getCapabilities", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                String bid = r3d != null ? r3d.getId() : render3dPredictedId();
                Rv o = ri.newModule();
                ri.addToObject(o, "backend", new Rv(bid));
                ri.addToObject(o, "m3gPresent", new Rv(AthenaM3G.isApiAvailable() ? 1 : 0));
                int mt = -1;
                if (r3d != null) {
                    mt = r3d.getEffectiveMaxTriangles();
                } else if ("soft".equals(bid)) {
                    mt = 1024;
                }
                ri.addToObject(o, "maxTriangles", new Rv(mt));
                ri.addToObject(o, "depthBufferOption", new Rv("soft".equals(bid) ? 1 : 0));
                return o;
            }
        })));
        ri.addToObject(_Render3D, "setTextureFilter",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setTextureFilter", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                Rv a0 = Rv.argAt(args, start, num, 0);
                if (a0 == null || a0 == Rv._undefined) {
                    return Rv.error("setTextureFilter: \"nearest\" | \"linear\"");
                }
                String s = a0.toStr().str;
                if (s == null) {
                    return Rv.error("setTextureFilter: string");
                }
                s = s.trim().toLowerCase();
                if ("nearest".equals(s)) {
                    r3d.setTextureFilterNearest(true);
                } else if ("linear".equals(s)) {
                    r3d.setTextureFilterNearest(false);
                } else {
                    return Rv.error("setTextureFilter: nearest | linear");
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setTextureWrap",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setTextureWrap", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                Rv a0 = Rv.argAt(args, start, num, 0);
                if (a0 == null || a0 == Rv._undefined) {
                    return Rv.error("setTextureWrap: \"repeat\" | \"clamp\"");
                }
                String s = a0.toStr().str;
                if (s == null) {
                    return Rv.error("setTextureWrap: string");
                }
                s = s.trim().toLowerCase();
                if ("repeat".equals(s)) {
                    r3d.setTextureWrapRepeat(true);
                } else if ("clamp".equals(s)) {
                    r3d.setTextureWrapRepeat(false);
                } else {
                    return Rv.error("setTextureWrap: repeat | clamp");
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setBackend",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setBackend", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv a0 = Rv.argAt(args, start, num, 0);
                if (a0 == null || a0 == Rv._undefined) {
                    return Rv.error("setBackend: pass \"m3g\" | \"soft\" | \"auto\"");
                }
                String id = a0.toStr().str;
                if (id == null) {
                    return Rv.error("setBackend: invalid string");
                }
                String li = id.trim().toLowerCase();
                String newMode;
                if ("auto".equals(li) || "default".equals(li)) {
                    newMode = null;
                } else if ("soft".equals(li) || "software".equals(li)) {
                    newMode = "soft";
                } else if ("m3g".equals(li) || "hw".equals(li)) {
                    if (!AthenaM3G.isApiAvailable()) {
                        return Rv.error("setBackend: m3g not available; use soft or auto");
                    }
                    newMode = "m3g";
                } else {
                    return Rv.error("setBackend: m3g | soft | auto");
                }
                if (r3d != null) {
                    try {
                        r3d.end();
                    } catch (Throwable t) { }
                }
                r3d = null;
                r3dMode = newMode;
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "init",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.init", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                int tw = canvas.getTargetWidth3D();
                int th = canvas.getTargetHeight3D();
                float aspect = th > 0 ? ((float) tw) / th : 1.0f;
                r3d.setPerspectiveFromViewport(aspect, 55.0f, 0.1f, 200.0f);
                r3d.setBackgroundColor(0, 0, 0);
                r3d.setCameraPosition(0.0f, 0.0f, 5.0f);
                r3d.setGlobalLightDirection(0.0f, 1.0f, 0.0f);
                r3d.setMaterialAmbient(136, 136, 204);
                r3d.setMaterialDiffuse(255, 255, 255);
                r3d.setMaxTriangles(1024);
                r3d.setBackfaceCulling(true);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setPerspective",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setPerspective", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                int tw = canvas.getTargetWidth3D();
                int th = canvas.getTargetHeight3D();
                float aspect = th > 0 ? ((float) tw) / th : 1.0f;
                float fov = num > 0 ? jsFloat(Rv.argAt(args, start, num, 0)) : 60.0f;
                float n = num > 1 ? jsFloat(Rv.argAt(args, start, num, 1)) : 0.1f;
                float f = num > 2 ? jsFloat(Rv.argAt(args, start, num, 2)) : 200.0f;
                r3d.setPerspectiveFromViewport(aspect, fov, n, f);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setBackground",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setBackground", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                r3d.setBackgroundColor(
                        jsInt(Rv.argAt(args, start, num, 0)),
                        jsInt(Rv.argAt(args, start, num, 1)),
                        jsInt(Rv.argAt(args, start, num, 2)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setCamera",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setCamera", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                r3d.setCameraPosition(
                        jsFloat(Rv.argAt(args, start, num, 0)),
                        jsFloat(Rv.argAt(args, start, num, 1)),
                        jsFloat(Rv.argAt(args, start, num, 2)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setLookAt",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setLookAt", new NativeFunctionFast() {
            public final int length = 9;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                r3d.setLookAt(
                        jsFloat(Rv.argAt(args, start, num, 0)),
                        jsFloat(Rv.argAt(args, start, num, 1)),
                        jsFloat(Rv.argAt(args, start, num, 2)),
                        jsFloat(Rv.argAt(args, start, num, 3)),
                        jsFloat(Rv.argAt(args, start, num, 4)),
                        jsFloat(Rv.argAt(args, start, num, 5)),
                        jsFloat(Rv.argAt(args, start, num, 6)),
                        jsFloat(Rv.argAt(args, start, num, 7)),
                        jsFloat(Rv.argAt(args, start, num, 8)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setMaxTriangles",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setMaxTriangles", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.setMaxTriangles(jsInt(Rv.argAt(args, start, num, 0)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setDepthBuffer",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setDepthBuffer", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                Rv a0 = Rv.argAt(args, start, num, 0);
                r3d.setDepthBuffer(a0 != Rv._false && a0 != Rv._null && a0 != Rv._undefined);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setBackfaceCulling",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setBackfaceCulling", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                Rv a0 = Rv.argAt(args, start, num, 0);
                r3d.setBackfaceCulling(a0 != Rv._false && a0 != Rv._null && a0 != Rv._undefined);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setGlobalLight",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setGlobalLight", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                r3d.setGlobalLightDirection(
                        jsFloat(Rv.argAt(args, start, num, 0)),
                        jsFloat(Rv.argAt(args, start, num, 1)),
                        jsFloat(Rv.argAt(args, start, num, 2)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setMaterialAmbient",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setMaterialAmbient", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                r3d.setMaterialAmbient(
                        jsInt(Rv.argAt(args, start, num, 0)),
                        jsInt(Rv.argAt(args, start, num, 1)),
                        jsInt(Rv.argAt(args, start, num, 2)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setMaterialDiffuse",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setMaterialDiffuse", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                r3d.setMaterialDiffuse(
                        jsInt(Rv.argAt(args, start, num, 0)),
                        jsInt(Rv.argAt(args, start, num, 1)),
                        jsInt(Rv.argAt(args, start, num, 2)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setTexture",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setTexture", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                Rv a0 = Rv.argAt(args, start, num, 0);
                r3d.setTexture2DPath(a0 != null && a0 != Rv._undefined ? a0.toStr().str : null);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setTexCoords",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setTexCoords", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv a0 = Rv.argAt(args, start, num, 0);
                if (a0 == null) {
                    return Rv._undefined;
                }
                float[] uv = uvFloatsFromRArray(a0);
                ensureR3D();
                r3d.init(canvas);
                r3d.setTexCoords(uv);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setIndexedMesh",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setIndexedMesh", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv a0 = Rv.argAt(args, start, num, 0);
                Rv a1 = Rv.argAt(args, start, num, 1);
                if (a0 == null || a1 == null) {
                    return Rv.error("setIndexedMesh: need positions and indices");
                }
                float[] pos = floatsFromRArray(a0);
                int[] idx = intsFromRArray(a1);
                if (pos == null || idx == null) {
                    return Rv.error("setIndexedMesh: invalid positions or indices");
                }
                Rv a2 = Rv.argAt(args, start, num, 2);
                float[] nrm = (num > 2 && a2 != null && a2 != Rv._undefined && a2 != Rv._null)
                        ? floatsFromRArray(a2) : null;
                if (nrm != null && nrm.length != pos.length) {
                    return Rv.error("setIndexedMesh: normals length must match positions");
                }
                ensureR3D();
                r3d.init(canvas);
                r3d.setIndexedTriangleMesh(pos, idx, nrm);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "pushObjectMatrix",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.pushObjectMatrix", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (r3d != null) {
                    r3d.pushObjectMatrix();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "popObjectMatrix",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.popObjectMatrix", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (r3d != null) {
                    r3d.popObjectMatrix();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "getSceneInfo",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.getSceneInfo", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                String t = r3d.getSceneInfo();
                return new Rv(t != null ? t : "");
            }
        })));
        ri.addToObject(_Render3D, "worldAnimate",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.worldAnimate", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (r3d == null) {
                    return Rv._undefined;
                }
                r3d.worldAnimate(jsInt(Rv.argAt(args, start, num, 0)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "m3gNodeTranslate",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gNodeTranslate", new NativeFunctionFast() {
            public final int length = 4;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                float dx = jsFloat(Rv.argAt(args, start, num, 1));
                float dy = jsFloat(Rv.argAt(args, start, num, 2));
                float dz = jsFloat(Rv.argAt(args, start, num, 3));
                String err = r3d.m3gNodeTranslate(uid, dx, dy, dz);
                return err != null ? new Rv(err) : Rv._null;
            }
        })));
        ri.addToObject(_Render3D, "m3gNodeSetTranslation",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gNodeSetTranslation", new NativeFunctionFast() {
            public final int length = 4;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                float x = jsFloat(Rv.argAt(args, start, num, 1));
                float y = jsFloat(Rv.argAt(args, start, num, 2));
                float z = jsFloat(Rv.argAt(args, start, num, 3));
                String err = r3d.m3gNodeSetTranslation(uid, x, y, z);
                return err != null ? new Rv(err) : Rv._null;
            }
        })));
        ri.addToObject(_Render3D, "m3gNodeGetTranslation",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gNodeGetTranslation", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                float[] t = r3d.m3gNodeGetTranslation(uid);
                if (t == null || t.length < 3) {
                    return Rv._null;
                }
                return Rv.newJsArray3((double) t[0], (double) t[1], (double) t[2]);
            }
        })));
        ri.addToObject(_Render3D, "m3gNodeSetOrientation",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gNodeSetOrientation", new NativeFunctionFast() {
            public final int length = 5;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                float ang = jsFloat(Rv.argAt(args, start, num, 1));
                float ax = jsFloat(Rv.argAt(args, start, num, 2));
                float ay = jsFloat(Rv.argAt(args, start, num, 3));
                float az = jsFloat(Rv.argAt(args, start, num, 4));
                String err = r3d.m3gNodeSetOrientation(uid, ang, ax, ay, az);
                return err != null ? new Rv(err) : Rv._null;
            }
        })));
        ri.addToObject(_Render3D, "m3gAnimSetActiveInterval",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gAnimSetActiveInterval", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                int t0 = jsInt(Rv.argAt(args, start, num, 1));
                int t1 = jsInt(Rv.argAt(args, start, num, 2));
                String err = r3d.m3gAnimSetActiveInterval(uid, t0, t1);
                return err != null ? new Rv(err) : Rv._null;
            }
        })));
        ri.addToObject(_Render3D, "m3gAnimSetPosition",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gAnimSetPosition", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                int seq = jsInt(Rv.argAt(args, start, num, 1));
                int tm = jsInt(Rv.argAt(args, start, num, 2));
                String err = r3d.m3gAnimSetPosition(uid, seq, tm);
                return err != null ? new Rv(err) : Rv._null;
            }
        })));
        ri.addToObject(_Render3D, "m3gAnimSetSpeed",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gAnimSetSpeed", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                float sp = jsFloat(Rv.argAt(args, start, num, 1));
                String err = r3d.m3gAnimSetSpeed(uid, sp);
                return err != null ? new Rv(err) : Rv._null;
            }
        })));
        ri.addToObject(_Render3D, "m3gKeyframeDurationTrack0",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.m3gKeyframeDurationTrack0", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                int uid = jsInt(Rv.argAt(args, start, num, 0));
                return new Rv((double) r3d.m3gKeyframeDurationTrack0(uid));
            }
        })));
        ri.addToObject(_Render3D, "setMeshRotation",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setMeshRotation", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.setObjectRotationY(jsFloat(Rv.argAt(args, start, num, 0)));
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setObjectMatrix",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setObjectMatrix", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                Rv arr = Rv.argAt(args, start, num, 0);
                if (m3gMatrix16FromArray(arr, m3gUserMatrix16)) {
                    r3d.setObjectTransformFromColumnMajor(m3gUserMatrix16);
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "setObjectMatrixIdentity",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setObjectMatrixIdentity", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.setObjectTransformIdentity();
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "load",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.load", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                String p = Rv.argAt(args, start, num, 0).toStr().str;
                String err = r3d.loadM3G(p);
                if (err != null) {
                    return new Rv(err);
                }
                return Rv._null;
            }
        })));
        ri.addToObject(_Render3D, "setTriangleStripMesh",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.setTriangleStripMesh", new NativeFunctionFast() {
            public final int length = 2;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                Rv a0 = Rv.argAt(args, start, num, 0);
                Rv a1 = Rv.argAt(args, start, num, 1);
                if (a0 == null || a1 == null) {
                    return Rv.error("setTriangleStripMesh: need positions and stripLens");
                }
                float[] pos = floatsFromRArray(a0);
                int[] sl = intsFromRArray(a1);
                if (pos == null || sl == null) {
                    return Rv.error("setTriangleStripMesh: invalid positions (n*3) or stripLens");
                }
                Rv a2 = Rv.argAt(args, start, num, 2);
                float[] nrm = (num > 2 && a2 != null && a2 != Rv._undefined && a2 != Rv._null)
                        ? floatsFromRArray(a2) : null;
                if (nrm != null && nrm.length != pos.length) {
                    return Rv.error("setTriangleStripMesh: normals length must match positions");
                }
                ensureR3D();
                r3d.init(canvas);
                r3d.setTriangleStripMesh(pos, sl, nrm);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "clearMesh",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.clearMesh", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (r3d != null) {
                    r3d.clearImmediateMesh();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "begin",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.begin", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                ensureR3D();
                r3d.init(canvas);
                r3d.beginFrame(canvas);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "render",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.render", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (r3d == null) {
                    return Rv._undefined;
                }
                r3d.renderImmediate(canvas);
                return Rv._undefined;
            }
        })));
        ri.addToObject(_Render3D, "end",
            ri.addNativeFunction(new NativeFunctionListEntry("Render3D.end", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (r3d != null) {
                    r3d.end();
                }
                return Rv._undefined;
            }
        })));
        ri.addToObject(callObj, "Render3D", _Render3D);

        Rv _Draw = ri.newModule();
        ri.addToObject(_Draw, "line", 
            ri.addNativeFunction(new NativeFunctionListEntry("Draw.line", new NativeFunctionFast() {
                public final int length = 5;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int x1 = jsInt(Rv.argAt(args, start, num, 0));
                    int y1 = jsInt(Rv.argAt(args, start, num, 1));
                    int x2 = jsInt(Rv.argAt(args, start, num, 2));
                    int y2 = jsInt(Rv.argAt(args, start, num, 3));
                    int color = jsInt(Rv.argAt(args, start, num, 4));

                    canvas.drawLine(x1, y1, x2, y2, color);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Draw, "triangle", 
            ri.addNativeFunction(new NativeFunctionListEntry("Draw.triangle", new NativeFunctionFast() {
                public final int length = 5;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int x1 = jsInt(Rv.argAt(args, start, num, 0));
                    int y1 = jsInt(Rv.argAt(args, start, num, 1));
                    int x2 = jsInt(Rv.argAt(args, start, num, 2));
                    int y2 = jsInt(Rv.argAt(args, start, num, 3));
                    int x3 = jsInt(Rv.argAt(args, start, num, 4));
                    int y3 = jsInt(Rv.argAt(args, start, num, 5));
                    int color = jsInt(Rv.argAt(args, start, num, 6));

                    canvas.drawTriangle(x1, y1, x2, y2, x3, y3, color);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Draw, "rect", 
            ri.addNativeFunction(new NativeFunctionListEntry("Draw.rect", new NativeFunctionFast() {
                public final int length = 5;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int x = jsInt(Rv.argAt(args, start, num, 0));
                    int y = jsInt(Rv.argAt(args, start, num, 1));
                    int w = jsInt(Rv.argAt(args, start, num, 2));
                    int h = jsInt(Rv.argAt(args, start, num, 3));
                    int color = jsInt(Rv.argAt(args, start, num, 4));

                    canvas.drawRect(x, y, w, h, color);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Draw, "rects",
            ri.addNativeFunction(new NativeFunctionListEntry("Draw.rects", new NativeFunctionFast() {
                public final int length = 2;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    Rv arr = Rv.argAt(args, start, num, 0);
                    if (arr == null || arr.type != Rv.INT32_ARRAY || !(arr.opaque instanceof Rv.Int32View)) {
                        return Rv._undefined;
                    }
                    Rv.Int32View iv = (Rv.Int32View) arr.opaque;
                    int count = jsInt(Rv.argAt(args, start, num, 1));
                    int stride = num > 2 ? jsInt(Rv.argAt(args, start, num, 2)) : 5;
                    int xOff = num > 3 ? jsInt(Rv.argAt(args, start, num, 3)) : 0;
                    int yOff = num > 4 ? jsInt(Rv.argAt(args, start, num, 4)) : 1;
                    int wOff = num > 5 ? jsInt(Rv.argAt(args, start, num, 5)) : 2;
                    int hOff = num > 6 ? jsInt(Rv.argAt(args, start, num, 6)) : 3;
                    int colorOff = num > 7 ? jsInt(Rv.argAt(args, start, num, 7)) : 4;
                    canvas.drawRects(iv.data, iv.offset, iv.byteLength >> 2,
                            count, stride, xOff, yOff, wOff, hOff, colorOff);
                    return Rv._undefined;
                }
        })));

        ri.addToObject(callObj, "Draw", _Draw);

        final Rv _Image = ri.newModule();

        ri.addNativeFunction(new NativeFunctionListEntry("Image", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _Image);

                    String name = args.get("0").toStr().str;

                    Image img = canvas.loadImage(name);
                    ImageView view = new ImageView(img);
                    ret.opaque = view;

                    int iw = img.getWidth();
                    int ih = img.getHeight();
                    ri.addToObject(ret, "startx", Rv.smallInt(0));
                    ri.addToObject(ret, "starty", Rv.smallInt(0));
                    ri.addToObject(ret, "endx", new Rv(iw));
                    ri.addToObject(ret, "endy", new Rv(ih));
                    ri.addToObject(ret, "width", new Rv(iw));
                    ri.addToObject(ret, "height", new Rv(ih));

                    return ret;
                }
        }));

        _Image.nativeCtor("Image", callObj);
        ri.addToObject(_Image.ctorOrProt, "draw", 
            ri.addNativeFunction(new NativeFunctionListEntry("Image.draw", new NativeFunctionFast() {
                public final int length = 3;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int x = jsInt(Rv.argAt(args, start, num, 0));
                    int y = jsInt(Rv.argAt(args, start, num, 1));
                    int startx, starty, endx, endy;
                    Object op = _this.opaque;
                    if (op instanceof ImageView) {
                        ImageView iv = (ImageView) op;
                        startx = iv.startx;
                        starty = iv.starty;
                        endx = iv.endx;
                        endy = iv.endy;
                    } else {
                        startx = jsInt(_this.get("startx"));
                        starty = jsInt(_this.get("starty"));
                        endx = jsInt(_this.get("endx"));
                        endy = jsInt(_this.get("endy"));
                    }
                    Image img0 = (op instanceof ImageView) ? ((ImageView) op).image : (Image) op;
                    canvas.drawImageRegion(img0, x, y, startx, starty, endx, endy);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Image.ctorOrProt, "free", 
            ri.addNativeFunction(new NativeFunctionListEntry("Image.free", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    _this.opaque = null;

                    return Rv._undefined;
                }
        })));

        ri.addToObject(callObj, "Image", _Image);

        final Rv _Font = ri.newModule();

        ri.addNativeFunction(new NativeFunctionListEntry("Font", new NativeFunction() {
            public final int length = 3;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _Font);

                    Font font = null;
                    Rv font_face =  args.get("0");

                    if (font_face.isStr()) {
                        if (font_face.toStr().str.compareTo("default") == 0) {
                            font = Font.getDefaultFont();
                        } 
                    } else {
                        int font_style = Font.STYLE_PLAIN;
                        int font_size =  Font.SIZE_MEDIUM;

                        if (args.num > 1) {
                            font_style = jsInt(args.get("1"));
                        }

                        if (args.num > 2) {
                            font_size =  jsInt(args.get("2"));
                        }

                        font = Font.getFont(jsInt(font_face), font_style, font_size);
                    }

                    int align0 = canvas.ALIGN_NONE;
                    int col0 = 0x00ffffff;
                    FontView fv = new FontView(font, align0, col0);
                    ret.opaque = fv;

                    ri.addToObject(ret, "align", new Rv(align0));
                    ri.addToObject(ret, "color", new Rv(col0));

                    return ret;
                }
        }));

        _Font.nativeCtor("Font", callObj);
        ri.addToObject(_Font, "STYLE_PLAIN", new Rv(Font.STYLE_PLAIN));
        ri.addToObject(_Font, "STYLE_BOLD", new Rv(Font.STYLE_BOLD));
        ri.addToObject(_Font, "STYLE_ITALIC", new Rv(Font.STYLE_ITALIC));
        ri.addToObject(_Font, "STYLE_UNDERLINED", new Rv(Font.STYLE_UNDERLINED));

        ri.addToObject(_Font, "FACE_MONOSPACE", new Rv(Font.FACE_MONOSPACE));
        ri.addToObject(_Font, "FACE_PROPORTIONAL", new Rv(Font.FACE_PROPORTIONAL));
        ri.addToObject(_Font, "FACE_SYSTEM", new Rv(Font.FACE_SYSTEM));

        ri.addToObject(_Font, "SIZE_SMALL", new Rv(Font.SIZE_SMALL));
        ri.addToObject(_Font, "SIZE_MEDIUM", new Rv(Font.SIZE_MEDIUM));
        ri.addToObject(_Font, "SIZE_LARGE", new Rv(Font.SIZE_LARGE));

        /* Match AthenaEnv (ath_font.c): same anchor integers as {@link #ALIGN_NONE} on Font instances. */
        ri.addToObject(_Font, "ALIGN_TOP", new Rv(canvas.ALIGN_TOP));
        ri.addToObject(_Font, "ALIGN_BOTTOM", new Rv(canvas.ALIGN_BOTTOM));
        ri.addToObject(_Font, "ALIGN_VCENTER", new Rv(canvas.ALIGN_VCENTER));
        ri.addToObject(_Font, "ALIGN_LEFT", new Rv(canvas.ALIGN_LEFT));
        ri.addToObject(_Font, "ALIGN_RIGHT", new Rv(canvas.ALIGN_RIGHT));
        ri.addToObject(_Font, "ALIGN_HCENTER", new Rv(canvas.ALIGN_HCENTER));
        ri.addToObject(_Font, "ALIGN_NONE", new Rv(canvas.ALIGN_NONE));
        ri.addToObject(_Font, "ALIGN_CENTER", new Rv(canvas.ALIGN_CENTER));

        ri.addToObject(_Font.ctorOrProt, "print", 
            ri.addNativeFunction(new NativeFunctionListEntry("Font.print", new NativeFunctionFast() {
            public final int length = 3;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    String text = Rv.argAt(args, start, num, 0).toStr().str;
                    int x = jsInt(Rv.argAt(args, start, num, 1));
                    int y = jsInt(Rv.argAt(args, start, num, 2));
                    int color, align;
                    Object op = _this.opaque;
                    if (op instanceof FontView) {
                        FontView fv = (FontView) op;
                        color = fv.color;
                        align = fv.align;
                    } else {
                        color = jsInt(_this.get("color"));
                        align = jsInt(_this.get("align"));
                    }

                    javax.microedition.lcdui.Font jf = (op instanceof FontView) ? ((FontView) op).font : null;
                    canvas.drawFont(jf, text, x, y, align, color);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Font.ctorOrProt, "getTextSize",
            ri.addNativeFunction(new NativeFunctionListEntry("Font.getTextSize", new NativeFunctionFast() {
            public final int length = 1;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    String text = Rv.argAt(args, start, num, 0).toStr().str;
                    if (text == null) {
                        text = "";
                    }
                    javax.microedition.lcdui.Font jf = null;
                    Object op = _this.opaque;
                    if (op instanceof FontView) {
                        jf = ((FontView) op).font;
                    }
                    int w = 0, h = 0;
                    if (jf != null) {
                        w = jf.stringWidth(text);
                        h = jf.getHeight();
                    }
                    if (op instanceof FontView) {
                        FontView fv = (FontView) op;
                        if (fv.textSizeModule == null) {
                            fv.textSizeModule = ri.newModule();
                            fv.textSizeW = new Rv(0);
                            fv.textSizeH = new Rv(0);
                            ri.addToObject(fv.textSizeModule, "width", fv.textSizeW);
                            ri.addToObject(fv.textSizeModule, "height", fv.textSizeH);
                        }
                        fv.textSizeW.num = w;
                        fv.textSizeW.f = false;
                        fv.textSizeH.num = h;
                        fv.textSizeH.f = false;
                        return fv.textSizeModule;
                    }
                    Rv o = ri.newModule();
                    ri.addToObject(o, "width", Rv.smallInt(w));
                    ri.addToObject(o, "height", Rv.smallInt(h));
                    return o;
                }
        })));

        ri.addToObject(_Font.ctorOrProt, "free", 
            ri.addNativeFunction(new NativeFunctionListEntry("Font.free", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    _this.opaque = null;

                    return Rv._undefined;
                }
        })));

        ri.addToObject(callObj, "Font", _Font);

        Rv _FontAlign = ri.newModule();
        ri.addToObject(_FontAlign, "TOP", new Rv(canvas.ALIGN_TOP));
        ri.addToObject(_FontAlign, "BOTTOM", new Rv(canvas.ALIGN_BOTTOM));
        ri.addToObject(_FontAlign, "VCENTER", new Rv(canvas.ALIGN_VCENTER));
        ri.addToObject(_FontAlign, "LEFT", new Rv(canvas.ALIGN_LEFT));
        ri.addToObject(_FontAlign, "RIGHT", new Rv(canvas.ALIGN_RIGHT));
        ri.addToObject(_FontAlign, "HCENTER", new Rv(canvas.ALIGN_HCENTER));
        ri.addToObject(_FontAlign, "NONE", new Rv(canvas.ALIGN_NONE));
        ri.addToObject(_FontAlign, "CENTER", new Rv(canvas.ALIGN_CENTER));

        ri.addToObject(callObj, "FontAlign", _FontAlign);

        Rv _Color = ri.newModule();
        ri.addToObject(_Color, "new", 
            ri.addNativeFunction(new NativeFunctionListEntry("Color.new", new NativeFunctionFast() {
            public final int length = 4;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int r = jsInt(Rv.argAt(args, start, num, 0));
                    int g = jsInt(Rv.argAt(args, start, num, 1));
                    int b = jsInt(Rv.argAt(args, start, num, 2));
                    int a = num > 3 ? jsInt(Rv.argAt(args, start, num, 3)) : 0;

                    return new Rv(AthenaColor.color(r, g, b, a));
                }
        })));

        ri.addToObject(callObj, "Color", _Color);

        Rv _Pad = ri.newModule();
        ri.addToObject(_Pad, "update", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.update", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    canvas.padUpdate();
                    synchronized (jsRuntimeLock) {
                        dispatchPadListeners(ri, callObj);
                    }
                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Pad, "addListener",
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.addListener", new NativeFunctionFast() {
            public final int length = 3;
            public Rv callFast(boolean isNew, Rv _thiz, Pack args, int start, int num, RocksInterpreter ri) {
                if (num < 3) {
                    return new Rv(-1.0);
                }
                Rv cb = Rv.argAt(args, start, num, 2);
                if (!cb.isCallable()) {
                    return new Rv(-1.0);
                }
                int mask = jsInt(Rv.argAt(args, start, num, 0));
                int kind = jsInt(Rv.argAt(args, start, num, 1));
                if (mask == 0 || kind < 0 || kind > 2) {
                    return new Rv(-1.0);
                }
                synchronized (jsRuntimeLock) {
                    int id = padListenerNextId;
                    if (++padListenerNextId < 0) {
                        padListenerNextId = 1;
                    }
                    addPadListener(new PadListener(id, mask, kind, cb));
                    return new Rv((double) id);
                }
            }
        })));

        ri.addToObject(_Pad, "clearListener",
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.clearListener", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _thiz, Pack args, int start, int num, RocksInterpreter ri) {
                int id = num > 0 ? jsInt(Rv.argAt(args, start, num, 0)) : 0;
                if (id <= 0) {
                    return Rv._undefined;
                }
                synchronized (jsRuntimeLock) {
                    removePadListenerById(id);
                }
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Pad, "pressed", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.pressed", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int buttons = jsInt(Rv.argAt(args, start, num, 0));
                    return canvas.padPressed(buttons) ? Rv._true : Rv._false;
                }
        })));

        ri.addToObject(_Pad, "justPressed", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.justPressed", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int buttons = jsInt(Rv.argAt(args, start, num, 0));
                    return canvas.padJustPressed(buttons) ? Rv._true : Rv._false;
                }
        })));

        ri.addToObject(_Pad, "UP", new Rv(canvas.UP_PRESSED));
        ri.addToObject(_Pad, "DOWN", new Rv(canvas.DOWN_PRESSED));
        ri.addToObject(_Pad, "LEFT", new Rv(canvas.LEFT_PRESSED));
        ri.addToObject(_Pad, "RIGHT", new Rv(canvas.RIGHT_PRESSED));
        ri.addToObject(_Pad, "FIRE", new Rv(canvas.FIRE_PRESSED));
        ri.addToObject(_Pad, "GAME_A", new Rv(canvas.GAME_A_PRESSED));
        ri.addToObject(_Pad, "GAME_B", new Rv(canvas.GAME_B_PRESSED));
        ri.addToObject(_Pad, "GAME_C", new Rv(canvas.GAME_C_PRESSED));
        ri.addToObject(_Pad, "GAME_D", new Rv(canvas.GAME_D_PRESSED));

        ri.addToObject(_Pad, "PRESSED", new Rv(0.0));
        ri.addToObject(_Pad, "JUST_PRESSED", new Rv(1.0));
        ri.addToObject(_Pad, "NON_PRESSED", new Rv(2.0));

        ri.addToObject(callObj, "Pad", _Pad);

        Rv _Keyboard = ri.newModule();
        ri.addToObject(_Keyboard, "get", 
            ri.addNativeFunction(new NativeFunctionListEntry("Keyboard.get", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int k = canvas.getKeypad();
                    return Rv.smallInt(k);
                }
        })));

        ri.addToObject(_Keyboard, "KEY_NUM0", new Rv(canvas.KEY_NUM0));
        ri.addToObject(_Keyboard, "KEY_NUM1", new Rv(canvas.KEY_NUM1));
        ri.addToObject(_Keyboard, "KEY_NUM2", new Rv(canvas.KEY_NUM2));
        ri.addToObject(_Keyboard, "KEY_NUM3", new Rv(canvas.KEY_NUM3));
        ri.addToObject(_Keyboard, "KEY_NUM4", new Rv(canvas.KEY_NUM4));
        ri.addToObject(_Keyboard, "KEY_NUM5", new Rv(canvas.KEY_NUM5));
        ri.addToObject(_Keyboard, "KEY_NUM6", new Rv(canvas.KEY_NUM6));
        ri.addToObject(_Keyboard, "KEY_NUM7", new Rv(canvas.KEY_NUM7));
        ri.addToObject(_Keyboard, "KEY_NUM8", new Rv(canvas.KEY_NUM8));
        ri.addToObject(_Keyboard, "KEY_NUM9", new Rv(canvas.KEY_NUM9));
        ri.addToObject(_Keyboard, "KEY_STAR", new Rv(canvas.KEY_STAR));
        ri.addToObject(_Keyboard, "KEY_POUND", new Rv(canvas.KEY_POUND));

        ri.addToObject(callObj, "Keyboard", _Keyboard);

        final Rv _Request = ri.newModule();
        ri.addNativeFunction(new NativeFunctionListEntry("Request", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _Request);
                ret.opaque = new AthenaRequest();
                ri.addToObject(ret, "keepalive", new Rv(0));
                ri.addToObject(ret, "useragent", new Rv(""));
                ri.addToObject(ret, "userpwd", new Rv(""));
                ri.addToObject(ret, "headers", ri.newEmptyArray());
                ri.addToObject(ret, "responseCode", new Rv(-1));
                ri.addToObject(ret, "error", new Rv(""));
                ri.addToObject(ret, "contentLength", new Rv(0));
                return ret;
            }
        }));
        _Request.nativeCtor("Request", callObj);

        ri.addToObject(_Request.ctorOrProt, "get",
            ri.addNativeFunction(new NativeFunctionListEntry("Request.get", new NativeFunctionFast() {
                public final int length = 1;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    AthenaRequest ar = (AthenaRequest) _this.opaque;
                    return ar.getPromise(ri, _this, Rv.argAt(args, start, num, 0).toStr().str);
                }
            })));

        ri.addToObject(_Request.ctorOrProt, "post",
            ri.addNativeFunction(new NativeFunctionListEntry("Request.post", new NativeFunction() {
                public final int length = 2;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaRequest ar = (AthenaRequest) _this.opaque;
                    return ar.postPromise(ri, _this, args.get("0").toStr().str, bytesFromBufferArg(args.get("1")));
                }
            })));

        ri.addToObject(_Request.ctorOrProt, "download",
            ri.addNativeFunction(new NativeFunctionListEntry("Request.download", new NativeFunction() {
                public final int length = 2;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaRequest ar = (AthenaRequest) _this.opaque;
                    return ar.downloadPromise(ri, _this, args.get("0").toStr().str, args.get("1").toStr().str);
                }
            })));

        ri.addToObject(callObj, "Request", _Request);

        final Rv _SocketMod = ri.newModule();
        ri.addToObject(_SocketMod, "AF_INET", new Rv(AthenaSocket.AF_INET));
        ri.addToObject(_SocketMod, "SOCK_STREAM", new Rv(AthenaSocket.SOCK_STREAM));
        ri.addToObject(_SocketMod, "SOCK_DGRAM", new Rv(AthenaSocket.SOCK_DGRAM));
        ri.addToObject(_SocketMod, "SOCK_RAW", new Rv(AthenaSocket.SOCK_RAW));

        ri.addNativeFunction(new NativeFunctionListEntry("Socket", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _SocketMod);
                int dom = jsInt(args.get("0"));
                int typ = jsInt(args.get("1"));
                ret.opaque = new AthenaSocket(dom, typ);
                return ret;
            }
        }));
        _SocketMod.nativeCtor("Socket", callObj);

        ri.addToObject(_SocketMod.ctorOrProt, "connect",
            ri.addNativeFunction(new NativeFunctionListEntry("Socket.connect", new NativeFunction() {
                public final int length = 2;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaSocket s = (AthenaSocket) _this.opaque;
                    try {
                        s.connect(args.get("0").toStr().str, jsInt(args.get("1")));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Rv._undefined;
                }
            })));

        ri.addToObject(_SocketMod.ctorOrProt, "bind",
            ri.addNativeFunction(new NativeFunctionListEntry("Socket.bind", new NativeFunction() {
                public final int length = 2;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaSocket s = (AthenaSocket) _this.opaque;
                    try {
                        s.bind(args.get("0").toStr().str, jsInt(args.get("1")));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Rv._undefined;
                }
            })));

        ri.addToObject(_SocketMod.ctorOrProt, "listen",
            ri.addNativeFunction(new NativeFunctionListEntry("Socket.listen", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaSocket s = (AthenaSocket) _this.opaque;
                    try {
                        s.listen();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Rv._undefined;
                }
            })));

        ri.addToObject(_SocketMod.ctorOrProt, "accept",
            ri.addNativeFunction(new NativeFunctionListEntry("Socket.accept", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaSocket s = (AthenaSocket) _this.opaque;
                    try {
                        AthenaSocket ch = s.accept();
                        Rv ret = new Rv(Rv.OBJECT, _SocketMod);
                        ret.opaque = ch;
                        return ret;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return Rv._undefined;
                    }
                }
            })));

        ri.addToObject(_SocketMod.ctorOrProt, "send",
            ri.addNativeFunction(new NativeFunctionListEntry("Socket.send", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaSocket s = (AthenaSocket) _this.opaque;
                    byte[] data = bytesFromBufferArg(args.get("0"));
                    try {
                        return new Rv(s.send(data, 0, data.length));
                    } catch (Exception e) {
                        e.printStackTrace();
                        return new Rv(-1);
                    }
                }
            })));

        ri.addToObject(_SocketMod.ctorOrProt, "recv",
            ri.addNativeFunction(new NativeFunctionListEntry("Socket.recv", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaSocket s = (AthenaSocket) _this.opaque;
                    int size = jsInt(args.get("0"));
                    if (size < 1) {
                        size = 1024;
                    }
                    byte[] buf = new byte[size];
                    try {
                        int n = s.recv(buf, 0, size);
                        if (n <= 0) {
                            return newUint8Array(ri, new byte[0]);
                        }
                        if (n == size) {
                            return newUint8Array(ri, buf);
                        }
                        byte[] t = new byte[n];
                        System.arraycopy(buf, 0, t, 0, n);
                        return newUint8Array(ri, t);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return newUint8Array(ri, new byte[0]);
                    }
                }
            })));

        ri.addToObject(_SocketMod.ctorOrProt, "close",
            ri.addNativeFunction(new NativeFunctionListEntry("Socket.close", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaSocket s = (AthenaSocket) _this.opaque;
                    if (s != null) {
                        s.close();
                    }
                    _this.opaque = null;
                    return Rv._undefined;
                }
            })));

        ri.addToObject(callObj, "Socket", _SocketMod);

        final Rv _BTSocketMod = ri.newModule();
        ri.addNativeFunction(new NativeFunctionListEntry("BTSocket", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _BTSocketMod);
                ret.opaque = null;
                return ret;
            }
        }));
        _BTSocketMod.nativeCtor("BTSocket", callObj);

        ri.addToObject(_BTSocketMod.ctorOrProt, "connect",
            ri.addNativeFunction(new NativeFunctionListEntry("BTSocket.connect", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    return AthenaBTSocket.connectPromise(ri, _this, args.get("0").toStr().str);
                }
            })));

        ri.addToObject(_BTSocketMod.ctorOrProt, "send",
            ri.addNativeFunction(new NativeFunctionListEntry("BTSocket.send", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    if (!(_this.opaque instanceof AthenaBTSocket)) {
                        return new Rv(-1);
                    }
                    AthenaBTSocket s = (AthenaBTSocket) _this.opaque;
                    byte[] data = bytesFromBufferArg(args.get("0"));
                    try {
                        return new Rv(s.send(data, 0, data.length));
                    } catch (Exception e) {
                        e.printStackTrace();
                        return new Rv(-1);
                    }
                }
            })));

        ri.addToObject(_BTSocketMod.ctorOrProt, "recv",
            ri.addNativeFunction(new NativeFunctionListEntry("BTSocket.recv", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    if (!(_this.opaque instanceof AthenaBTSocket)) {
                        return newUint8Array(ri, new byte[0]);
                    }
                    AthenaBTSocket s = (AthenaBTSocket) _this.opaque;
                    int size = jsInt(args.get("0"));
                    if (size < 1) {
                        size = 1024;
                    }
                    byte[] buf = new byte[size];
                    try {
                        int n = s.recv(buf, 0, size);
                        if (n <= 0) {
                            return newUint8Array(ri, new byte[0]);
                        }
                        if (n == size) {
                            return newUint8Array(ri, buf);
                        }
                        byte[] t = new byte[n];
                        System.arraycopy(buf, 0, t, 0, n);
                        return newUint8Array(ri, t);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return newUint8Array(ri, new byte[0]);
                    }
                }
            })));

        ri.addToObject(_BTSocketMod.ctorOrProt, "close",
            ri.addNativeFunction(new NativeFunctionListEntry("BTSocket.close", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    if (_this.opaque instanceof AthenaBTSocket) {
                        ((AthenaBTSocket) _this.opaque).close();
                    }
                    _this.opaque = null;
                    return Rv._undefined;
                }
            })));

        ri.addToObject(callObj, "BTSocket", _BTSocketMod);

        final Rv _WebSocket = ri.newModule();
        ri.addNativeFunction(new NativeFunctionListEntry("WebSocket", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _WebSocket);
                String url = args.get("0").toStr().str;
                try {
                    ret.opaque = new AthenaWebSocket(url);
                    ri.addToObject(ret, "error", new Rv(""));
                } catch (Exception e) {
                    ret.opaque = null;
                    ri.addToObject(ret, "error", new Rv(e.getMessage() != null ? e.getMessage() : "websocket error"));
                }
                return ret;
            }
        }));
        _WebSocket.nativeCtor("WebSocket", callObj);

        ri.addToObject(_WebSocket.ctorOrProt, "send",
            ri.addNativeFunction(new NativeFunctionListEntry("WebSocket.send", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    if (!(_this.opaque instanceof AthenaWebSocket)) {
                        return Rv._undefined;
                    }
                    AthenaWebSocket ws = (AthenaWebSocket) _this.opaque;
                    byte[] data = bytesFromBufferArg(args.get("0"));
                    try {
                        ws.sendBinary(data, 0, data.length);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return Rv._undefined;
                }
            })));

        ri.addToObject(_WebSocket.ctorOrProt, "recv",
            ri.addNativeFunction(new NativeFunctionListEntry("WebSocket.recv", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    if (!(_this.opaque instanceof AthenaWebSocket)) {
                        return newUint8Array(ri, new byte[0]);
                    }
                    AthenaWebSocket ws = (AthenaWebSocket) _this.opaque;
                    try {
                        byte[] p = ws.recvFrame();
                        return newUint8Array(ri, p != null ? p : new byte[0]);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return newUint8Array(ri, new byte[0]);
                    }
                }
            })));

        ri.addToObject(_WebSocket.ctorOrProt, "close",
            ri.addNativeFunction(new NativeFunctionListEntry("WebSocket.close", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    if (_this.opaque instanceof AthenaWebSocket) {
                        ((AthenaWebSocket) _this.opaque).close();
                    }
                    _this.opaque = null;
                    return Rv._undefined;
                }
            })));

        ri.addToObject(callObj, "WebSocket", _WebSocket);

        final Rv _Timer = ri.newModule();

        ri.addNativeFunction(new NativeFunctionListEntry("Timer", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _Timer);

                    AthenaTimer timer = new AthenaTimer(RocksInterpreter.bootTime);

                    ret.opaque = (Object)timer;

                    return ret;
                }
        }));

        _Timer.nativeCtor("Timer", callObj);

        ri.addToObject(_Timer.ctorOrProt, "get", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.get", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;
                    int t = timer.get();
                    return (t >= 0 && t < 256) ? Rv.smallInt(t) : new Rv(t);
                }
        })));

        ri.addToObject(_Timer.ctorOrProt, "set", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.set", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;

                    int value = jsInt(args.get("0"));

                    timer.set(value);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Timer.ctorOrProt, "pause", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.pause", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;

                    timer.pause();

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Timer.ctorOrProt, "resume", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.resume", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;

                    timer.resume();

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Timer.ctorOrProt, "reset", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.reset", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;

                    timer.reset();

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Timer.ctorOrProt, "playing", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.playing", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;

                    return timer.playing() ? Rv._true : Rv._false;
                }
        })));

        ri.addToObject(_Timer.ctorOrProt, "free", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.free", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    _this.opaque = null;

                    return Rv._undefined;
                }
        })));

        ri.addToObject(callObj, "Timer", _Timer);

        // --- Sound (Stream + Sfx) — MMAPI ---
        final Rv _StreamF = ri.addNativeFunction(new NativeFunctionListEntry("Stream", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                return Rv._undefined;
            }
        }));
        _StreamF.nativeCtor("Stream", callObj);
        final Rv _SfxF = ri.addNativeFunction(new NativeFunctionListEntry("Sfx", new NativeFunction() {
            public Rv func(boolean isNew, Rv _this, Rv args) {
                return Rv._undefined;
            }
        }));
        _SfxF.nativeCtor("Sfx", callObj);

        Rv _Sound = ri.newModule();
        ri.addToObject(_Sound, "setVolume",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.setVolume", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                    int v = jsInt(Rv.argAt(args, start, num, 0));
                    AthenaSound.setMasterVolume(v);
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_Sound, "findChannel",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.findChannel", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                    int c = AthenaSound.findChannel();
                    if (c < 0) {
                        return Rv._undefined;
                    }
                    return Rv.smallInt(c);
                }
        })));
        ri.addToObject(_Sound, "Stream",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _th, Rv args) {
                    Rv ret = new Rv(Rv.OBJECT, _StreamF);
                    String path = args.get("0").toStr().str;
                    AthenaSound.StreamHandle h = AthenaSound.createStream(path);
                    int len0 = 0;
                    if (h.p != null) {
                        len0 = AthenaSound.streamGetLengthMs(h.p);
                    }
                    StreamView sv = new StreamView(h, len0);
                    ret.opaque = sv;
                    ri.addToObject(ret, "position", Rv.smallInt(0));
                    ri.addToObject(ret, "length", new Rv(len0));
                    ri.addToObject(ret, "loop", Rv.smallInt(0));
                    return ret;
                }
        })));
        ri.addToObject(_Sound, "Sfx",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Sfx", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _th, Rv args) {
                    Rv ret = new Rv(Rv.OBJECT, _SfxF);
                    String path = args.get("0").toStr().str;
                    AthenaSound.SfxData s = AthenaSound.loadSfxData(path);
                    SfxView sv = new SfxView(s);
                    ret.opaque = sv;
                    ri.addToObject(ret, "volume", new Rv(100));
                    ri.addToObject(ret, "pan", Rv.smallInt(0));
                    ri.addToObject(ret, "pitch", Rv.smallInt(0));
                    return ret;
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "play",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.play", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof StreamView)) {
                        return Rv._undefined;
                    }
                    StreamView v = (StreamView) o;
                    AthenaSound.StreamHandle h = v.h;
                    if (h == null || h.p == null) {
                        return Rv._undefined;
                    }
                    AthenaSound.streamSetPositionMs(h.p, v.position);
                    AthenaSound.streamSetLoop(h.p, v.loop != 0);
                    AthenaSound.applyMasterVolumeToPlayer(h.p);
                    AthenaSound.streamStart(h.p);
                    ri.addToObject(_this, "position", new Rv(AthenaSound.streamGetPositionMs(h.p)));
                    ri.addToObject(_this, "length", new Rv(AthenaSound.streamGetLengthMs(h.p)));
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "pause",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.pause", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof StreamView)) {
                        return Rv._undefined;
                    }
                    StreamView v = (StreamView) o;
                    AthenaSound.StreamHandle h = v.h;
                    if (h == null || h.p == null) {
                        return Rv._undefined;
                    }
                    AthenaSound.streamStop(h.p);
                    ri.addToObject(_this, "position", new Rv(AthenaSound.streamGetPositionMs(h.p)));
                    ri.addToObject(_this, "length", new Rv(AthenaSound.streamGetLengthMs(h.p)));
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "playing",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.playing", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof StreamView)) {
                        return Rv._false;
                    }
                    StreamView v = (StreamView) o;
                    AthenaSound.StreamHandle h = v.h;
                    if (h == null || h.p == null) {
                        return Rv._false;
                    }
                    ri.addToObject(_this, "position", new Rv(AthenaSound.streamGetPositionMs(h.p)));
                    ri.addToObject(_this, "length", new Rv(AthenaSound.streamGetLengthMs(h.p)));
                    return AthenaSound.streamIsPlaying(h.p) ? Rv._true : Rv._false;
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "rewind",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.rewind", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof StreamView)) {
                        return Rv._undefined;
                    }
                    StreamView v = (StreamView) o;
                    AthenaSound.StreamHandle h = v.h;
                    if (h == null || h.p == null) {
                        return Rv._undefined;
                    }
                    AthenaSound.streamSetPositionMs(h.p, 0);
                    ri.addToObject(_this, "position", Rv.smallInt(0));
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "free",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.free", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (o instanceof StreamView) {
                        AthenaSound.StreamHandle h = ((StreamView) o).h;
                        if (h != null) {
                            h.close();
                        }
                    }
                    _this.opaque = null;
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_SfxF.ctorOrProt, "play",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Sfx.play", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof SfxView)) {
                        return Rv._undefined;
                    }
                    SfxView sv = (SfxView) o;
                    AthenaSound.SfxData s = sv.data;
                    if (s == null) {
                        return Rv._undefined;
                    }
                    Rv a0 = args.get("0");
                    boolean hasCh = a0 != null && a0 != Rv._undefined;
                    int v = sv.volume;
                    int pan = sv.pan;
                    int pitc = sv.pitch;
                    if (hasCh) {
                        int chw = jsInt(a0);
                        AthenaSound.playSfx(s, chw, v, pan, pitc);
                        return Rv._undefined;
                    }
                    int r = AthenaSound.playSfx(s, -1, v, pan, pitc);
                    if (r < 0) {
                        return Rv._undefined;
                    }
                    return Rv.smallInt(r);
                }
        })));
        ri.addToObject(_SfxF.ctorOrProt, "free",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Sfx.free", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (o instanceof SfxView) {
                        AthenaSound.SfxData d = ((SfxView) o).data;
                        if (d != null) {
                            AthenaSound.freeSfxData(d);
                        }
                    }
                    _this.opaque = null;
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_SfxF.ctorOrProt, "playing",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Sfx.playing", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int ch = jsInt(args.get("0"));
                    return AthenaSound.isSfxChannelPlaying(ch) ? Rv._true : Rv._false;
                }
        })));
        ri.addToObject(callObj, "Sound", _Sound);

        final Rv globalObj = callObj;
        final RocksInterpreter interp = ri;

        ri.addToObject(callObj, "require",
            ri.addNativeFunction(new NativeFunctionListEntry("require", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv a0 = args.get("0");
                if (a0 == null || a0 == Rv._undefined) {
                    return Rv._undefined;
                }
                String canon = canonicalResourcePath(a0.toStr().str);
                synchronized (jsRuntimeLock) {
                    Rv hit = (Rv) moduleCache.get(canon);
                    if (hit != null) {
                        return hit;
                    }
                    String userSrc = readResourceUtf8(canon);
                    if (userSrc == null) {
                        return Rv._undefined;
                    }
                    Rv exports = interp.newModule();
                    Rv module = interp.newModule();
                    interp.addToObject(module, "exports", exports);
                    interp.addToObject(globalObj, "____rqE", exports);
                    interp.addToObject(globalObj, "____rqM", module);
                    String wrapper = "(function(exports,module,require){\n" + userSrc + "\n})(____rqE,____rqM,require);\n";
                    try {
                        interp.runInGlobalScope(wrapper, globalObj);
                    } finally {
                        interp.addToObject(globalObj, "____rqE", Rv._undefined);
                        interp.addToObject(globalObj, "____rqM", Rv._undefined);
                    }
                    Rv ex = module.get("exports");
                    if (ex == null || ex == Rv._undefined) {
                        ex = exports;
                    }
                    // Reclaim lex/preproc temporaries on small heaps after loading a module.
                    System.gc();
                    moduleCache.put(canon, ex);
                    return ex;
                }
            }
        })));

        ri.addToObject(callObj, "loadScript",
            ri.addNativeFunction(new NativeFunctionListEntry("loadScript", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                Rv a0 = args.get("0");
                if (a0 == null || a0 == Rv._undefined) {
                    return Rv._undefined;
                }
                String canon = canonicalResourcePath(a0.toStr().str);
                String userSrc = readResourceUtf8(canon);
                if (userSrc == null) {
                    return Rv._undefined;
                }
                synchronized (jsRuntimeLock) {
                    interp.runInGlobalScope(userSrc, globalObj);
                }
                return Rv._undefined;
            }
        })));

        jsThis = callObj;

        final Rv _rv = rv;
        final Rv _callObj = callObj;
        final Object bootJsLock = jsRuntimeLock;

        jsRunning = true;
        jsThread = new Thread(new Runnable() {
            public void run() {
                try {
                    synchronized (bootJsLock) {
                        ri.call(false, _rv, _callObj, null, null, 0, 0);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    long deadline = System.currentTimeMillis() + 30000L;
                    while (System.currentTimeMillis() < deadline) {
                        synchronized (bootJsLock) {
                            PromiseRuntime.drain(ri);
                        }
                        if (!PromiseRuntime.hasPending() && AthenaRequest.getHttpInFlight() == 0
                                && AthenaBluetooth.getBluetoothInFlight() == 0) {
                            break;
                        }
                        try {
                            Thread.sleep(5L);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    jsRunning = false;
                }
            }
        });
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            if (jsExitHandler != null) {
                try {
                    synchronized (jsRuntimeLock) {
                        ri.call(false, jsExitHandler, jsExitHandler.co, jsThis, null, 0, 0);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

            destroyApp(false);
            notifyDestroyed();
        }
    }
    
    private static String canonicalResourcePath(String path) {
        if (path == null) {
            return "/";
        }
        int n = path.length();
        StringBuffer sb = new StringBuffer(n);
        for (int i = 0; i < n; i++) {
            char c = path.charAt(i);
            if (c == '\\') {
                c = '/';
            }
            sb.append(c);
        }
        String p = sb.toString().trim();
        if (p.length() == 0) {
            return "/";
        }
        if (p.charAt(0) != '/') {
            p = "/" + p;
        }
        return p;
    }

    private static String readResourceUtf8(String absPath) {
        InputStream is = null;
        try {
            is = "".getClass().getResourceAsStream(absPath);
            if (is == null) {
                return null;
            }
            return readUTF(readData(is));
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    static final byte[] readData(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] bb = new byte[2000];
        int len;
        while ((len = is.read(bb)) > 0) {
            bos.write(bb, 0, len);
        }
        return bos.toByteArray();
    }

    public static final String readUTF(byte[] data) {
        byte[] bb = new byte[data.length + 2];
        System.arraycopy(data, 0, bb, 2, data.length);
        bb[0] = (byte) (data.length >> 8);
        bb[1] = (byte) data.length;
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bb));
        String ret = null;
        try {
            ret = dis.readUTF();
            if (ret.charAt(0) == '\uFEFF') { // remove BOM
                ret = ret.substring(1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ret;
    }

    /**
     * Tag for object instances managed by {@link JsObjectPool}. Lives in
     * {@link Rv#opaque}; distinct from native wrappers that use opaque for Java handles.
     */
    private static final class PoolMember {
        final JsObjectPool pool;
        final int index;
        boolean checkedOut;

        PoolMember(JsObjectPool pool, int index) {
            this.pool = pool;
            this.index = index;
        }
    }

    /**
     * Pre-allocated reusable JS objects: same {@link Rv} shells, re-run constructor as
     * initializer via {@link RocksInterpreter#invokeJS}.
     */
    private static final class JsObjectPool {
        private final RocksInterpreter ri;
        private final Rv ctor;
        private final int capacity;
        private final Rv[] objects;
        private final PoolMember[] members;
        private final int[] freeList;
        private int freeCnt;
        private int inUseCnt;

        JsObjectPool(RocksInterpreter ri, Rv ctor, int capacity) {
            this.ri = ri;
            this.ctor = ctor;
            this.capacity = capacity;
            ctor.get("prototype");
            this.objects = new Rv[capacity];
            this.members = new PoolMember[capacity];
            this.freeList = new int[capacity];
            this.freeCnt = capacity;
            this.inUseCnt = 0;
            for (int i = 0; i < capacity; i++) {
                Rv o = new Rv(Rv.OBJECT, ctor);
                PoolMember m = new PoolMember(this, i);
                o.opaque = m;
                objects[i] = o;
                members[i] = m;
                freeList[i] = i;
            }
        }

        int capacity() {
            return capacity;
        }

        int available() {
            return freeCnt;
        }

        int inUse() {
            return inUseCnt;
        }

        Rv acquire(RocksInterpreter riUse, Pack args, int start, int num) {
            if (freeCnt <= 0) {
                return Rv._null;
            }
            int idx = freeList[--freeCnt];
            PoolMember m = members[idx];
            m.checkedOut = true;
            inUseCnt++;
            Rv obj = objects[idx];
            RocksInterpreter use = riUse != null ? riUse : ri;
            use.invokeJS(ctor, obj, args, start, num);
            return obj;
        }

        void release(Rv obj) {
            if (obj == null || obj == Rv._undefined || obj == Rv._null) {
                return;
            }
            Object oo = obj.opaque;
            if (!(oo instanceof PoolMember)) {
                return;
            }
            PoolMember m = (PoolMember) oo;
            if (m.pool != this) {
                return;
            }
            if (!m.checkedOut) {
                return;
            }
            m.checkedOut = false;
            freeList[freeCnt++] = m.index;
            inUseCnt--;
        }
    }

    private static final String RS_PREPROC = "A2MjsPP";

    /**
     * Loads cached startup script text if the source hash and ES6 mode still match
     * (Faster cold start after the first run.)
     *
     * @param wantEs6 true if body was produced with {@link net.cnjm.j2me.tinybro.Es6Preproc}; false for raw/legacy
     */
    private String tryLoadPreprocessedSourceFromRms(int hash, boolean wantEs6) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RS_PREPROC, false);
        } catch (Exception e) {
            return null;
        }
        try {
            if (rs.getNumRecords() < 2) {
                return null;
            }
            byte[] hrec = rs.getRecord(1);
            if (hrec == null || hrec.length < 4) {
                return null;
            }
            int h = (hrec[0] << 24) | ((hrec[1] & 0xff) << 16) | ((hrec[2] & 0xff) << 8) | (hrec[3] & 0xff);
            if (h != hash) {
                return null;
            }
            boolean storedEs6;
            if (hrec.length < 5) {
                storedEs6 = true;
            } else {
                storedEs6 = hrec[4] != 0;
            }
            if (storedEs6 != wantEs6) {
                return null;
            }
            byte[] body = rs.getRecord(2);
            if (body == null) {
                return null;
            }
            return new String(body, "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) { }
            }
        }
    }

    private void savePreprocessedSourceToRms(int hash, String preprocessed, boolean es6Mode) {
        if (preprocessed == null) {
            return;
        }
        byte[] hrec = new byte[5];
        hrec[0] = (byte) (hash >> 24);
        hrec[1] = (byte) (hash >> 16);
        hrec[2] = (byte) (hash >> 8);
        hrec[3] = (byte) hash;
        hrec[4] = (byte) (es6Mode ? 1 : 0);
        byte[] b;
        try {
            b = preprocessed.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            b = preprocessed.getBytes();
        }
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RS_PREPROC, true);
            if (rs.getNumRecords() < 1) {
                rs.addRecord(hrec, 0, 5);
            } else {
                rs.setRecord(1, hrec, 0, 5);
            }
            if (rs.getNumRecords() < 2) {
                rs.addRecord(b, 0, b.length);
            } else {
                rs.setRecord(2, b, 0, b.length);
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) { }
            }
        }
    }
    
}
