package net.cnjm.j2me.sync;

/**
 * Counting semaphore with an upper bound on permits ({@code release} cannot exceed {@code maxPermits}).
 */
public final class Semaphore {

    private int permits;
    private final int maxPermits;

    public Semaphore(int initialPermits, int maxPermits) {
        int max = maxPermits;
        if (max < 1) {
            max = Integer.MAX_VALUE;
        }
        int initial = initialPermits;
        if (initial < 0) {
            initial = 0;
        }
        if (initial > max) {
            initial = max;
        }
        this.permits = initial;
        this.maxPermits = max;
    }

    public synchronized void acquire() throws InterruptedException {
        while (permits <= 0) {
            wait();
        }
        permits--;
    }

    public synchronized boolean tryAcquire() {
        if (permits <= 0) {
            return false;
        }
        permits--;
        return true;
    }

    public synchronized void release() {
        if (permits >= maxPermits) {
            return;
        }
        permits++;
        notify();
    }

    public synchronized int availablePermits() {
        return permits;
    }
}
