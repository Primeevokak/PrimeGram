package org.telegram.messenger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * PrimeGram: one HTTPS request to an address we chose ourselves.
 *
 * <p>Written for services that refuse whole countries. A resolver that answers with its own
 * gateway makes them reachable again, but only if the connection actually goes to the address
 * that resolver gave - and on Android there is no way to tell {@code HttpURLConnection} where to
 * connect. It resolves the host itself, through the system, before any code of ours is consulted.
 *
 * <p>So the socket is made here. The address comes from the caller; the TLS handshake still
 * announces the real host name, both as SNI - which is how such gateways know where to forward -
 * and to the certificate check, which is performed against that name and not against the address.
 * The gateway sees an encrypted stream addressed to somebody else, exactly as it should.
 *
 * <p>Deliberately small: one POST, a body the caller writes, the response as a string. Anything
 * that needs redirects, keep-alive or cookies wants a real client, not this.
 */
public final class PrimeDirectHttps {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    /** A refusal or an error page; nothing this is used for returns more. */
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    public interface BodyWriter {
        void write(OutputStream out) throws Exception;
    }

    public static final class Response {
        public int code;
        public String body = "";
    }

    private PrimeDirectHttps() {
    }

    public static Response post(URL url, InetAddress address, Map<String, String> headers,
                                long contentLength, BodyWriter writer) throws Exception {
        final String host = url.getHost();
        final int port = url.getPort() > 0 ? url.getPort() : 443;
        String path = url.getFile();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        Socket raw = null;
        SSLSocket socket = null;
        try {
            raw = new Socket();
            raw.setTcpNoDelay(true);
            raw.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            raw.setSoTimeout(READ_TIMEOUT_MS);

            socket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(raw, host, port, true);
            final SSLParameters params = socket.getSSLParameters();
            final List<SNIServerName> names = new ArrayList<>();
            names.add(new SNIHostName(host));
            params.setServerNames(names);
            socket.setSSLParameters(params);
            socket.startHandshake();

            // Against the name, never the address. Skipping this is how a redirected connection
            // stops being encrypted in any sense that matters.
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.getSession())) {
                throw new java.io.IOException("Сертификат не соответствует " + host);
            }

            final OutputStream out = socket.getOutputStream();
            final StringBuilder request = new StringBuilder();
            request.append("POST ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(host).append("\r\n");
            // No keep-alive: the response then ends at end of stream and there is no third way
            // for the body to be framed.
            request.append("Connection: close\r\n");
            request.append("Content-Length: ").append(contentLength).append("\r\n");
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    request.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
                }
            }
            request.append("\r\n");
            out.write(request.toString().getBytes("UTF-8"));
            writer.write(out);
            out.flush();

            return readResponse(socket.getInputStream());
        } finally {
            closeQuietly(socket);
            closeQuietly(raw);
        }
    }

    private static Response readResponse(InputStream in) throws Exception {
        final Response response = new Response();
        final String statusLine = readLine(in);
        if (statusLine == null) {
            throw new java.io.IOException("Пустой ответ");
        }
        final String[] parts = statusLine.split(" ");
        response.code = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;

        final Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            final int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }

        final String encoding = headers.get("transfer-encoding");
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            readChunked(in, body);
        } else {
            final byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                body.write(buffer, 0, read);
                if (body.size() > MAX_BODY_BYTES) {
                    break;
                }
            }
        }
        response.body = body.toString("UTF-8");
        return response;
    }

    private static void readChunked(InputStream in, ByteArrayOutputStream body) throws Exception {
        while (true) {
            final String header = readLine(in);
            if (header == null) {
                return;
            }
            final int semicolon = header.indexOf(';');
            final String size = (semicolon > 0 ? header.substring(0, semicolon) : header).trim();
            if (size.isEmpty()) {
                continue;
            }
            final int length = Integer.parseInt(size, 16);
            if (length == 0) {
                return;
            }
            final byte[] chunk = new byte[length];
            int filled = 0;
            while (filled < length) {
                final int read = in.read(chunk, filled, length - filled);
                if (read < 0) {
                    return;
                }
                filled += read;
            }
            body.write(chunk, 0, filled);
            if (body.size() > MAX_BODY_BYTES) {
                return;
            }
            readLine(in); // the CRLF that closes the chunk
        }
    }

    /** Reads one CRLF-terminated line without buffering past it - the body follows immediately. */
    private static String readLine(InputStream in) throws Exception {
        final ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) >= 0) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                line.write(value);
            }
            if (line.size() > 8192) {
                break;
            }
        }
        if (value < 0 && line.size() == 0) {
            return null;
        }
        return line.toString("UTF-8");
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
