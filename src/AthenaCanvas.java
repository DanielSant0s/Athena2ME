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

public class AthenaCanvas extends GameCanvas {
    static final int TEXT_COLOR = 0x00ffffff;
    static final int CLEAR_COLOR = 0x00000000;

    private int pressed_buttons = 0;
    private int old_pressed_buttons = 0;

    private int key = 0;

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

    public void _drawImage(Image img, int x, int y) {
        g.drawImage(img, x, y, g.TOP | g.LEFT);
    }

    public void _drawImageRegion(Image img, int x, int y, int startx, int starty, int endx, int endy) {
        //g.drawImage(img, x, y, g.TOP | g.LEFT);
        g.drawRegion(img,
            startx,
            starty,
            endx,
            endy,
            Sprite.TRANS_NONE,
            x,
            y,
            g.TOP | g.LEFT);
    }

    public Image loadImage(String name) {
        Image ret = null;
        try {
            ret = Image.createImage(name);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return ret;
    }

    public void screenUpdate() {
        flushGraphics();
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
}
