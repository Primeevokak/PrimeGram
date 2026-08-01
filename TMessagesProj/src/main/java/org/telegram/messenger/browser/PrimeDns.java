package org.telegram.messenger.browser;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;

/**
 * PrimeGram: DNS-over-HTTPS resolver.
 *
 * <p>Two reasons this exists. First, the ISP's DNS is the cheapest place to block a site, and
 * an encrypted resolver walks straight past that — which is the whole point of this client.
 * Second, AdGuard's default resolver already answers 0.0.0.0 for ad and tracker domains, so
 * asking it a question doubles as an ad-blocking decision without shipping a single filter
 * list.
 *
 * <p>Wire format is RFC 8484 (a plain DNS message POSTed to an HTTPS endpoint), which is why
 * there is no dependency here: building an A/AAAA query and walking the answer section is less
 * code than pulling in a resolver library.
 *
 * <p>Every call blocks on network I/O. Never call it from the main thread.
 */
public class PrimeDns {

    public static final String KEY_ENABLED = "primegram_dns_enabled";
    public static final String KEY_PRESET = "primegram_dns_preset";
    public static final String KEY_CUSTOM = "primegram_dns_custom";

    /** Index into {@link #PRESET_URLS} meaning "use whatever is in {@link #KEY_CUSTOM}". */
    public static final int PRESET_CUSTOM = -1;
    public static final int PRESET_ADGUARD = 0;

    public static final String[] PRESET_NAMES = {
            "AdGuard DNS — блокирует рекламу и трекеры",
            "AdGuard DNS Family — плюс взрослый контент",
            "AdGuard DNS без фильтрации",
            "Cloudflare",
            "Google",
            "xbox-dns — открывает заблокированные по стране сервисы",
    };

    public static final String[] PRESET_URLS = {
            "https://dns.adguard-dns.com/dns-query",
            "https://family.adguard-dns.com/dns-query",
            "https://unfiltered.adguard-dns.com/dns-query",
            "https://cloudflare-dns.com/dns-query",
            "https://dns.google/dns-query",
            "https://xbox-dns.ru/dns-query",
    };

    private static final int TYPE_A = 1;
    private static final int TYPE_AAAA = 28;
    private static final int TYPE_CNAME = 5;

    private static final int RCODE_NXDOMAIN = 3;

    /** Answers live at least this long even when the server hands us a tiny TTL. */
    private static final long MIN_CACHE_MS = 30_000L;
    private static final long MAX_CACHE_MS = 30 * 60_000L;
    /** A failed lookup is remembered briefly so a dead resolver can't stall every request. */
    private static final long FAILURE_CACHE_MS = 10_000L;

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    public static class Result {
        /** Resolved addresses, empty when the name does not exist or the lookup failed. */
        public final ArrayList<InetAddress> addresses = new ArrayList<>();
        /** The resolver deliberately refused this name: NXDOMAIN, 0.0.0.0 or ::. */
        public boolean blocked;
        /** We never got a usable answer — caller should fall back to the system resolver. */
        public boolean failed;

        long expiresAt;
    }

    private static final ConcurrentHashMap<String, Result> cache = new ConcurrentHashMap<>();
    /** Bumped whenever the endpoint changes so stale answers from another resolver are dropped. */
    private static volatile String cachedEndpoint;

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean isEnabled() {
        try {
            return prefs().getBoolean(KEY_ENABLED, true);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
        cache.clear();
    }

    public static int getPreset() {
        return prefs().getInt(KEY_PRESET, PRESET_ADGUARD);
    }

    public static void setPreset(int preset) {
        prefs().edit().putInt(KEY_PRESET, preset).apply();
        cache.clear();
    }

    public static String getCustomEndpoint() {
        return prefs().getString(KEY_CUSTOM, "");
    }

    public static void setCustomEndpoint(String url) {
        prefs().edit().putString(KEY_CUSTOM, url == null ? "" : url.trim()).apply();
        cache.clear();
    }

    /**
     * A custom entry may be typed as a bare host ("dns.example.com") or as a full DoH URL. Both
     * are accepted; anything that still isn't https after normalising is rejected, because a
     * plaintext resolver would give up exactly the property we came here for.
     */
    public static String normalizeEndpoint(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String url = value.trim();
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return null;
        }
        if (!url.contains("/dns-query") && url.indexOf('/', "https://".length()) < 0) {
            url = url + "/dns-query";
        }
        return url;
    }

    /** The endpoint in use right now, or null when DoH is switched off / misconfigured. */
    public static String currentEndpoint() {
        if (!isEnabled()) {
            return null;
        }
        int preset = getPreset();
        if (preset == PRESET_CUSTOM) {
            return normalizeEndpoint(getCustomEndpoint());
        }
        if (preset < 0 || preset >= PRESET_URLS.length) {
            preset = PRESET_ADGUARD;
        }
        return PRESET_URLS[preset];
    }

    public static String currentName() {
        if (!isEnabled()) {
            return "Системный DNS";
        }
        int preset = getPreset();
        if (preset == PRESET_CUSTOM) {
            String custom = normalizeEndpoint(getCustomEndpoint());
            return custom == null ? "Системный DNS" : custom;
        }
        if (preset < 0 || preset >= PRESET_NAMES.length) {
            preset = PRESET_ADGUARD;
        }
        return PRESET_NAMES[preset];
    }

    public static void clearCache() {
        cache.clear();
    }

    /**
     * Resolves {@code host} through the configured DoH endpoint. Returns null when DoH is off,
     * so callers can tell "not configured" from "configured and failed".
     */
    public static Result resolve(String host) {
        return resolveVia(currentEndpoint(), host);
    }

    /**
     * The same lookup against a resolver of the caller's choosing.
     *
     * <p>Exists because one feature can need a particular resolver without the browser's setting
     * following it around: a service that refuses whole countries is reachable through a resolver
     * that answers with its own gateway, and that is a decision about that service, not about
     * everything the user browses.
     */
    public static Result resolveVia(String endpoint, String host) {
        if (endpoint == null || TextUtils.isEmpty(host)) {
            return null;
        }
        // Keyed by resolver as well as name: two resolvers answer differently on purpose, and
        // that is the entire reason this overload exists.
        final String key = endpoint + "|" + host.toLowerCase(Locale.ROOT);
        final String name = host.toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        Result cached = cache.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached;
        }

        Result result = new Result();
        try {
            byte[] response = post(endpoint, buildQuery(name, TYPE_A));
            long ttlMs = parseAnswer(response, result);
            if (result.addresses.isEmpty() && !result.blocked) {
                // No A record is not automatically an error — the host may be v6 only.
                byte[] response6 = post(endpoint, buildQuery(name, TYPE_AAAA));
                long ttl6 = parseAnswer(response6, result);
                ttlMs = Math.max(ttlMs, ttl6);
            }
            result.expiresAt = now + Math.max(MIN_CACHE_MS, Math.min(MAX_CACHE_MS, ttlMs));
        } catch (Throwable t) {
            result.failed = true;
            result.expiresAt = now + FAILURE_CACHE_MS;
            if (org.telegram.messenger.BuildVars.LOGS_ENABLED) {
                FileLog.d("PrimeDns: lookup of " + key + " failed: " + t.getMessage());
            }
        }
        cache.put(key, result);
        return result;
    }

    /** Convenience for the ad blocker: true only when the resolver actively refused the name. */
    public static boolean isRefusedByResolver(String host) {
        Result result = resolve(host);
        return result != null && result.blocked;
    }

    private static byte[] post(String endpoint, byte[] query) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/dns-message");
            connection.setRequestProperty("Accept", "application/dns-message");
            if (connection instanceof HttpsURLConnection) {
                // Nothing custom, just being explicit that plaintext is never acceptable here.
                ((HttpsURLConnection) connection).setHostnameVerifier(
                        HttpsURLConnection.getDefaultHostnameVerifier());
            }
            OutputStream out = connection.getOutputStream();
            out.write(query);
            out.flush();
            out.close();

            if (connection.getResponseCode() / 100 != 2) {
                throw new IllegalStateException("http " + connection.getResponseCode());
            }
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
            byte[] chunk = new byte[512];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > 8192) {
                    break;
                }
            }
            in.close();
            return buffer.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] buildQuery(String host, int type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0);        // id — DoH is request/response, so it carries no meaning here
        out.writeShort(0x0100);   // recursion desired
        out.writeShort(1);        // one question
        out.writeShort(0);        // no answers
        out.writeShort(0);        // no authority records
        out.writeShort(0);        // no additional records
        for (String label : host.split("\\.")) {
            byte[] raw = label.getBytes("UTF-8");
            if (raw.length == 0 || raw.length > 63) {
                throw new IllegalArgumentException("bad label");
            }
            out.writeByte(raw.length);
            out.write(raw);
        }
        out.writeByte(0);         // root label terminates the name
        out.writeShort(type);
        out.writeShort(1);        // class IN
        out.flush();
        return bytes.toByteArray();
    }

    /**
     * Walks the answer section, filling {@code result}. Returns the smallest TTL seen, in ms.
     */
    private static long parseAnswer(byte[] data, Result result) throws Exception {
        if (data == null || data.length < 12) {
            throw new IllegalStateException("short response");
        }
        int rcode = data[3] & 0x0F;
        if (rcode == RCODE_NXDOMAIN) {
            // AdGuard answers NXDOMAIN for some blocked names and 0.0.0.0 for others.
            result.blocked = true;
            return 0;
        }
        if (rcode != 0) {
            throw new IllegalStateException("rcode " + rcode);
        }
        int questions = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int answers = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);

        int offset = 12;
        for (int i = 0; i < questions; i++) {
            offset = skipName(data, offset);
            offset += 4; // type + class
        }

        long minTtlMs = Long.MAX_VALUE;
        for (int i = 0; i < answers && offset + 10 <= data.length; i++) {
            offset = skipName(data, offset);
            int type = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            long ttl = ((long) (data[offset + 4] & 0xFF) << 24)
                    | ((data[offset + 5] & 0xFF) << 16)
                    | ((data[offset + 6] & 0xFF) << 8)
                    | (data[offset + 7] & 0xFF);
            int length = ((data[offset + 8] & 0xFF) << 8) | (data[offset + 9] & 0xFF);
            offset += 10;
            if (offset + length > data.length) {
                break;
            }
            if (type == TYPE_A && length == 4 || type == TYPE_AAAA && length == 16) {
                byte[] address = new byte[length];
                System.arraycopy(data, offset, address, 0, length);
                if (isNullAddress(address)) {
                    // The classic "blocked" answer: the resolver points the name at nowhere.
                    result.blocked = true;
                } else {
                    result.addresses.add(InetAddress.getByAddress(address));
                }
                minTtlMs = Math.min(minTtlMs, ttl * 1000L);
            } else if (type != TYPE_CNAME) {
                // Anything else is not useful to us; CNAMEs are followed by the resolver itself.
                minTtlMs = Math.min(minTtlMs, ttl * 1000L);
            }
            offset += length;
        }
        return minTtlMs == Long.MAX_VALUE ? 0 : minTtlMs;
    }

    private static boolean isNullAddress(byte[] address) {
        for (byte b : address) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /** Names are length-prefixed labels, possibly ending in a compression pointer. */
    private static int skipName(byte[] data, int offset) {
        while (offset < data.length) {
            int length = data[offset] & 0xFF;
            if (length == 0) {
                return offset + 1;
            }
            if ((length & 0xC0) == 0xC0) {
                return offset + 2; // pointer, and a pointer always ends the name
            }
            offset += length + 1;
        }
        return offset;
    }
}
