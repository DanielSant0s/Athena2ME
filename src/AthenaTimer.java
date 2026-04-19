/**
 * Simple elapsed-time stopwatch. All external values are kept in a single
 * time base (relative milliseconds) to avoid mixing deltas and absolute
 * System.currentTimeMillis() timestamps the way the original implementation did.
 *
 *  - When playing: get() returns accumulated + (now - startMs)
 *  - When paused:  get() returns accumulated
 */
public class AthenaTimer {
    private long startMs = 0L;     // when the current play span began (abs ms)
    private int accum = 0;         // ms accumulated from previous play spans
    private boolean isPlaying = false;

    public AthenaTimer(long timerState) {
        // `timerState` is treated as "this timer was started N ms ago in wall time".
        // Keep it internally consistent with the playing model.
        startMs = timerState;
        accum = 0;
        isPlaying = true;
    }

    public int get() {
        if (isPlaying) {
            return accum + (int) (System.currentTimeMillis() - startMs);
        }
        return accum;
    }

    /**
     * Returns the sum of current elapsed time and `val`, in milliseconds.
     * Preserves the original public contract used by the interpreter binding.
     */
    public int set(int val) {
        return get() + val;
    }

    public void pause() {
        if (!isPlaying) return;
        accum += (int) (System.currentTimeMillis() - startMs);
        isPlaying = false;
    }

    public void resume() {
        if (isPlaying) return;
        startMs = System.currentTimeMillis();
        isPlaying = true;
    }

    public void reset() {
        accum = 0;
        startMs = System.currentTimeMillis();
    }

    public boolean playing() {
        return isPlaying;
    }
}
