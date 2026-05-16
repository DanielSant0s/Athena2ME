package net.cnjm.j2me.util;

/**
 * Tiny reusable {@code byte[]} pool for short-lived I/O scratch buffers on CLDC
 * (no {@code ThreadLocal}). Safe across threads via synchronization; callers
 * must {@link #release} in a {@code finally} block when possible.
 */
public final class IoByteBufferPool {

    private static final int SLOTS = 4;
    private static final int MAX_CACHED_LEN = 16384;
    private static final byte[][] slots = new byte[SLOTS][];
    private static final Object lock = new Object();

    private IoByteBufferPool() {
    }

    /**
     * Borrows a buffer with length {@code >= minLen}. May return a larger array;
     * callers should only use the first {@code minLen} bytes unless they know
     * the true length from a prior borrow.
     */
    public static byte[] borrow(int minLen) {
        if (minLen <= 0) {
            minLen = 1;
        }
        if (minLen > MAX_CACHED_LEN) {
            return new byte[minLen];
        }
        synchronized (lock) {
            for (int i = 0; i < SLOTS; i++) {
                byte[] b = slots[i];
                if (b != null && b.length >= minLen) {
                    slots[i] = null;
                    return b;
                }
            }
        }
        int cap = 2048;
        while (cap < minLen && cap < MAX_CACHED_LEN) {
            cap <<= 1;
        }
        return new byte[Math.max(minLen, Math.min(cap, MAX_CACHED_LEN))];
    }

    public static void release(byte[] buf) {
        if (buf == null || buf.length > MAX_CACHED_LEN) {
            return;
        }
        synchronized (lock) {
            for (int i = 0; i < SLOTS; i++) {
                if (slots[i] == null) {
                    slots[i] = buf;
                    return;
                }
                if (buf.length > slots[i].length) {
                    slots[i] = buf;
                    return;
                }
            }
        }
    }
}
