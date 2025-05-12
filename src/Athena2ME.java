import java.io.*;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;

import net.cnjm.j2me.tinybro.*;
import net.cnjm.j2me.util.*;

public class Athena2ME extends MIDlet implements CommandListener {
    RocksInterpreter ri;
    private AthenaCanvas athenaScreen;
    private Command exitCmd = new Command("Exit", Command.EXIT, 3);
    

    public Athena2ME() {
        athenaScreen = new AthenaCanvas();
        athenaScreen.addCommand(exitCmd);
        athenaScreen.setCommandListener(this);
    }

    protected void destroyApp(boolean unconditional) {
        Display.getDisplay(this).setCurrent((Displayable)null);

    }

    protected void pauseApp() {
        // TODO Auto-generated method stub

    }

    protected void startApp() throws MIDletStateChangeException {
        Display.getDisplay(this).setCurrent(athenaScreen);

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

                    athenaScreen.clearScreen(arg.toNum().num);

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
                    
                    athenaScreen.drawFont(text.toStr().str, x.toNum().num, y.toNum().num, anchor.toNum().num, color.toNum().num);

                    return Rv._undefined;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.update", new NativeFunction() {
            public final int length = 0;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    athenaScreen.screenUpdate();

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
                    
                    athenaScreen.drawRect(x.toNum().num, y.toNum().num, w.toNum().num, h.toNum().num, color.toNum().num);

                    return Rv._undefined;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.loadImage", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv name = args.get("0");

                    return new Rv(athenaScreen.loadImage(name.toStr().str));
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.freeImage", new NativeFunction() {
            public final int length = 1;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv id = args.get("0");
                    athenaScreen.freeImage(id.toNum().num);

                    return Rv._undefined;
                }
        }));

        ri.addNativeFunction(new NativeFunctionListEntry("Screen.drawImage", new NativeFunction() {
            public final int length = 5;
                public Rv func(boolean isNew, Rv _this, Rv args) {
                    Rv id = args.get("0");
                    Rv x = args.get("1");
                    Rv y = args.get("2");
                    
                    athenaScreen._drawImage(id.toNum().num, x.toNum().num, y.toNum().num);

                    return Rv._undefined;
                }
        }));

        Rv _Screen = ri.newModule();
        ri.addToObject(_Screen, "width", new Rv(athenaScreen.getWidth()));
        ri.addToObject(_Screen, "height", new Rv(athenaScreen.getHeight()));
        ri.addToObject(_Screen, "clear", ri.newNativeFunction("Screen.clear"));
        ri.addToObject(_Screen, "drawText", ri.newNativeFunction("Screen.drawText"));
        ri.addToObject(_Screen, "update", ri.newNativeFunction("Screen.update"));
        ri.addToObject(_Screen, "drawRect", ri.newNativeFunction("Screen.drawRect"));
        ri.addToObject(_Screen, "loadImage", ri.newNativeFunction("Screen.loadImage"));
        ri.addToObject(_Screen, "freeImage", ri.newNativeFunction("Screen.freeImage"));
        ri.addToObject(_Screen, "drawImage", ri.newNativeFunction("Screen.drawImage"));

        ri.addToObject(callObj, "Screen", _Screen);

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
