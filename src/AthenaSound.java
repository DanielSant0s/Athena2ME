import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.PitchControl;
import javax.microedition.media.control.VolumeControl;

/**
 * Sound module: BGM stream + short SFX on a channel pool, MMAPI only.
 * Formats: WAV (PCM) for Stream and Sfx; MIDI (Stream only).
 */
public final class AthenaSound {

    public static final int MAX_CHANNELS = 8;

    private static int masterVolume = 100;

    private static final Object channelsLock = new Object();
    private static final Player[] channelPlayer = new Player[MAX_CHANNELS];

    public static final String MIME_WAV = "audio/x-wav";
    public static final String MIME_MIDI = "audio/midi";

    public static int getMasterVolume() {
        return masterVolume;
    }

    public static void setMasterVolume(int v) {
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        masterVolume = v;
    }

    public static int findChannel() {
        synchronized (channelsLock) {
            for (int c = 0; c < MAX_CHANNELS; c++) {
                if (channelPlayer[c] == null) {
                    return c;
                }
                if (!isChannelPlaying(c)) {
                    return c;
                }
            }
        }
        return -1;
    }

    private static boolean isChannelPlaying(int ch) {
        Player p = channelPlayer[ch];
        if (p == null) {
            return false;
        }
        int st = p.getState();
        return st == Player.STARTED;
    }

    public static void shutdown() {
        synchronized (channelsLock) {
            for (int c = 0; c < MAX_CHANNELS; c++) {
                releaseChannel(c);
            }
        }
    }

    private static void releaseChannel(int c) {
        Player p = channelPlayer[c];
        if (p != null) {
            try {
                p.deallocate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                p.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        channelPlayer[c] = null;
    }

    public static byte[] loadResource(String path) {
        if (path == null || path.length() == 0) {
            return null;
        }
        String a = path.charAt(0) == '/' ? path : ("/" + path);
        InputStream in = null;
        try {
            in = "".getClass().getResourceAsStream(a);
            if (in == null) {
                in = "".getClass().getResourceAsStream(path);
            }
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    public static String guessMimeForStreamPath(String path) {
        if (path == null) {
            return MIME_WAV;
        }
        String low = path.toLowerCase();
        if (low.endsWith(".mid") || low.endsWith(".midi")) {
            return MIME_MIDI;
        }
        return MIME_WAV;
    }

    public static boolean isMidiPath(String path) {
        if (path == null) {
            return false;
        }
        String low = path.toLowerCase();
        return low.endsWith(".mid") || low.endsWith(".midi");
    }

    // --- stream ---

    public static final class StreamHandle {
        public Player p;
        public boolean midi;
        public String error;

        public void close() {
            if (p != null) {
                try {
                    p.deallocate();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    p.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                p = null;
            }
        }
    }

    public static StreamHandle createStream(String path) {
        StreamHandle h = new StreamHandle();
        byte[] bytes = loadResource(path);
        if (bytes == null) {
            h.error = "not found";
            return h;
        }
        h.midi = isMidiPath(path);
        String mime = guessMimeForStreamPath(path);
        try {
            h.p = Manager.createPlayer(new ByteArrayInputStream(bytes), mime);
            h.p.realize();
            h.p.prefetch();
        } catch (Exception e) {
            e.printStackTrace();
            h.error = e.getMessage() != null ? e.getMessage() : "err";
            h.close();
        }
        return h;
    }

    public static int streamGetPositionMs(Player p) {
        if (p == null) {
            return 0;
        }
        try {
            return (int) (p.getMediaTime() / 1000L);
        } catch (Exception e) {
            return 0;
        }
    }

    public static int streamGetLengthMs(Player p) {
        if (p == null) {
            return 0;
        }
        try {
            long du = p.getDuration();
            if (du < 0) {
                return 0;
            }
            return (int) (du / 1000L);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void streamSetPositionMs(Player p, int ms) {
        if (p == null) {
            return;
        }
        if (ms < 0) {
            ms = 0;
        }
        try {
            p.setMediaTime((long) ms * 1000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void streamSetLoop(Player p, boolean loop) {
        if (p == null) {
            return;
        }
        try {
            if (loop) {
                p.setLoopCount(-1);
            } else {
                p.setLoopCount(1);
            }
        } catch (Exception e) {
            if (loop) {
                try {
                    p.setLoopCount(2000000000);
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            } else {
                e.printStackTrace();
            }
        }
    }

    public static void applyMasterVolumeToPlayer(Player p) {
        if (p == null) {
            return;
        }
        try {
            VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
            if (vc != null) {
                vc.setLevel(masterVolume);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean streamIsPlaying(Player p) {
        if (p == null) {
            return false;
        }
        try {
            return p.getState() == Player.STARTED;
        } catch (Exception e) {
            return false;
        }
    }

    public static void streamStart(Player p) {
        if (p == null) {
            return;
        }
        try {
            p.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void streamStop(Player p) {
        if (p == null) {
            return;
        }
        try {
            p.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- SFX ---

    public static final class SfxData {
        public String path;
        public byte[] wav;
        public int volume; // 0-100, default 100
        public int pan;    // -100..100
        public int pitch;  // -100..100

        public SfxData() {
            volume = 100;
        }
    }

    public static SfxData loadSfxData(String path) {
        SfxData s = new SfxData();
        s.path = path;
        if (isMidiPath(path)) {
            s.wav = null;
            return s;
        }
        s.wav = loadResource(path);
        return s;
    }

    /**
     * @param channel -1 to pick a free channel; else force 0..MAX_CHANNELS-1
     * @return channel used (0..) or -1 on failure. Caller maps explicit to JS undefined.
     */
    public static int playSfx(SfxData data, int channel, int localVol, int pan, int pitch) {
        if (data == null || data.wav == null) {
            return -1;
        }
        int ch;
        if (channel < 0) {
            ch = findChannel();
            if (ch < 0) {
                return -1;
            }
        } else {
            ch = channel;
            if (ch < 0 || ch >= MAX_CHANNELS) {
                return -1;
            }
        }

        synchronized (channelsLock) {
            releaseChannel(ch);
            try {
                Player p = Manager.createPlayer(new ByteArrayInputStream(data.wav), MIME_WAV);
                p.addPlayerListener(new SfxEndListener(ch));
                p.realize();
                p.prefetch();
                applySfxLevel(p, localVol, pan, pitch);
                p.start();
                channelPlayer[ch] = p;
                return ch;
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }
        }
    }

    private static void applySfxLevel(Player p, int localVol, int pan, int pitch) {
        if (p == null) {
            return;
        }
        int v = (masterVolume * clamp(localVol, 0, 100)) / 100;
        if (v < 0) {
            v = 0;
        }
        if (v > 100) {
            v = 100;
        }
        try {
            VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
            if (vc != null) {
                vc.setLevel(v);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            PitchControl pc = (PitchControl) p.getControl("PitchControl");
            if (pc != null && pitch != 0) {
                int r = (pc.getMaxPitch() - pc.getMinPitch()) / 2;
                int t = (pitch * r) / 100;
                int mid = (pc.getMaxPitch() + pc.getMinPitch()) / 2;
                int np = mid + t;
                if (np < pc.getMinPitch()) {
                    np = pc.getMinPitch();
                } else if (np > pc.getMaxPitch()) {
                    np = pc.getMaxPitch();
                }
                pc.setPitch(np);
            }
        } catch (Exception e) {
        }
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    public static boolean isSfxChannelPlaying(int ch) {
        if (ch < 0 || ch >= MAX_CHANNELS) {
            return false;
        }
        synchronized (channelsLock) {
            return isChannelPlaying(ch);
        }
    }

    public static void freeSfxData(SfxData d) {
        if (d != null) {
            d.wav = null;
            d.path = null;
        }
    }

    private static final class SfxEndListener implements PlayerListener {
        private final int ch;

        SfxEndListener(int channel) {
            this.ch = channel;
        }

        public void playerUpdate(Player p, String event, Object data) {
            if (event != null && event.equals(PlayerListener.END_OF_MEDIA)) {
                synchronized (channelsLock) {
                    if (channelPlayer[ch] == p) {
                        releaseChannel(ch);
                    }
                }
            }
        }
    }
}
