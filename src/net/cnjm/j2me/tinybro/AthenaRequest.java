package net.cnjm.j2me.tinybro;

import java.io.*;

import javax.microedition.io.*;
import javax.microedition.io.file.*;

/**
 * HTTP/HTTPS client: worker thread completes I/O; results settle a {@link PromiseRuntime} Promise on the JS thread.
 */
public final class AthenaRequest {

    private static volatile int httpInFlight;

    private volatile boolean busy;
    private Thread worker;

    private static final int MAX_BODY = 512 * 1024;

    public AthenaRequest() {
    }

    public static int getHttpInFlight() {
        return httpInFlight;
    }

    private static void incHttpInFlight() {
        synchronized (AthenaRequest.class) {
            httpInFlight++;
        }
    }

    private static void decHttpInFlight() {
        synchronized (AthenaRequest.class) {
            httpInFlight--;
        }
    }

    private static String strProp(Rv o, String key, String def) {
        if (o == null) {
            return def;
        }
        Rv v = o.get(key);
        if (v == null || v == Rv._undefined) {
            return def;
        }
        return v.toStr().str;
    }

    private static void applyUserHeaders(Rv self, HttpConnection hc) throws IOException {
        Rv hdr = self.get("headers");
        if (hdr == null || hdr.type != Rv.ARRAY) {
            return;
        }
        int n = hdr.num;
        for (int i = 0; i + 1 < n; i += 2) {
            Rv k = hdr.get(Rv.intStr(i));
            Rv val = hdr.get(Rv.intStr(i + 1));
            if (k == null || k == Rv._undefined || val == null || val == Rv._undefined) {
                continue;
            }
            String ks = k.toStr().str;
            String vs = val.toStr().str;
            if (ks.length() > 0) {
                hc.setRequestProperty(ks, vs);
            }
        }
    }

    private static void applyAuth(Rv self, HttpConnection hc) throws IOException {
        String up = strProp(self, "userpwd", "");
        if (up.length() == 0) {
            return;
        }
        hc.setRequestProperty("Authorization", "Basic " + Base64Codec.encode(up.getBytes("UTF-8")));
    }

    private static byte[] readAllLimited(InputStream is, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int total = 0;
        int n;
        while ((n = is.read(buf)) > 0) {
            total += n;
            if (total > max) {
                throw new IOException("response too large");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static void applyKeepalive(Rv self, HttpConnection hc) {
        Rv v = self.get("keepalive");
        if (v == null || v == Rv._undefined) {
            return;
        }
        Rv n = v.toNum();
        if (n == Rv._NaN || Rv.numValue(n) == 0) {
            return;
        }
        try {
            hc.setRequestProperty("Connection", "keep-alive");
        } catch (Throwable ignored) {
        }
    }

    /**
     * GET: fulfilled value {@code { responseCode, error, contentLength, body }} ({@code body} is {@code Uint8Array}).
     */
    public synchronized Rv getPromise(final RocksInterpreter ri, final Rv self, final String url) {
        if (busy || ri == null) {
            return PromiseRuntime.rejected(ri, Rv.error(busy ? "Request busy" : "no interpreter"));
        }
        busy = true;
        final Rv promise = PromiseRuntime.createPending(ri);
        incHttpInFlight();
        worker = new Thread(new Runnable() {
            public void run() {
                int code = -1;
                String err = "";
                byte[] body = null;
                int contentLength = 0;
                try {
                    HttpConnection hc = null;
                    InputStream ins = null;
                    try {
                        hc = (HttpConnection) Connector.open(url);
                        hc.setRequestMethod(HttpConnection.GET);
                        String ua = strProp(self, "useragent", "");
                        if (ua.length() > 0) {
                            hc.setRequestProperty("User-Agent", ua);
                        }
                        applyKeepalive(self, hc);
                        applyAuth(self, hc);
                        applyUserHeaders(self, hc);
                        code = hc.getResponseCode();
                        ins = hc.openInputStream();
                        body = readAllLimited(ins, MAX_BODY);
                        contentLength = body != null ? body.length : 0;
                    } finally {
                        if (ins != null) {
                            try {
                                ins.close();
                            } catch (IOException ignored) {
                            }
                        }
                        if (hc != null) {
                            try {
                                hc.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    code = -1;
                    err = t.getMessage() != null ? t.getMessage() : "error";
                    body = null;
                    contentLength = 0;
                }
                final int fCode = code;
                final String fErr = err;
                final byte[] fBody = body;
                final int fLen = contentLength;
                PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                    public void run(RocksInterpreter ri2) {
                        RocksInterpreter use = ri2 != null ? ri2 : ri;
                        try {
                            if (fCode < 0) {
                                PromiseRuntime.reject(use, promise, Rv.error(fErr.length() > 0 ? fErr : "HTTP error"));
                            } else {
                                Rv o = StdLib.newObject();
                                o.putl("responseCode", new Rv(fCode));
                                o.putl("error", new Rv(fErr));
                                o.putl("contentLength", new Rv(fLen));
                                o.putl("body", PromiseRuntime.newUint8Array(use, fBody != null ? fBody : new byte[0]));
                                syncPropsToSelf(self, fCode, fErr, fLen);
                                PromiseRuntime.fulfill(use, promise, o);
                            }
                        } finally {
                            synchronized (AthenaRequest.this) {
                                busy = false;
                            }
                            decHttpInFlight();
                        }
                    }
                });
            }
        });
        worker.start();
        return promise;
    }

    /**
     * POST: same shape as GET; {@code postBody} may be {@code null}.
     */
    public synchronized Rv postPromise(final RocksInterpreter ri, final Rv self, final String url, final byte[] postBody) {
        if (busy || ri == null) {
            return PromiseRuntime.rejected(ri, Rv.error(busy ? "Request busy" : "no interpreter"));
        }
        busy = true;
        final Rv promise = PromiseRuntime.createPending(ri);
        incHttpInFlight();
        worker = new Thread(new Runnable() {
            public void run() {
                int code = -1;
                String err = "";
                byte[] body = null;
                int contentLength = 0;
                try {
                    HttpConnection hc = null;
                    InputStream ins = null;
                    OutputStream outs = null;
                    try {
                        hc = (HttpConnection) Connector.open(url);
                        hc.setRequestMethod(HttpConnection.POST);
                        String ua = strProp(self, "useragent", "");
                        if (ua.length() > 0) {
                            hc.setRequestProperty("User-Agent", ua);
                        }
                        applyKeepalive(self, hc);
                        applyAuth(self, hc);
                        applyUserHeaders(self, hc);
                        byte[] pb = postBody;
                        if (pb != null && pb.length > 0) {
                            hc.setRequestProperty("Content-Length", String.valueOf(pb.length));
                            outs = hc.openOutputStream();
                            outs.write(pb);
                            outs.flush();
                            outs.close();
                            outs = null;
                        }
                        code = hc.getResponseCode();
                        ins = hc.openInputStream();
                        body = readAllLimited(ins, MAX_BODY);
                        contentLength = body != null ? body.length : 0;
                    } finally {
                        if (outs != null) {
                            try {
                                outs.close();
                            } catch (IOException ignored) {
                            }
                        }
                        if (ins != null) {
                            try {
                                ins.close();
                            } catch (IOException ignored) {
                            }
                        }
                        if (hc != null) {
                            try {
                                hc.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    code = -1;
                    err = t.getMessage() != null ? t.getMessage() : "error";
                    body = null;
                    contentLength = 0;
                }
                final int fCode = code;
                final String fErr = err;
                final byte[] fBody = body;
                final int fLen = contentLength;
                PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                    public void run(RocksInterpreter ri2) {
                        RocksInterpreter use = ri2 != null ? ri2 : ri;
                        try {
                            if (fCode < 0) {
                                PromiseRuntime.reject(use, promise, Rv.error(fErr.length() > 0 ? fErr : "HTTP error"));
                            } else {
                                Rv o = StdLib.newObject();
                                o.putl("responseCode", new Rv(fCode));
                                o.putl("error", new Rv(fErr));
                                o.putl("contentLength", new Rv(fLen));
                                o.putl("body", PromiseRuntime.newUint8Array(use, fBody != null ? fBody : new byte[0]));
                                syncPropsToSelf(self, fCode, fErr, fLen);
                                PromiseRuntime.fulfill(use, promise, o);
                            }
                        } finally {
                            synchronized (AthenaRequest.this) {
                                busy = false;
                            }
                            decHttpInFlight();
                        }
                    }
                });
            }
        });
        worker.start();
        return promise;
    }

    /**
     * Download to file: fulfilled value {@code { responseCode, error, contentLength, fileUrl }} (body not returned).
     */
    public synchronized Rv downloadPromise(final RocksInterpreter ri, final Rv self, final String url, final String fileUrl) {
        if (busy || ri == null) {
            return PromiseRuntime.rejected(ri, Rv.error(busy ? "Request busy" : "no interpreter"));
        }
        busy = true;
        final Rv promise = PromiseRuntime.createPending(ri);
        incHttpInFlight();
        worker = new Thread(new Runnable() {
            public void run() {
                int code = -1;
                String err = "";
                int contentLength = 0;
                try {
                    HttpConnection hc = null;
                    InputStream ins = null;
                    try {
                        hc = (HttpConnection) Connector.open(url);
                        hc.setRequestMethod(HttpConnection.GET);
                        String ua = strProp(self, "useragent", "");
                        if (ua.length() > 0) {
                            hc.setRequestProperty("User-Agent", ua);
                        }
                        applyKeepalive(self, hc);
                        applyAuth(self, hc);
                        applyUserHeaders(self, hc);
                        code = hc.getResponseCode();
                        ins = hc.openInputStream();
                        byte[] body = readAllLimited(ins, MAX_BODY);
                        contentLength = body != null ? body.length : 0;
                        FileConnection fc = null;
                        OutputStream os = null;
                        try {
                            fc = (FileConnection) Connector.open(fileUrl, Connector.READ_WRITE);
                            if (!fc.exists()) {
                                fc.create();
                            }
                            os = fc.openOutputStream();
                            os.write(body);
                            os.flush();
                        } finally {
                            if (os != null) {
                                try {
                                    os.close();
                                } catch (IOException ignored) {
                                }
                            }
                            if (fc != null) {
                                try {
                                    fc.close();
                                } catch (IOException ignored) {
                                }
                            }
                        }
                    } finally {
                        if (ins != null) {
                            try {
                                ins.close();
                            } catch (IOException ignored) {
                            }
                        }
                        if (hc != null) {
                            try {
                                hc.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    code = -1;
                    err = t.getMessage() != null ? t.getMessage() : "error";
                    contentLength = 0;
                }
                final int fCode = code;
                final String fErr = err;
                final int fLen = contentLength;
                final String fFile = fileUrl;
                PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                    public void run(RocksInterpreter ri2) {
                        RocksInterpreter use = ri2 != null ? ri2 : ri;
                        try {
                            if (fCode < 0) {
                                PromiseRuntime.reject(use, promise, Rv.error(fErr.length() > 0 ? fErr : "HTTP error"));
                            } else {
                                Rv o = StdLib.newObject();
                                o.putl("responseCode", new Rv(fCode));
                                o.putl("error", new Rv(fErr));
                                o.putl("contentLength", new Rv(fLen));
                                o.putl("fileUrl", new Rv(fFile != null ? fFile : ""));
                                syncPropsToSelf(self, fCode, fErr, fLen);
                                PromiseRuntime.fulfill(use, promise, o);
                            }
                        } finally {
                            synchronized (AthenaRequest.this) {
                                busy = false;
                            }
                            decHttpInFlight();
                        }
                    }
                });
            }
        });
        worker.start();
        return promise;
    }

    private static void syncPropsToSelf(Rv self, int code, String err, int contentLength) {
        self.putl("responseCode", new Rv(code));
        self.putl("error", new Rv(err != null ? err : ""));
        self.putl("contentLength", new Rv(contentLength));
    }
}

/** Minimal Base64 (no line breaks) for Basic auth and WebSocket key. */
final class Base64Codec {
    private static final char[] T = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xff;
            int b1 = i < data.length ? data[i++] & 0xff : -1;
            int b2 = i < data.length ? data[i++] & 0xff : -1;
            sb.append(T[b0 >> 2]);
            if (b1 >= 0) {
                sb.append(T[((b0 << 4) & 0x3f) | (b1 >> 4)]);
                if (b2 >= 0) {
                    sb.append(T[((b1 << 2) & 0x3f) | (b2 >> 6)]);
                    sb.append(T[b2 & 0x3f]);
                } else {
                    sb.append(T[(b1 << 2) & 0x3f]);
                    sb.append('=');
                }
            } else {
                sb.append(T[(b0 << 4) & 0x3f]);
                sb.append("==");
            }
        }
        return sb.toString();
    }
}
