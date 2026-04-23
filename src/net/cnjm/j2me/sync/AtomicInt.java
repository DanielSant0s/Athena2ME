package net.cnjm.j2me.sync;

/**
 * Simple atomic integer for J2ME (no {@code java.util.concurrent}).
 */
public final class AtomicInt {

    private int value;

    public AtomicInt(int initial) {
        this.value = initial;
    }

    public synchronized int get() {
        return value;
    }

    public synchronized void set(int v) {
        this.value = v;
    }

    public synchronized int addAndGet(int delta) {
        value += delta;
        return value;
    }
}
