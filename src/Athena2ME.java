import java.io.*;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;

import net.cnjm.j2me.tinybro.*;
import net.cnjm.j2me.util.*;

public class Athena2ME extends MIDlet implements CommandListener {
    RocksInterpreter ri;
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

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.clear", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int color = args.num > 0 ? args.get("0").toNum().num : canvas.CLEAR_COLOR;

                    canvas.clearScreen(color);

                    return Rv._undefined;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.drawText", new NativeFunction() {
            public final int length = 5;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv text = args.get("0");
                    Rv x = args.get("1");
                    Rv y = args.get("2");
                    Rv anchor = args.get("3");
                    Rv color = args.get("4");
                    
                    canvas.drawFont(text.toStr().str, x.toNum().num, y.toNum().num, anchor.toNum().num, color.toNum().num);

                    return Rv._undefined;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.update", new NativeFunction() {
            public final int length = 0;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    canvas.screenUpdate();

                    return Rv._undefined;
                }
        }));

        Rv _Screen = ri.newModule();
        ri.addToObject(_Screen, "width", new Rv(canvas.getWidth()));
        ri.addToObject(_Screen, "height", new Rv(canvas.getHeight()));
        ri.addToObject(_Screen, "clear", ri.newNativeFunction("Screen.clear"));
        ri.addToObject(_Screen, "drawText", ri.newNativeFunction("Screen.drawText"));
        ri.addToObject(_Screen, "update", ri.newNativeFunction("Screen.update"));

        ri.addToObject(callObj, "Screen", _Screen);

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
        }));

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
        }));

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
        }));

        Rv _Draw = ri.newModule();
        ri.addToObject(_Draw, "line", ri.newNativeFunction("Draw.line"));
        ri.addToObject(_Draw, "triangle", ri.newNativeFunction("Draw.triangle"));
        ri.addToObject(_Draw, "rect", ri.newNativeFunction("Draw.rect"));

        ri.addToObject(callObj, "Draw", _Draw);

        final Rv _Image = ri.newModule();

        ri.addNativeFunction(new NativeFunctionListEntry("Image", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv ret = isNew ? _this : new Rv(Rv.OBJECT, _Image);

                    Rv name = args.get("0");

                    int id = canvas.loadImage(name.toStr().str);

                    ri.addToObject(ret, "id", new Rv(id));
                    ri.addToObject(ret, "startx", new Rv(0));
                    ri.addToObject(ret, "starty", new Rv(0));
                    ri.addToObject(ret, "endx", new Rv(canvas.getImageWidth(id)));
                    ri.addToObject(ret, "endy", new Rv(canvas.getImageHeight(id)));
                    ri.addToObject(ret, "width", new Rv(canvas.getImageWidth(id)));
                    ri.addToObject(ret, "height", new Rv(canvas.getImageHeight(id)));

                    return ret;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Image.free", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv id = _this.get("id");
                    canvas.freeImage(id.toNum().num);

                    return Rv._undefined;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Image.draw", new NativeFunction() {
            public final int length = 3;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int id = _this.get("id").toNum().num;
                    int x = args.get("0").toNum().num;
                    int y = args.get("1").toNum().num;

                    int startx = _this.get("startx").toNum().num;
                    int starty = _this.get("starty").toNum().num;
                    int endx = _this.get("endx").toNum().num;
                    int endy = _this.get("endy").toNum().num;

                    canvas._drawImageRegion(id, x, y, startx, starty, endx, endy);

                    return Rv._undefined;
                }
        }));

        _Image.nativeCtor("Image", callObj);
        ri.addToObject(_Image.ctorOrProt, "draw", ri.newNativeFunction("Image.draw"));
        ri.addToObject(_Image.ctorOrProt, "free", ri.newNativeFunction("Image.free"));

        ri.addToObject(callObj, "Image", _Image);

        ri.addNativeFunction(new NativeFunctionListEntry("Color.new", new NativeFunction() {
            public final int length = 4;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    int r = args.get("0").toNum().num;
                    int g = args.get("1").toNum().num;
                    int b = args.get("2").toNum().num;

                    int a = args.num > 3? args.get("3").toNum().num : 0;

                    return new Rv(AthenaColor.color(r, g, b, a));
                }
        }));

        Rv _Color = ri.newModule();
        ri.addToObject(_Color, "new", ri.newNativeFunction("Color.new"));

        ri.addToObject(callObj, "Color", _Color);

        ri.addNativeFunction(new NativeFunctionListEntry("Pad.pressed", new NativeFunction() {
            public final int length = 0;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv buttons = args.get("0");
                    return new Rv(canvas.padPressed(buttons.toNum().num)? 1 : 0);
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Pad.justPressed", new NativeFunction() {
            public final int length = 0;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv buttons = args.get("0");
                    return new Rv(canvas.padJustPressed(buttons.toNum().num)? 1 : 0);
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Pad.update", new NativeFunction() {
            public final int length = 0;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    canvas.padUpdate();

                    return Rv._undefined;
                }
        }));

        Rv _Pad = ri.newModule();
        ri.addToObject(_Pad, "update", ri.newNativeFunction("Pad.update"));
        ri.addToObject(_Pad, "pressed", ri.newNativeFunction("Pad.pressed"));
        ri.addToObject(_Pad, "justPressed", ri.newNativeFunction("Pad.justPressed"));

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

        ri.addNativeFunction(new NativeFunctionListEntry("Keyboard.get", new NativeFunction() {
            public final int length = 0;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    return new Rv(canvas.getKeypad());
                }
        }));

        Rv _Keyboard = ri.newModule();
        ri.addToObject(_Keyboard, "get", ri.newNativeFunction("Keyboard.get"));

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

        ri.call(false, rv, callObj, null, null, 0, 0);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
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
