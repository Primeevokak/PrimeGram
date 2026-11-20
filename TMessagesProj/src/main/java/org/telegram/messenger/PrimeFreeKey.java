package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PrimeGram: asking the VPGram service for a free key - rebuilt so that asking costs as little
 * privacy as possible.
 *
 * <p>The earlier version was removed because of how it worked, not that it existed: it sent a device
 * identifier derived from ANDROID_ID, took the service's reply as a whole Xray configuration and ran
 * it as it was (the service chose the local port, the routing, everything), hid the server address
 * from the user, refreshed itself in the background, and first fetched its own list of addresses from
 * three other hosts at every start. Here:
 *
 * <ul>
 * <li><b>Only when the user asks, once per tap.</b> No background requests, no retries, no refresh,
 *     nothing at startup.
 * <li><b>Nothing about the device.</b> The only thing sent is a random identifier made for this one
 *     request and never stored, never derived from anything on the phone. The service accepts any
 *     (checked), so there is no reason to give it a real one.
 * <li><b>One fixed address, over HTTPS, no redirects.</b> Not taken from a remote list that could
 *     point the app elsewhere. A fixed generic User-Agent, no cookies, no other headers.
 * <li><b>The reply is data, not configuration.</b> Only the fields of each server that are needed to
 *     connect are read, each checked against a strict pattern, and rebuilt into an ordinary
 *     {@code vless://} link - the same thing a user could paste, going through the same code. The
 *     service's inbound, routing, DNS and logging sections are discarded, and so is anything that
 *     points at the phone's own network.
 * <li><b>Visible.</b> The result is a normal entry in the servers list: its address can be seen and
 *     it can be deleted. It is never switched on by itself.
 * </ul>
 *
 * <p>What this cannot change: whoever runs the server can see the phone's IP address and which
 * addresses it connects to while a key is in use. The confirmation says so.
 */
public final class PrimeFreeKey {

    /** The service's registration endpoint, the one the old client used. Fixed on purpose. */
    static final String ENDPOINT = "https://vpg.colgonet.top/auth/register";
    /** Shown in the confirmation. */
    public static final String SERVICE_NAME = "VPGram (vpg.colgonet.top)";
    public static final String NAME_PREFIX = "Бесплатный ключ";

    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final int MAX_SERVERS = 12;
    private static final long MIN_INTERVAL_MS = 30_000;

    private static long lastRequestAt;

    public interface Callback {
        /** On the UI thread. {@code error} is null on success; {@code added} is how many servers were added. */
        void onResult(int added, String error);
    }

    private PrimeFreeKey() {
    }

    /** Whether a free key is already in the list - asking again would only pile up more. */
    public static boolean hasFreeKey() {
        for (PrimeVpnServerStore.Server server : PrimeVpnServerStore.getServers()) {
            if (server.name != null && server.name.startsWith(NAME_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    public static void request(Callback callback) {
        final long now = System.currentTimeMillis();
        if (now - lastRequestAt < MIN_INTERVAL_MS) {
            callback.onResult(0, "Подождите немного перед следующей попыткой");
            return;
        }
        lastRequestAt = now;
        Utilities.globalQueue.postRunnable(() -> {
            int added = 0;
            String error = null;
            try {
                final List<String> links = fetchLinks();
                if (links.isEmpty()) {
                    error = "Сервис не вернул подходящих серверов";
                } else {
                    int n = 1;
                    for (String link : links) {
                        PrimeVpnServerStore.addServer(NAME_PREFIX + (links.size() > 1 ? " " + n : ""), link);
                        n++;
                        added++;
                    }
                }
            } catch (Throwable e) {
                // Deliberately not logged with the response: it holds the credentials.
                error = "Не удалось получить ключ. Проверьте соединение и попробуйте ещё раз.";
            }
            final int addedFinal = added;
            final String errorFinal = error;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(addedFinal, errorFinal));
        });
    }

    private static List<String> fetchLinks() throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            // The same string for everyone: nothing here says which app or version this is.
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            final byte[] body = ("{\"deviceId\":\"" + UUID.randomUUID() + "\"}").getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            final int code = connection.getResponseCode();
            if (code < 200 || code > 299) {
                throw new IllegalStateException("http " + code);
            }
            final String text = readCapped(connection.getInputStream());
            return linksFrom(new JSONObject(text));
        } finally {
            connection.disconnect();
        }
    }

    private static String readCapped(InputStream in) throws Exception {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] chunk = new byte[8 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) > 0) {
            total += n;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("response too large");
            }
            buffer.write(chunk, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    // ── The reply, as data ──────────────────────────────────────────────────────────────────

    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HOST_RE = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
    private static final Pattern IPV4_RE = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern PBK_RE = Pattern.compile("^[A-Za-z0-9_-]{20,64}$");
    private static final Pattern SID_RE = Pattern.compile("^[0-9a-fA-F]{0,16}$");
    private static final Pattern FP_RE = Pattern.compile("^[a-z0-9]{2,20}$");
    private static final Pattern PATH_RE = Pattern.compile("^/[A-Za-z0-9/._~-]{0,199}$");
    private static final Pattern FLOW_RE = Pattern.compile("^[a-z0-9-]{0,32}$");

    /** Every server in the reply that passes every check, as a vless:// link. Never throws on a bad entry - skips it. */
    static List<String> linksFrom(JSONObject reply) {
        final List<String> links = new ArrayList<>();
        final JSONObject config = reply.optJSONObject("config");
        final JSONArray outbounds = config == null ? null : config.optJSONArray("outbounds");
        if (outbounds == null) {
            return links;
        }
        for (int i = 0; i < outbounds.length() && links.size() < MAX_SERVERS; i++) {
            try {
                final String link = linkFrom(outbounds.optJSONObject(i));
                if (link != null) {
                    links.add(link);
                }
            } catch (Throwable ignore) {
                // One malformed server must not cost the others.
            }
        }
        return links;
    }

    private static String linkFrom(JSONObject outbound) throws Exception {
        if (outbound == null || !"vless".equals(outbound.optString("protocol"))) {
            return null;
        }
        final JSONObject settings = outbound.optJSONObject("settings");
        final JSONArray vnext = settings == null ? null : settings.optJSONArray("vnext");
        final JSONObject node = vnext == null ? null : vnext.optJSONObject(0);
        final JSONArray users = node == null ? null : node.optJSONArray("users");
        final JSONObject user = users == null ? null : users.optJSONObject(0);
        if (node == null || user == null) {
            return null;
        }
        final String host = node.optString("address");
        final int port = node.optInt("port", -1);
        final String id = user.optString("id");
        final String flow = user.optString("flow", "");
        if (!publicHost(host) || port < 1 || port > 65535 || !UUID_RE.matcher(id).matches()
                || !"none".equals(user.optString("encryption", "none")) || !FLOW_RE.matcher(flow).matches()) {
            return null;
        }

        final JSONObject stream = outbound.optJSONObject("streamSettings");
        if (stream == null) {
            return null;
        }
        final String network = stream.optString("network", "tcp");
        final String security = stream.optString("security", "none");
        // Never an unencrypted hop: a free key must not be a way to read your traffic in the clear.
        if (!"reality".equals(security) && !"tls".equals(security)) {
            return null;
        }
        if (!"tcp".equals(network) && !"xhttp".equals(network)) {
            return null;
        }

        final StringBuilder query = new StringBuilder();
        append(query, "type", network);
        append(query, "security", security);
        if (!flow.isEmpty()) {
            append(query, "flow", flow);
        }
        if ("reality".equals(security)) {
            final JSONObject reality = stream.optJSONObject("realitySettings");
            if (reality == null) {
                return null;
            }
            final String pbk = reality.optString("publicKey");
            final String sid = reality.optString("shortId", "");
            final String sni = reality.optString("serverName");
            final String fp = reality.optString("fingerprint", "chrome");
            if (!PBK_RE.matcher(pbk).matches() || !SID_RE.matcher(sid).matches() || !publicHost(sni) || !FP_RE.matcher(fp).matches()) {
                return null;
            }
            append(query, "pbk", pbk);
            if (!sid.isEmpty()) {
                append(query, "sid", sid);
            }
            append(query, "sni", sni);
            append(query, "fp", fp);
        } else {
            final JSONObject tls = stream.optJSONObject("tlsSettings");
            final String sni = tls == null ? host : tls.optString("serverName", host);
            final String fp = tls == null ? "chrome" : tls.optString("fingerprint", "chrome");
            if (!HOST_RE.matcher(sni).matches() || !FP_RE.matcher(fp).matches()) {
                return null;
            }
            append(query, "sni", sni);
            append(query, "fp", fp);
        }
        if ("xhttp".equals(network)) {
            final JSONObject xhttp = stream.optJSONObject("xhttpSettings");
            final String path = xhttp == null ? "/" : xhttp.optString("path", "/");
            final String mode = xhttp == null ? "auto" : xhttp.optString("mode", "auto");
            final String hostHeader = xhttp == null ? "" : xhttp.optString("host", "");
            if (!PATH_RE.matcher(path).matches()
                    || !("auto".equals(mode) || "packet-up".equals(mode) || "stream-up".equals(mode) || "stream-one".equals(mode))
                    || (!hostHeader.isEmpty() && !HOST_RE.matcher(hostHeader).matches())) {
                return null;
            }
            append(query, "path", path);
            append(query, "mode", mode);
            if (!hostHeader.isEmpty()) {
                append(query, "host", hostHeader);
            }
        }
        return "vless://" + id + "@" + host + ":" + port + "?" + query;
    }

    private static void append(StringBuilder query, String key, String value) throws Exception {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(key).append('=').append(URLEncoder.encode(value, "UTF-8"));
    }

    /** A host or address that is plausibly on the public internet - nothing that points back at this phone or its LAN. */
    static boolean publicHost(String host) {
        if (host == null || !HOST_RE.matcher(host).matches()) {
            return false;
        }
        final String lower = host.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".local") || lower.endsWith(".localhost")
                || lower.endsWith(".internal") || lower.endsWith(".lan") || lower.endsWith(".home")) {
            return false;
        }
        final java.util.regex.Matcher ip = IPV4_RE.matcher(host);
        if (ip.matches()) {
            final int a = Integer.parseInt(ip.group(1));
            final int b = Integer.parseInt(ip.group(2));
            final int c = Integer.parseInt(ip.group(3));
            final int d = Integer.parseInt(ip.group(4));
            if (a > 255 || b > 255 || c > 255 || d > 255) {
                return false;
            }
            return !(a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168) || (a == 100 && b >= 64 && b <= 127) || a >= 224);
        }
        return host.indexOf('.') > 0;
    }
}
