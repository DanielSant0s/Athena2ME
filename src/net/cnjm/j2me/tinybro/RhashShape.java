package net.cnjm.j2me.tinybro;

import java.util.Hashtable;

/**
 * Insertion-order shape for {@link Rhash} instances: two maps that receive keys in the
 * same order share the same {@link RhashShape} node (interned), enabling future PIC
 * specialisation across many object instances.
 */
public final class RhashShape {

    public final RhashShape parent;
    public final String keyAdded;

    private Hashtable children;

    public static final RhashShape ROOT = new RhashShape(null, null);

    private RhashShape(RhashShape parent, String keyAdded) {
        this.parent = parent;
        this.keyAdded = keyAdded;
    }

    /**
     * Returns the interned child shape reached by appending {@code key} to {@code cur}.
     */
    public static RhashShape refine(RhashShape cur, String key) {
        if (key == null) {
            return cur != null ? cur : ROOT;
        }
        if (cur == null) {
            cur = ROOT;
        }
        Hashtable ch = cur.children;
        if (ch == null) {
            cur.children = ch = new Hashtable(5);
        }
        RhashShape next = (RhashShape) ch.get(key);
        if (next == null) {
            next = new RhashShape(cur, key);
            ch.put(key, next);
        }
        return next;
    }
}
