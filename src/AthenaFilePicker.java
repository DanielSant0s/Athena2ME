import java.util.Enumeration;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;

/**
 * A simple file picker using LCDUI List for J2ME.
 */
public class AthenaFilePicker extends List implements CommandListener {
    private String currentPath = null; // null means roots
    private final MIDlet midlet;
    private final Command selectCmd = new Command("Select", Command.ITEM, 1);
    private final Command backCmd = new Command("Back", Command.BACK, 2);
    private final Command cancelCmd = new Command("Cancel", Command.CANCEL, 3);
    
    private String selectedPath = null;
    private boolean done = false;

    public AthenaFilePicker(MIDlet midlet) {
        super("Select Script", List.IMPLICIT);
        this.midlet = midlet;
        addCommand(selectCmd);
        addCommand(backCmd);
        addCommand(cancelCmd);
        setCommandListener(this);
        refresh();
    }

    public String pick(Display display, Displayable parent) {
        display.setCurrent(this);
        String res = waitSelected();
        display.setCurrent(parent);
        return res;
    }

    private synchronized String waitSelected() {
        while (!done) {
            try {
                wait(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        return selectedPath;
    }

    private void refresh() {
        deleteAll();
        if (currentPath == null) {
            Enumeration e = FileSystemRegistry.listRoots();
            while (e.hasMoreElements()) {
                append((String) e.nextElement(), null);
            }
            setTitle("Select Root");
        } else {
            FileConnection fc = null;
            try {
                fc = (FileConnection) Connector.open("file:///" + currentPath, Connector.READ);
                Enumeration e = fc.list("*", false);
                while (e.hasMoreElements()) {
                    append((String) e.nextElement(), null);
                }
                setTitle("/" + currentPath);
            } catch (Exception ex) {
                ex.printStackTrace();
                currentPath = null;
                refresh();
            } finally {
                if (fc != null) {
                    try { fc.close(); } catch (Exception e) {}
                }
            }
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == List.SELECT_COMMAND || c == selectCmd) {
            int idx = getSelectedIndex();
            if (idx >= 0) {
                String item = getString(idx);
                if (item.endsWith("/")) {
                    if (currentPath == null) {
                        currentPath = item;
                    } else {
                        currentPath += item;
                    }
                    refresh();
                } else {
                    selectedPath = "file:///" + (currentPath == null ? "" : currentPath) + item;
                    synchronized (this) {
                        done = true;
                        notifyAll();
                    }
                }
            }
        } else if (c == backCmd) {
            if (currentPath != null) {
                int lastSlash = currentPath.lastIndexOf('/', currentPath.length() - 2);
                if (lastSlash < 0) {
                    currentPath = null;
                } else {
                    currentPath = currentPath.substring(0, lastSlash + 1);
                }
                refresh();
            }
        } else if (c == cancelCmd) {
            synchronized (this) {
                done = true;
                notifyAll();
            }
        }
    }
}
