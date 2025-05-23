import java.io.*;

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
    

    public Athena2ME() {
        canvas = new AthenaCanvas(false);
        canvas.addCommand(exitCmd);
        canvas.setCommandListener(this);
    }

    protected void destroyApp(boolean unconditional) {
        Display.getDisplay(this).setCurrent((Displayable)null);

    }

    protected void pauseApp() {
        // TODO Auto-generated method stub

    }

    protected void startApp() throws MIDletStateChangeException {
        Display.getDisplay(this).setCurrent(canvas);

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
                int flags = args.get("1").toNum().num;
            
                return new Rv(AthenaFile.open(path, flags));
            }
        })));

        ri.addToObject(_os, "close", 
            ri.addNativeFunction(new NativeFunctionListEntry("os.close", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int fd = args.get("0").toNum().num;

                AthenaFile.close(fd);
            
                return Rv._undefined;
            }
        })));

        ri.addToObject(_os, "seek", 
            ri.addNativeFunction(new NativeFunctionListEntry("os.seek", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int fd = args.get("0").toNum().num;
                int offset = args.get("1").toNum().num;
                int whence = args.get("2").toNum().num;
            
                return new Rv(AthenaFile.seek(fd, offset, whence));
            }
        })));

        ri.addToObject(callObj, "os", _os);

        Rv _Screen = ri.newModule();
        ri.addToObject(_Screen, "width", new Rv(canvas.getWidth()));
        ri.addToObject(_Screen, "height", new Rv(canvas.getHeight()));

        ri.addToObject(_Screen, "clear", 
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.clear", new NativeFunction() {
            public final int length = 1;
            public Rv func(boolean isNew, Rv _this, Rv args) {
                int color = args.num > 0 ? args.get("0").toNum().num : canvas.CLEAR_COLOR;
            
                canvas.clearScreen(color);
            
                return Rv._undefined;
            }
        })));

        ri.addToObject(_Screen, "update", 
            ri.addNativeFunction(new NativeFunctionListEntry("Screen.update", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    canvas.screenUpdate();

                    return Rv._undefined;
                }
        })));

        ri.addToObject(callObj, "Screen", _Screen);

        Rv _Draw = ri.newModule();
        ri.addToObject(_Draw, "line", 
            ri.addNativeFunction(new NativeFunctionListEntry("Draw.line", new NativeFunction() {
                public final int length = 5;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int x1 = args.get("0").toNum().num;
                    int y1 = args.get("1").toNum().num;
                    int x2 = args.get("2").toNum().num;
                    int y2 = args.get("3").toNum().num;
                    int color = args.get("4").toNum().num;
                    
                    canvas.drawLine(x1, y1, x2, y2, color);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Draw, "triangle", 
            ri.addNativeFunction(new NativeFunctionListEntry("Draw.triangle", new NativeFunction() {
                public final int length = 5;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int x1 = args.get("0").toNum().num;
                    int y1 = args.get("1").toNum().num;
                    int x2 = args.get("2").toNum().num;
                    int y2 = args.get("3").toNum().num;
                    int x3 = args.get("4").toNum().num;
                    int y3 = args.get("5").toNum().num;
                    int color = args.get("6").toNum().num;
                    
                    canvas.drawTriangle(x1, y1, x2, y2, x3, y3, color);

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Draw, "rect", 
            ri.addNativeFunction(new NativeFunctionListEntry("Draw.rect", new NativeFunction() {
                public final int length = 5;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv x = args.get("0");
                    Rv y = args.get("1");
                    Rv w = args.get("2");
                    Rv h = args.get("3");
                    Rv color = args.get("4");
                    
                    canvas.drawRect(x.toNum().num, y.toNum().num, w.toNum().num, h.toNum().num, color.toNum().num);

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
            ri.addNativeFunction(new NativeFunctionListEntry("Image.draw", new NativeFunction() {
                public final int length = 3;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int x = args.get("0").toNum().num;
                    int y = args.get("1").toNum().num;

                    int startx = _this.get("startx").toNum().num;
                    int starty = _this.get("starty").toNum().num;
                    int endx = _this.get("endx").toNum().num;
                    int endy = _this.get("endy").toNum().num;

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
                            font_style = args.get("1").toNum().num;
                        }

                        if (args.num > 2) {
                            font_size =  args.get("2").toNum().num;
                        }

                        font = Font.getFont(font_face.toNum().num, font_style, font_size);
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
            ri.addNativeFunction(new NativeFunctionListEntry("Font.print", new NativeFunction() {
            public final int length = 3;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    String text = args.get("0").toStr().str;
                    int x = args.get("1").toNum().num;
                    int y = args.get("2").toNum().num;

                    int color = _this.get("color").toNum().num;
                    int align = _this.get("align").toNum().num;

                    //canvas._drawImageRegion((Image)_this.opaque, x, y, startx, starty, endx, endy);
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
            ri.addNativeFunction(new NativeFunctionListEntry("Color.new", new NativeFunction() {
            public final int length = 4;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int r = args.get("0").toNum().num;
                    int g = args.get("1").toNum().num;
                    int b = args.get("2").toNum().num;

                    int a = args.num > 3? args.get("3").toNum().num : 0;

                    return new Rv(AthenaColor.color(r, g, b, a));
                }
        })));

        ri.addToObject(callObj, "Color", _Color);

        Rv _Pad = ri.newModule();
        ri.addToObject(_Pad, "update", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.update", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    canvas.padUpdate();

                    return Rv._undefined;
                }
        })));

        ri.addToObject(_Pad, "pressed", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.pressed", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv buttons = args.get("0");
                    return new Rv(canvas.padPressed(buttons.toNum().num)? 1 : 0);
                }
        })));

        ri.addToObject(_Pad, "justPressed", 
            ri.addNativeFunction(new NativeFunctionListEntry("Pad.justPressed", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv buttons = args.get("0");
                    return new Rv(canvas.padJustPressed(buttons.toNum().num)? 1 : 0);
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
            ri.addNativeFunction(new NativeFunctionListEntry("Keyboard.get", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
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
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.get", new NativeFunction() {
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;

                    return new Rv(timer.get());
                }
        })));

        ri.addToObject(_Timer.ctorOrProt, "set", 
            ri.addNativeFunction(new NativeFunctionListEntry("Timer.set", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    AthenaTimer timer = (AthenaTimer)_this.opaque;

                    int value = args.get("0").toNum().num;

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

        ri.call(false, rv, callObj, null, null, 0, 0);

        jsThis = callObj;
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            if (jsExitHandler != null) {
                ri.call(false, jsExitHandler, jsExitHandler.co, jsThis, null, 0, 0);
            }

            destroyApp(false);
            notifyDestroyed();
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
