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
    private Command exitCmd = new Command("Exit", Command.EXIT, 3);
    

    public Athena2ME() {
        canvas = new AthenaCanvas();
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
                    Rv arg = args.get("0");

                    canvas.clearScreen(arg.toNum().num);

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

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.drawRect", new NativeFunction() {
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

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.loadImage", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv name = args.get("0");

                    return new Rv(canvas.loadImage(name.toStr().str));
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.freeImage", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv id = args.get("0");
                    canvas.freeImage(id.toNum().num);

                    return Rv._undefined;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.drawImage", new NativeFunction() {
            public final int length = 5;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv id = args.get("0");
                    Rv x = args.get("1");
                    Rv y = args.get("2");
                    
                    canvas._drawImage(id.toNum().num, x.toNum().num, y.toNum().num);

                    return Rv._undefined;
                }
        }));

        Rv _Screen = ri.newModule();
        ri.addToObject(_Screen, "width", new Rv(canvas.getWidth()));
        ri.addToObject(_Screen, "height", new Rv(canvas.getHeight()));
        ri.addToObject(_Screen, "clear", ri.newNativeFunction("Screen.clear"));
        ri.addToObject(_Screen, "drawText", ri.newNativeFunction("Screen.drawText"));
        ri.addToObject(_Screen, "update", ri.newNativeFunction("Screen.update"));
        ri.addToObject(_Screen, "drawRect", ri.newNativeFunction("Screen.drawRect"));
        ri.addToObject(_Screen, "loadImage", ri.newNativeFunction("Screen.loadImage"));
        ri.addToObject(_Screen, "freeImage", ri.newNativeFunction("Screen.freeImage"));
        ri.addToObject(_Screen, "drawImage", ri.newNativeFunction("Screen.drawImage"));

        ri.addToObject(callObj, "Screen", _Screen);

        ri.addNativeFunction(new NativeFunctionListEntry("Pads.pressed", new NativeFunction() {
            public final int length = 0;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv buttons = args.get("0");
                    return new Rv(canvas.padPressed(buttons.toNum().num)? 1 : 0);
                }
        }));

        Rv _Pads = ri.newModule();
        ri.addToObject(_Pads, "pressed", ri.newNativeFunction("Pads.pressed"));

        ri.addToObject(_Pads, "UP", new Rv(canvas.UP));
        ri.addToObject(_Pads, "DOWN", new Rv(canvas.DOWN));
        ri.addToObject(_Pads, "LEFT", new Rv(canvas.LEFT));
        ri.addToObject(_Pads, "RIGHT", new Rv(canvas.RIGHT));
        ri.addToObject(_Pads, "FIRE", new Rv(canvas.FIRE));
        ri.addToObject(_Pads, "GAME_A", new Rv(canvas.GAME_A));
        ri.addToObject(_Pads, "GAME_B", new Rv(canvas.GAME_B));
        ri.addToObject(_Pads, "GAME_C", new Rv(canvas.GAME_C));
        ri.addToObject(_Pads, "GAME_D", new Rv(canvas.GAME_D));

        ri.addToObject(_Pads, "KEY_NUM0", new Rv(canvas.KEY_NUM0));
        ri.addToObject(_Pads, "KEY_NUM1", new Rv(canvas.KEY_NUM1));
        ri.addToObject(_Pads, "KEY_NUM2", new Rv(canvas.KEY_NUM2));
        ri.addToObject(_Pads, "KEY_NUM3", new Rv(canvas.KEY_NUM3));
        ri.addToObject(_Pads, "KEY_NUM4", new Rv(canvas.KEY_NUM4));
        ri.addToObject(_Pads, "KEY_NUM5", new Rv(canvas.KEY_NUM5));
        ri.addToObject(_Pads, "KEY_NUM6", new Rv(canvas.KEY_NUM6));
        ri.addToObject(_Pads, "KEY_NUM7", new Rv(canvas.KEY_NUM7));
        ri.addToObject(_Pads, "KEY_NUM8", new Rv(canvas.KEY_NUM8));
        ri.addToObject(_Pads, "KEY_NUM9", new Rv(canvas.KEY_NUM9));
        ri.addToObject(_Pads, "KEY_STAR", new Rv(canvas.KEY_STAR));
        ri.addToObject(_Pads, "KEY_POUND", new Rv(canvas.KEY_POUND));
        
        ri.addToObject(callObj, "Pads", _Pads);

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
