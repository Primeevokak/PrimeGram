package org.telegram.messenger.browser;

import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PrimeGram: a local HTTP proxy so the browser's WebView really uses our DNS.
 *
 * <p>The DoH resolver covers everything the client itself looks up, but WebView does its own
 * name resolution inside the system stack, where nothing we set can reach. Pointing WebView at a
 * proxy moves the lookup to us: the browser hands over a host name, and we are the ones who turn
 * it into an address.
 *
 * <p>Deliberately not a TLS-intercepting proxy. An {@code HTTP CONNECT} is answered by opening a
 * plain socket to the resolved address and copying bytes in both directions, so the encrypted
 * stream is never touched, no certificate is forged, and nothing about the page's security
 * changes. We learn the host name - which the browser was going to send in SNI anyway - and
 * nothing else.
 */
public class PrimeWebProxy {

    /** 0 lets the system pick a free port; the real one is read back after binding. */
    private static final int ANY_PORT = 0;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int HEADER_LIMIT = 16 * 1024;

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static volatile int port = -1;
    private static ServerSocket serverSocket;
    private static ExecutorService executor;

    /** The port to point WebView at, or -1 when the proxy is not up. */
    public static int getPort() {
        return running.get() ? port : -1;
    }

    /**
     * Starts the proxy if it is not already running. Returns the port, or -1 on failure.
     *
     * <p>Bound to loopback only, so nothing outside this device can reach it.
     */
    public static synchronized int start() {
        if (running.get()) {
            return port;
        }
        try {
            serverSocket = new ServerSocket(ANY_PORT, 50, InetAddress.getByName("127.0.0.1"));
            port = serverSocket.getLocalPort();
            executor = Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "prime-web-proxy");
                thread.setDaemon(true);
                return thread;
            });
            running.set(true);
            executor.submit(PrimeWebProxy::acceptLoop);
            return port;
        } catch (Throwable t) {
            FileLog.e("PrimeWebProxy.start", t);
            stop();
            return -1;
        }
    }

    public static synchronized void stop() {
        running.set(false);
        port = -1;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Throwable ignore) {
        }
        serverSocket = null;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void acceptLoop() {
        while (running.get()) {
            final Socket client;
            try {
                client = serverSocket.accept();
            } catch (Throwable t) {
                if (running.get()) {
                    FileLog.e("PrimeWebProxy.accept", t);
                }
                return;
            }
            final ExecutorService pool = executor;
            if (pool == null) {
                closeQuietly(client);
                return;
            }
            pool.submit(() -> handle(client));
        }
    }

    private static void handle(Socket client) {
        Socket remote = null;
        try {
            client.setTcpNoDelay(true);
            final InputStream in = new BufferedInputStream(client.getInputStream(), 8192);
            final OutputStream out = client.getOutputStream();

            final String requestLine = readLine(in);
            if (requestLine == null) {
                return;
            }
            final String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                return;
            }
            final String method = parts[0];
            final String target = parts[1];

            if ("CONNECT".equalsIgnoreCase(method)) {
                // target is host:port - the ordinary shape of an HTTPS request through a proxy.
                final int colon = target.lastIndexOf(':');
                final String host = colon > 0 ? target.substring(0, colon) : target;
                final int remotePort = colon > 0 ? parseInt(target.substring(colon + 1), 443) : 443;
                drainHeaders(in);
                remote = connect(host, remotePort);
                if (remote == null) {
                    out.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".getBytes("ISO-8859-1"));
                    out.flush();
                    return;
                }
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
                out.flush();
                pipeBoth(client, in, out, remote);
                return;
            }

            // Plain HTTP: the request line carries an absolute URI, which has to become a normal
            // origin-form path before it is forwarded, or the far end will reject it.
            if (!target.regionMatches(true, 0, "http://", 0, 7)) {
                return;
            }
            final int pathStart = target.indexOf('/', 7);
            final String authority = pathStart < 0 ? target.substring(7) : target.substring(7, pathStart);
            final String path = pathStart < 0 ? "/" : target.substring(pathStart);
            final int colon = authority.lastIndexOf(':');
            final String host = colon > 0 ? authority.substring(0, colon) : authority;
            final int remotePort = colon > 0 ? parseInt(authority.substring(colon + 1), 80) : 80;

            remote = connect(host, remotePort);
            if (remote == null) {
                out.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".getBytes("ISO-8859-1"));
                out.flush();
                return;
            }
            final OutputStream remoteOut = remote.getOutputStream();
            final StringBuilder head = new StringBuilder();
            head.append(method).append(' ').append(path).append(' ').append(parts[2]).append("\r\n");
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // Hop-by-hop, and meaningless to the origin server.
                final String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("proxy-connection:")) {
                    continue;
                }
                head.append(line).append("\r\n");
            }
            head.append("\r\n");
            remoteOut.write(head.toString().getBytes("ISO-8859-1"));
            remoteOut.flush();
            pipeBoth(client, in, out, remote);
        } catch (Throwable t) {
            // A browser closing a connection mid-request is completely ordinary; this is only
            // worth a log line, never a crash.
            FileLog.e("PrimeWebProxy.handle", t);
        } finally {
            closeQuietly(client);
            closeQuietly(remote);
        }
    }

    /**
     * Opens a socket to {@code host}, resolving through our DoH resolver when it is enabled.
     *
     * <p>Falls back to the system resolver when ours has nothing to say - a browser that cannot
     * reach a site because our resolver was unreachable would be a worse outcome than a lookup
     * that leaked. A name the resolver deliberately refuses is a different matter and fails here,
     * because that is the whole point of a blocking resolver.
     */
    private static Socket connect(String host, int remotePort) {
        try {
            InetAddress address = null;
            final PrimeDns.Result result = PrimeDns.isEnabled() ? PrimeDns.resolve(host) : null;
            if (result != null) {
                if (result.blocked) {
                    return null;
                }
                if (!result.addresses.isEmpty()) {
                    address = result.addresses.get(0);
                }
            }
            final Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            final InetSocketAddress endpoint = address != null
                    ? new InetSocketAddress(address, remotePort)
                    : new InetSocketAddress(host, remotePort);
            socket.connect(endpoint, CONNECT_TIMEOUT_MS);
            return socket;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void pipeBoth(Socket client, InputStream clientIn, OutputStream clientOut, Socket remote) {
        final ExecutorService pool = executor;
        if (pool == null) {
            return;
        }
        pool.submit(() -> {
            try {
                copy(clientIn, remote.getOutputStream());
            } catch (Throwable ignore) {
            } finally {
                closeQuietly(remote);
                closeQuietly(client);
            }
        });
        try {
            copy(remote.getInputStream(), clientOut);
        } catch (Throwable ignore) {
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        final byte[] buffer = new byte[16384];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            out.flush();
        }
    }

    private static void drainHeaders(InputStream in) throws IOException {
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            // discarded on purpose: a CONNECT carries nothing the tunnel needs
        }
    }

    /** Reads one CRLF-terminated line, bounded so a hostile peer cannot grow it without end. */
    private static String readLine(InputStream in) throws IOException {
        final StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                final int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.setLength(length - 1);
                }
                return line.toString();
            }
            line.append((char) c);
            if (line.length() > HEADER_LIMIT) {
                throw new IOException("header too long");
            }
        }
        return line.length() > 0 ? line.toString() : null;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Throwable ignore) {
            }
        }
    }
}
