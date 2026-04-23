import java.io.*;
import java.util.Hashtable;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;

import net.cnjm.j2me.tinybro.*;
import net.cnjm.j2me.util.*;

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

    /** Cache for {@code require()}: canonical resource path → module {@code exports} object. */
    private final Hashtable moduleCache = new Hashtable();

    /** Truncate a JS number toward zero for integer-only MIDP APIs. */
    private static int jsInt(Rv v) {
        if (v == null || v == Rv._undefined) return 0;
        Rv n = v.toNum();
        if (n == Rv._NaN) return 0;
        return (int) Rv.numValue(n);
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

    public Athena2ME() {
        canvas = new AthenaCanvas(false);
        canvas.addCommand(exitCmd);
        canvas.setCommandListener(this);
    }

    protected void destroyApp(boolean unconditional) {
        frameRunning = false;
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
        Display.getDisplay(this).setCurrent(canvas);
        moduleCache.clear();

        InputStream is = "".getClass().getResourceAsStream("/main.js");
        String src = "";

        try {
            src = readUTF(readData(is));
            is.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (ri == null) {
            ri = new RocksInterpreter(src, null, 0, src.length());
            ri.evalString = true;
            ri.DEBUG = false;
        } else {
            ri.reset(src, null, 0, src.length());
        }

        Node func = ri.astNode(null, '{', 0, 0);
        ri.astNode(func, '{', 0, ri.endpos);
        Rv rv = new Rv(false, func, 0);
        Rv callObj = rv.co = ri.initGlobalObject();

        Rv _os = ri.newModule();
        ri.addToObject(_os, "platform", new Rv("j2me"));

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

        ri.addToObject(_os, "sleep",
            ri.addNativeFunction(new NativeFunctionListEntry("os.sleep", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                int ms = jsInt(Rv.argAt(args, start, num, 0));
                if (ms < 0) ms = 0;
                PromiseRuntime.drain(ri);
                try { Thread.sleep(ms); } catch (InterruptedException e) {}
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "flushPromises",
            ri.addNativeFunction(new NativeFunctionListEntry("os.flushPromises", new NativeFunctionFast() {
            public final int length = 0;
            public Rv callFast(boolean isNew, Rv thiz, Pack args, int start, int num, RocksInterpreter ri) {
                PromiseRuntime.drain(ri);
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
                if (fps < 1) fps = 1;
                if (fps > 120) fps = 120;
                final int frameMs = 1000 / fps;
                final RocksInterpreter interp = ri;
                final Rv thisRef = _this;
                self.frameRunning = true;
                final AthenaCanvas cv = self.canvas;
                self.frameThread = new Thread(new Runnable() {
                    public void run() {
                        while (self.frameRunning) {
                            long deadline = System.currentTimeMillis() + frameMs;
                            try {
                                cv.padUpdate();
                                PromiseRuntime.drain(interp);
                                interp.call(false, fn, fn.co, thisRef, null, 0, 0);
                                cv.screenUpdate();
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

        ri.addToObject(callObj, "os", _os);

        Rv _Screen = ri.newModule();
        ri.addToObject(_Screen, "width", new Rv(canvas.getWidth()));
        ri.addToObject(_Screen, "height", new Rv(canvas.getHeight()));

        ri.addToObject(_Screen, "clear", 
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.clear", new NativeFunctionFast() {
            public final int length = 1;
            public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                int color = num > 0 ? jsInt(Rv.argAt(args, start, num, 0)) : canvas.CLEAR_COLOR;

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

        ri.addToObject(callObj, "Screen", _Screen);

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

        ri.addToObject(callObj, "Draw", _Draw);

        final Rv _Image = ri.newModule();

        ri.addNativeFunction(new NativeFunctionListEntry("Image", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _Image);

                    String name = args.get("0").toStr().str;

                    Image img = canvas.loadImage(name);

                    ret.opaque = (Object)img;

                    ri.addToObject(ret, "startx", new Rv(0));
                    ri.addToObject(ret, "starty", new Rv(0));
                    ri.addToObject(ret, "endx", new Rv(img.getWidth()));
                    ri.addToObject(ret, "endy", new Rv(img.getHeight()));
                    ri.addToObject(ret, "width", new Rv(img.getWidth()));
                    ri.addToObject(ret, "height", new Rv(img.getHeight()));

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

                    int startx = jsInt(_this.get("startx"));
                    int starty = jsInt(_this.get("starty"));
                    int endx = jsInt(_this.get("endx"));
                    int endy = jsInt(_this.get("endy"));

                    canvas._drawImageRegion((Image)_this.opaque, x, y, startx, starty, endx, endy);

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

                    ret.opaque = (Object)font;

                    ri.addToObject(ret, "align", new Rv(canvas.ALIGN_NONE));
                    ri.addToObject(ret, "color", new Rv(0xFFFFFF));

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

        ri.addToObject(_Font.ctorOrProt, "print", 
            ri.addNativeFunction(new NativeFunctionListEntry("Font.print", new NativeFunctionFast() {
            public final int length = 3;
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    String text = Rv.argAt(args, start, num, 0).toStr().str;
                    int x = jsInt(Rv.argAt(args, start, num, 1));
                    int y = jsInt(Rv.argAt(args, start, num, 2));

                    int color = jsInt(_this.get("color"));
                    int align = jsInt(_this.get("align"));

                    canvas.drawFont(text, x, y, align, color);

                    return Rv._undefined;
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
                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Pad, "pressed", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.pressed", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int buttons = jsInt(Rv.argAt(args, start, num, 0));
                    return new Rv(canvas.padPressed(buttons) ? 1 : 0);
                }
        })));

        ri.addToObject(_Pad, "justPressed", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.justPressed", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    int buttons = jsInt(Rv.argAt(args, start, num, 0));
                    return new Rv(canvas.padJustPressed(buttons) ? 1 : 0);
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
        
        ri.addToObject(callObj, "Pad", _Pad);

        Rv _Keyboard = ri.newModule();
        ri.addToObject(_Keyboard, "get", 
            ri.addNativeFunction(new NativeFunctionListEntry("Keyboard.get", new NativeFunctionFast() {
                public Rv callFast(boolean isNew, Rv _this, Pack args, int start, int num, RocksInterpreter ri) {
                    return new Rv(canvas.getKeypad());
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
                    return new Rv(timer.get());
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

                    return new Rv(timer.playing()? 1 : 0);
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
                    return new Rv(c);
                }
        })));
        ri.addToObject(_Sound, "Stream",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream", new NativeFunction() {
                public final int length = 1;
                public Rv func(boolean isNew, Rv _th, Rv args) {
                    Rv ret = new Rv(Rv.OBJECT, _StreamF);
                    String path = args.get("0").toStr().str;
                    AthenaSound.StreamHandle h = AthenaSound.createStream(path);
                    ret.opaque = h;
                    int len0 = 0;
                    if (h.p != null) {
                        len0 = AthenaSound.streamGetLengthMs(h.p);
                    }
                    ri.addToObject(ret, "position", new Rv(0));
                    ri.addToObject(ret, "length", new Rv(len0));
                    ri.addToObject(ret, "loop", new Rv(0));
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
                    ret.opaque = s;
                    ri.addToObject(ret, "volume", new Rv(100));
                    ri.addToObject(ret, "pan", new Rv(0));
                    ri.addToObject(ret, "pitch", new Rv(0));
                    return ret;
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "play",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.play", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof AthenaSound.StreamHandle)) {
                        return Rv._undefined;
                    }
                    AthenaSound.StreamHandle h = (AthenaSound.StreamHandle) o;
                    if (h.p == null) {
                        return Rv._undefined;
                    }
                    int posMs = jsInt(_this.get("position"));
                    AthenaSound.streamSetPositionMs(h.p, posMs);
                    int loopB = jsInt(_this.get("loop"));
                    AthenaSound.streamSetLoop(h.p, loopB != 0);
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
                    if (!(o instanceof AthenaSound.StreamHandle)) {
                        return Rv._undefined;
                    }
                    AthenaSound.StreamHandle h = (AthenaSound.StreamHandle) o;
                    if (h.p == null) {
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
                    if (!(o instanceof AthenaSound.StreamHandle)) {
                        return new Rv(0);
                    }
                    AthenaSound.StreamHandle h = (AthenaSound.StreamHandle) o;
                    if (h.p == null) {
                        return new Rv(0);
                    }
                    ri.addToObject(_this, "position", new Rv(AthenaSound.streamGetPositionMs(h.p)));
                    ri.addToObject(_this, "length", new Rv(AthenaSound.streamGetLengthMs(h.p)));
                    return new Rv(AthenaSound.streamIsPlaying(h.p) ? 1 : 0);
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "rewind",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.rewind", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof AthenaSound.StreamHandle)) {
                        return Rv._undefined;
                    }
                    AthenaSound.StreamHandle h = (AthenaSound.StreamHandle) o;
                    if (h.p == null) {
                        return Rv._undefined;
                    }
                    AthenaSound.streamSetPositionMs(h.p, 0);
                    ri.addToObject(_this, "position", new Rv(0));
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_StreamF.ctorOrProt, "free",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Stream.free", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (o instanceof AthenaSound.StreamHandle) {
                        ((AthenaSound.StreamHandle) o).close();
                    }
                    _this.opaque = null;
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_SfxF.ctorOrProt, "play",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Sfx.play", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (!(o instanceof AthenaSound.SfxData)) {
                        return Rv._undefined;
                    }
                    AthenaSound.SfxData s = (AthenaSound.SfxData) o;
                    Rv a0 = args.get("0");
                    boolean hasCh = a0 != null && a0 != Rv._undefined;
                    int v = jsInt(_this.get("volume"));
                    int pan = jsInt(_this.get("pan"));
                    int pitc = jsInt(_this.get("pitch"));
                    if (hasCh) {
                        int chw = jsInt(a0);
                        AthenaSound.playSfx(s, chw, v, pan, pitc);
                        return Rv._undefined;
                    }
                    int r = AthenaSound.playSfx(s, -1, v, pan, pitc);
                    if (r < 0) {
                        return Rv._undefined;
                    }
                    return new Rv(r);
                }
        })));
        ri.addToObject(_SfxF.ctorOrProt, "free",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Sfx.free", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Object o = _this.opaque;
                    if (o instanceof AthenaSound.SfxData) {
                        AthenaSound.freeSfxData((AthenaSound.SfxData) o);
                    }
                    _this.opaque = null;
                    return Rv._undefined;
                }
        })));
        ri.addToObject(_SfxF.ctorOrProt, "playing",
            ri.addNativeFunction(new NativeFunctionListEntry("Sound.Sfx.playing", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int ch = jsInt(args.get("0"));
                    return new Rv(AthenaSound.isSfxChannelPlaying(ch) ? 1 : 0);
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
                moduleCache.put(canon, ex);
                return ex;
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
                interp.runInGlobalScope(userSrc, globalObj);
                return Rv._undefined;
            }
        })));

        jsThis = callObj;

        final Rv _rv = rv;
        final Rv _callObj = callObj;

        jsRunning = true;
        jsThread = new Thread(new Runnable() {
            public void run() {
                try {
                    ri.call(false, _rv, _callObj, null, null, 0, 0);
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    long deadline = System.currentTimeMillis() + 30000L;
                    while (System.currentTimeMillis() < deadline) {
                        PromiseRuntime.drain(ri);
                        if (!PromiseRuntime.hasPending() && AthenaRequest.getHttpInFlight() == 0) {
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
        jsThread.start();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            if (jsExitHandler != null) {
                try {
                    ri.call(false, jsExitHandler, jsExitHandler.co, jsThis, null, 0, 0);
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
    
}
