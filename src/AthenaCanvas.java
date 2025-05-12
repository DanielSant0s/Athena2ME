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
            InputStream is = "".getClass().getResourceAsStream(name);
            loaded_images.addElement(Image.createImage(name));
            is.close();
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
}
