package net.cnjm.j2me.tinybro;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.microedition.lcdui.Image;

/**
 * {@link Host} for {@link Page} layout: classpath images, optional solid-color
 * placeholders, and local CSS text. HTTP(S) document loads are handled from JS
 * via {@code Request}; {@link #getResource} returns {@code null} for http(s)
 * URLs so layout does not block the JS thread.
 */
public final class AthenaPageHost implements Host {

    private static final int MAX_RESOURCE_BYTES = 262144;

    private final Object classLoaderLock;
    private final Class midletClass;

    public AthenaPageHost(Object classLoaderLock, Class midletClass) {
        this.classLoaderLock = classLoaderLock;
        this.midletClass = midletClass;
    }

    public final Object getResource(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        if (n.length() == 0) {
            return null;
        }
        if (n.startsWith("http:") || n.startsWith("https:")) {
            return null;
        }
        if (n.startsWith("rgbImage@")) {
            return parseRgbPlaceholder(n);
        }
        String path = n.startsWith("/") ? n : "/" + n;
        InputStream is = null;
        try {
            synchronized (classLoaderLock) {
                is = midletClass.getResourceAsStream(path);
            }
            if (is == null) {
                return null;
            }
            byte[] data = readAllLimited(is, MAX_RESOURCE_BYTES);
            is.close();
            if (data == null || data.length == 0) {
                return null;
            }
            String lower = path.toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif")) {
                try {
                    return Image.createImage(data, 0, data.length);
                } catch (Throwable t) {
                    return null;
                }
            }
            return new String(data, 0, data.length);
        } catch (Throwable t) {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    /**
     * @return {@code true} if the event should proceed (default); subclasses may
     *         return {@code false} to cancel form submit.
     */
    public boolean handleEvent(Node src, int eventId) {
        return true;
    }

    private static Image parseRgbPlaceholder(String spec) {
        try {
            int at = spec.indexOf('@');
            if (at < 0) {
                return null;
            }
            int c1 = spec.indexOf(',', at + 1);
            int c2 = spec.indexOf(',', c1 + 1);
            if (c1 < 0 || c2 < 0) {
                return null;
            }
            int w = Integer.parseInt(spec.substring(at + 1, c1).trim());
            int h = Integer.parseInt(spec.substring(c1 + 1, c2).trim());
            if (w <= 0 || h > 512 || w > 512) {
                return null;
            }
            String hex = spec.substring(c2 + 1).trim();
            if (hex.length() != 8) {
                return null;
            }
            int argb = (int) Long.parseLong(hex, 16);
            int[] rgb = new int[w * h];
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = argb;
            }
            return Image.createRGBImage(rgb, w, h, true);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readAllLimited(InputStream is, int max) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[2048];
        int total = 0;
        while (true) {
            int n = is.read(buf);
            if (n < 0) {
                break;
            }
            total += n;
            if (total > max) {
                throw new java.io.IOException("resource too large");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
