package net.cnjm.j2me.tinybro;

import java.io.*;

import javax.microedition.io.*;

/**
 * TCP/UDP sockets. {@link #SOCK_RAW} is not supported on MIDP.
 */
public final class AthenaSocket {

    public static final int AF_INET = 2;
    public static final int SOCK_STREAM = 1;
    public static final int SOCK_DGRAM = 2;
    public static final int SOCK_RAW = 3;

    private final int domain;
    private final int type;

    private SocketConnection tcpConn;
    private InputStream tcpIn;
    private OutputStream tcpOut;

    private ServerSocketConnection tcpServer;

    private DatagramConnection dgramConn;
    private String lastDatagramAddress;

    public AthenaSocket(int domain, int type) {
        this.domain = domain;
        this.type = type;
    }

    public void connect(String host, int port) throws IOException {
        if (type == SOCK_RAW) {
            throw new IOException("SOCK_RAW not supported on J2ME");
        }
        closeQuiet();
        if (type == SOCK_STREAM) {
            String h = host == null ? "localhost" : host;
            tcpConn = (SocketConnection) Connector.open("socket://" + h + ":" + port);
            tcpIn = tcpConn.openInputStream();
            tcpOut = tcpConn.openOutputStream();
        } else if (type == SOCK_DGRAM) {
            String h = host == null ? "localhost" : host;
            dgramConn = (DatagramConnection) Connector.open("datagram://" + h + ":" + port);
        }
    }

    public void bind(String host, int port) throws IOException {
        if (type == SOCK_RAW) {
            throw new IOException("SOCK_RAW not supported on J2ME");
        }
        closeQuiet();
        if (type == SOCK_STREAM) {
            tcpServer = (ServerSocketConnection) Connector.open("serversocket://:" + port);
        } else if (type == SOCK_DGRAM) {
            dgramConn = (DatagramConnection) Connector.open("datagram://:" + port);
        }
    }

    /** MIDP {@link ServerSocketConnection} has no {@code listen()}; binding is listening once opened. */
    public void listen() throws IOException {
        if (tcpServer == null) {
            throw new IOException("not a listening TCP socket");
        }
    }

    public AthenaSocket accept() throws IOException {
        if (tcpServer == null) {
            throw new IOException("not a listening TCP socket");
        }
        SocketConnection sc = (SocketConnection) tcpServer.acceptAndOpen();
        AthenaSocket ch = new AthenaSocket(AF_INET, SOCK_STREAM);
        ch.tcpConn = sc;
        ch.tcpIn = sc.openInputStream();
        ch.tcpOut = sc.openOutputStream();
        return ch;
    }

    public int send(byte[] data, int off, int len) throws IOException {
        if (data == null || len <= 0) return 0;
        if (type == SOCK_STREAM) {
            if (tcpOut == null) throw new IOException("not connected");
            tcpOut.write(data, off, len);
            tcpOut.flush();
            return len;
        }
        if (type == SOCK_DGRAM) {
            if (dgramConn == null) throw new IOException("datagram not open");
            Datagram d = dgramConn.newDatagram(len + 64);
            d.setData(data, off, len);
            if (lastDatagramAddress != null) {
                d.setAddress(lastDatagramAddress);
            }
            dgramConn.send(d);
            return len;
        }
        throw new IOException("bad socket type");
    }

    public int recv(byte[] buf, int off, int maxLen) throws IOException {
        if (buf == null || maxLen <= 0) return 0;
        if (type == SOCK_STREAM) {
            if (tcpIn == null) throw new IOException("not connected");
            return tcpIn.read(buf, off, maxLen);
        }
        if (type == SOCK_DGRAM) {
            if (dgramConn == null) throw new IOException("datagram not open");
            int cap = maxLen < 256 ? 256 : (maxLen > 65496 ? 65496 : maxLen);
            Datagram d = dgramConn.newDatagram(cap);
            dgramConn.receive(d);
            int n = d.getLength();
            if (n > maxLen) n = maxLen;
            System.arraycopy(d.getData(), d.getOffset(), buf, off, n);
            lastDatagramAddress = d.getAddress();
            return n;
        }
        throw new IOException("bad socket type");
    }

    public void close() {
        closeQuiet();
    }

    private void closeQuiet() {
        if (tcpIn != null) try { tcpIn.close(); } catch (IOException ignored) {}
        tcpIn = null;
        if (tcpOut != null) try { tcpOut.close(); } catch (IOException ignored) {}
        tcpOut = null;
        if (tcpConn != null) try { tcpConn.close(); } catch (IOException ignored) {}
        tcpConn = null;
        if (tcpServer != null) try { tcpServer.close(); } catch (IOException ignored) {}
        tcpServer = null;
        if (dgramConn != null) try { dgramConn.close(); } catch (IOException ignored) {}
        dgramConn = null;
        lastDatagramAddress = null;
    }
}
