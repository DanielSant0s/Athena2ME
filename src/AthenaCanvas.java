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

import javax.microedition.lcdui.Canvas;
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

public class AthenaCanvas extends Canvas {
    static final int TEXT_COLOR = 0x00ffffff;
    static final int CLEAR_COLOR = 0x00000000;
    private boolean update = false;
    private Vector draw_queue = new Vector();
    private Vector loaded_images = new Vector();
    private int pressed_buttons = 0;


    static {
        Font defaultFont = Font.getDefaultFont();
    }

    public AthenaCanvas() {

    }

    public void drawFont(final String text, final int x, final int y, final int anchor, final int color) {
        draw_queue.addElement(new DrawableElement() {
            public void draw(Graphics g) {
                g.setColor(color);
                g.drawString(text, x, y, g.TOP | g.LEFT);
            }
        });
    }

    public void clearScreen(final int color) {
        draw_queue.addElement(new DrawableElement() {
            public void draw(Graphics g) {
                g.setColor(color);
                g.fillRect(0, 0, getWidth(), getHeight());
                
            }
        });
    }

    public void drawRect(final int x, final int y, final int w, final int h, final int color) {
        draw_queue.addElement(new DrawableElement() {
            public void draw(Graphics g) {
                g.setColor(color);
                g.fillRect(x, y, w, h);
                
            }
        });
    }

    public void _drawImage(final int id, final int x, final int y) {
        draw_queue.addElement(new DrawableElement() {
            public void draw(Graphics g) {
                g.drawImage((Image)loaded_images.elementAt(id), x, y, g.TOP | g.LEFT);
                
            }
        });
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
        update = true;
        repaint();
    }

    public void paint(Graphics g) {
        if (update) {
            update = false;

            while (draw_queue.size() > 0) {
                DrawableElement elem = (DrawableElement)draw_queue.elementAt(0);
                elem.draw(g);

                draw_queue.removeElementAt(0);
            }
        }
    }

    public final int UP =    1 << 0;
    public final int DOWN =  1 << 1;
    public final int LEFT =  1 << 2;
    public final int RIGHT = 1 << 3;
    public final int FIRE =  1 << 4;

    public final int GAME_A =  1 << 5;
    public final int GAME_B =  1 << 6;
    public final int GAME_C =  1 << 7;
    public final int GAME_D =  1 << 8;

    public final int KEY_NUM0 =   1 << 9;
    public final int KEY_NUM1 =   1 << 10;
    public final int KEY_NUM2 =   1 << 11;
    public final int KEY_NUM3 =   1 << 12;
    public final int KEY_NUM4 =   1 << 13;
    public final int KEY_NUM5 =   1 << 14;
    public final int KEY_NUM6 =   1 << 15;
    public final int KEY_NUM7 =   1 << 16;
    public final int KEY_NUM8 =   1 << 17;
    public final int KEY_NUM9 =   1 << 18;
    public final int KEY_STAR =   1 << 19;
    public final int KEY_POUND =  1 << 20;

    public void keyPressed(int keyCode) {
        pressed_buttons = 0;
        switch (getGameAction(keyCode)) {
        case Canvas.UP:
            pressed_buttons |= UP;
            break;

        case Canvas.DOWN:
            pressed_buttons |= DOWN;
            break;

        case Canvas.LEFT:
            pressed_buttons |= LEFT;
            break;

        case Canvas.RIGHT:
            pressed_buttons |= RIGHT;
            break;

        case Canvas.FIRE:
            pressed_buttons |= FIRE;
            break;

        case Canvas.GAME_A:
            pressed_buttons |= GAME_A;
            break;
        
        case Canvas.GAME_B:
            pressed_buttons |= GAME_B;
            break;

        case Canvas.GAME_C:
            pressed_buttons |= GAME_C;
            break;

        case Canvas.GAME_D:
            pressed_buttons |= GAME_D;
            break;

        case 0:

            // There is no game action.. Use keypad constants instead
            switch (keyCode) {
                case Canvas.KEY_NUM0:
                    pressed_buttons |= KEY_NUM0;
                    break;

                case Canvas.KEY_NUM1:
                    pressed_buttons |= KEY_NUM1;
                    break;

                case Canvas.KEY_NUM2:
                    pressed_buttons |= KEY_NUM2;
                    break;

                case Canvas.KEY_NUM3:
                    pressed_buttons |= KEY_NUM3;
                    break;

                case Canvas.KEY_NUM4:
                    pressed_buttons |= KEY_NUM4;
                    break;

                case Canvas.KEY_NUM5:
                    pressed_buttons |= KEY_NUM5;
                    break;

                case Canvas.KEY_NUM6:
                    pressed_buttons |= KEY_NUM6;
                    break;

                case Canvas.KEY_NUM7:
                    pressed_buttons |= KEY_NUM7;
                    break;

                case Canvas.KEY_NUM8:
                    pressed_buttons |= KEY_NUM8;
                    break;

                case Canvas.KEY_NUM9:
                    pressed_buttons |= KEY_NUM9;
                    break;

                case Canvas.KEY_STAR:
                    pressed_buttons |= KEY_NUM8;
                    break;

                case Canvas.KEY_POUND:
                    pressed_buttons |= KEY_NUM9;
                    break;
            }

            break;
        }
    }

    public void keyReleased(int keyCode) {
        pressed_buttons = 0;
        switch (getGameAction(keyCode)) {
        case Canvas.UP:
            pressed_buttons &= ~UP;
            break;

        case Canvas.DOWN:
            pressed_buttons &= ~DOWN;
            break;

        case Canvas.LEFT:
            pressed_buttons &= ~LEFT;
            break;

        case Canvas.RIGHT:
            pressed_buttons &= ~RIGHT;
            break;

        case Canvas.FIRE:
            pressed_buttons &= ~FIRE;
            break;

        case Canvas.GAME_A:
            pressed_buttons &= ~GAME_A;
            break;
        
        case Canvas.GAME_B:
            pressed_buttons &= ~GAME_B;
            break;

        case Canvas.GAME_C:
            pressed_buttons &= ~GAME_C;
            break;

        case Canvas.GAME_D:
            pressed_buttons &= ~GAME_D;
            break;

        case 0:

            // There is no game action.. Use keypad constants instead
            switch (keyCode) {
                case Canvas.KEY_NUM0:
                    pressed_buttons &= ~KEY_NUM0;
                    break;

                case Canvas.KEY_NUM1:
                    pressed_buttons &= ~KEY_NUM1;
                    break;

                case Canvas.KEY_NUM2:
                    pressed_buttons &= ~KEY_NUM2;
                    break;

                case Canvas.KEY_NUM3:
                    pressed_buttons &= ~KEY_NUM3;
                    break;

                case Canvas.KEY_NUM4:
                    pressed_buttons &= ~KEY_NUM4;
                    break;

                case Canvas.KEY_NUM5:
                    pressed_buttons &= ~KEY_NUM5;
                    break;

                case Canvas.KEY_NUM6:
                    pressed_buttons &= ~KEY_NUM6;
                    break;

                case Canvas.KEY_NUM7:
                    pressed_buttons &= ~KEY_NUM7;
                    break;

                case Canvas.KEY_NUM8:
                    pressed_buttons &= ~KEY_NUM8;
                    break;

                case Canvas.KEY_NUM9:
                    pressed_buttons &= ~KEY_NUM9;
                    break;

                case Canvas.KEY_STAR:
                    pressed_buttons &= ~KEY_NUM8;
                    break;

                case Canvas.KEY_POUND:
                    pressed_buttons &= ~KEY_NUM9;
                    break;
            }

            break;
        }
    }

    public boolean padPressed(int buttons) {
        return (pressed_buttons & buttons) != 0;
    }
}
