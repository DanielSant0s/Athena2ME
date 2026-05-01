import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.microedition.rms.RecordStore;

public class AthenaStorage {
    private static final String RS_NAME = "localStorage";
    private static Hashtable cache = null;

    private static void load() {
        if (cache != null) return;
        cache = new Hashtable();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RS_NAME, true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                if (data != null && data.length > 0) {
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                    int size = dis.readInt();
                    for (int i = 0; i < size; i++) {
                        String k = dis.readUTF();
                        String v = dis.readUTF();
                        cache.put(k, v);
                    }
                    dis.close();
                }
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignore) {}
        }
    }

    private static void save() {
        if (cache == null) return;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RS_NAME, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(cache.size());
            Enumeration e = cache.keys();
            while (e.hasMoreElements()) {
                String k = (String) e.nextElement();
                String v = (String) cache.get(k);
                dos.writeUTF(k);
                dos.writeUTF(v);
            }
            dos.close();
            byte[] data = baos.toByteArray();
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignore) {}
        }
    }

    public static void setItem(String key, String value) {
        load();
        if (key == null) key = "null";
        if (value == null) value = "null";
        cache.put(key, value);
        save();
    }

    public static String getItem(String key) {
        load();
        if (key == null) key = "null";
        return (String) cache.get(key);
    }

    public static void removeItem(String key) {
        load();
        if (key == null) key = "null";
        if (cache.containsKey(key)) {
            cache.remove(key);
            save();
        }
    }

    public static void clear() {
        cache = new Hashtable();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(RS_NAME, true);
            if (rs.getNumRecords() > 0) {
                rs.deleteRecord(1);
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignore) {}
        }
    }
}
