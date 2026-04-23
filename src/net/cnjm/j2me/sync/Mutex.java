package net.cnjm.j2me.sync;

/**
 * Non-reentrant mutual exclusion lock (J2ME: {@code synchronized} + {@code wait}/{@code notify}).
 */
public final class Mutex {

    private boolean locked;
    private Thread owner;

    public Mutex() {
    }

    public synchronized void lock() throws InterruptedException {
        Thread me = Thread.currentThread();
        while (locked && owner != me) {
            wait();
        }
        if (locked && owner == me) {
            throw new IllegalStateException("Mutex is not reentrant");
        }
        locked = true;
        owner = me;
    }

    /**
     * @return true if the lock was acquired
     */
    public synchronized boolean tryLock() {
        if (locked) {
            return false;
        }
        locked = true;
        owner = Thread.currentThread();
        return true;
    }

    public synchronized void unlock() {
        if (!locked || owner != Thread.currentThread()) {
            return;
        }
        locked = false;
        owner = null;
        notify();
    }
}
