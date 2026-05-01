package net.cnjm.j2me.tinybro;

import java.io.*;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.microedition.io.*;

/**
 * Minimal WebSocket client ({@code ws://} only). {@code wss://} requires TLS not wired here.
 */
public final class AthenaWebSocket {

    private SocketConnection conn;
    private InputStream in;
    private OutputStream out;
    private boolean open;
    private String lastError = "";

    private final byte[] wsHdr2 = new byte[2];
    private final byte[] wsExt2 = new byte[2];
    private final byte[] wsExt8 = new byte[8];
    private final byte[] wsMaskKey = new byte[4];
    private byte[] wsPayloadScratch;
    private byte[] wsFrameScratch;

    public AthenaWebSocket(String url) throws IOException {
        final String[] host = new String[1];
        final int[] port = new int[1];
        final StringBuffer path = new StringBuffer();
        parseWsUrl(url, host, port, path);
        conn = (SocketConnection) Connector.open("socket://" + host[0] + ":" + port[0]);
        in = conn.openInputStream();
        out = conn.openOutputStream();
        doHandshake(host[0], port[0], path.toString(), out, in);
        open = true;
    }

    private static void parseWsUrl(String url, String[] outHost, int[] outPort, StringBuffer outPath) throws IOException {
        if (url == null || !url.startsWith("ws://")) {
            throw new IOException("only ws:// is supported (wss:// needs TLS)");
        }
        int slash = url.indexOf('/', 5);
        String hostPort = slash < 0 ? url.substring(5) : url.substring(5, slash);
        outPath.setLength(0);
        outPath.append(slash >= 0 ? url.substring(slash) : "/");
        int colon = hostPort.indexOf(':');
        if (colon < 0) {
            outHost[0] = hostPort.length() > 0 ? hostPort : "localhost";
            outPort[0] = 80;
        } else {
            outHost[0] = hostPort.substring(0, colon);
            if (outHost[0].length() == 0) {
                outHost[0] = "localhost";
            }
            try {
                outPort[0] = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IOException("bad port");
            }
        }
    }

    private static void readFully(InputStream is, byte[] b, int off, int n) throws IOException {
        int r = 0;
        while (r < n) {
            int k = is.read(b, off + r, n - r);
            if (k < 0) {
                throw new IOException("unexpected EOF");
            }
            r += k;
        }
    }

    private static void doHandshake(String host, int port, String path,
            OutputStream os, InputStream is) throws IOException {
        byte[] keyRand = new byte[16];
        for (int i = 0; i < 16; i++) {
            keyRand[i] = (byte) ((System.currentTimeMillis() ^ (i * 31) ^ Runtime.getRuntime().freeMemory()) & 0xff);
        }
        String key = Base64Codec.encode(keyRand);
        String hostHdr = port == 80 ? host : (host + ":" + port);
        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHdr + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n";
        byte[] rb = req.getBytes("ISO8859-1");
        os.write(rb);
        os.flush();

        ByteArrayOutputStream hdr = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        while (hdr.size() < 32768) {
            int n = is.read(buf);
            if (n < 0) {
                throw new IOException("bad handshake response");
            }
            hdr.write(buf, 0, n);
            if (indexOfCrLfCrLf(hdr.toByteArray()) >= 0) {
                break;
            }
        }
        String head = new String(hdr.toByteArray(), "ISO8859-1");
        int lineEnd = head.indexOf('\r');
        String first = lineEnd > 0 ? head.substring(0, lineEnd) : head;
        if (first.indexOf("101") < 0) {
            throw new IOException("expected 101: " + first);
        }
        String acceptHdr = null;
        int p = 0;
        while (p < head.length()) {
            int nl = head.indexOf('\n', p);
            if (nl < 0) break;
            String line = head.substring(p, nl).trim();
            p = nl + 1;
            if (line.length() == 0) break;
            if (line.regionMatches(true, 0, "Sec-WebSocket-Accept:", 0, 21)) {
                acceptHdr = line.substring(21).trim();
            }
        }
        if (acceptHdr != null) {
            try {
                String expect = computeAccept(key);
                if (!expect.equals(acceptHdr)) {
                    throw new IOException("Sec-WebSocket-Accept mismatch");
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static int indexOfCrLfCrLf(byte[] a) {
        for (int i = 0; i + 3 < a.length; i++) {
            if (a[i] == '\r' && a[i + 1] == '\n' && a[i + 2] == '\r' && a[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static String computeAccept(String key) throws IOException {
        try {
            String mag = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] magBytes = mag.getBytes("ISO8859-1");
            md.update(magBytes, 0, magBytes.length);
            byte[] dig = new byte[20];
            md.digest(dig, 0, dig.length);
            return Base64Codec.encode(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 N/A");
        } catch (DigestException e) {
            throw new IOException("SHA-1 digest failed");
        }
    }

    private byte[] ensureFrameBuf(int need) {
        if (wsFrameScratch == null || wsFrameScratch.length < need) {
            int n = wsFrameScratch == null ? need : wsFrameScratch.length * 2;
            while (n < need) {
                n *= 2;
            }
            wsFrameScratch = new byte[n];
        }
        return wsFrameScratch;
    }

    private byte[] ensurePayloadBuf(int need) {
        if (wsPayloadScratch == null || wsPayloadScratch.length < need) {
            int n = wsPayloadScratch == null ? need : wsPayloadScratch.length * 2;
            while (n < need) {
                n *= 2;
            }
            wsPayloadScratch = new byte[n];
        }
        return wsPayloadScratch;
    }

    public void sendBinary(byte[] data, int off, int len) throws IOException {
        if (!open) throw new IOException("closed");
        if (data == null || len <= 0) return;
        int headerLen;
        if (len < 126) {
            headerLen = 6;
        } else if (len <= 65535) {
            headerLen = 8;
        } else {
            throw new IOException("frame too large");
        }
        int frameTotal = headerLen + len + 4;
        byte[] frame = ensureFrameBuf(frameTotal);
        int i = 0;
        frame[i++] = (byte) 0x82;
        if (len < 126) {
            frame[i++] = (byte) (0x80 | len);
        } else {
            frame[i++] = (byte) (0x80 | 126);
            frame[i++] = (byte) (len >> 8);
            frame[i++] = (byte) (len & 0xff);
        }
        for (int m = 0; m < 4; m++) {
            wsMaskKey[m] = (byte) ((System.currentTimeMillis() >> (m * 8)) & 0xff);
        }
        System.arraycopy(wsMaskKey, 0, frame, i, 4);
        i += 4;
        for (int j = 0; j < len; j++) {
            frame[i + j] = (byte) (data[off + j] ^ wsMaskKey[j & 3]);
        }
        out.write(frame, 0, i + len);
        out.flush();
    }

    public byte[] recvFrame() throws IOException {
        if (!open) throw new IOException("closed");
        while (true) {
            readFully(in, wsHdr2, 0, 2);
            int b0 = wsHdr2[0] & 0xff;
            int b1 = wsHdr2[1] & 0xff;
            int opcode = b0 & 0x0f;
            boolean masked = (b1 & 0x80) != 0;
            long plen = b1 & 0x7f;
            if (plen == 126) {
                readFully(in, wsExt2, 0, 2);
                plen = ((wsExt2[0] & 0xff) << 8) | (wsExt2[1] & 0xff);
            } else if (plen == 127) {
                readFully(in, wsExt8, 0, 8);
                plen = 0;
                for (int k = 0; k < 8; k++) {
                    plen = (plen << 8) | (wsExt8[k] & 0xff);
                }
                if (plen > Integer.MAX_VALUE) {
                    throw new IOException("frame too large");
                }
            }
            int payloadLen = (int) plen;
            if (masked) {
                readFully(in, wsMaskKey, 0, 4);
            }
            byte[] payload = ensurePayloadBuf(payloadLen);
            if (payloadLen > 0) {
                readFully(in, payload, 0, payloadLen);
            }
            if (masked) {
                for (int j = 0; j < payloadLen; j++) {
                    payload[j] ^= wsMaskKey[j & 3];
                }
            }
            if (opcode == 0x8) {
                close();
                return null;
            }
            if (opcode == 0x9) {
                byte[] pongCopy = payloadLen <= 0 ? new byte[0] : new byte[payloadLen];
                if (payloadLen > 0) {
                    System.arraycopy(payload, 0, pongCopy, 0, payloadLen);
                }
                sendPong(pongCopy);
                continue;
            }
            if (opcode == 0xA) {
                continue;
            }
            if (opcode == 0x1 || opcode == 0x2) {
                if (payloadLen <= 0) {
                    return new byte[0];
                }
                byte[] out = new byte[payloadLen];
                System.arraycopy(payload, 0, out, 0, payloadLen);
                return out;
            }
        }
    }

    private void sendPong(byte[] pingPayload) throws IOException {
        if (!open) return;
        int len = pingPayload != null ? pingPayload.length : 0;
        int headerLen = len < 126 ? 6 : 8;
        int frameTotal = headerLen + len + 4;
        byte[] frame = ensureFrameBuf(frameTotal);
        int i = 0;
        frame[i++] = (byte) 0x8A;
        if (len < 126) {
            frame[i++] = (byte) (0x80 | len);
        } else {
            frame[i++] = (byte) (0x80 | 126);
            frame[i++] = (byte) (len >> 8);
            frame[i++] = (byte) (len & 0xff);
        }
        for (int m = 0; m < 4; m++) {
            wsMaskKey[m] = (byte) ((System.currentTimeMillis() >> (m * 8)) & 0xff);
        }
        System.arraycopy(wsMaskKey, 0, frame, i, 4);
        i += 4;
        if (len > 0) {
            for (int j = 0; j < len; j++) {
                frame[i + j] = (byte) (pingPayload[j] ^ wsMaskKey[j & 3]);
            }
        }
        out.write(frame, 0, i + len);
        out.flush();
    }

    public void close() {
        open = false;
        if (in != null) try { in.close(); } catch (IOException ignored) {}
        in = null;
        if (out != null) try { out.close(); } catch (IOException ignored) {}
        out = null;
        if (conn != null) try { conn.close(); } catch (IOException ignored) {}
        conn = null;
    }

    public boolean isOpen() {
        return open;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String e) {
        lastError = e != null ? e : "";
    }
}
