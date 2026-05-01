package net.cnjm.j2me.util;

/**
 * Hash map from int key to {@code int} + optional {@code Object}.
 * Uses embedded chaining list nodes (no {@link Pack} per slot — less GC pressure than nested arrays).
 */
public class IntHash {

    private static final int LOAD_FACTOR = 75;

    private static final class Entry {
        int key;
        int ival;
        Object oval;
        Entry next;

        Entry(int key, int ival, Object oval, Entry next) {
            this.key = key;
            this.ival = ival;
            this.oval = oval;
            this.next = next;
        }
    }

    private Entry[] table;
    private int threshold;

    public int size;

    public IntHash(int initialCapacity) {
        reset(initialCapacity);
    }

    public final IntHash reset(int initialCapacity) {
        table = new Entry[initialCapacity];
        size = 0;
        threshold = initialCapacity * LOAD_FACTOR / 100;
        return this;
    }

    public final int get(int key, int defValue) {
        Entry[] tab = table;
        int index = (key & 0x7fffffff) % tab.length;
        for (Entry p = tab[index]; p != null; p = p.next) {
            if (p.key == key) {
                return p.ival;
            }
        }
        return defValue;
    }

    public final Object get(int key) {
        Entry[] tab = table;
        int index = (key & 0x7fffffff) % tab.length;
        for (Entry p = tab[index]; p != null; p = p.next) {
            if (p.key == key) {
                return p.oval;
            }
        }
        return null;
    }

    public final IntHash put(int key, int iVal, Object oVal) {
        if (size >= threshold) {
            rehash();
        }
        Entry[] tab = table;
        int index = (key & 0x7fffffff) % tab.length;
        Entry p = tab[index];
        for (; p != null; p = p.next) {
            if (p.key == key) {
                p.ival = iVal;
                p.oval = oVal;
                return this;
            }
        }
        tab[index] = new Entry(key, iVal, oVal, tab[index]);
        ++size;
        return this;
    }

    public final IntHash remove(int key) {
        Entry[] tab = table;
        int index = (key & 0x7fffffff) % tab.length;
        Entry p = tab[index];
        Entry prev = null;
        while (p != null) {
            if (p.key == key) {
                if (prev != null) {
                    prev.next = p.next;
                } else {
                    tab[index] = p.next;
                }
                --size;
                break;
            }
            prev = p;
            p = p.next;
        }
        return this;
    }

    public final Pack keys() {
        Pack ret = new Pack(size, -1).setSize(size, -1);
        int[] newiarr = ret.iArray;
        Entry[] tab = table;
        int ii = 0;
        for (int i = tab.length; --i >= 0;) {
            Entry p = tab[i];
            while (p != null) {
                newiarr[ii++] = p.key;
                p = p.next;
            }
        }
        return ret;
    }

    final void rehash() {
        Entry[] oldtab = table;
        int oldlen = oldtab.length;
        int newlen = oldlen * 2 + 1;
        Entry[] newtab = new Entry[newlen];
        threshold = (newlen * LOAD_FACTOR) / 100;
        table = newtab;
        size = 0;
        for (int i = oldlen; --i >= 0;) {
            Entry p = oldtab[i];
            while (p != null) {
                Entry next = p.next;
                int index = (p.key & 0x7fffffff) % newlen;
                p.next = newtab[index];
                newtab[index] = p;
                p = next;
                size++;
            }
        }
    }
}
