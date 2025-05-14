public class AthenaTimer {
    private int tick = 0;
    private long t_starter = 0;
    private boolean isPlaying = false;

    public AthenaTimer(long timer_state) {
        t_starter = timer_state;
        tick = (int)(System.currentTimeMillis()-timer_state);
        isPlaying = true;
    }

    public int get() {
        return (isPlaying? (int)(System.currentTimeMillis()-tick) : tick);
    }

    public int set(int val) {
        return (isPlaying? (int)((System.currentTimeMillis()-t_starter)+val) : val);
    }

    public void pause() {
        if (!isPlaying) 
            return;

        tick = (int)(System.currentTimeMillis()-tick);
        isPlaying = false;
    }

    public void resume() {
        if (isPlaying) 
            return;

        tick = (int)(System.currentTimeMillis()-tick);
        isPlaying = true;
    }

    public void reset() {
        tick = (isPlaying? (int)(System.currentTimeMillis()-t_starter) : 0);
    }

    public boolean playing() {
        return isPlaying;
    }
}
