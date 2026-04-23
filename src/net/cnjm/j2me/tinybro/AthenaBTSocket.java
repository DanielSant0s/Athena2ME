package net.cnjm.j2me.tinybro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

/**
 * RFCOMM / SPP client over {@code btspp://} URLs via {@link Connector#open}.
 */
public final class AthenaBTSocket {

    private StreamConnection conn;
    private InputStream in;
    private OutputStream out;

    public AthenaBTSocket() {
    }

    public void connect(String url) throws IOException {
        closeQuiet();
        if (url == null || url.length() == 0) {
            throw new IOException("btspp url required");
        }
        conn = (StreamConnection) Connector.open(url);
        in = conn.openInputStream();
        out = conn.openOutputStream();
    }

    public int send(byte[] data, int off, int len) throws IOException {
        if (data == null || len <= 0) {
            return 0;
        }
        if (out == null) {
            throw new IOException("not connected");
        }
        out.write(data, off, len);
        out.flush();
        return len;
    }

    public int recv(byte[] buf, int off, int maxLen) throws IOException {
        if (buf == null || maxLen <= 0) {
            return 0;
        }
        if (in == null) {
            throw new IOException("not connected");
        }
        return in.read(buf, off, maxLen);
    }

    public void close() {
        closeQuiet();
    }

    private void closeQuiet() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            in = null;
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
            out = null;
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException ignored) {
            }
            conn = null;
        }
    }

    /**
     * Opens {@code btspp} on a worker thread; fulfills with {@code self} after
     * {@code self.opaque} is set.
     */
    public static Rv connectPromise(final RocksInterpreter ri, final Rv self, final String url) {
        if (ri == null) {
            return PromiseRuntime.rejected(null, Rv.error("no interpreter"));
        }
        if (self == null) {
            return PromiseRuntime.rejected(ri, Rv.error("bad BTSocket"));
        }
        if (self.opaque != null) {
            return PromiseRuntime.rejected(ri, Rv.error("BTSocket already connected"));
        }
        final Rv promise = PromiseRuntime.createPending(ri);
        AthenaBluetooth.btOpBegin();
        new Thread(new Runnable() {
            public void run() {
                final AthenaBTSocket sock = new AthenaBTSocket();
                try {
                    sock.connect(url);
                    PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                        public void run(RocksInterpreter ri2) {
                            RocksInterpreter use = ri2 != null ? ri2 : ri;
                            self.opaque = sock;
                            AthenaBluetooth.btOpEnd();
                            PromiseRuntime.fulfill(use, promise, self);
                        }
                    });
                } catch (Throwable t) {
                    final String msg = t.getMessage() != null ? t.getMessage() : "btspp connect failed";
                    PromiseRuntime.enqueue(new PromiseRuntime.Microtask() {
                        public void run(RocksInterpreter ri2) {
                            RocksInterpreter use = ri2 != null ? ri2 : ri;
                            AthenaBluetooth.btOpEnd();
                            PromiseRuntime.reject(use, promise, Rv.error(msg));
                        }
                    });
                }
            }
        }).start();
        return promise;
    }
}
