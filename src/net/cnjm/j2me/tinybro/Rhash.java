package net.cnjm.j2me.tinybro;

import net.cnjm.j2me.util.Pack;

public class Rhash {

    private static final int LOAD_FACTOR = 75;

    private Rv[] table;
    private int threshold;
    private Pack keys;
    private boolean updatekey;

    public int size;
    
    public Rhash(int initialCapacity) {
        reset(initialCapacity);
    }
    
    public final Rhash reset(int initialCapacity) {
        table = new Rv[initialCapacity];
        size = 0;
        threshold = initialCapacity * LOAD_FACTOR / 100;
        updatekey = true;
        keys = new Pack(-1, -1);
        return this;
    }
    
    public final int get(int key, int defValue) {
        Rv entry = getEntry(key, null);
        return entry != null ? entry.num : defValue;
    }
    
    public final Rv get(String key) {
        Rv entry = getEntry(0, key);
        return entry != null ? entry.co : null;
    }
    
    public final Rhash put(int key, int value) {
        Rv entry = new Rv();
        entry.num = value;
        return putEntry(key, null, entry);
    }
    
    public final Rhash put(String key, int value) {
        Rv entry = new Rv();
        entry.num = value;
        return putEntry(0, key, entry);
    }
    
    public final Rhash put(String key, Rv value) {
        Rv entry = new Rv();
        entry.co = value;
        return putEntry(0, key, entry);
    }
    
    public final Rv getEntry(int iKey, String sKey) {
        if (sKey != null) iKey = sKey.hashCode(); // key's object, iKey is ignored
        Rv[] tab;
        Rv p;
        int index = (iKey & 0x7fffffff) % (tab = table).length;
        for (p = tab[index]; p != null; p = p.prev) {
            if (iKey == p.type && (sKey == null || sKey.equals(p.str))) { // found
                return p;
            }
        }
        return null;
    }
    
    public final Rhash putEntry(int iKey, String sKey, Rv entry) {
        if (sKey != null) iKey = sKey.hashCode(); // key's object, iKey is ignored
        if (size >= threshold) rehash();
        Rv[] tab;
        Rv p, pr;
        entry.type = iKey;
        entry.str = sKey;
        int index = (iKey & 0x7fffffff) % (tab = table).length;
        for (pr = null, p = tab[index]; p != null; pr = p, p = p.prev) {
            if (iKey == p.type && (sKey == null || sKey.equals(p.str))) { // found
                if (pr != null) {
                    pr.prev = entry;
                } else {
                    tab[index] = entry;
                }
                entry.prev = p.prev;
                return this;
            }
        }
        Rv next = tab[index];
        tab[index] = entry;
        entry.prev = next;
        ++size;
        updatekey = true;
        return this;
    }
    
    public final Rv remove(int iKey, String sKey) {
        if (sKey != null) iKey = sKey.hashCode(); // key's object, iKey is ignored
        Rv[] tab;
        Rv p, pr;
        int index = (iKey & 0x7fffffff) % (tab = table).length;
        for (pr = null, p = tab[index]; p != null; pr = p, p = p.prev) {
            if (iKey == p.type && (sKey == null || sKey.equals(p.str))) { // found
                if (pr != null) {
                    pr.prev = p.prev;
                } else {
                    tab[index] = p.prev;
                }
                p.prev = null;
                --size;
                updatekey = true;
                return p;
            }
        }
        return null;
    }
    
    public final Pack keys() {
        if (updatekey) {
            Pack ret = keys.reset(size, size);
            Rv[] tab;
            for (int i = (tab = table).length; --i >= 0;) {
                Rv p;
                for (p = tab[i]; p != null; p = p.prev) {
                    if (p.str == null) {
                        ret.add(p.type);
                    } else {
                        ret.add(p.str);
                    }
                }
            }
            updatekey = false;
        }
        return keys;
    }
    
    final void rehash() {
        Rv[] oldtab, newtab;
        int oldlen = (oldtab = table).length;
        int newlen = oldlen * 2 + 1;
        table = newtab = new Rv[newlen];
        threshold = newlen * LOAD_FACTOR / 100;
        for (int i = oldlen; --i >= 0;) {
            Rv p, q;
            for (p = q = oldtab[i]; (p = q) != null;) {
                int index = (p.type & 0x7fffffff) % newlen;
                Rv next = newtab[index];
                newtab[index] = p;
                q = p.prev;
                p.prev = next;
            }
        }
    }
    
}
