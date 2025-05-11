package net.cnjm.j2me.util;

public class IntHash {
    
    private static final int LOAD_FACTOR = 75;
    private static final int VALUE = 0;
    private static final int NEXT = 1;

    private Pack[] table;
    private int threshold;

    public int size;

    public IntHash(int initialCapacity) {
        reset(initialCapacity);
    }
    
    public final IntHash reset(int initialCapacity) {
        table = new Pack[initialCapacity];
        size = 0;
        threshold = initialCapacity * LOAD_FACTOR / 100;
        return this;
    }
    
    public final int get(int key, int defValue) {
        Pack[] tab;
        int index = (key & 0x7fffffff) % (tab = table).length;
        for (Pack p = tab[index]; p != null; p = (Pack) p.oArray[NEXT]) {
            if (p.iSize == key) { // found
                return p.oSize;
            }
        }
        return defValue;
    }
    
    public final Object get(int key) {
        Pack[] tab;
        int index = (key & 0x7fffffff) % (tab = table).length;
        for (Pack p = tab[index]; p != null; p = (Pack) p.oArray[NEXT]) {
            if (p.iSize == key) { // found
                return p.oArray[VALUE];
            }
        }
        return null;
    }
    
    public final IntHash put(int key, int iVal, Object oVal) {
        if (size >= threshold) {
            rehash();
        }
        Pack[] tab;
        int index = (key & 0x7fffffff) % (tab = table).length;
        Pack p, pb;
        for (p = pb = tab[index]; p != null; p = (Pack) p.oArray[NEXT]) {
            if (p.iSize == key) { // found
                p.oSize = iVal;
                p.oArray[VALUE] = oVal;
                return this;
            }
        }
        tab[index] = p = new Pack(-1, 2);
        p.iSize = key;
        p.oSize = iVal;
        Object[] oarr;
        (oarr = p.oArray)[VALUE] = oVal;
        oarr[NEXT] = pb;
        ++size;
        return this;
    }
    
    public final IntHash remove(int key) {
        Pack[] tab;
        int index = (key & 0x7fffffff) % (tab = table).length;
        Pack p, prev;
        for (p = tab[index], prev = null; p != null; prev = p, p = (Pack) p.oArray[NEXT]) {
            if (p.iSize == key) { // found
                if (prev != null) {
                    prev.oArray[NEXT] = p.oArray[NEXT];
                } else {
                    tab[index] = (Pack) p.oArray[NEXT];
                }
                --size;
                break;
            }
        }
        return this;
    }
    
    public final Pack keys() {
        Pack ret = new Pack(size, -1).setSize(size, -1);
        int[] newiarr = ret.iArray;
        Pack[] tab;
        int len = (tab = table).length;
        for (int i = len, ii = 0; --i >= 0;) {
            Pack p;
            if ((p = tab[i]) == null) continue;
            do {
                newiarr[ii++] = p.iSize;
                p = (Pack) p.oArray[NEXT];
            } while (p != null);

        }
        return ret;
    }
    
    final void rehash() {
        Pack[] oldtab;
        int oldlen = (oldtab = table).length;
        int newlen = oldlen * 2 + 1;
        Pack[] newtab = new Pack[newlen];
        threshold = (newlen * LOAD_FACTOR) / 100;
        table = newtab;
        for (int i = oldlen; --i >= 0;) {
            Pack p;
            if ((p = oldtab[i]) == null) continue;
            do {
                int index = (p.iSize & 0x7fffffff) % newlen;
                Pack next = newtab[index];
                newtab[index] = p;
                Object[] oarr;
                p = (Pack) (oarr = p.oArray)[NEXT];
                oarr[NEXT] = next;
            } while (p != null);
        }
    }
    
}
