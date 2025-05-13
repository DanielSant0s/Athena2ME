import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.InterruptedException;
import java.lang.Runnable;
import java.lang.System;

import java.util.Vector;

import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.ToneControl;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

interface DrawableElement {
    void draw(Graphics g);
}

public class AthenaCanvas extends GameCanvas {
    static final int TEXT_COLOR = 0x00ffffff;
    static final int CLEAR_COLOR = 0x00000000;

    private Vector loaded_images = new Vector();
    private int pressed_buttons = 0;
    private int old_pressed_buttons = 0;

    private Graphics g = null;

    static {
        Font defaultFont = Font.getDefaultFont();
    }

    public AthenaCanvas(boolean suppressKeyEvents) {
        super(suppressKeyEvents);

        g = getGraphics();
    }

    public void drawFont(final String text, final int x, final int y, final int anchor, final int color) {
        g.setColor(color);
        g.drawString(text, x, y, g.TOP | g.LEFT);
    }

    public void clearScreen(final int color) {
        g.setColor(color);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    public void drawRect(int x, int y, int w, int h, int color) {
        g.setColor(color);
        g.fillRect(x, y, w, h);
    }

    public void drawLine(int x1, int y1, int x2, int y2, int color) {
        g.setColor(color);
        g.drawLine(x1, y1, x2, y2);
    }

    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        g.setColor(color);
        g.fillTriangle(x1, y1, x2, y2, x3, y3);
    }

    public void _drawImage(int id, int x, int y) {
        g.drawImage((Image)loaded_images.elementAt(id), x, y, g.TOP | g.LEFT);
    }

    public void _drawImageRegion(int id, int x, int y, int startx, int starty, int endx, int endy) {
        //g.drawImage((Image)loaded_images.elementAt(id), x, y, g.TOP | g.LEFT);
        g.drawRegion((Image)loaded_images.elementAt(id),
            startx,
            starty,
            endx,
            endy,
            Sprite.TRANS_NONE,
            x,
            y,
            g.TOP | g.LEFT);
    }

    public int getImageWidth(int id) {
        Image img = (Image)loaded_images.elementAt(id);

        return img.getWidth();
    }

    public int getImageHeight(int id) {
        Image img = (Image)loaded_images.elementAt(id);

        return img.getHeight();
    }

    public int loadImage(String name) {
        try {
            loaded_images.addElement(Image.createImage(name));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return loaded_images.size()-1;
    }

    public void freeImage(int id) {
        loaded_images.removeElementAt(id);
    }

    public void screenUpdate() {
        flushGraphics();
    }

    public final int KEY_NUM0 =   1 << 1;
    public final int KEY_NUM1 =   1 << 2;
    public final int KEY_NUM2 =   1 << 3;
    public final int KEY_NUM3 =   1 << 4;
    public final int KEY_NUM4 =   1 << 5;
    public final int KEY_NUM5 =   1 << 6;
    public final int KEY_NUM6 =   1 << 7;
    public final int KEY_NUM7 =   1 << 8;
    public final int KEY_NUM8 =   1 << 9;
    public final int KEY_NUM9 =   1 << 10;
    public final int KEY_STAR =   1 << 11;
    public final int KEY_POUND =  1 << 12;

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
}
