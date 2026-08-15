package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.app.Activity;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.ConnectionsManager;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * TgWsProxyService — полный порт tg-ws-proxy (backend.py) на Java.
 *
 * Архитектура:
 *   1. ForegroundService запускается вместе с приложением
 *   2. Слушает 127.0.0.1:1080 как SOCKS5-сервер
 *   3. При подключении Telegram (через NDK) — определяет DC по IP назначения
 *   4. Открывает WebSocket к домену Telegram DC через HTTPS/TLS
 *   5. Перешифровывает MTProto трафик через WebSocket (обходя блокировки)
 *
 * Эквивалент backend.py из LemoLev/tgwsp-android-rewrite.
 */
public class TgWsProxyService extends Service {

    public static final String CHANNEL_ID = "tg_ws_proxy_channel";
    public static final int NOTIFICATION_ID = 7777;
    public static final int PROXY_PORT = 1080;

    private static final String TAG = "TgWsProxyService";

    // ─── User-settable bits ────────────────────────────────────────────────
    //
    // The service was written with everything decided for the user: port 1080, and whichever of
    // ten domains answered a probe first. Both are good defaults and neither survives contact with
    // a hostile network - a blocked domain stays blocked while the prober keeps picking it, and a
    // port can be taken by whatever else the user runs. So both are now settings, with "leave it
    // alone" as the default value rather than a separate switch.

    private static final String PREF_PORT = "primegram_tgws_port";
    private static final String PREF_DOMAIN = "primegram_tgws_domain";

    private static SharedPreferences settings() {
        return MessagesController.getGlobalMainSettings();
    }

    /** The current candidate pool, in whatever order it's currently in - the live-fetched one
     *  once {@link #scheduleCfProxyDomainListRefresh} has succeeded at least once, the hardcoded
     *  seed ({@link #BASE_DOMAINS}) until then or if it never does. */
    public static String[] baseDomains() {
        return activeBaseDomains.clone();
    }

    /** The port the user asked for. The bind still walks forward if it is taken. */
    public static int configuredPort() {
        final int port = settings().getInt(PREF_PORT, PROXY_PORT);
        return port >= 1024 && port <= 65535 ? port : PROXY_PORT;
    }

    public static void setConfiguredPort(int port) {
        settings().edit().putInt(PREF_PORT, port).apply();
    }

    /** The domain the user pinned, or empty for "measure and choose". */
    public static String forcedDomain() {
        final String domain = settings().getString(PREF_DOMAIN, "");
        return domain == null ? "" : domain;
    }

    public static void setForcedDomain(String domain) {
        settings().edit().putString(PREF_DOMAIN, domain == null ? "" : domain).apply();
        synchronized (domainLock) {
            // Forget what we are on, so the next connection picks up the new answer rather than
            // waiting out the thirty-second cooldown on a domain the user just rejected.
            currentBaseDomain = null;
            cachedBaseAddresses.clear();
            lastDomainSelectionTime = 0;
        }
    }

    /** Whichever domain traffic is going through right now, or null before the first connection. */
    public static String currentDomain() {
        synchronized (domainLock) {
            return currentBaseDomain;
        }
    }

    /**
     * How long a TLS handshake with this domain takes, in milliseconds, or -1 when it fails.
     *
     * <p>Blocking, and meant to be called from a background thread - the settings screen runs ten
     * of these at once. It measures the same thing the service's own chooser measures, which is
     * the point: a number on screen that came from a different test would be a number that
     * disagrees with the domain the service then picks.
     */
    public static long probeDomain(String domain, int dcId) {
        SSLSocket socket = null;
        final long started = System.currentTimeMillis();
        // The running service if there is one, so the probe resolves names the same way the
        // service does - including its DNS-over-HTTPS fallback, which is the whole reason a
        // blocked network still reaches these domains. With the server stopped there is nothing
        // to borrow, and the system resolver has to do; a probe run in that state can therefore
        // fail on a domain that would have worked, which is the honest answer for "stopped".
        final TgWsProxyService service = instance;
        try {
            String host = "kws" + dcId + "." + domain;
            InetAddress[] resolved = service != null ? service.resolveWithFallbackDns(host) : null;
            if (resolved == null || resolved.length == 0) {
                host = "kws." + domain;
                resolved = service != null ? service.resolveWithFallbackDns(host) : null;
                if (resolved == null || resolved.length == 0) {
                    host = domain;
                }
            }
            final SSLSocketFactory factory = service != null && service.sslSocketFactory != null
                    ? service.sslSocketFactory : buildTrustAllSslFactory();
            socket = (SSLSocket) factory.createSocket();
            socket.connect(new java.net.InetSocketAddress(host, 443), 2500);
            socket.setSoTimeout(2500);
            socket.startHandshake();
            return System.currentTimeMillis() - started;
        } catch (Exception e) {
            return -1;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    public interface LogListener {
        void onLogAdded(String line);
    }
    private static final List<String> logBuffer = new ArrayList<>();
    private static LogListener logListener;

    /**
     * The log has a lock of its own, and that is the whole point of it existing.
     * <p>
     * These three methods used to be {@code static synchronized}, which locks the class object -
     * the same monitor the domain selection below holds for as long as it probes, and that can run
     * into tens of seconds when every candidate is unreachable. Anything that wrote a line to the
     * log during that window waited for the probe to finish. If that caller happened to be the
     * main thread, the app froze for as long as the network took to give up.
     */
    private static final Object logLock = new Object();

    /** Guards the chosen domain and its cached address. Never held while writing to the log. */
    private static final Object domainLock = new Object();

    public static void addLog(String line) {
        // Millisecond precision + thread name: several distinct events routinely land in the same
        // second (a pool refill burst, several SOCKS5 accepts back to back), and without either of
        // these there was no way to tell their actual order or which of several concurrent
        // sessions/threads a given line even belonged to - exactly the ambiguity that made it
        // impossible to confirm (rather than guess) that a "Socket closed" was the same session as
        // the "WsPool refilled" burst three lines above it.
        final String formatted = String.format("[%tT.%<tL][%s] %s", System.currentTimeMillis(), Thread.currentThread().getName(), line);
        final LogListener listener;
        synchronized (logLock) {
            logBuffer.add(formatted);
            if (logBuffer.size() > 200) {
                logBuffer.remove(0);
            }
            listener = logListener;
        }
        // outside the lock: the listener draws on screen, and the log must never wait on the UI
        if (listener != null) {
            listener.onLogAdded(formatted);
        }
    }

    public static List<String> getLogBuffer() {
        synchronized (logLock) {
            return new ArrayList<>(logBuffer);
        }
    }

    public static void setLogListener(LogListener listener) {
        synchronized (logLock) {
            logListener = listener;
        }
    }

    /**
     * Records a tunnel milestone into the cold-start trace, but only while a cold start is still
     * being traced and only for a bounded number of events.
     *
     * <p>The trace keeps 200 marks. A proxy that reconnects in a loop would fill all of them and
     * push out the startup milestones that give the numbers their meaning, so the tunnel gets a
     * fixed share: enough to show the first connection to each DC and the first several failures,
     * which is what identifies the pattern. Everything beyond that is still in the proxy log.
     */
    private static final java.util.concurrent.atomic.AtomicInteger primeTraceBudget =
            new java.util.concurrent.atomic.AtomicInteger(90);

    private static void primeTraceConnect(String msg) {
        if (primeTraceBudget.get() <= 0 || primeTraceBudget.getAndDecrement() <= 0) {
            return;
        }
        PrimeStartupTrace.mark(msg);
    }

    /**
     * Hex-dumps every relayed packet. Debugging aid for the obfuscation layer only.
     *
     * <p>Deliberately not tied to {@link BuildVars#LOGS_ENABLED}, which this fork forces to true:
     * this dump belongs to the relay hot path and has to be off in any build a user runs.
     */
    private static final boolean DUMP_PACKET_HEX = false;

    private static void logInfo(String msg) {
        FileLog.d(msg);
        addLog("[INFO] " + msg);
    }

    private static void logError(String msg, Throwable e) {
        FileLog.e(msg, e);
        addLog("[ERROR] " + msg + (e != null ? ": " + e.getMessage() : ""));
    }

    // DC IP mapping — from backend.py _IP_TO_DC
    private static final Map<String, int[]> IP_TO_DC = new HashMap<>();

    static {
        // DC1
        IP_TO_DC.put("149.154.175.50",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.51",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.53",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.54",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.52",  new int[]{1, 1}); // media
        // DC2
        IP_TO_DC.put("149.154.167.41",  new int[]{2, 0});
        IP_TO_DC.put("149.154.167.50",  new int[]{2, 0});
        IP_TO_DC.put("149.154.167.51",  new int[]{2, 0});
        IP_TO_DC.put("149.154.167.220", new int[]{2, 0});
        IP_TO_DC.put("95.161.76.100",   new int[]{2, 0});
        IP_TO_DC.put("149.154.167.151", new int[]{2, 1});
        IP_TO_DC.put("149.154.167.222", new int[]{2, 1});
        IP_TO_DC.put("149.154.167.223", new int[]{2, 1});
        IP_TO_DC.put("149.154.162.123", new int[]{2, 1});
        // DC3
        IP_TO_DC.put("149.154.175.100", new int[]{3, 0});
        IP_TO_DC.put("149.154.175.101", new int[]{3, 0});
        IP_TO_DC.put("149.154.175.102", new int[]{3, 1});
        // DC4
        IP_TO_DC.put("149.154.167.91",  new int[]{4, 0});
        IP_TO_DC.put("149.154.167.92",  new int[]{4, 0});
        IP_TO_DC.put("149.154.164.250", new int[]{4, 1});
        IP_TO_DC.put("149.154.166.120", new int[]{4, 1});
        IP_TO_DC.put("149.154.166.121", new int[]{4, 1});
        IP_TO_DC.put("149.154.167.118", new int[]{4, 1});
        IP_TO_DC.put("149.154.165.111", new int[]{4, 1});
        // DC5
        IP_TO_DC.put("91.108.56.100",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.101",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.116",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.126",   new int[]{5, 0});
        IP_TO_DC.put("149.154.171.5",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.102",   new int[]{5, 1});
        IP_TO_DC.put("91.108.56.128",   new int[]{5, 1});
        IP_TO_DC.put("91.108.56.151",   new int[]{5, 1});
        // DC203
        IP_TO_DC.put("91.105.192.100",  new int[]{203, 0});
    }

    // DC WebSocket domains — from tg-ws-proxy config
    private static final Map<Integer, String[]> DC_WS_DOMAINS = new HashMap<>();

    static {
        // DC1: web.telegram.org/apiws -> actual DC IPs
        DC_WS_DOMAINS.put(1, new String[]{"kws1.web.telegram.org", "kws1-1.web.telegram.org"});
        DC_WS_DOMAINS.put(2, new String[]{"kws2.web.telegram.org", "kws2-1.web.telegram.org"});
        DC_WS_DOMAINS.put(3, new String[]{"kws3.web.telegram.org", "kws3-1.web.telegram.org"});
        DC_WS_DOMAINS.put(4, new String[]{"kws4.web.telegram.org", "kws4-1.web.telegram.org"});
        DC_WS_DOMAINS.put(5, new String[]{"kws5.web.telegram.org", "kws5-1.web.telegram.org"});
        DC_WS_DOMAINS.put(203, new String[]{"kws2.web.telegram.org", "kws2-1.web.telegram.org"});
    }

    // Telegram WebSocket domains (same as used in tg-ws-proxy)
    private static final Map<Integer, String> DC_DOMAINS = new HashMap<>();

    static {
        DC_DOMAINS.put(1, "web.telegram.org");
        DC_DOMAINS.put(2, "web.telegram.org");
        DC_DOMAINS.put(3, "web.telegram.org");
        DC_DOMAINS.put(4, "web.telegram.org");
        DC_DOMAINS.put(5, "web.telegram.org");
        DC_DOMAINS.put(203, "web.telegram.org");
    }

    // Cloudflare Worker bypass domains from tg-ws-proxy - the seed/fallback pool, used until (and
    // whenever) refreshCfProxyDomainList() below successfully fetches a live one. This snapshot
    // was quietly stuck on just the first 10 of what is now upstream's 20-domain pool for a while
    // (see git history) - users on the exact hostile/DPI networks this whole feature exists for
    // reported every one of those first 10 failing with a WS close code 1000/"404", consistent
    // with them being the older half and more likely to have been specifically targeted since.
    private static final String[] BASE_DOMAINS = {
        "pclead.co.uk",
        "offshor.co.uk",
        "cakeisalie.co.uk",
        "noskomnadzor.co.uk",
        "lovetrue.co.uk",
        "sorokdva.co.uk",
        "pyatdesyatdva.co.uk",
        "kartoshka.co.uk",
        "sorokodin.co.uk",
        "pyatdesyatodin.co.uk",
        "notelega.co.uk",
        "ebally.co.uk",
        "nebally.co.uk",
        "havegreatday.co.uk",
        "pomogite.co.uk",
        "fixtelega.co.uk",
        "sadnews.co.uk",
        "onedaychamp.co.uk",
        "stopblocking.co.uk",
        "nothingthere.co.uk"
    };

    /** The pool actually in use - {@link #BASE_DOMAINS} until a live fetch replaces it, and
     *  falls back to whatever it currently holds (not necessarily the original seed) if a later
     *  fetch fails, same as upstream's own "keep current pool on bad/empty response" behavior. */
    private static volatile String[] activeBaseDomains = BASE_DOMAINS;

    private static final String CFPROXY_DOMAINS_URL =
            "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt";
    /** raw.githubusercontent.com's own current IP - pinned so a DNS-blocked github.com doesn't
     *  also take the domain-list refresh down on exactly the networks that need it most. Same
     *  technique upstream's own {@code build_github_opener()} uses. */
    private static final String GITHUB_RAW_PINNED_IP = "185.199.109.133";
    /** Below this many valid decoded domains, a fetch is treated as a bad/corrupted response and
     *  discarded rather than replacing a working pool with a near-empty one. */
    private static final int MIN_VALID_FETCHED_DOMAINS = 3;
    private static final long DOMAIN_LIST_REFRESH_INTERVAL_MS = 60 * 60_000L; // 1h, matches upstream

    /**
     * Reverse of upstream's own {@code _dd()} obfuscation (proxy/config.py): each decoded domain
     * is published as a same-length {@code .com} string with every letter Caesar-shifted by the
     * count of letters in that string, so a casual look at the published list (or a DPI box doing
     * plain-text keyword matching on it in transit) doesn't read as a domain list at all. Ported
     * by testing against upstream's own published output, not guessed.
     */
    private static String decodeCfProxyDomain(String s) {
        if (s == null || !s.endsWith(".com")) {
            return s;
        }
        final String p = s.substring(0, s.length() - 4);
        int n = 0;
        for (int i = 0; i < p.length(); i++) {
            if (Character.isLetter(p.charAt(i))) n++;
        }
        final StringBuilder out = new StringBuilder(p.length() + 6);
        for (int i = 0; i < p.length(); i++) {
            final char c = p.charAt(i);
            if (Character.isLetter(c)) {
                final int base = Character.isLowerCase(c) ? 'a' : 'A';
                final int shifted = (((c - base - n) % 26) + 26) % 26 + base;
                out.append((char) shifted);
            } else {
                out.append(c);
            }
        }
        return out.append(".co.uk").toString();
    }

    private static boolean isValidCfProxyDomain(String domain) {
        if (domain == null || domain.isEmpty() || domain.length() > 253) return false;
        if (domain.startsWith(".") || domain.endsWith(".")) return false;
        final String[] labels = domain.split("\\.");
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) return false;
            if (label.startsWith("-") || label.endsWith("-")) return false;
            for (int i = 0; i < label.length(); i++) {
                final char c = label.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '-') return false;
            }
        }
        final String tld = labels[labels.length - 1];
        if (tld.length() < 2) return false;
        for (int i = 0; i < tld.length(); i++) {
            if (Character.isLetter(tld.charAt(i))) return true;
        }
        return false;
    }

    /** Best-effort GET of a small text resource: normal system-DNS HTTPS first, then a fallback
     *  through {@code pinnedIp} (real hostname only in SNI/Host) if that fails outright - not a
     *  persistent tunnel, so a plain one-shot request/response is enough. Returns null on any
     *  failure; callers already treat "couldn't refresh" as "keep the existing pool." */
    private static String httpsGetBestEffort(String urlStr, String host, String pinnedIp, int timeoutMs) {
        try {
            final HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "PrimeGram");
            final int code = conn.getResponseCode();
            if (code == 200) {
                return readAllUtf8(conn.getInputStream());
            }
        } catch (Exception systemDnsFailed) {
            // fall through to the pinned-IP path below
        }
        if (pinnedIp == null) {
            return null;
        }
        Socket plain = null;
        SSLSocket tls = null;
        try {
            final String path = new URL(urlStr).getFile();
            plain = new Socket();
            plain.connect(new java.net.InetSocketAddress(pinnedIp, 443), timeoutMs);
            tls = (SSLSocket) buildTrustAllSslFactory().createSocket(plain, host, 443, true);
            tls.setUseClientMode(true);
            tls.setSoTimeout(timeoutMs);
            tls.startHandshake();
            final String req = "GET " + path + " HTTP/1.1\r\nHost: " + host
                    + "\r\nUser-Agent: PrimeGram\r\nConnection: close\r\n\r\n";
            tls.getOutputStream().write(req.getBytes("UTF-8"));
            tls.getOutputStream().flush();
            final String raw = readAllUtf8(tls.getInputStream());
            final int sep = raw.indexOf("\r\n\r\n");
            return sep >= 0 ? raw.substring(sep + 4) : null;
        } catch (Exception pinnedFailed) {
            return null;
        } finally {
            try { if (tls != null) tls.close(); else if (plain != null) plain.close(); } catch (Exception ignore) {}
        }
    }

    private static String readAllUtf8(InputStream in) throws IOException {
        final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        final byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString("UTF-8");
    }

    /** Fetches and decodes the live domain list, and swaps {@link #activeBaseDomains} in only if
     *  it looks like a genuine, healthy response - same "keep current pool on bad/empty response"
     *  guard upstream's own {@code refresh_cfproxy_domains()} uses, so a transient bad fetch can't
     *  wipe out a working pool with an empty one. Safe to call from any thread; does its own I/O
     *  off the caller's thread when invoked via {@link #scheduleCfProxyDomainListRefresh}. */
    private static void refreshCfProxyDomainListOnce() {
        final String text = httpsGetBestEffort(
                CFPROXY_DOMAINS_URL + "?" + Long.toString(System.nanoTime(), 36),
                "raw.githubusercontent.com", GITHUB_RAW_PINNED_IP, 10_000);
        if (text == null) {
            logInfo("CF proxy domain list refresh failed (network); keeping current pool");
            return;
        }
        final java.util.LinkedHashSet<String> valid = new java.util.LinkedHashSet<>();
        for (String line : text.split("\n")) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            final String decoded = decodeCfProxyDomain(trimmed).toLowerCase(java.util.Locale.ROOT);
            if (isValidCfProxyDomain(decoded)) {
                valid.add(decoded);
            }
        }
        if (valid.size() < MIN_VALID_FETCHED_DOMAINS) {
            logInfo("CF proxy domain list refresh returned only " + valid.size()
                    + " valid domain(s), need >= " + MIN_VALID_FETCHED_DOMAINS + "; keeping current pool");
            return;
        }
        activeBaseDomains = valid.toArray(new String[0]);
        logInfo("CF proxy domain pool updated from GitHub (" + activeBaseDomains.length + " domains)");
    }

    private static final Object domainRefreshLock = new Object();
    private static boolean domainRefreshScheduled;

    /** Starts the hourly background refresh if it isn't already running - idempotent, safe to
     *  call from {@code onCreate()} every time the service (re)starts. */
    private static void scheduleCfProxyDomainListRefresh() {
        synchronized (domainRefreshLock) {
            if (domainRefreshScheduled) {
                return;
            }
            domainRefreshScheduled = true;
        }
        final Thread t = new Thread(() -> {
            while (true) {
                try {
                    refreshCfProxyDomainListOnce();
                } catch (Throwable ignore) {
                }
                try {
                    Thread.sleep(DOMAIN_LIST_REFRESH_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }, "tgws-domain-refresh");
        t.setDaemon(true);
        t.start();
    }

    // Hardcoded Cloudflare IP addresses as ultimate fallback
    // These are anycast IPs that Cloudflare Workers respond on
    private static final String[] FALLBACK_IPS = {
        "104.16.0.0",     // Cloudflare anycast range
        "104.16.1.0",
        "104.17.0.0",
        "104.18.0.0",
        "104.19.0.0",
        "104.20.0.0",
        "172.64.0.0",
        "172.65.0.0",
        "172.66.0.0",
        "172.67.0.0"
    };

    // MTProto handshake constants — from utils.py
    private static final int HANDSHAKE_LEN  = 64;
    private static final int SKIP_LEN       = 8;
    private static final int PREKEY_LEN     = 32;
    private static final int IV_LEN         = 16;
    private static final int PROTO_TAG_POS  = 56;
    private static final int DC_IDX_POS     = 60;

    private static final byte[] PROTO_TAG_ABRIDGED    = {(byte)0xEF, (byte)0xEF, (byte)0xEF, (byte)0xEF};
    private static final byte[] PROTO_TAG_INTERMEDIATE = {(byte)0xEE, (byte)0xEE, (byte)0xEE, (byte)0xEE};
    private static final byte[] PROTO_TAG_SECURE       = {(byte)0xDD, (byte)0xDD, (byte)0xDD, (byte)0xDD};

    private static final int PROTO_ABRIDGED_INT     = 0xEFEFEFEF;
    private static final int PROTO_INTERMEDIATE_INT = 0xEEEEEEEE;
    private static final int PROTO_SECURE_INT       = 0xDDDDDDDD;

    private static final SecureRandom RANDOM = new SecureRandom();

    // Running state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private ExecutorService executor;
    // Pool warm-up (refillWsPoolAsync) used to run on the same bounded `executor` as live client
    // sessions. A warm-up burst - e.g. warmupActiveDcs() firing for several recently-active DCs
    // at once - occupies several of those threads for seconds each (a sequential run of TLS
    // handshakes), and a real log showed this exact thing: "WsPool refilled ... pool size: 8"
    // repeated back to back, immediately followed by "Unsupported SOCKS5 command: -1" / "Broken
    // pipe" on brand-new local SOCKS5 connections - tgnet's own connect had queued behind the
    // warm-up work long enough that it gave up before handleClient ever got a thread to read its
    // first byte. Warm-up is background housekeeping, never something a real session should wait
    // behind, so it gets its own small, separate pool.
    private ExecutorService poolWarmExecutor;
    private SSLSocketFactory sslSocketFactory;
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.Semaphore> dcSemaphores = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> ipFailUntil = new java.util.concurrent.ConcurrentHashMap<>();


    /**
     * PrimeGram: each active proxied connection holds up to 2 OS threads for its whole lifetime
     * (the executor thread running handleClient/bridgeConnections plus the dedicated toWs relay
     * thread) - a real phone has no business running hundreds of them. With up to ~5 DCs and a
     * media/non-media variant each, the old cap of 20 per bucket allowed a theoretical 200
     * concurrent connections (400 threads); a real session needs a small handful per DC at once.
     *
     * <p>Tightening this (economy mode) genuinely helped battery/thread count, but on the exact
     * hostile/DPI-filtered networks this whole feature exists for, real users reported it also
     * meant the tunnel no longer kept enough spare connections warm to survive a domain hiccup -
     * see {@link #proxyMode()}. Standard restores the original, generous cap; Turbo goes further
     * still for anyone who wants the tunnel to win every race it can, battery be damned.
     */
    private static final int MAX_CONCURRENT_PER_DC_ECONOMY = 6;
    private static final int MAX_CONCURRENT_PER_DC_STANDARD = 20;
    private static final int MAX_CONCURRENT_PER_DC_TURBO = 40;

    public static final int PROXY_MODE_ECONOMY = 0;
    public static final int PROXY_MODE_STANDARD = 1;
    public static final int PROXY_MODE_TURBO = 2;

    private static final String PREF_PROXY_MODE = "primegram_tgws_proxy_mode";

    /** Standard (generous, old behavior) by default: users on the networks this tunnel is for
     *  said plainly they'd rather it eat battery than drop connections. Economy and Turbo are
     *  both opt-in, in opposite directions from that default. */
    public static int proxyMode() {
        final int mode = settings().getInt(PREF_PROXY_MODE, PROXY_MODE_STANDARD);
        return mode == PROXY_MODE_ECONOMY || mode == PROXY_MODE_TURBO ? mode : PROXY_MODE_STANDARD;
    }

    public static void setProxyMode(int mode) {
        settings().edit().putInt(PREF_PROXY_MODE, mode).apply();
    }

    private static int maxConcurrentPerDc() {
        switch (proxyMode()) {
            case PROXY_MODE_ECONOMY: return MAX_CONCURRENT_PER_DC_ECONOMY;
            case PROXY_MODE_TURBO: return MAX_CONCURRENT_PER_DC_TURBO;
            default: return MAX_CONCURRENT_PER_DC_STANDARD;
        }
    }

    private java.util.concurrent.Semaphore getSemaphoreForDc(int dcId, boolean isMedia) {
        String key = dcId + "_" + isMedia;
        java.util.concurrent.Semaphore sem = dcSemaphores.get(key);
        if (sem == null) {
            synchronized (dcSemaphores) {
                sem = dcSemaphores.get(key);
                if (sem == null) {
                    sem = new java.util.concurrent.Semaphore(maxConcurrentPerDc());
                    dcSemaphores.put(key, sem);
                }
            }
        }
        return sem;
    }

    // Singleton for external access
    private static TgWsProxyService instance;

    public static int activeProxyPort = 1080;
    public static volatile boolean isSocketBound = false;
    private static volatile String currentBaseDomain = null;
    /** Consecutive failed connects against the currently selected base domain. See connectToWebSocket. */
    private static final java.util.concurrent.atomic.AtomicInteger consecutiveConnectFailures = new java.util.concurrent.atomic.AtomicInteger();
    public static String getCurrentBaseDomain() {
        return currentBaseDomain;
    }
    /** Per-hostname, not one shared IP for the whole base domain - it used to be a single field,
     *  which meant whichever kwsN.<domain> subdomain resolved FIRST got its IP silently reused for
     *  every OTHER subdomain under the same base domain too (kws4 connecting on kws2's cached IP,
     *  logged literally as "Connecting to resolved address kws2.../ for host kws4..."). Harmless
     *  when Cloudflare's anycast network routes every one of its edge IPs identically regardless of
     *  which specific IP you dialed, but not guaranteed, and a real 404 source if a subdomain's
     *  actual edge IP isn't interchangeable with another's. Still resolved once and reused per
     *  host (not re-resolved every connection) for the original reason this existed - fewer DNS
     *  round trips and a consistent edge IP across repeated connections to the SAME host. */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.net.InetAddress> cachedBaseAddresses = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long lastDomainSelectionTime = 0;

    private final Object socketLock = new Object();
    private final List<Socket> activeClientSockets = new ArrayList<>();
    private ConnectivityManager.NetworkCallback networkCallback;
    // onAvailable fires per network-validation event, not just on a real switch - the same
    // still-connected Wi-Fi can re-trigger it repeatedly (DHCP renew, signal re-validation)
    // with no actual outage. Reacting every time tore down the whole warm pool and fired a
    // burst of fresh TLS handshakes through the bounded executor, which is what starved the
    // local SOCKS5 accept loop and read as "Broken pipe"/"Unsupported SOCKS5 command: -1"
    // right after a perfectly healthy session kept working. Only react to an actual different
    // Network, and never more than once every few seconds even then.
    private volatile Network lastAvailableNetwork;
    private volatile long lastNetworkAvailableHandledAt;
    private static final long NETWORK_AVAILABLE_DEBOUNCE_MS = 5_000L;

    private void addClientSocket(Socket socket) {
        synchronized (socketLock) {
            activeClientSockets.add(socket);
        }
    }

    private void removeClientSocket(Socket socket) {
        synchronized (socketLock) {
            activeClientSockets.remove(socket);
        }
    }

    private void closeActiveClientSockets() {
        synchronized (socketLock) {
            for (Socket socket : activeClientSockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
            activeClientSockets.clear();
        }
    }

    private void restartProxySockets() {
        logInfo("Triggering restart of proxy sockets...");
        closeActiveClientSockets();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }

    private void startWatchdog() {
        // Background housekeeping, not client-facing traffic - runs on poolWarmExecutor rather
        // than the live-session `executor` for the same reason refillWsPoolAsync moved there: this
        // loop sleeps and wakes forever for the service's whole lifetime, which would otherwise
        // permanently pin one of the bounded executor's few core threads to nothing but sleeping.
        poolWarmExecutor.submit(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(15_000); // Check every 15 seconds
                    if (running.get()) {
                        if (serverSocket == null || serverSocket.isClosed() || !serverSocket.isBound()) {
                            logInfo("Watchdog detected server socket is closed/unbound! Restarting server socket...");
                            restartProxySockets();
                        }
                        // Cheap snapshot every 15s so a thread-starvation episode shows up in the
                        // log even when nothing else happened to trip an event-based log line -
                        // queued > 0 or active at the ceiling is worth seeing even in isolation.
                        if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                            java.util.concurrent.ThreadPoolExecutor tpe = (java.util.concurrent.ThreadPoolExecutor) executor;
                            if (tpe.getQueue().size() > 0 || tpe.getActiveCount() >= tpe.getMaximumPoolSize()) {
                                logInfo("Watchdog: client executor under pressure - " + executorStats());
                            }
                        }
                        maintainWsPool();
                        // The only thing still running when the app is swiped away, so this is
                        // where a temporary subscription can expire without the user reopening
                        // the client. Returns immediately unless something is actually due.
                        TempSubStore.checkExpiredInBackground();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    logError("Watchdog error", e);
                }
            }
        });
    }

    private void updateNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PrimeGram Proxy")
                .setContentText("Прокси активен (порт " + activeProxyPort + ")")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    public static boolean isRunning() {
        return instance != null && instance.running.get();
    }

    // ─── Android Service lifecycle ─────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        scheduleCfProxyDomainListRefresh();
        // Every thread here does TLS handshakes and AES on the proxied stream - real CPU work. At
        // the default priority those threads are equal to the one drawing the screen, so a burst
        // of connection attempts competes with scrolling and shows up as stutter. Nothing in this
        // pool is ever more urgent than the next frame.
        //
        // PrimeGram: this used to be Executors.newCachedThreadPool() - genuinely unbounded, a new
        // OS thread for every accepted local SOCKS5 connection before the per-DC semaphore (see
        // getSemaphoreForDc) even gets a chance to queue it. A reconnect burst could spin up
        // dozens of these, each blocking on sem.acquire() rather than doing anything, and cached
        // pools only reclaim threads that are actually IDLE - a thread parked in acquire() never
        // qualifies, so nothing ever gave them back. A bounded pool turns "unlimited threads
        // waiting on a semaphore" into "a bounded queue waiting on the same semaphore" - same
        // ordering, a fixed thread cost. The ceiling itself still follows economy/standard mode
        // (see maxConcurrentPerDc()) - a cap tighter than what the per-DC semaphores can actually
        // hand out would silently throttle standard mode's higher connection count right back
        // down, defeating the whole point of the mode switch.
        //
        // PrimeGram: corePoolSize used to be a fixed 4, independent of maxThreads. A real log
        // (executorStats() added specifically to check this) showed active=4/120, poolSize=4,
        // queued=1-3 held constant for the entire session, no matter how much concurrent load
        // there was - java.util.concurrent.ThreadPoolExecutor only ever grows PAST corePoolSize
        // once the queue is completely full (a `LinkedBlockingQueue(128)` almost never fills from
        // just 1-3 queued items), so the other 116 threads of headroom this pool was sized for
        // were never actually reachable. Every new SOCKS5 connection was queuing behind whatever
        // 4 tasks (some of them LONG-LIVED - a live relay session pins its handling thread for the
        // session's entire lifetime) already had the 4 core threads, with real observed dispatch
        // delays of 4-25 SECONDS before a thread ever picked it up - long past tgnet's own
        // patience, which is the direct, confirmed (not guessed) cause of the "Unsupported SOCKS5
        // command: -1" / "Broken pipe" pattern: the client had already given up and closed before
        // a thread ever read its first byte. Making corePoolSize == maximumPoolSize makes the
        // executor actually create a new thread for every task up to the real ceiling before it
        // ever queues, which is the scale-up behavior this pool was already sized and paying
        // memory for but never receiving; allowCoreThreadTimeOut still reclaims idle capacity
        // after 60s so this doesn't cost anything at rest.
        final int maxThreads = Math.max(24, maxConcurrentPerDc() * 3);
        executor = new java.util.concurrent.ThreadPoolExecutor(
            maxThreads, maxThreads, 60L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(128),
            r -> new Thread(() -> {
                // Deliberately not THREAD_PRIORITY_BACKGROUND: that moves the thread into the
                // background cgroup, which caps the whole group at a few percent of one core and
                // would throttle the tunnel every message goes through. This is just below default -
                // the UI wins a tie, the proxy still gets the CPU it asks for.
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT + 4);
                } catch (Throwable ignore) {}
                r.run();
            }, "tgws-proxy")
        );
        ((java.util.concurrent.ThreadPoolExecutor) executor).allowCoreThreadTimeOut(true);
        poolWarmExecutor = java.util.concurrent.Executors.newFixedThreadPool(2, r -> new Thread(() -> {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT + 4);
            } catch (Throwable ignore) {}
            r.run();
        }, "tgws-pool-warm"));
        sslSocketFactory = buildTrustAllSslFactory();
        primeWatchForeground();

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    long now = System.currentTimeMillis();
                    if (network.equals(lastAvailableNetwork) && now - lastNetworkAvailableHandledAt < NETWORK_AVAILABLE_DEBOUNCE_MS) {
                        logInfo("Ignoring redundant onAvailable for the same network");
                        return;
                    }
                    lastAvailableNetwork = network;
                    lastNetworkAvailableHandledAt = now;
                    logInfo("Network connection changed: available.");
                    // closeActiveClientSockets(); // Disabled: causes JNI/SOCKS5 SIGPIPE crash
                    clearWsPool();
                    warmupActiveDcs();
                }
                @Override
                public void onLost(Network network) {
                    logInfo("Network connection lost.");
                    // closeActiveClientSockets(); // Disabled: causes JNI/SOCKS5 SIGPIPE crash
                }
            };
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private android.os.PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PrimeGram Proxy")
                .setContentText("Прокси активен (порт " + activeProxyPort + ")")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        if (wakeLock == null) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "PrimeGram:ProxyWakeLock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        if (!running.getAndSet(true)) {
            currentBaseDomain = null;
            cachedBaseAddresses.clear();
            executor.submit(this::runProxyServer);
            startWatchdog();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running.set(false);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        instance = null;
        isSocketBound = false;
        currentBaseDomain = null;
        cachedBaseAddresses.clear();
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        clearWsPool();
        restartProxySockets();
        if (executor != null) executor.shutdown();
        if (poolWarmExecutor != null) poolWarmExecutor.shutdown();
        
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.content.SharedPreferences preferences = org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE);
                // Only tear down the native proxy setting if the user actually asked us to stop
                // (primegram_tgws_enabled == false). Any other death (OS/OEM kill, low memory,
                // START_STICKY restart) is transient: leaving proxy_enabled=true means MTProto
                // just retries against 127.0.0.1 until the service comes back, instead of
                // permanently falling back to a direct connection that a censored network blocks.
                boolean userDisabled = !preferences.getBoolean("primegram_tgws_enabled", true);
                if (userDisabled && preferences.getBoolean("proxy_enabled", false)) {
                    String proxyIp = preferences.getString("proxy_ip", "");
                    if ("127.0.0.1".equals(proxyIp)) {
                        android.content.SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean("proxy_enabled", false);
                        editor.apply();
                        org.telegram.tgnet.ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
                        logInfo("Disabled native Telegram proxy because user turned off the built-in proxy.");
                    }
                } else if (!userDisabled) {
                    logInfo("TgWsProxyService stopped unexpectedly, keeping proxy_enabled so it self-heals on restart.");
                    scheduleQuickRestart();
                }
            } catch (Exception e) {
                logError("Failed to update native proxy state in onDestroy", e);
            }
        });
    }

    /**
     * START_STICKY alone leaves the restart timing entirely up to Android, which a real log
     * showed taking close to two minutes after an unexpected kill (a network-transition moment,
     * not a user action) - far longer than the whole point of this service (keeping MTProto
     * reachable on a censored network) can tolerate sitting idle. This is a best-effort nudge, not
     * a guarantee: a plain (inexact) alarm needs no extra manifest permission, unlike
     * setExactAndAllowWhileIdle, so it can be subject to Doze/battery-saver batching on some
     * devices - but the service was very likely still recently foregrounded when this fires
     * (right at the moment it died), so in practice it should still land within a few seconds on
     * most devices instead of waiting on Android's own unspecified restart schedule.
     */
    private void scheduleQuickRestart() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(this, TgWsProxyService.class);
            int flags = android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
            android.app.PendingIntent pi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? android.app.PendingIntent.getForegroundService(this, 0, intent, flags)
                    : android.app.PendingIntent.getService(this, 0, intent, flags);
            am.set(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 4_000, pi);
            logInfo("Scheduled quick self-restart in ~4s via AlarmManager");
        } catch (Exception e) {
            logError("Failed to schedule quick self-restart", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Proxy server loop ─────────────────────────────────────────────────

    private void runProxyServer() {
        while (running.get()) {
            try {
                // SO_REUSEADDR has to be set on an unbound socket: `new ServerSocket(port)`
                // binds immediately, so the setReuseAddress() that used to follow it did
                // nothing at all. Without it, our own just-closed listener sits in TIME_WAIT
                // and blocks the rebind, so every watchdog restart walked to the next port
                // and rewrote proxy_port in the settings. The client then pointed at a port
                // that had just been abandoned — which is why the proxy could keep working
                // with the switch off (the old listener was still up) and fail with it on.
                int port = configuredPort();
                ServerSocket bound = null;
                for (int i = 0; i < 10 && bound == null; i++, port++) {
                    ServerSocket candidate = new ServerSocket();
                    try {
                        candidate.setReuseAddress(true);
                        candidate.bind(new java.net.InetSocketAddress(port), 50);
                        bound = candidate;
                        activeProxyPort = port;
                    } catch (IOException e) {
                        try { candidate.close(); } catch (IOException ignored) {}
                        logInfo("Port " + port + " is in use, trying next...");
                    }
                }
                if (bound == null) {
                    bound = new ServerSocket();
                    bound.setReuseAddress(true);
                    bound.bind(new java.net.InetSocketAddress(0), 50);
                    activeProxyPort = bound.getLocalPort();
                }
                serverSocket = bound;
                isSocketBound = true;

                logInfo("Listening on wildcard address (IPv4/IPv6 loopback allowed) port: " + activeProxyPort);
                updateNotification();

                int finalPort = activeProxyPort;
                // Was posted to the UI thread. Nothing in here needs it, and everything in here is
                // slow: a SharedPreferences read that can block until the file finishes loading,
                // an edit().apply(), a JNI call per account, a connection kick per account, and a
                // log line whose lock every proxy thread is also competing for. The startup trace
                // caught the main thread sitting inside this block.
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                        // Self-heal: as long as the user hasn't turned the built-in proxy off and
                        // hasn't picked a different manual proxy, re-announce ourselves to
                        // ConnectionsManager on every successful (re)bind — including after a
                        // restart that followed an unexpected kill, when proxy_enabled may have
                        // been left true (see onDestroy) or, on a fresh process, defaulted false.
                        boolean userDisabled = !preferences.getBoolean("primegram_tgws_enabled", true);
                        String proxyAddress = preferences.getString("proxy_ip", "");
                        boolean isOurAddress = proxyAddress.isEmpty() || "127.0.0.1".equals(proxyAddress);
                        if (!userDisabled && isOurAddress) {
                            String proxyUsername = preferences.getString("proxy_user", "");
                            String proxyPassword = preferences.getString("proxy_pass", "");
                            String proxySecret = preferences.getString("proxy_secret", "");

                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean("proxy_enabled", true);
                            editor.putString("proxy_ip", "127.0.0.1");
                            editor.putInt("proxy_port", finalPort);
                            editor.apply();

                            ConnectionsManager.setProxySettings(true, "127.0.0.1", finalPort, proxyUsername, proxyPassword, proxySecret);
                            // Applying the settings is not enough on a cold start: by the time
                            // the socket binds, ConnectionsManager has usually already failed a
                            // few connects against the not-yet-listening port and backed off.
                            // Without this kick it sits out the backoff, which is what made the
                            // dialog list appear only after ~20 seconds.
                            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                try {
                                    if (UserConfig.getInstance(a).isClientActivated()) {
                                        ConnectionsManager.getInstance(a).checkConnection();
                                    }
                                } catch (Throwable ignore) {}
                            }
                            logInfo("Re-applied native proxy settings after (re)bind on port " + finalPort);
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                });

                while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        if (!client.getInetAddress().isLoopbackAddress()) {
                            try { client.close(); } catch (IOException ignored) {}
                            continue;
                        }
                        final long acceptedAt = System.currentTimeMillis();
                        try {
                            executor.submit(() -> handleClient(client, acceptedAt));
                        } catch (java.util.concurrent.RejectedExecutionException saturated) {
                            // The bounded pool/queue (see executor's construction above) is
                            // completely full - a real overload, not a socket problem. Drop this
                            // one connection instead of falsely marking the server socket unbound,
                            // which would otherwise trigger a pointless rebind.
                            logError("Client executor saturated (" + executorStats() + "), dropping accepted connection", null);
                            try { client.close(); } catch (IOException ignored) {}
                        }
                    } catch (IOException e) {
                        isSocketBound = false;
                        if (running.get()) {
                            logInfo("Accept interrupted or socket closed, will re-bind if running.");
                        }
                    } catch (Throwable t) {
                        // A single bad client connection must never take down the accept loop.
                        isSocketBound = false;
                        logError("Unexpected error accepting/dispatching a client, continuing", t);
                    }
                }
            } catch (Throwable t) {
                // Catch everything here, not just IOException: any uncaught exception used to
                // silently kill this whole thread, after which the watchdog (which only closes
                // sockets, expecting this loop to notice and re-bind) had nobody left to react —
                // the proxy would stay dead until the whole service was restarted externally.
                isSocketBound = false;
                logError("server error, resting before retry...", t);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
        isSocketBound = false;
        running.set(false);
    }

    // ─── SOCKS5 handshake ──────────────────────────────────────────────────

    /**
     * Обрабатывает входящее соединение от Telegram NDK:
     *   1. SOCKS5 handshake
     *   2. Получаем целевой IP (DC Telegram)
     *   3. Определяем DC ID
     *   4. Устанавливаем WebSocket к Telegram DC
     *   5. Re-encrypt и проксируем MTProto трафик
     */
    // ─── AES-CTR & Crypto Helpers ──────────────────────────────────────────

    public static class AESCTR {
        private final Cipher cipher;

        public AESCTR(byte[] key, byte[] iv) throws Exception {
            cipher = Cipher.getInstance("AES/CTR/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        }

        public byte[] update(byte[] data) {
            return cipher.update(data);
        }

        public int update(byte[] in, int inOff, int len, byte[] out, int outOff) throws Exception {
            return cipher.update(in, inOff, len, out, outOff);
        }
    }

    private static class CryptoCtx {
        AESCTR cltDec;
        AESCTR cltEnc;
        AESCTR tgEnc;
        AESCTR tgDec;
    }

    private static byte[] sha256(byte[] data1, byte[] data2) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(data1);
        if (data2 != null) digest.update(data2);
        return digest.digest();
    }

    private static CryptoCtx initCrypto(byte[] clientHandshake, byte[] relayInit, byte[] secret) throws Exception {
        CryptoCtx ctx = new CryptoCtx();

        boolean useSecret = secret != null && secret.length > 0;

        // 1. Client Decryptor (Normal order of clientHandshake[8:56])
        byte[] cltDecPrekey = new byte[32];
        byte[] cltDecIv = new byte[16];
        System.arraycopy(clientHandshake, 8, cltDecPrekey, 0, 32);
        System.arraycopy(clientHandshake, 40, cltDecIv, 0, 16);
        byte[] cltDecKey = useSecret ? sha256(cltDecPrekey, secret) : cltDecPrekey;
        ctx.cltDec = new AESCTR(cltDecKey, cltDecIv);

        // 2. Client Encryptor (Reversed order of clientHandshake[8:56])
        byte[] clientReversed = reverseBytes(clientHandshake);
        byte[] cltEncPrekey = new byte[32];
        byte[] cltEncIv = new byte[16];
        System.arraycopy(clientReversed, 8, cltEncPrekey, 0, 32);
        System.arraycopy(clientReversed, 40, cltEncIv, 0, 16);
        byte[] cltEncKey = useSecret ? sha256(cltEncPrekey, secret) : cltEncPrekey;
        ctx.cltEnc = new AESCTR(cltEncKey, cltEncIv);

        // 3. Telegram Encryptor (Normal order of relayInit[8:56])
        byte[] tgEncKey = new byte[32];
        byte[] tgEncIv = new byte[16];
        System.arraycopy(relayInit, 8, tgEncKey, 0, 32);
        System.arraycopy(relayInit, 40, tgEncIv, 0, 16);
        ctx.tgEnc = new AESCTR(tgEncKey, tgEncIv);

        // 4. Telegram Decryptor (Reversed order of relayInit[8:56])
        byte[] relayReversed = reverseBytes(relayInit);
        byte[] tgDecKey = new byte[32];
        byte[] tgDecIv = new byte[16];
        System.arraycopy(relayReversed, 8, tgDecKey, 0, 32);
        System.arraycopy(relayReversed, 40, tgDecIv, 0, 16);
        ctx.tgDec = new AESCTR(tgDecKey, tgDecIv);

        // Advance 64 bytes using zero buffer on dec/enc per Python spec
        byte[] zero64 = new byte[64];
        ctx.cltDec.update(zero64);
        ctx.tgEnc.update(zero64);

        return ctx;
    }

    private static byte[] reverseBytes(byte[] handshake) {
        byte[] reversed = new byte[64];
        for (int i = 0; i < 48; i++) {
            reversed[i + 8] = handshake[55 - i];
        }
        return reversed;
    }

    private static boolean isReqPqMulti(byte[] plain) {
        if (plain == null || plain.length < 25) {
            return false;
        }
        if (plain.length == 41 && (plain[0] & 0xFF) == 0x0A) {
            for (int i = 1; i <= 8; i++) {
                if (plain[i] != 0) return false;
            }
            if ((plain[17] & 0xFF) == 0x14 && plain[18] == 0 && plain[19] == 0 && plain[20] == 0) {
                if ((plain[21] & 0xFF) == 0xF1 && (plain[22] & 0xFF) == 0x8E &&
                    (plain[23] & 0xFF) == 0x7E && (plain[24] & 0xFF) == (byte)0xBE) {
                    return true;
                }
            }
        }
        if (plain.length == 44) {
            if ((plain[0] & 0xFF) == 0x28 && plain[1] == 0 && plain[2] == 0 && plain[3] == 0) {
                for (int i = 4; i <= 11; i++) {
                    if (plain[i] != 0) return false;
                }
                if ((plain[20] & 0xFF) == 0x14 && plain[21] == 0 && plain[22] == 0 && plain[23] == 0) {
                    if ((plain[24] & 0xFF) == 0xF1 && (plain[25] & 0xFF) == 0x8E &&
                        (plain[26] & 0xFF) == 0x7E && (plain[27] & 0xFF) == (byte)0xBE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Определяет, является ли пакет (в формате MTProto Abridged) сообщением msgs_ack
     * в неавторизованном контексте (auth_key_id == 0).
     * Конструктор msgs_ack = 0x62D6B459
     */
    private static boolean isUnencryptedMsgsAck(byte[] plain) {
        // Абридж-формат: [lenByte] [8 байт auth_key_id=0] [8 байт msg_id] [4 байта msg_len] [4 байта constructor ...]
        // Итого минимум: 1 + 8 + 8 + 4 + 4 = 25 байт
        if (plain == null || plain.length < 25) return false;
        // auth_key_id должен быть 0 (байты 1-8)
        for (int i = 1; i <= 8; i++) {
            if (plain[i] != 0) return false;
        }
        // Находим начало тела: зависит от формата (Abridged: 1 байт header, Intermediate: 4 байта)
        // Для Abridged: тело начинается с байта 1 (без header-байта), но мы передаём пакет ВКЛЮЧАЯ header.
        // Структура в буфере: [abr_hdr(1)] [auth_key_id(8)] [msg_id(8)] [msg_len(4)] [body...]
        // constructor at offset 21
        if (plain.length < 25) return false;
        return (plain[21] & 0xFF) == 0x59
            && (plain[22] & 0xFF) == 0xB4
            && (plain[23] & 0xFF) == 0xD6
            && (plain[24] & 0xFF) == 0x62;
    }

    // ─── MsgSplitter ───────────────────────────────────────────────────────

    private static class MsgSplitter {
        private final int proto;
        private byte[] plainBuf = new byte[5 * 1024 * 1024];
        private int bufLen = 0;
        private boolean disabled = false;

        public MsgSplitter(int proto) {
            this.proto = proto;
        }

        public synchronized List<byte[]> split(byte[] chunk) {
            return split(chunk, 0, chunk != null ? chunk.length : 0);
        }

        public synchronized List<byte[]> split(byte[] chunk, int off, int len) {
            if (chunk == null || len == 0) {
                return new ArrayList<>();
            }
            if (disabled) {
                List<byte[]> res = new ArrayList<>();
                byte[] copy = new byte[len];
                System.arraycopy(chunk, off, copy, 0, len);
                res.add(copy);
                return res;
            }

            if (bufLen + len > plainBuf.length) {
                byte[] newBuf = new byte[Math.max(plainBuf.length * 2, bufLen + len + 1024 * 1024)];
                System.arraycopy(plainBuf, 0, newBuf, 0, bufLen);
                plainBuf = newBuf;
            }

            System.arraycopy(chunk, off, plainBuf, bufLen, len);
            bufLen += len;

            List<byte[]> parts = new ArrayList<>();
            int offset = 0;

            while (offset < bufLen) {
                Integer packetLen = nextPacketLen(offset, bufLen - offset);
                if (packetLen == null) {
                    break;
                }
                if (packetLen <= 0) {
                    byte[] tail = new byte[bufLen - offset];
                    System.arraycopy(plainBuf, offset, tail, 0, tail.length);
                    parts.add(tail);
                    offset = bufLen;
                    disabled = true;
                    break;
                }
                byte[] pkt = new byte[packetLen];
                System.arraycopy(plainBuf, offset, pkt, 0, packetLen);
                parts.add(pkt);
                offset += packetLen;
            }

            if (offset > 0) {
                int remaining = bufLen - offset;
                if (remaining > 0) {
                    System.arraycopy(plainBuf, offset, plainBuf, 0, remaining);
                }
                bufLen = remaining;
            }

            return parts;
        }

        private Integer nextPacketLen(int offset, int avail) {
            if (avail <= 0) return null;
            if (proto == PROTO_ABRIDGED_INT) {
                return nextAbridgedLen(offset, avail);
            }
            if (proto == PROTO_INTERMEDIATE_INT || proto == PROTO_SECURE_INT) {
                return nextIntermediateLen(offset, avail);
            }
            return 0;
        }

        private Integer nextAbridgedLen(int offset, int avail) {
            int first = plainBuf[offset] & 0xFF;
            int payloadLen;
            int headerLen;
            if (first == 0x7F || first == 0xFF) {
                if (avail < 4) return null;
                payloadLen = ((plainBuf[offset + 3] & 0xFF) << 16) |
                             ((plainBuf[offset + 2] & 0xFF) << 8) |
                              (plainBuf[offset + 1] & 0xFF);
                payloadLen *= 4;
                headerLen = 4;
            } else {
                payloadLen = (first & 0x7F) * 4;
                headerLen = 1;
            }
            if (payloadLen <= 0) return 0;
            int packetLen = headerLen + payloadLen;
            if (avail < packetLen) return null;
            return packetLen;
        }

        private Integer nextIntermediateLen(int offset, int avail) {
            if (avail < 4) return null;
            int payloadLen = ((plainBuf[offset + 3] & 0xFF) << 24) |
                             ((plainBuf[offset + 2] & 0xFF) << 16) |
                             ((plainBuf[offset + 1] & 0xFF) << 8) |
                              (plainBuf[offset] & 0xFF);
            payloadLen &= 0x7FFFFFFF;
            if (payloadLen <= 0) return 0;
            int packetLen = 4 + payloadLen;
            if (avail < packetLen) return null;
            return packetLen;
        }
    }

    private static int[] getDcByIpRange(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return null;
            int b0 = Integer.parseInt(parts[0]);
            int b1 = Integer.parseInt(parts[1]);
            int b2 = Integer.parseInt(parts[2]);
            int b3 = Integer.parseInt(parts[3]);

            // DC5: 91.108.56.0/22 (91.108.56.0 - 91.108.59.255)
            if (b0 == 91 && b1 == 108 && b2 >= 56 && b2 <= 59) {
                boolean isMedia = (b3 == 102 || b3 >= 128);
                return new int[]{5, isMedia ? 1 : 0};
            }
            
            // DC5: 149.154.171.0/24
            if (b0 == 149 && b1 == 154 && b2 == 171) {
                return new int[]{5, 0};
            }

            // DC1 & DC3: 149.154.175.X
            if (b0 == 149 && b1 == 154 && b2 == 175) {
                if (b3 >= 100 && b3 <= 120) {
                    boolean isMedia = (b3 == 102);
                    return new int[]{3, isMedia ? 1 : 0};
                } else {
                    boolean isMedia = (b3 == 52);
                    return new int[]{1, isMedia ? 1 : 0};
                }
            }

            // DC2 & DC4: 149.154.167.X
            if (b0 == 149 && b1 == 154 && b2 == 167) {
                if (b3 >= 90 && b3 <= 199) {
                    boolean isMedia = (b3 == 118 || b3 == 151);
                    return new int[]{4, isMedia ? 1 : 0};
                } else {
                    return new int[]{2, 0};
                }
            }

            // DC4 extra subnets: 149.154.164.X, 149.154.165.X, 149.154.166.X
            if (b0 == 149 && b1 == 154 && (b2 == 164 || b2 == 165 || b2 == 166)) {
                return new int[]{4, 1};
            }

            // DC2 extra IPs
            if (b0 == 95 && b1 == 161 && b2 == 76 && b3 == 100) {
                return new int[]{2, 0};
            }
            if (b0 == 149 && b1 == 154 && b2 == 162 && b3 == 123) {
                return new int[]{2, 1};
            }

        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Consecutive sessions that died within seconds of being established.
     *
     * <p>Previously a single such session dropped {@link #currentBaseDomain}, which sent every
     * other connection to a freshly chosen domain. Combined with a pool still holding sockets
     * for the previous domain, that produced a self-sustaining loop: a stale pooled socket
     * dies instantly, the domain flips, the pool hands out another stale socket, the domain
     * flips again. The logs of that failure show the client walking its whole domain list in
     * seconds while never actually being broken — restarting the app "fixed" it only because
     * it emptied the pool.
     */
    private static final java.util.concurrent.atomic.AtomicInteger fastFailureCount = new java.util.concurrent.atomic.AtomicInteger();
    /** How many fast failures in a row justify abandoning the current domain. */
    private static final int FAST_FAILURE_THRESHOLD = 3;

    /**
     * Domains currently getting their sessions cut short, mapped to when they are worth trying
     * again.
     *
     * <p>A domain that just triggered {@link #noteFastSessionFailure} is whatever is killing
     * sessions right now - DPI that lets the handshake through and then resets the connection, or
     * the shared Worker behind it choking under load from every other user of the same public
     * domain list. Forgetting {@link #currentBaseDomain} alone was not enough: the very next pick
     * was a uniform random draw over all ten candidates, so the domain that just failed could be
     * (and in the logs, often was) picked again immediately. This keeps it out of that draw for a
     * while so failover actually moves toward whatever is working right now instead of re-rolling
     * the same bad domain.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> domainSickUntil = new java.util.concurrent.ConcurrentHashMap<>();
    /** Short on purpose - this is "actively being cut right now", not "unreachable" like ipFailUntil's hour. */
    private static final long DOMAIN_SICK_COOLDOWN_MS = 4 * 60_000L;

    /** The declared candidates minus whichever are currently sick, or all of them if that would leave nothing. */
    private static List<String> healthyBaseDomains() {
        final long now = System.currentTimeMillis();
        List<String> healthy = new ArrayList<>();
        for (String domain : activeBaseDomains) {
            Long until = domainSickUntil.get(domain);
            if (until == null || until <= now) {
                healthy.add(domain);
            }
        }
        return healthy.isEmpty() ? new ArrayList<>(Arrays.asList(activeBaseDomains)) : healthy;
    }

    /**
     * Rotating the base domain fixes a session that dies fast because THAT domain is being cut -
     * it does nothing when the thing dying is one specific DC's upstream on the shared backend,
     * which fails identically on every domain in the list (confirmed from a real log: DC2 got a
     * clean WS accept followed by an instant close/"404" on five different domains in under 30s,
     * while nothing else about the connection - TLS, WS upgrade - ever failed). Without this,
     * healthyBaseDomains() exhausting its list just returns the whole list again, so the client
     * spins forever re-dialing a DC that is not coming back until the backend fixes it, burning a
     * real 1-3s SOCKS5 hang (a stuck outgoing message) on every single attempt. This tracks fast
     * failures per (DC, media-flag) pair, independent of which domain they happened on, and once
     * a pair has failed fast across several distinct domains in a row, backs it off outright
     * instead of retrying blindly - connectToWebSocket returns null immediately during the
     * backoff, so the SOCKS5 client gets a fast, clean failure (letting tgnet's own retry/backoff
     * take over) instead of a slow, doomed one.
     *
     * <p>Keyed by (dcId, isMedia) together, not dcId alone: the media flag is encoded into the
     * same relay-init handshake the backend uses to pick its upstream target, so DC2's main
     * connection and DC2's media connection can resolve to two entirely different upstream
     * endpoints - one broken does not imply the other is. A single dcId key would back off a
     * perfectly working half (e.g. media uploads) just because the other half (e.g. messaging)
     * is currently 404ing on the shared backend.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> dcFastFailureStreak = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> dcBackoffUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int DC_FAST_FAILURE_THRESHOLD = 3;
    private static final long DC_BACKOFF_MS = 20_000L;

    private static String dcKey(int dcId, boolean isMedia) {
        return dcId + "_" + isMedia;
    }

    private static boolean isDcInBackoff(int dcId, boolean isMedia) {
        Long until = dcBackoffUntil.get(dcKey(dcId, isMedia));
        return until != null && until > System.currentTimeMillis();
    }

    /** @return true iff this call just tripped the breaker (streak crossed the threshold). */
    private static boolean noteDcSessionOutcome(int dcId, boolean isMedia, boolean fastFailure) {
        String key = dcKey(dcId, isMedia);
        if (!fastFailure) {
            dcFastFailureStreak.remove(key);
            return false;
        }
        int streak = dcFastFailureStreak.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        if (streak >= DC_FAST_FAILURE_THRESHOLD) {
            dcFastFailureStreak.remove(key);
            dcBackoffUntil.put(key, System.currentTimeMillis() + DC_BACKOFF_MS);
            logInfo("DC" + dcId + " (media=" + isMedia + ") failed fast across " + DC_FAST_FAILURE_THRESHOLD + " distinct attempts - backing off for " + (DC_BACKOFF_MS / 1000) + "s (likely broken upstream on the shared backend, not a domain issue)");
            return true;
        }
        return false;
    }

    /** @return true iff this call just tripped the DC breaker - callers use this to also purge
     *  any already-pooled sockets for that (dc, media), since they were dialed during the same
     *  bad window and would otherwise be handed to the next client only to die instantly too. */
    private static boolean noteFastSessionFailure(String connectedDomain, int dcId, boolean isMedia) {
        boolean trippedBreaker = noteDcSessionOutcome(dcId, isMedia, true);
        if (fastFailureCount.incrementAndGet() < FAST_FAILURE_THRESHOLD) {
            return trippedBreaker;
        }
        fastFailureCount.set(0);
        if (connectedDomain != null) {
            for (String baseDomain : activeBaseDomains) {
                if (connectedDomain.endsWith(baseDomain)) {
                    domainSickUntil.put(baseDomain, System.currentTimeMillis() + DOMAIN_SICK_COOLDOWN_MS);
                    logInfo("Marking " + baseDomain + " sick for " + (DOMAIN_SICK_COOLDOWN_MS / 1000) + "s after " + FAST_FAILURE_THRESHOLD + " short-lived sessions");
                    break;
                }
            }
        }
        synchronized (domainLock) {
            logInfo("Resetting currentBaseDomain after " + FAST_FAILURE_THRESHOLD + " short-lived sessions");
            currentBaseDomain = null;
            cachedBaseAddresses.clear();
        }
        return trippedBreaker;
    }

    private static class WsConnection {
        final SSLSocket tlsSocket;
        final String domain;
        final long createdAt;

        WsConnection(SSLSocket tlsSocket, String domain) {
            this.tlsSocket = tlsSocket;
            this.domain = domain;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<WsConnection>> wsPoolMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean> wsPoolRefilling = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> activeDcs = new java.util.concurrent.ConcurrentHashMap<>();

    public void clearWsPool() {
        for (java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q : wsPoolMap.values()) {
            WsConnection conn;
            while ((conn = q.poll()) != null) {
                try { conn.tlsSocket.close(); } catch (Exception ignored) {}
            }
        }
        logInfo("Cleared WS pool due to network change");
    }

    public void warmupActiveDcs() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : activeDcs.entrySet()) {
            if (now - entry.getValue() < 15 * 60 * 1000) {
                String[] parts = entry.getKey().split("_");
                int dcId = Integer.parseInt(parts[0]);
                boolean isMedia = Boolean.parseBoolean(parts[1]);
                refillWsPoolAsync(dcId, isMedia);
            }
        }
        logInfo("Warming up WS pool for recently active DCs");
    }

    /**
     * Rebuilds the pool when the app returns to the foreground.
     *
     * <p>Backgrounding makes tgnet drop its connections, and reopening the app makes it rebuild
     * them - which is what puts "Connecting to proxy" on screen. That banner is honest: the proxy
     * service never stopped, but tgnet's connection through it has to be made again, and a fresh
     * WebSocket costs a TLS handshake plus an upgrade that measurements put at about 1.7 seconds.
     *
     * <p>The pool exists to absorb exactly that, but pooled sockets are only handed out for 30
     * seconds, so any longer absence left it useless. Warming here means the sockets are usually
     * ready before tgnet asks for them, and the banner passes quickly or never appears.
     */
    private static void primeWatchForeground() {
        try {
            org.telegram.ui.Components.ForegroundDetector.getInstance().addListener(
                    new org.telegram.ui.Components.ForegroundDetector.Listener() {
                        @Override
                        public void onBecameForeground() {
                            final TgWsProxyService service = instance;
                            if (service != null && service.running.get() && PrimeTweaks.optimizations()) {
                                service.warmupActiveDcs();
                            }
                        }

                        @Override
                        public void onBecameBackground() {
                        }
                    });
        } catch (Throwable t) {
            // The detector needs the Application to have registered its activity callbacks; if we
            // are somehow earlier than that, the pool simply keeps its old timing.
            FileLog.e("TgWsProxyService.primeWatchForeground", t);
        }
    }

    private void maintainWsPool() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, java.util.concurrent.ConcurrentLinkedQueue<WsConnection>> entry : wsPoolMap.entrySet()) {
            String key = entry.getKey();
            java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q = entry.getValue();
            
            java.util.Iterator<WsConnection> it = q.iterator();
            while (it.hasNext()) {
                WsConnection conn = it.next();
                // Same liveness probe getPooledWsConnection uses at hand-out time, run here too so
                // a socket the edge already closed gets evicted (and refilled) proactively during
                // this periodic sweep, rather than only being discovered the next time something
                // actually tries to use it.
                if (now - conn.createdAt > 100_000 || conn.tlsSocket.isClosed() || !isPooledConnectionAlive(conn.tlsSocket)) {
                    try { conn.tlsSocket.close(); } catch (Exception ignored) {}
                    it.remove();
                }
            }
            
            Long lastActive = activeDcs.get(key);
            if (lastActive != null && (now - lastActive < 15 * 60 * 1000)) {
                String[] parts = key.split("_");
                if (q.size() < poolTargetFor(Boolean.parseBoolean(parts[1]))) {
                    int dcId = Integer.parseInt(parts[0]);
                    boolean isMedia = Boolean.parseBoolean(parts[1]);
                    refillWsPoolAsync(dcId, isMedia);
                }
            } else if (lastActive != null && (now - lastActive >= 15 * 60 * 1000)) {
                activeDcs.remove(key);
            }
        }
    }

    /**
     * Tells a genuinely dead pooled socket apart from a merely idle one before it gets handed to
     * a real session.
     *
     * <p>Cloudflare (and similar edge proxies) close idle WebSockets server-side well within the
     * pool's old 30s age ceiling, but the local {@link SSLSocket#isClosed()} only ever reflects a
     * *local* close - it stays false until the next actual read/write touches the dead socket.
     * Without this check, {@link #getPooledWsConnection} would hand out a socket that looks fine,
     * {@code handleClient} would log "Session established" before ever reading a byte, and the
     * first real read would surface a WS CLOSE frame within a second or two - which is exactly the
     * "short-lived session" pattern that then marks a perfectly healthy domain sick and forces a
     * failover loop. A 1ms peek read costs nothing on a genuinely idle-but-alive socket
     * (immediately throws {@link java.net.SocketTimeoutException}, the expected/good case) and
     * catches a dead one (EOF or any other IO error) before it does any damage.
     */
    private static boolean isPooledConnectionAlive(SSLSocket socket) {
        try {
            socket.setSoTimeout(1);
            int b = socket.getInputStream().read();
            if (b == -1) {
                return false; // remote already closed (EOF)
            }
            // The server sent something unsolicited before we ever wrote a byte - not ours to
            // silently discard (a real session would need it), so treat this socket as unusable
            // rather than risk desyncing the stream.
            return false;
        } catch (java.net.SocketTimeoutException expected) {
            return true; // nothing waiting to be read - the normal, alive-and-idle case
        } catch (Exception e) {
            return false; // any other IO error means the socket is actually dead
        } finally {
            try {
                socket.setSoTimeout(0);
            } catch (Exception ignored) {}
        }
    }

    private WsConnection getPooledWsConnection(int dcId, boolean isMedia) {
        String key = dcId + "_" + isMedia;
        activeDcs.put(key, System.currentTimeMillis());
        java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q = wsPoolMap.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
        final String activeDomain = currentBaseDomain;
        WsConnection conn = null;
        while ((conn = q.poll()) != null) {
            long age = System.currentTimeMillis() - conn.createdAt;
            // Drop sockets built for a domain we have since moved away from. Handing one of
            // those out is what turned a single failure into a domain-hopping loop: it dies
            // immediately, which looks like the new domain failing too.
            boolean wrongDomain = activeDomain != null && conn.domain != null && !conn.domain.endsWith(activeDomain);
            // Idle WebSockets get closed by the edge well before the old 100s ceiling, so a
            // "fresh" pooled socket could already be dead on arrival - isPooledConnectionAlive
            // verifies liveness directly rather than only guessing from age, so the age ceiling
            // itself is just a backstop and can afford to follow the proxy mode: each step up
            // keeps idle sockets around longer, matching its bigger pool - more warm spares ready
            // rather than torn down and reconnected on every use.
            final long ageLimit;
            switch (proxyMode()) {
                case PROXY_MODE_ECONOMY: ageLimit = 20_000; break;
                case PROXY_MODE_TURBO: ageLimit = 120_000; break;
                default: ageLimit = 60_000; break;
            }
            if (age > ageLimit || wrongDomain || conn.tlsSocket.isClosed() || !isPooledConnectionAlive(conn.tlsSocket)) {
                try { conn.tlsSocket.close(); } catch (Exception ignored) {}
                continue;
            }
            logInfo("WsPool hit for DC" + dcId + " (media=" + isMedia + "), age=" + age + "ms");
            break;
        }
        refillWsPoolAsync(dcId, isMedia);
        return conn;
    }

    /** Closes and forgets every pooled connection. Called when the base domain changes. */
    private void discardPooledConnections() {
        int dropped = 0;
        for (java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q : wsPoolMap.values()) {
            WsConnection conn;
            while ((conn = q.poll()) != null) {
                try { conn.tlsSocket.close(); } catch (Exception ignored) {}
                dropped++;
            }
        }
        if (dropped > 0) {
            logInfo("Discarded " + dropped + " pooled connections after base domain change");
        }
    }

    /**
     * How many spare connections to keep for a data centre.
     *
     * <p>Deeper for media, because media does not arrive one file at a time: a tray of stories or
     * a screen of photos asks for a dozen files at once, and every request beyond the pool pays
     * for a fresh WebSocket - a handshake measured at roughly 1.7 seconds through this tunnel. A
     * pool of two covers a conversation; it does not cover a burst.
     */
    private static int poolTargetFor(boolean isMedia) {
        if (!PrimeTweaks.optimizations()) {
            return 2;
        }
        // Bumped from 2/4: a burst of new SOCKS5 connections (e.g. everything tgnet opens
        // around one big outgoing message) mostly missed this pool at the old size and went
        // to a live connect instead - which is exactly what synchronously hammers the
        // capacity-limited shared backends (149.154.167.220, the .co.uk balancer) and is the
        // real mechanism behind "sending a lot at once breaks the proxy". A deeper pool means
        // more of that burst gets served from an already-warm socket instantly instead of
        // adding to the concurrent-connect spike.
        //
        // Media specifically scales further with proxy mode: individual Worker-relayed sessions
        // now have a confirmed, real ceiling on how long they last (Cloudflare's own CPU-time
        // budget per session - see TgWsProxyService's connectToWebSocket ordering comment), so a
        // burst of parallel file/thumbnail requests needs a deeper standing pool to keep pulling
        // fresh warm sockets rather than queuing behind connects. Turbo asks for exactly this
        // trade (more standing connections, more battery) explicitly; Economy stays put.
        if (isMedia) {
            switch (proxyMode()) {
                case PROXY_MODE_ECONOMY: return 6;
                case PROXY_MODE_TURBO: return 12;
                default: return 8;
            }
        }
        return 5;
    }

    private void refillWsPoolAsync(int dcId, boolean isMedia) {
        String key = dcId + "_" + isMedia;
        java.util.concurrent.atomic.AtomicBoolean refilling = wsPoolRefilling.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicBoolean(false));
        if (refilling.compareAndSet(false, true)) {
            poolWarmExecutor.submit(() -> {
                try {
                    java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q = wsPoolMap.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
                    while (q.size() < poolTargetFor(isMedia)) {
                        // Every spare sourced from connectToWebSocket lands on the same shared
                        // backend as the live session it is meant to back up (dc-redirect or the
                        // .co.uk balancer) - a synchronized overload there kills the active
                        // session AND every spare at once, which is exactly what left the pool
                        // empty right when a live session just died and a fast replacement
                        // mattered most. Alternating which path is tried first (by the pool's
                        // current size, not a flag needing its own state) keeps at least one spare
                        // on the worker pool - an unrelated Cloudflare account - even while the
                        // primary path is perfectly healthy, instead of only diversifying once
                        // something has already broken.
                        String dst = DC_DEFAULT_IPS.get(dcId);
                        boolean workerFirst = dst != null && (q.size() % 2 == 1);
                        WsConnection conn = workerFirst ? connectThroughWorker(dcId, isMedia, dst) : connectToWebSocket(dcId, isMedia);
                        if (conn == null) {
                            conn = workerFirst ? connectToWebSocket(dcId, isMedia)
                                    : (dst != null ? connectThroughWorker(dcId, isMedia, dst) : null);
                        }
                        if (conn != null) {
                            q.add(conn);
                            logInfo("WsPool refilled for DC" + dcId + " (media=" + isMedia + "), pool size: " + q.size());
                        } else {
                            break;
                        }
                    }
                } finally {
                    refilling.set(false);
                }
            });
        }
    }

    /**
     * Upstream tg-ws-proxy's actual primary connection path for DC2/DC4 (its {@code dc_redirects}
     * config, default {@code {2: '149.154.167.220', 4: '149.154.167.220'}}) - dial this fixed,
     * known-good Cloudflare IP directly, no DNS resolution of any {@code kwsN.<domain>} decoy at
     * all, using Telegram's own {@code kws{dc}.web.telegram.org} as the SNI/Host (a domain that
     * can't be blocked without breaking Telegram Web itself). The {@code .co.uk} domain-rotation
     * balancer this file was entirely built around is, in upstream, only ever reached for DCs
     * *not* in this map (DC1/3/5 by default) or as a last-resort fallback - it was never meant to
     * carry DC2/DC4 traffic, which is most of what any account actually generates. Confirmed live:
     * every {@code .co.uk} candidate that was failing ~80% of the time direct-resolved failed
     * identically, while this fixed IP answered 101 cleanly 7/7 on the exact same domains used only
     * as Host/SNI. This is why the whole day's domain-rotation debugging never fully fixed anything -
     * it was hardening a path upstream barely uses for this traffic, while the actual primary path
     * was simply missing from the port.
     */
    private static final java.util.Map<Integer, String> DC_REDIRECTS = new java.util.HashMap<Integer, String>() {{
        put(2, "149.154.167.220");
        put(4, "149.154.167.220");
    }};

    /** Upstream's {@code DC_DEFAULT_IPS}: each DC's real address. Used as the {@code dst=} target
     *  for the CF-worker fallback - the raw SOCKS5 destination this service parses is usually the
     *  synthetic {@code "dc2.telegram"}-style label this app's own ConnectionsManager sends (see
     *  the SOCKS5 handler above), never a real hostname a Worker's {@code connect()} could resolve,
     *  so forwarding it as-is made that fallback a guaranteed no-op every time it was reached. */
    private static final java.util.Map<Integer, String> DC_DEFAULT_IPS = new java.util.HashMap<Integer, String>() {{
        put(1, "149.154.175.50");
        put(2, "149.154.167.51");
        put(3, "149.154.175.100");
        put(4, "149.154.167.91");
        put(5, "149.154.171.5");
        put(203, "91.105.192.100");
    }};

    /** Matches upstream's {@code ws_domains()}: media traffic tries the {@code -1} variant first. */
    private static String[] wsDomainsForDc(int dcId, boolean isMedia) {
        String base = "kws" + dcId + ".web.telegram.org";
        String alt = "kws" + dcId + "-1.web.telegram.org";
        return isMedia ? new String[]{alt, base} : new String[]{base, alt};
    }

    /**
     * A burst of new SOCKS5 connections (everything tgnet opens around one big outgoing
     * message) used to fire its TLS handshakes to {@code 149.154.167.220} essentially
     * simultaneously - a real log showed a dozen-plus connects landing within the same second,
     * synchronously hammering the one shared IP every DC2/DC4 session goes through by default.
     * This staggers real network attempts to that fixed IP by a small, cheap amount so a burst
     * turns into a fast trickle instead of a synchronized spike - imperceptible per-connection
     * (tens of ms), but it changes the shape of the load this shared backend actually sees.
     */
    private static final Object dcRedirectPacer = new Object();
    private static volatile long dcRedirectNextSlotAt = 0;
    private static final long DC_REDIRECT_MIN_GAP_MS = 60L;

    private static void paceDcRedirectAttempt() {
        long waitMs;
        synchronized (dcRedirectPacer) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, dcRedirectNextSlotAt);
            waitMs = slot - now;
            dcRedirectNextSlotAt = slot + DC_REDIRECT_MIN_GAP_MS;
        }
        if (waitMs > 0) {
            try { Thread.sleep(waitMs); } catch (InterruptedException ignored) {}
        }
    }

    private WsConnection tryDcRedirectConnect(int dcId, boolean isMedia) {
        String fixedIp = DC_REDIRECTS.get(dcId);
        if (fixedIp == null) {
            return null;
        }
        for (String domain : wsDomainsForDc(dcId, isMedia)) {
            SSLSocket tlsSocket = null;
            final long startedAt = System.currentTimeMillis();
            try {
                paceDcRedirectAttempt();
                tlsSocket = createTlsSocketToFixedIp(fixedIp, domain, 443, 6_000);
                wsHandshake(tlsSocket, domain, dcId, false);
                logInfo("DC redirect: connected to " + fixedIp + " as " + domain + " (DC" + dcId + ") in "
                        + (System.currentTimeMillis() - startedAt) + "ms");
                return new WsConnection(tlsSocket, domain);
            } catch (Exception e) {
                logError("DC redirect via " + fixedIp + " as " + domain + " failed", e);
                if (tlsSocket != null) {
                    try { tlsSocket.close(); } catch (IOException ignored) {}
                }
            }
        }
        return null;
    }

    private SSLSocket createTlsSocketToFixedIp(String ip, String sniHost, int port, int timeoutMs) throws IOException {
        Socket plainSocket = new Socket();
        tuneTunnelSocketBuffers(plainSocket);
        plainSocket.connect(new java.net.InetSocketAddress(InetAddress.getByName(ip), port), timeoutMs);
        SSLSocket tlsSocket = (SSLSocket) sslSocketFactory.createSocket(plainSocket, sniHost, port, true);
        tlsSocket.setUseClientMode(true);
        tlsSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        tlsSocket.setTcpNoDelay(true);
        tlsSocket.setSoTimeout(timeoutMs);
        return tlsSocket;
    }

    private WsConnection connectToWebSocket(int dcId, boolean isMedia) {
        // Checked before EITHER path now: the DC-redirect fixed IP turned out to be shared
        // public infrastructure too (same "Server: cloudflare" as the .co.uk balancer) - under
        // sustained load it starts closing every session instantly with the same 1000/"404"
        // signature, and without this check first, that path bypassed the breaker entirely and
        // kept hammering a dead endpoint in a tight loop instead of backing off.
        if (isDcInBackoff(dcId, isMedia)) {
            logInfo("DC" + dcId + " (media=" + isMedia + ") is in backoff after repeated fast failures, skipping connect attempt");
            return null;
        }

        // Reverted back to dc-redirect first, worker second (the original ordering from before
        // workers existed, which is what stayed stable for every user at 12.9.0.6). The brief
        // window where workers were tried first was based on one evening's logs showing
        // 149.154.167.220 failing under a client-side connect burst that the pacer/backoff added
        // since then now smooths out. What logs since then actually show is the opposite problem:
        // individual free-tier Workers self-terminate an established session after roughly
        // 15-30s (a WS CLOSE frame with no status code, from many different worker accounts, not
        // just one) - almost certainly a Cloudflare Workers free-plan connection-duration limit,
        // not anything this app's code controls. A worker session dying mid-relay is what tears
        // down tgnet's own connection through the local SOCKS proxy and shows "Connecting to
        // proxy" on screen, over and over. The direct IP is real Telegram infrastructure with no
        // such lifetime cap, so it goes back to being the primary path; workers remain in the
        // spare pool (refillWsPoolAsync's alternation) and as the fallback here if the direct IP
        // is blocked outright.
        WsConnection viaRedirect = tryDcRedirectConnect(dcId, isMedia);
        if (viaRedirect != null) {
            return viaRedirect;
        }

        String dcDst = DC_DEFAULT_IPS.get(dcId);
        if (dcDst != null) {
            WsConnection viaWorker = connectThroughWorker(dcId, isMedia, dcDst);
            if (viaWorker != null) {
                return viaWorker;
            }
        }

        SSLSocket tlsSocket = null;
        String chosenDomain = null;

        try {
            Thread.sleep(20 + RANDOM.nextInt(280));
        } catch (InterruptedException ignored) {}

        String baseDomain;
        synchronized (domainLock) {
            final String pinned = forcedDomain();
            if (currentBaseDomain == null && !pinned.isEmpty()) {
                // The user picked one. No probing, no failover to a different domain: if it stops
                // working they will see it stop working, which is the point of pinning it.
                currentBaseDomain = pinned;
                cachedBaseAddresses.clear();
                lastDomainSelectionTime = System.currentTimeMillis();
                logInfo("Using domain pinned by the user: " + pinned);
            }
            if (currentBaseDomain == null) {
                if (System.currentTimeMillis() - lastDomainSelectionTime > 30_000) {
                    logInfo("Selecting base domain from candidates using pair-wise latency tests...");
                    List<String> candidates = healthyBaseDomains();
                    Collections.shuffle(candidates);

                    final String[] selected = new String[1];
                    final int targetDcId = dcId;

                    // Was: pairs of two, one pair at a time, 1400 ms per pair. With ten candidates
                    // that is up to seven seconds - and this whole block holds a lock on the class,
                    // so every other DC's connection attempt waits behind it. Probing all of them
                    // at once costs the same 1400 ms whether one domain is reachable or none is,
                    // and the winner is still whichever answers first. executor is a cached pool,
                    // so the extra probes cost threads, not time.
                    final java.util.concurrent.CountDownLatch firstAnswer = new java.util.concurrent.CountDownLatch(1);
                    final java.util.concurrent.CountDownLatch allDone = new java.util.concurrent.CountDownLatch(candidates.size());
                    for (int i = 0; i < candidates.size(); i++) {
                        final String dom = candidates.get(i);
                        executor.submit(() -> {
                            SSLSocket testSocket = null;
                            try {
                                String testHost = "kws" + targetDcId + "." + dom;
                                InetAddress[] testResolve = resolveWithFallbackDns(testHost);
                                if (testResolve == null || testResolve.length == 0) {
                                    testHost = "kws." + dom;
                                    testResolve = resolveWithFallbackDns(testHost);
                                    if (testResolve == null || testResolve.length == 0) {
                                        testHost = dom;
                                    }
                                }
                                testSocket = (SSLSocket) sslSocketFactory.createSocket();
                                testSocket.connect(new java.net.InetSocketAddress(testHost, 443), 1200);
                                testSocket.setSoTimeout(1200);
                                testSocket.startHandshake();
                                synchronized (selected) {
                                    if (selected[0] == null) {
                                        selected[0] = dom;
                                    }
                                }
                                firstAnswer.countDown();
                            } catch (Exception ignored) {
                            } finally {
                                if (testSocket != null) {
                                    try { testSocket.close(); } catch (IOException ignored) {}
                                }
                                allDone.countDown();
                            }
                        });
                    }

                    try {
                        // Returns as soon as one domain answers; falls through at 1400 ms if none
                        // does, without waiting on the stragglers.
                        if (!firstAnswer.await(1400, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            allDone.await(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException ignored) {}

                    if (selected[0] == null) {
                        selected[0] = candidates.get(RANDOM.nextInt(candidates.size()));
                        logInfo("All latency probes failed. Selected fallback: " + selected[0]);
                        PrimeStartupTrace.mark("!! proxy: all domain probes failed, guessing " + selected[0]);
                    } else {
                        logInfo("Fastest base domain selected: " + selected[0]);
                        PrimeStartupTrace.mark("proxy: domain selected " + selected[0] + " (DC" + dcId + ")");
                    }
                    currentBaseDomain = selected[0];
                    cachedBaseAddresses.clear();
                    lastDomainSelectionTime = System.currentTimeMillis();
                } else {
                    List<String> healthy = healthyBaseDomains();
                    currentBaseDomain = healthy.get(RANDOM.nextInt(healthy.size()));
                    cachedBaseAddresses.clear();
                    logInfo("Quick failover (cooldown active): selected random domain: " + currentBaseDomain);
                }
                // Sockets pooled for the previous domain are worthless now, and worse than
                // worthless if handed out — they fail instantly and look like a new outage.
                discardPooledConnections();
                baseDomain = currentBaseDomain;
            } else {
                baseDomain = currentBaseDomain;
            }
        }
        boolean isUnified = false;
        String wsDomain = "kws" + dcId + "." + baseDomain;

        if (System.currentTimeMillis() < ipFailUntil.getOrDefault(wsDomain, 0L)) {
            logInfo(wsDomain + " is in IP fail cooldown, skipping.");
            synchronized (domainLock) {
                if (baseDomain.equals(currentBaseDomain)) {
                    currentBaseDomain = null;
                    cachedBaseAddresses.clear();
                }
            }
            return null;
        }

        InetAddress[] checkResolve = resolveWithFallbackDns(wsDomain);
        if (checkResolve == null || checkResolve.length == 0) {
            wsDomain = baseDomain;
        }

        logInfo("Connecting to CF proxy " + wsDomain + ":443 for DC" + dcId + " (unified=" + isUnified + ")");
        // Timed because a cold start showed nearly two minutes between "dialogs requested" and
        // "dialogs visible" with the main thread idle throughout - the wait was in the tunnel,
        // and the tunnel recorded nothing at all. Each leg is measured separately: a slow TLS
        // connect, a slow upgrade and a fast connect that is simply retried many times are three
        // different faults that look identical from the outside.
        final long connectStartedAt = System.currentTimeMillis();
        long tlsReadyAt = connectStartedAt;
        try {
            tlsSocket = createTlsSocketWithIpv4Preference(wsDomain, 443, 10_000);
            tlsSocket.setUseClientMode(true);
            tlsSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            tlsSocket.setTcpNoDelay(true);
            tlsSocket.setSoTimeout(10_000); // 10s timeout for handshake

            tlsReadyAt = System.currentTimeMillis();
            wsHandshake(tlsSocket, wsDomain, dcId, isUnified);
            chosenDomain = wsDomain;
            final long now = System.currentTimeMillis();
            primeTraceConnect("proxy: DC" + dcId + (isMedia ? "m" : "") + " up in " + (now - connectStartedAt)
                    + " ms (tls " + (tlsReadyAt - connectStartedAt) + ", ws " + (now - tlsReadyAt) + ") via " + wsDomain);
            logInfo("Successfully connected to " + wsDomain + " (DC" + dcId + ")");

            // clear IP fail cooldown on success
            ipFailUntil.remove(wsDomain);
            consecutiveConnectFailures.set(0);
            return new WsConnection(tlsSocket, chosenDomain);
        } catch (Exception e) {
            boolean isTimeout = e instanceof java.net.SocketTimeoutException || (e.getMessage() != null && e.getMessage().contains("timed out"));
            // No fronting fallback here anymore: it never once succeeded across a full day of
            // real logs, and it never could - Cloudflare stopped honoring SNI/Host mismatches for
            // routing back in 2018 specifically to kill this exact technique. Every attempt landed
            // on max.ru's or yandex.ru's own real backend (confirmed by their own Server/response
            // headers, e.g. "Server: kittenx" for max.ru, X-Yandex-Req-Id for yandex.ru), which
            // naturally 404/406'd on our /apiws path since it has nothing to do with our Worker.
            // It only ever added two guaranteed-doomed round trips (1-2s each) on top of every
            // failed direct attempt. Failing fast here instead lets the DC-level circuit breaker
            // and domain rotation react sooner, which matters far more than a fallback that has a
            // 0% success rate.
            primeTraceConnect("!! proxy: DC" + dcId + (isMedia ? "m" : "") + " FAILED after "
                    + (System.currentTimeMillis() - connectStartedAt) + " ms on " + wsDomain + " (" + e.getMessage() + ")");

            if (isTimeout) {
                ipFailUntil.put(wsDomain, System.currentTimeMillis() + 3600_000L);
                logInfo("Added " + wsDomain + " to ipFail cooldown for 1 hour");
            }

            // Dropping the domain after a single failed connect made every transient error
            // cost a full re-probe, and the re-probe holds the class lock - so one flaky
            // socket stalled every other DC too. A domain has to fail twice in a row before
            // we give up on it; any success resets the count.
            if (consecutiveConnectFailures.incrementAndGet() >= 2) {
                synchronized (domainLock) {
                    if (baseDomain.equals(currentBaseDomain)) {
                        currentBaseDomain = null;
                        cachedBaseAddresses.clear();
                        logInfo("Resetting currentBaseDomain (failover triggered)");
                    }
                }
                consecutiveConnectFailures.set(0);
            }
            if (tlsSocket != null) {
                try { tlsSocket.close(); } catch (IOException ignored) {}
            }
            return null;
        }
    }

    /** Every log line for one client session carries this so the several lines it produces can be
     *  told apart from a concurrent session's interleaved lines, and correlated end to end - which
     *  domain it connected through, whether it died fast, how many bytes actually moved - by
     *  grepping one number instead of guessing from timestamps and DC/media alone. */
    private static final java.util.concurrent.atomic.AtomicLong sessionIdCounter = new java.util.concurrent.atomic.AtomicLong();

    private String executorStats() {
        if (!(executor instanceof java.util.concurrent.ThreadPoolExecutor)) {
            return "n/a";
        }
        java.util.concurrent.ThreadPoolExecutor tpe = (java.util.concurrent.ThreadPoolExecutor) executor;
        return "active=" + tpe.getActiveCount() + "/" + tpe.getMaximumPoolSize()
                + ", queued=" + tpe.getQueue().size() + ", poolSize=" + tpe.getPoolSize();
    }

    private void handleClient(Socket client, long acceptedAt) {
        final long sid = sessionIdCounter.incrementAndGet();
        addClientSocket(client);
        boolean semaphoreAcquired = false;
        int dcId = -1;
        boolean isMedia = false;
        try {
            client.setTcpNoDelay(true);
            client.setKeepAlive(true);
            client.setSoTimeout(30_000);

            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // dispatchDelay is the time this connection sat accepted-but-unhandled, waiting for an
            // executor thread. A real log showed brand-new local SOCKS5 connections failing with
            // "Unsupported SOCKS5 command: -1" (i.e. read() got EOF before a single real byte)
            // right after a burst of pool-warming work - this number, plus the executor's own
            // queue/active-thread snapshot, is what actually confirms or rules out "tgnet gave up
            // waiting for a thread" instead of guessing from proximity in the log.
            long dispatchDelay = System.currentTimeMillis() - acceptedAt;
            logInfo("[s" + sid + "] New SOCKS5 connection from " + client.getRemoteSocketAddress()
                    + (dispatchDelay > 20 ? " (dispatch delay " + dispatchDelay + "ms, executor: " + executorStats() + ")" : ""));

            // SOCKS5 handshake: [ver=5, nmethods, methods...]
            int ver = in.read();
            if (ver != 5) {
                logInfo("Invalid SOCKS5 version: " + ver);
                client.close();
                return;
            }
            int nmethods = in.read();
            byte[] methods = new byte[nmethods];
            readFully(in, methods, 0, nmethods);

            // Ответ: [ver=5, method=0 (NO AUTH)]
            out.write(new byte[]{0x05, 0x00});
            out.flush();

            // SOCKS5 request: [ver=5, cmd, rsv, atyp, dst, port]
            int reqVer = in.read();
            int cmd    = in.read();
            in.read(); // rsv
            int atyp   = in.read();

            if (reqVer != 5 || cmd != 1) { // cmd=1 = CONNECT
                logInfo("[s" + sid + "] Unsupported SOCKS5 command: " + cmd + " (reqVer=" + reqVer
                        + ", waited " + (System.currentTimeMillis() - acceptedAt) + "ms since accept)");
                out.write(new byte[]{0x05, 0x07, 0x00, 0x01, 0,0,0,0, 0,0});
                out.flush();
                client.close();
                return;
            }

            String destIp;
            int destPort;

            if (atyp == 1) { // IPv4
                byte[] addr = new byte[4];
                readFully(in, addr, 0, 4);
                destIp = (addr[0] & 0xFF) + "." + (addr[1] & 0xFF) + "." + (addr[2] & 0xFF) + "." + (addr[3] & 0xFF);
                destPort = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (atyp == 3) { // Domain
                int len = in.read();
                byte[] domain = new byte[len];
                readFully(in, domain, 0, len);
                destIp = new String(domain);
                destPort = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (atyp == 4) { // IPv6
                byte[] addr = new byte[16];
                readFully(in, addr, 0, 16);
                try {
                    destIp = java.net.InetAddress.getByAddress(addr).getHostAddress();
                } catch (Exception e) {
                    destIp = "::1";
                }
                destPort = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else {
                logInfo("Unsupported address type: " + atyp);
                out.write(new byte[]{0x05, 0x08, 0x00, 0x01, 0,0,0,0, 0,0});
                out.flush();
                client.close();
                return;
            }

            logInfo("[s" + sid + "] SOCKS5 Request to " + destIp + ":" + destPort);

            // Определяем DC
            int[] dcInfo = null;
            if (atyp == 3 && destIp.startsWith("dc") && destIp.endsWith(".telegram")) {
                try {
                    boolean mediaFlag = destIp.contains("media");
                    String numStr = destIp.replace("dc", "").replace("media", "").replace(".telegram", "");
                    int parsedDcId = Integer.parseInt(numStr);
                    dcInfo = new int[]{parsedDcId, mediaFlag ? 1 : 0};
                } catch (Exception ignored) {}
            }
            if (dcInfo == null) {
                dcInfo = IP_TO_DC.get(destIp);
            }
            if (dcInfo == null) {
                dcInfo = getDcByIpRange(destIp);
            }
            dcId = -1;
            isMedia = false;

            if (dcInfo != null) {
                dcId = dcInfo[0];
                isMedia = dcInfo[1] == 1;
            } else if (atyp == 4) {
                // Пытаемся распарсить DC из IPv6 адреса Telegram (например, 2001:67c:4e8:f002::a)
                // Байты: 20 01 | 06 7c | 04 e8 | f0 02 ...
                byte[] addrBytes = null;
                try {
                    addrBytes = java.net.InetAddress.getByName(destIp).getAddress();
                } catch (Exception ignored) {}
                
                if (addrBytes != null && addrBytes.length == 16) {
                    if (addrBytes[0] == 0x20 && addrBytes[1] == 0x01 && 
                        addrBytes[2] == 0x06 && addrBytes[3] == 0x7c && 
                        addrBytes[4] == 0x04 && addrBytes[5] == (byte)0xe8) {
                        
                        if (addrBytes[6] == (byte)0xf0) {
                            dcId = addrBytes[7] & 0xFF;
                        }
                    }
                }
            }

            if (dcId == -1) {
                logInfo("No DC match for " + destIp + ", connecting direct...");
                connectDirect(client, in, out, destIp, destPort);
                return;
            }

            // Нормализуем DC203 -> DC2
            if (dcId == 203) dcId = 2;

            logInfo("[s" + sid + "] Matched DC" + dcId + " (media=" + isMedia + ")");

            // SOCKS5 ответ: успех
            out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
            out.flush();

            // Теперь клиент (Telegram NDK) начнёт слать MTProto данные
            // Читаем handshake (64 байта)
            client.setSoTimeout(15_000);
            byte[] handshake = new byte[HANDSHAKE_LEN];
            try {
                readFully(in, handshake, 0, HANDSHAKE_LEN);
            } catch (IOException e) {
                logInfo("Client closed connection before sending handshake");
                client.close();
                return;
            }

            // Получаем готовое WebSocket-соединение из пула (или создаем на лету)
            java.util.concurrent.Semaphore sem = getSemaphoreForDc(dcId, isMedia);
            long semWaitStart = System.currentTimeMillis();
            try {
                if (!sem.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    logInfo("[s" + sid + "] Timeout waiting for connection semaphore for DC" + dcId
                            + " (media=" + isMedia + ", " + sem.getQueueLength() + " others also waiting), rejecting duplicate connection");
                    client.close();
                    return;
                }
                semaphoreAcquired = true;
                long semWait = System.currentTimeMillis() - semWaitStart;
                if (semWait > 50) {
                    logInfo("[s" + sid + "] Waited " + semWait + "ms for DC" + dcId + " (media=" + isMedia + ") connection semaphore");
                }
            } catch (InterruptedException e) {
                logInfo("[s" + sid + "] Interrupted waiting for connection semaphore for DC" + dcId);
                return;
            }

            long wsConnectStart = System.currentTimeMillis();
            WsConnection wsConn = getPooledWsConnection(dcId, isMedia);
            boolean fromPool = wsConn != null;
            if (wsConn == null) {
                wsConn = connectToWebSocket(dcId, isMedia);
            }
            if (wsConn == null) {
                // Everything of ours is unreachable - last resort, the CF-worker pool. destIp is
                // usually the synthetic "dc2.telegram" label (see the SOCKS5 parsing above), not
                // a real address a Worker's connect() could resolve, so it can't be forwarded
                // as-is; a literal IPv4 destIp (the rarer real-CONNECT case) is used verbatim,
                // otherwise DC_DEFAULT_IPS supplies the real target for this DC.
                String workerDst = destIp != null && destIp.matches("\\d{1,3}(\\.\\d{1,3}){3}")
                        ? destIp : DC_DEFAULT_IPS.get(dcId);
                wsConn = connectThroughWorker(dcId, isMedia, workerDst);
            }
            if (wsConn == null) {
                // Absolute last resort, upstream's own _tcp_fallback: skip Cloudflare entirely
                // and dial the real Telegram IP directly, speaking the same obfuscated2 handshake
                // raw - exactly what an unproxied client does. Useless against SNI/domain-based
                // blocking (that's what the whole rest of this file exists to route around), but
                // free capacity-wise: it shares nothing with the Cloudflare-fronted paths that
                // just failed, so it survives exactly the "shared backend is out of capacity"
                // failure mode those paths cannot.
                String rawIp = DC_DEFAULT_IPS.get(dcId);
                if (rawIp != null && tryRawTcpFallback(client, in, out, handshake, dcId, isMedia, rawIp)) {
                    return;
                }
                logInfo("All CF proxy domains failed!");
                client.close();
                return;
            }
            SSLSocket tlsSocket = wsConn.tlsSocket;
            String chosenDomain = wsConn.domain;

            // Генерируем relay init для DC
            byte[] relayInit = generateRelayInit(handshake, dcId, isMedia);

            // Инициализируем криптографию
            CryptoCtx cryptoCtx;
            int protoVal;
            try {
                // 1. Декодируем handshake с помощью временного дешифратора, чтобы определить тип протокола
                byte[] cltDecPrekey = new byte[32];
                byte[] cltDecIv = new byte[16];
                System.arraycopy(handshake, 8, cltDecPrekey, 0, 32);
                System.arraycopy(handshake, 40, cltDecIv, 0, 16);
                byte[] cltDecKey = cltDecPrekey; // No hashing for direct connections
                AESCTR tempDec = new AESCTR(cltDecKey, cltDecIv);
                byte[] decrypted = tempDec.update(handshake);

                protoVal = ((decrypted[PROTO_TAG_POS] & 0xFF) << 24) |
                           ((decrypted[PROTO_TAG_POS + 1] & 0xFF) << 16) |
                           ((decrypted[PROTO_TAG_POS + 2] & 0xFF) << 8) |
                            (decrypted[PROTO_TAG_POS + 3] & 0xFF);
                
                // Запасной вариант если протокод не определился
                if (protoVal != PROTO_ABRIDGED_INT && protoVal != PROTO_INTERMEDIATE_INT && protoVal != PROTO_SECURE_INT) {
                    protoVal = PROTO_ABRIDGED_INT;
                }

                logInfo("Negotiated transport protocol: " + (protoVal == PROTO_ABRIDGED_INT ? "Abridged" : "Intermediate"));

                // 2. Создаем постоянный контекст шифрования (внутри initCrypto cltDec и tgEnc будут правильно смещены на 64 байта)
                cryptoCtx = initCrypto(handshake, relayInit, new byte[0]); 
            } catch (Exception e) {
                logError("Crypto initialization failed", e);
                client.close();
                tlsSocket.close();
                return;
            }

            // Инициализируем MsgSplitter
            MsgSplitter splitter;
            try {
                splitter = new MsgSplitter(protoVal);
            } catch (Exception e) {
                logError("MsgSplitter initialization failed", e);
                client.close();
                tlsSocket.close();
                return;
            }

            InputStream wsIn = new BufferedInputStream(tlsSocket.getInputStream(), TUNNEL_SOCKET_BUFFER_BYTES);
            OutputStream wsOut = tlsSocket.getOutputStream();

            // Отправляем relay init в WebSocket бинарном фрейме
            sendWsFrame(wsOut, relayInit, 0, relayInit.length);
            logInfo("Sent obfuscation handshake to remote WS");

            client.setKeepAlive(true);
            tlsSocket.setKeepAlive(true);
            client.setSoTimeout(120_000); // 2 minutes read timeout
            tlsSocket.setSoTimeout(120_000); // 2 minutes read timeout

            primeTraceConnect("proxy: DC" + dcId + (isMedia ? "m" : "") + " session established");
            logInfo("[s" + sid + "] Session established via " + chosenDomain + " (" + (fromPool ? "from pool" : "fresh connect, " + (System.currentTimeMillis() - wsConnectStart) + "ms")
                    + "), entering active relay bridge...");

            // Запускаем двунаправленный мост с ре-шифрованием
            bridgeConnections(client, in, out, tlsSocket, wsIn, wsOut, cryptoCtx, splitter, dcId, isMedia, chosenDomain, sid);

        } catch (Exception e) {
            logError("handleClient error", e);
        } finally {
            if (semaphoreAcquired) {
                java.util.concurrent.Semaphore sem = getSemaphoreForDc(dcId, isMedia);
                sem.release();
            }
            removeClientSocket(client);
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void connectDirect(Socket client, InputStream in, OutputStream out,
                               String destIp, int destPort) throws IOException {
        // Успех SOCKS5
        out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
        out.flush();

        try (Socket remote = new Socket(destIp, destPort)) {
            remote.setTcpNoDelay(true);
            client.setSoTimeout(0);

            InputStream remIn = remote.getInputStream();
            OutputStream remOut = remote.getOutputStream();

            // Простой двунаправленный pipe
            Thread t = new Thread(() -> {
                try { pipe(in, remOut); } catch (IOException ignored) {}
                try { remote.close(); } catch (IOException ignored) {}
            });
            t.setDaemon(true);
            t.start();

            try { pipe(remIn, out); } catch (IOException ignored) {}
        }
    }

    /**
     * Dials the real Telegram IP directly and bridges the same obfuscated2 handshake and
     * re-encryption a WS-based session would use, minus the WS framing and the
     * Abridged->Intermediate repackaging that only exists because Cloudflare's apiws backend
     * requires it - this talks straight to Telegram, which speaks whatever transport protocol
     * the client itself negotiated, so the bytes go through unmodified aside from the
     * outer-to-inner key re-encryption every path here does.
     */
    private boolean tryRawTcpFallback(Socket client, InputStream in, OutputStream out,
                                       byte[] handshake, int dcId, boolean isMedia, String realIp) {
        Socket tcpSocket = null;
        try {
            tcpSocket = new Socket();
            tuneTunnelSocketBuffers(tcpSocket);
            tcpSocket.connect(new java.net.InetSocketAddress(InetAddress.getByName(realIp), 443), 8_000);
            tcpSocket.setTcpNoDelay(true);

            byte[] relayInit = generateRelayInit(handshake, dcId, isMedia);
            CryptoCtx ctx = initCrypto(handshake, relayInit, new byte[0]);

            OutputStream tcpOut = tcpSocket.getOutputStream();
            InputStream tcpIn = tcpSocket.getInputStream();
            tcpOut.write(relayInit);
            tcpOut.flush();
            logInfo("Raw TCP fallback: connected directly to " + realIp + " (DC" + dcId + "), no Cloudflare intermediary");

            client.setKeepAlive(true);
            tcpSocket.setKeepAlive(true);
            client.setSoTimeout(120_000);
            tcpSocket.setSoTimeout(120_000);

            bridgeRawTcp(client, in, out, tcpSocket, tcpIn, tcpOut, ctx, dcId, isMedia);
            return true;
        } catch (Exception e) {
            logError("Raw TCP fallback to " + realIp + " failed", e);
            if (tcpSocket != null) {
                try { tcpSocket.close(); } catch (IOException ignored) {}
            }
            return false;
        }
    }

    /** Bidirectional client&lt;-&gt;real-Telegram-TCP bridge with re-encryption only - no WS
     *  frames, no packet splitting/repackaging, matching upstream's {@code _bridge_tcp_reencrypt}. */
    private void bridgeRawTcp(Socket client, InputStream in, OutputStream out, Socket tcpSocket,
                               InputStream tcpIn, OutputStream tcpOut, CryptoCtx ctx, int dcId, boolean isMedia) {
        AtomicBoolean closed = new AtomicBoolean(false);
        final long sessionStartTime = System.currentTimeMillis();

        Thread toTg = new Thread(() -> {
            try {
                byte[] buf = new byte[65536];
                byte[] decBuf = new byte[65536 + 64];
                byte[] encBuf = new byte[65536 + 64];
                int n;
                while (!closed.get() && (n = in.read(buf)) > 0) {
                    int decLen = ctx.cltDec.update(buf, 0, n, decBuf, 0);
                    int encLen = ctx.tgEnc.update(decBuf, 0, decLen, encBuf, 0);
                    if (encLen > 0) {
                        tcpOut.write(encBuf, 0, encLen);
                        tcpOut.flush();
                    }
                }
            } catch (Exception e) {
                logError("Error in client-to-tcp thread (raw fallback)", e);
            } finally {
                closed.set(true);
                try { tcpSocket.close(); } catch (IOException ignored) {}
            }
        });
        toTg.setDaemon(true);
        toTg.start();

        try {
            byte[] buf = new byte[65536];
            byte[] decBuf = new byte[65536 + 64];
            byte[] encBuf = new byte[65536 + 64];
            int n;
            while (!closed.get() && (n = tcpIn.read(buf)) > 0) {
                int decLen = ctx.tgDec.update(buf, 0, n, decBuf, 0);
                int encLen = ctx.cltEnc.update(decBuf, 0, decLen, encBuf, 0);
                if (encLen > 0) {
                    out.write(encBuf, 0, encLen);
                    out.flush();
                }
            }
        } catch (Exception e) {
            logError("Error in tcp-to-client thread (raw fallback)", e);
        } finally {
            closed.set(true);
            try { tcpSocket.close(); } catch (IOException ignored) {}
            try { toTg.join(2000); } catch (InterruptedException ignored) {}
            logInfo("Raw TCP fallback session ended after " + (System.currentTimeMillis() - sessionStartTime) + " ms");
        }
    }

    private void wsHandshake(SSLSocket socket, String domain) throws IOException {
        wsHandshake(socket, domain, 2, false);
    }

    private void wsHandshake(SSLSocket socket, String domain, int dcId) throws IOException {
        wsHandshake(socket, domain, dcId, false);
    }

    private void wsHandshake(SSLSocket socket, String domain, int dcId, boolean isUnified) throws IOException {
        wsHandshake(socket, domain, dcId, isUnified, isUnified ? ("/apiws?dc=" + dcId) : "/apiws");
    }

    private static final java.util.concurrent.atomic.AtomicLong wsAttemptCounter = new java.util.concurrent.atomic.AtomicLong();

    private void wsHandshake(SSLSocket socket, String domain, int dcId, boolean isUnified, String path) throws IOException {
        final long attemptId = wsAttemptCounter.incrementAndGet();
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        String wsKey = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP);

        String req =
                "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + domain + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Sec-WebSocket-Protocol: binary\r\n";

        if (isUnified) {
            req += "X-Telegram-DC: " + dcId + "\r\n";
        }

        req += "Origin: https://web.telegram.org\r\n" +
               "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
               "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36\r\n" +
               "\r\n";

        // Everything here exists to answer one question when a connect fails: what did THIS
        // specific attempt actually look like on the wire, and what did Cloudflare's edge say
        // back - not just "503", but which edge (cf-ray encodes the POP/colo), what TLS was
        // actually negotiated (version/cipher/ALPN - a mismatch here from what a browser sends
        // is exactly the kind of thing that gets an edge to treat a client as automated), and
        // which local (srcPort, thread) attempt this was, so concurrent attempts in the log can
        // be told apart instead of reading as one continuous mess.
        try {
            SSLSession sess = socket.getSession();
            logInfo("[ws#" + attemptId + "] TLS negotiated: " + sess.getProtocol() + " / " + sess.getCipherSuite()
                    + ", ALPN=" + (socket.getApplicationProtocol() == null || socket.getApplicationProtocol().isEmpty() ? "(none)" : socket.getApplicationProtocol())
                    + ", local=" + socket.getLocalSocketAddress() + ", remote=" + socket.getRemoteSocketAddress()
                    + ", thread=" + Thread.currentThread().getName());
        } catch (Exception ignored) {}

        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        logInfo("[ws#" + attemptId + "] -> GET " + path + " Host: " + domain + (isUnified ? " X-Telegram-DC: " + dcId : ""));
        out.write(req.getBytes("UTF-8"));
        out.flush();

        // Читаем ответ до \r\n\r\n
        StringBuilder response = new StringBuilder();
        int prev = -1;
        int ch;
        while ((ch = in.read()) != -1) {
            response.append((char) ch);
            if (prev == '\n' && ch == '\r') {
                int next = in.read();
                if (next == '\n') break;
                response.append((char) next);
            }
            prev = ch;
        }

        String[] headerLines = response.toString().split("\r\n");
        String firstLine = headerLines.length > 0 ? headerLines[0] : "";
        if (!firstLine.contains("101")) {
            // The full header block, not just the status line - cf-ray pins the exact Cloudflare
            // colo that answered (so repeated failures on the SAME colo vs. scattered across many
            // is the difference between "one bad edge" and "this IP/ASN is being treated
            // differently everywhere"), and any WAF/rate-limit-specific header rides along too.
            StringBuilder headerDump = new StringBuilder();
            for (int i = 1; i < headerLines.length; i++) {
                headerDump.append(" | ").append(headerLines[i]);
            }
            logError("[ws#" + attemptId + "] <- " + firstLine + headerDump, null);
            throw new IOException("WebSocket upgrade failed: " + firstLine + headerDump);
        } else {
            logInfo("[ws#" + attemptId + "] <- " + firstLine);
        }
    }

    private void sendWsFrame(OutputStream out, byte[] data, int off, int length) throws IOException {
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);

        ByteBuffer header;

        if (length < 126) {
            header = ByteBuffer.allocate(2 + 4);
            header.put((byte) 0x82); // FIN + binary
            header.put((byte) (0x80 | length)); // masked
        } else if (length < 65536) {
            header = ByteBuffer.allocate(4 + 4);
            header.put((byte) 0x82);
            header.put((byte) (0x80 | 126));
            header.putShort((short) length);
        } else {
            header = ByteBuffer.allocate(10 + 4);
            header.put((byte) 0x82);
            header.put((byte) (0x80 | 127));
            header.putLong(length);
        }
        header.put(mask);

        for (int i = 0; i < length; i++) {
            data[off + i] ^= mask[i % 4];
        }

        out.write(header.array());
        out.write(data, off, length);
        out.flush();
    }

    /** Per upstream tg-ws-proxy's own fix (PR #1187, "raw_websocket.py: continuation фреймы 0x0
     *  просто выбрасывались... длина фрейма шла в readexactly без лимита"): same 16 MiB cap on any
     *  single frame's declared length, defends against a malformed/hostile length field forcing a
     *  huge allocation before any of it is even read. */
    private static final long MAX_WS_FRAME_LEN = 16L * 1024 * 1024;
    /** Cap on a REASSEMBLED (continuation-joined) message, independent of the per-frame cap above -
     *  many small frames chained together could otherwise still grow unbounded. Generous margin
     *  above anything Telegram actually chunks through this tunnel. */
    private static final long MAX_WS_MESSAGE_LEN = 64L * 1024 * 1024;

    private byte[] recvWsFrame(InputStream in, OutputStream wsOut) throws IOException {
        return recvWsFrame(in, wsOut, "");
    }

    private byte[] recvWsFrame(InputStream in, OutputStream wsOut, String logPrefix) throws IOException {
        // PrimeGram: frames were being returned to the caller one at a time regardless of the FIN
        // bit (which wasn't even being read) - a message the remote fragmented across multiple WS
        // frames (routine for anything past a small chat message; media/file transfers routinely
        // fragment) came back to the relay bridge as several separate, incomplete chunks instead of
        // one reassembled payload, corrupting/truncating exactly the kind of transfer a user would
        // describe as "text works, media doesn't load." Upstream tg-ws-proxy hit and fixed the same
        // bug in its own Python implementation (continuation frames discarded outright there,
        // rather than mishandled like here) five hours before this fix, in raw_websocket.py.
        java.io.ByteArrayOutputStream assembled = null;
        while (true) {
            int b1 = in.read();
            int b2 = in.read();
            if (b1 < 0 || b2 < 0) return null;

            boolean fin = (b1 & 0x80) != 0;
            int opcode = b1 & 0x0F;
            long payloadLen = b2 & 0x7F;
            if (payloadLen == 126) {
                payloadLen = ((in.read() & 0xFFL) << 8) | (in.read() & 0xFFL);
            } else if (payloadLen == 127) {
                payloadLen = 0;
                for (int i = 0; i < 8; i++) payloadLen = (payloadLen << 8) | (in.read() & 0xFFL);
            }
            if (payloadLen < 0 || payloadLen > MAX_WS_FRAME_LEN) {
                throw new IOException("WS frame declared an unreasonable length: " + payloadLen);
            }

            boolean masked = (b2 & 0x80) != 0;
            byte[] maskKey = new byte[4];
            if (masked) readFully(in, maskKey, 0, 4);

            byte[] payload = new byte[(int) payloadLen];
            readFully(in, payload, 0, (int) payloadLen);

            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i % 4];
                }
            }

            if (opcode == 0x9) { // PING -> reply with PONG
                synchronized (wsOut) {
                    byte[] mask = new byte[4];
                    RANDOM.nextBytes(mask);
                    byte[] pong = new byte[2 + 4 + payload.length];
                    pong[0] = (byte) 0x8A; // FIN + PONG
                    pong[1] = (byte) (0x80 | payload.length);
                    System.arraycopy(mask, 0, pong, 2, 4);
                    for (int i = 0; i < payload.length; i++) {
                        pong[6 + i] = (byte) (payload[i] ^ mask[i % 4]);
                    }
                    wsOut.write(pong);
                    wsOut.flush();
                }
                logInfo("Received WS PING frame, replied with PONG");
                continue;
            }
            if (opcode == 0xA) { // PONG -> ignore
                logInfo("Received WS PONG frame");
                continue;
            }
            if (opcode == 0x8) { // CLOSE -> end stream
                // RFC 6455 5.5.1: the first two bytes of a CLOSE frame's payload are a big-endian
                // status code, optionally followed by a UTF-8 reason string - both were being
                // silently discarded, which is exactly the information needed to tell "the remote
                // is rate-limiting us" (1008/4xxx-ish app codes) apart from "the remote crashed"
                // (1011) or an ordinary close (1000) instead of guessing from connection timing.
                if (payload.length >= 2) {
                    final int code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                    String reason = "";
                    if (payload.length > 2) {
                        try {
                            reason = new String(payload, 2, payload.length - 2, "UTF-8");
                        } catch (Exception ignore) {
                        }
                    }
                    logInfo(logPrefix + "Received WS CLOSE frame, code=" + code + (reason.isEmpty() ? "" : ", reason=" + reason));
                } else {
                    logInfo(logPrefix + "Received WS CLOSE frame (no status code)");
                }
                return null;
            }

            // Data frame (0x0 continuation, 0x1 text, 0x2 binary) - control frames (PING/PONG/CLOSE
            // above) never fragment and are handled/consumed before reaching here, per RFC 6455,
            // even mid-message, so they don't disturb assembly across loop iterations.
            if (assembled == null && fin) {
                // The overwhelmingly common case: one frame, not fragmented - skip the extra copy
                // through a ByteArrayOutputStream entirely.
                return payload;
            }
            if (assembled == null) {
                assembled = new java.io.ByteArrayOutputStream();
            }
            if (assembled.size() + payload.length > MAX_WS_MESSAGE_LEN) {
                throw new IOException("WS reassembled message exceeded " + MAX_WS_MESSAGE_LEN + " bytes");
            }
            assembled.write(payload);
            if (fin) {
                return assembled.toByteArray();
            }
            // else: more continuation frames still to come - loop back around for the next one.
        }
    }

    /**
     * PrimeGram: a way out through a Worker the user deployed themselves.
     *
     * <p>Unlike our own domains, a Worker is not a Telegram endpoint at all: it opens a plain TCP
     * connection to the data centre's own address and shuttles bytes. Which means everything past
     * this point is unchanged - the same obfuscated init, the same bridge - because what the
     * client would have sent down a direct socket is exactly what goes into the pipe.
     *
     * <p>Tried in order, and only after our own domains have all failed. A Worker belongs to the
     * user and has a request budget attached to their Cloudflare account; spending it while the
     * ordinary route works would be rude.
     */
    private WsConnection connectThroughWorker(int dcId, boolean isMedia, String destIp) {
        if (destIp == null || destIp.isEmpty()) {
            return null;
        }
        // Bundled (subscriber-submitted, pre-verified) workers make this pool worth trying even
        // for someone who never opened the settings screen - PrimeCfWorkers.isEnabled() used to
        // gate the whole method, which made sense when this was purely a user-configured personal
        // fallback, but now that most of the pool ships with the app that gate would leave it
        // unused by default. getShuffledHealthyDomains() already returns an empty list when there
        // is truly nothing to try, so this falls through to the caller's next fallback either way.
        final List<String> domains = PrimeCfWorkers.getShuffledHealthyDomains();
        if (domains.isEmpty()) {
            return null;
        }
        final String path = "/apiws?dst=" + destIp + "&dc=" + dcId;
        for (String domain : domains) {
            final long startedAt = System.currentTimeMillis();
            SSLSocket socket = null;
            try {
                socket = createTlsSocketWithIpv4Preference(domain, 443, 10_000);
                socket.setUseClientMode(true);
                socket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(10_000);
                wsHandshake(socket, domain, dcId, false, path);
                PrimeCfWorkers.markHealthy(domain);
                primeTraceConnect("proxy: DC" + dcId + (isMedia ? "m" : "") + " up via worker "
                        + domain + " in " + (System.currentTimeMillis() - startedAt) + " ms");
                logInfo("Connected through worker " + domain + " -> " + destIp + " (DC" + dcId + ")");
                return new WsConnection(socket, domain);
            } catch (Exception e) {
                logError("Worker " + domain + " failed: " + e.getMessage(), null);
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
                PrimeCfWorkers.markSick(domain);
            }
        }
        return null;
    }

    private byte[] generateRelayInit(byte[] clientHandshake, int dcId, boolean isMedia) {
        int dcIdx = isMedia ? -dcId : dcId;
        // Force INTERMEDIATE protocol for WebSocket connections to the server
        // because Telegram's apiws backend strictly requires it for large payloads.
        byte[] protoTag = new byte[]{(byte) 0xee, (byte) 0xee, (byte) 0xee, (byte) 0xee};

        while (true) {
            byte[] rnd = new byte[HANDSHAKE_LEN];
            RANDOM.nextBytes(rnd);

            if ((rnd[0] & 0xFF) == 0xEF) continue;
            byte[] start4 = Arrays.copyOf(rnd, 4);
            if (Arrays.equals(start4, new byte[]{0x48, 0x45, 0x41, 0x44})) continue;
            if (Arrays.equals(start4, new byte[]{0x50, 0x4F, 0x53, 0x54})) continue;
            if (Arrays.equals(start4, new byte[]{0x47, 0x45, 0x54, 0x20})) continue;
            if (Arrays.equals(start4, new byte[]{(byte)0xEE,(byte)0xEE,(byte)0xEE,(byte)0xEE})) continue;
            if (Arrays.equals(start4, new byte[]{(byte)0xDD,(byte)0xDD,(byte)0xDD,(byte)0xDD})) continue;
            if (Arrays.equals(start4, new byte[]{0x16, 0x03, 0x01, 0x02})) continue;
            byte[] cont = Arrays.copyOfRange(rnd, 4, 8);
            if (Arrays.equals(cont, new byte[]{0,0,0,0})) continue;

            byte[] encKey = Arrays.copyOfRange(rnd, SKIP_LEN, SKIP_LEN + PREKEY_LEN);
            byte[] encIv  = Arrays.copyOfRange(rnd, SKIP_LEN + PREKEY_LEN, SKIP_LEN + PREKEY_LEN + IV_LEN);

            try {
                Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                SecretKeySpec keySpec = new SecretKeySpec(encKey, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(encIv);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

                byte[] encFull = cipher.update(rnd);

                byte[] result = Arrays.copyOf(rnd, HANDSHAKE_LEN);

                byte[] dcBytes = new byte[]{
                    (byte)(dcIdx & 0xFF),
                    (byte)((dcIdx >> 8) & 0xFF)
                };
                byte[] tail = new byte[8];
                System.arraycopy(protoTag, 0, tail, 0, 4);
                System.arraycopy(dcBytes, 0, tail, 4, 2);
                byte[] rnd2 = new byte[2];
                RANDOM.nextBytes(rnd2);
                System.arraycopy(rnd2, 0, tail, 6, 2);

                byte[] keystream = new byte[8];
                for (int i = 0; i < 8; i++) {
                    keystream[i] = (byte)(encFull[56 + i] ^ rnd[56 + i]);
                }

                for (int i = 0; i < 8; i++) {
                    result[PROTO_TAG_POS + i] = (byte)(tail[i] ^ keystream[i]);
                }

                return result;
            } catch (Exception e) {
                FileLog.e(TAG + ": generateRelayInit error", e);
                return new byte[HANDSHAKE_LEN];
            }
        }
    }

    /**
     * A live relay session, watched for one-sided silence.
     *
     * <p>A cold start showed two stretches of roughly fifty seconds each in which the tunnel
     * logged nothing at all. That is ambiguous: it looks the same whether the app had gone quiet
     * or whether it was sending requests into a connection the edge had already dropped, since
     * both reads block forever (the bridge sets no read timeout, on purpose - MTProto connections
     * are long-lived and idle for minutes at a time). Recording what each side last did makes the
     * two cases tell themselves apart.
     */
    private static class SessionActivity {
        final int dcId;
        final boolean isMedia;
        final String connectedDomain;
        final Socket client;
        final SSLSocket tlsSocket;
        volatile long lastClientSend;
        volatile long lastServerFrame;
        volatile long stallReportedAt;

        SessionActivity(int dcId, boolean isMedia, long now, String connectedDomain, Socket client, SSLSocket tlsSocket) {
            this.dcId = dcId;
            this.isMedia = isMedia;
            this.connectedDomain = connectedDomain;
            this.client = client;
            this.tlsSocket = tlsSocket;
            this.lastClientSend = now;
            this.lastServerFrame = now;
        }
    }

    private static final java.util.Set<SessionActivity> liveSessions =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static volatile Thread stallWatcher;

    /**
     * Reports - and, past a second, longer threshold, actively kills - sessions that sent
     * something and heard nothing back for a long time.
     *
     * <p>Used to be a pure observer ("closing a connection that merely looks dead would be a
     * guess, and a wrong guess costs a working session"). That was true when every path shared
     * infrastructure we could reason about - it stopped being true once most traffic started
     * going through third-party Cloudflare Workers: a Worker's own upstream TCP connection to
     * Telegram can die silently on ITS side while OUR WebSocket to the Worker stays perfectly
     * healthy (nothing obligates the Worker to tell us until it next tries to use that dead
     * upstream). That socket then looks alive by every check we have, gets pooled, gets handed
     * to a real client, and simply swallows everything sent into it forever - which reads to
     * the user as "Telegram says Connecting…, messages never send," and only resolves once
     * tgnet's own (much longer, and not ours to tune) patience runs out. Killing it ourselves
     * once the silence is long enough to no longer be plausibly "just slow" turns that into a
     * fast, clean failure tgnet can retry immediately, and lets the worker be marked sick so
     * the next attempt doesn't land on the same dead upstream.
     */
    private static void ensureStallWatcher() {
        if (stallWatcher != null) {
            return;
        }
        synchronized (TgWsProxyService.class) {
            if (stallWatcher != null) {
                return;
            }
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    final long now = System.currentTimeMillis();
                    for (SessionActivity s : liveSessions) {
                        final long silence = now - s.lastServerFrame;
                        // Only interesting when we are waiting on an answer: the client spoke
                        // after the last frame came back, and nothing has come back since.
                        if (silence < 10_000 || s.lastClientSend <= s.lastServerFrame) {
                            continue;
                        }
                        if (now - s.stallReportedAt < 15_000) {
                            continue;
                        }
                        s.stallReportedAt = now;
                        primeTraceConnect("!! proxy: DC" + s.dcId + (s.isMedia ? "m" : "")
                                + " sent " + (now - s.lastClientSend) + " ms ago, nothing back for "
                                + silence + " ms");

                        if (silence >= STALL_KILL_MS) {
                            logInfo("Killing stalled session DC" + s.dcId + (s.isMedia ? "m" : "")
                                    + " via " + s.connectedDomain + " - sent " + (now - s.lastClientSend)
                                    + " ms ago, no reply for " + silence + " ms (likely a dead upstream on the far end)");
                            if (s.connectedDomain != null) {
                                PrimeCfWorkers.markSick(s.connectedDomain);
                            }
                            try { s.tlsSocket.close(); } catch (Exception ignored) {}
                            try { s.client.close(); } catch (Exception ignored) {}
                        }
                    }
                }
            }, "prime-proxy-stall-watch");
            t.setDaemon(true);
            t.start();
            stallWatcher = t;
        }
    }

    /** How long a session can go unanswered after speaking before we conclude the far end is
     *  dead and force it closed ourselves, rather than let tgnet wait it out on its own clock. */
    private static final long STALL_KILL_MS = 25_000L;

    private void bridgeConnections(Socket client, InputStream in, OutputStream out,
                                   SSLSocket tlsSocket, InputStream wsIn, OutputStream wsOut,
                                   CryptoCtx ctx, MsgSplitter splitter, int dcId, boolean isMedia,
                                   String connectedDomain, long sid) {
        AtomicBoolean closed = new AtomicBoolean(false);
        MsgSplitter wsSplitter = new MsgSplitter(PROTO_INTERMEDIATE_INT);
        final SessionActivity activity = new SessionActivity(dcId, isMedia, System.currentTimeMillis(), connectedDomain, client, tlsSocket);
        liveSessions.add(activity);
        ensureStallWatcher();
        // Bytes actually moved in each direction - without this, "Socket closed" told you a
        // session died but not whether it ever carried anything at all. A session that dies at
        // 0 bytes-from-ws is a connect that never should have been called "established"; one that
        // died after megabytes clearly was a healthy transfer that got cut, and those two point at
        // completely different causes.
        final java.util.concurrent.atomic.AtomicLong bytesToWs = new java.util.concurrent.atomic.AtomicLong();
        final java.util.concurrent.atomic.AtomicLong bytesFromWs = new java.util.concurrent.atomic.AtomicLong();

        // Reset read timeouts to 0 (infinite) during active relay bridge,
        // relying on TCP keepalive.
        try { client.setSoTimeout(0); } catch (Exception ignored) {}
        try { tlsSocket.setSoTimeout(0); } catch (Exception ignored) {}

        // Thread: client -> WebSocket
        Thread toWs = new Thread(() -> {
            try {
                byte[] buf = new byte[65536];
                byte[] decBuf = new byte[65536 + 64];
                byte[] encBuf = new byte[65536 + 64];
                int n;
                while (!closed.get() && (n = in.read(buf)) > 0) {
                    int decLen = ctx.cltDec.update(buf, 0, n, decBuf, 0);

                    // This used to run unconditionally for every packet of 500 bytes or less -
                    // that is, for essentially all MTProto control traffic, which is exactly what
                    // a login and a first dialog fetch consist of. Each one cost up to 500
                    // String.format calls, a lock, a listener callback and a line written to disk,
                    // inside the relay loop that the client is waiting on. It is a debugging aid
                    // for the obfuscation layer, so it now costs nothing unless logging is on.
                    if (DUMP_PACKET_HEX && n <= 500) {
                        StringBuilder hex = new StringBuilder(n * 3);
                        for (int i = 0; i < n; i++) hex.append(String.format("%02X ", buf[i]));
                        logInfo("Decrypted client packet, hex: " + hex);
                    }

                    List<byte[]> packets = splitter.split(decBuf, 0, decLen);
                    // PrimeGram: every packet in this batch used to become its own WS frame - a
                    // burst of several small MTProto messages in one client read (routine: acks,
                    // pings, a request-plus-its-ack) meant that many separate `message` events on
                    // the far end. The Cloudflare Worker relaying this (see docs/CfWorker.md) pays
                    // CPU-time budget per event, not just per byte, and that budget is exactly what
                    // was found to be capping how long a session survives (see the "Socket closed"
                    // investigation) - fewer, larger frames for the same bytes is strictly cheaper
                    // there, and it's also fewer local write()/flush() syscalls either way. AES-CTR
                    // is a running keystream, so encrypting the whole batch in one update() call
                    // produces byte-for-byte the same ciphertext as encrypting each piece
                    // separately in sequence - concatenating first changes nothing about the crypto,
                    // only how many times sendWsFrame is called for the same data.
                    int batchLen = 0;
                    for (byte[] plain : packets) {
                        int headerLen = (plain[0] == 0x7F) ? 4 : 1;
                        batchLen += 4 + (plain.length - headerLen);
                    }
                    if (batchLen > 0) {
                        byte[] batch = new byte[batchLen];
                        int pos = 0;
                        for (byte[] plain : packets) {
                            // Repackage Abridged -> Intermediate for the Server
                            int headerLen = (plain[0] == 0x7F) ? 4 : 1;
                            int payloadLen = plain.length - headerLen;
                            batch[pos] = (byte) (payloadLen & 0xFF);
                            batch[pos + 1] = (byte) ((payloadLen >> 8) & 0xFF);
                            batch[pos + 2] = (byte) ((payloadLen >> 16) & 0xFF);
                            batch[pos + 3] = (byte) ((payloadLen >> 24) & 0xFF);
                            System.arraycopy(plain, headerLen, batch, pos + 4, payloadLen);
                            pos += 4 + payloadLen;
                        }

                        if (encBuf.length < batch.length + 64) {
                            encBuf = new byte[Math.max(encBuf.length * 2, batch.length + 64)];
                        }

                        int encLen = ctx.tgEnc.update(batch, 0, batch.length, encBuf, 0);
                        if (encLen > 0) {
                            synchronized (wsOut) {
                                sendWsFrame(wsOut, encBuf, 0, encLen);
                            }
                            bytesToWs.addAndGet(encLen);
                            activity.lastClientSend = System.currentTimeMillis();
                        }
                    }
                }
            } catch (Exception e) {
                logError("[s" + sid + "] Error in client-to-ws thread (sent " + bytesToWs.get() + " bytes total)", e);
            } finally {
                closed.set(true);
                try { tlsSocket.close(); } catch (IOException ignored) {}
            }
        });
        toWs.setDaemon(true);
        toWs.start();

        // Thread: WebSocket -> client
        final long sessionStartTime = System.currentTimeMillis();
        boolean firstFrame = true;
        try {
            byte[] decBuf = new byte[65536 + 64];
            while (!closed.get()) {
                String closePrefix = "[s" + sid + "] DC" + dcId + (isMedia ? "m" : "") + " via " + connectedDomain
                        + " (age=" + (System.currentTimeMillis() - sessionStartTime) + "ms, thread=" + Thread.currentThread().getName()
                        + ", toWs=" + bytesToWs.get() + "B, fromWs=" + bytesFromWs.get() + "B): ";
                byte[] frame = recvWsFrame(wsIn, wsOut, closePrefix);
                if (frame == null) break;

                activity.lastServerFrame = System.currentTimeMillis();
                if (firstFrame) {
                    firstFrame = false;
                    // Separates "the tunnel is slow to carry anything" from "the tunnel carries
                    // data promptly and the wait is on Telegram's side of it".
                    primeTraceConnect("proxy: first server frame after "
                            + (System.currentTimeMillis() - sessionStartTime) + " ms of session");
                }

                if (decBuf.length < frame.length + 64) {
                    decBuf = new byte[Math.max(decBuf.length * 2, frame.length + 64)];
                }
                
                int decLen = ctx.tgDec.update(frame, 0, frame.length, decBuf, 0);
                
                // Repackage Intermediate -> Abridged for the Client
                List<byte[]> serverPackets = wsSplitter.split(decBuf, 0, decLen);
                for (byte[] serverPlain : serverPackets) {
                    int payloadLen = serverPlain.length - 4;
                    byte[] payload = new byte[payloadLen];
                    System.arraycopy(serverPlain, 4, payload, 0, payloadLen);
                    
                    int words = payloadLen / 4;
                    byte[] abridged;
                    if (words < 127) {
                        abridged = new byte[1 + payloadLen];
                        abridged[0] = (byte) words;
                        System.arraycopy(payload, 0, abridged, 1, payloadLen);
                    } else {
                        abridged = new byte[4 + payloadLen];
                        abridged[0] = 0x7F;
                        abridged[1] = (byte) (words & 0xFF);
                        abridged[2] = (byte) ((words >> 8) & 0xFF);
                        abridged[3] = (byte) ((words >> 16) & 0xFF);
                        System.arraycopy(payload, 0, abridged, 4, payloadLen);
                    }
                    
                    ctx.cltEnc.update(abridged, 0, abridged.length, abridged, 0);
                    out.write(abridged);
                    bytesFromWs.addAndGet(abridged.length);
                }
                out.flush();
            }
        } catch (Exception e) {
            logError("[s" + sid + "] Error in ws-to-client thread (via " + connectedDomain + ", age="
                    + (System.currentTimeMillis() - sessionStartTime) + "ms, toWs=" + bytesToWs.get()
                    + "B, fromWs=" + bytesFromWs.get() + "B)", e);
        } finally {
            closed.set(true);
            liveSessions.remove(activity);
            try { client.close(); } catch (IOException ignored) {}

            // Every ending is recorded, not just the quick ones: a session that dies at forty
            // seconds is exactly the case the old threshold could not see.
            long sessionAge = System.currentTimeMillis() - sessionStartTime;
            primeTraceConnect("proxy: DC" + dcId + (isMedia ? "m" : "") + " session ended after "
                    + sessionAge + " ms"
                    + (firstFrame ? ", no server frame ever arrived" : ""));
            // Visible in the on-screen log too (primeTraceConnect isn't) - a zero-byte-either-way
            // ending is a connect that never should have counted as "established"; megabytes-then-
            // cut is a healthy transfer interrupted. Same "Socket closed" line meant either before.
            logInfo("[s" + sid + "] Session ended: DC" + dcId + (isMedia ? "m" : "") + " via " + connectedDomain
                    + ", age=" + sessionAge + "ms, toWs=" + bytesToWs.get() + "B, fromWs=" + bytesFromWs.get() + "B"
                    + (firstFrame ? ", no server frame ever arrived" : ""));

            if (sessionAge < 5000) {
                if (noteFastSessionFailure(connectedDomain, dcId, isMedia)) {
                    // The breaker just tripped for this (dc, media) - any sockets already sitting
                    // in the pool were dialed during the same bad window and would otherwise be
                    // handed to the very next client only to die just as fast.
                    java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q = wsPoolMap.get(dcId + "_" + isMedia);
                    if (q != null) {
                        WsConnection stale;
                        while ((stale = q.poll()) != null) {
                            try { stale.tlsSocket.close(); } catch (IOException ignored) {}
                        }
                    }
                }
            } else {
                // A session that lasted means the domain (and this DC/media pair on it) is fine;
                // forget earlier stumbles so unrelated failures spread over time never add up to a failover.
                fastFailureCount.set(0);
                noteDcSessionOutcome(dcId, isMedia, false);
            }
        }
    }

    // ─── Utilities ─────────────────────────────────────────────────────────

    /** Throughput over any path with real latency is capped by window-size / RTT, and this tunnel
     *  adds a real extra hop's worth of RTT (client -> Cloudflare edge/Worker -> Telegram) on top
     *  of an unproxied connection's own. Android/Linux auto-tunes TCP buffers reasonably well by
     *  default, but its ceiling on some devices/kernels still lands well under what a 200-300ms
     *  round trip to a Worker can actually use - a media download sitting well below the link's
     *  real bandwidth despite a healthy, unbroken WS session (as opposed to a session that keeps
     *  dying, which is a completely different problem this does nothing for) is exactly that
     *  ceiling. Setting an explicit floor removes it as a variable; it costs a fixed amount of
     *  memory per open tunnel (2 x 256 KiB), trivial next to what one media transfer already
     *  allocates in buffers elsewhere in this same class. Must be called before connect() - the
     *  send buffer size in particular is part of what the OS uses to size the TCP window during
     *  the handshake, and setting it after connecting only affects some platforms' interpretation.
     */
    private static final int TUNNEL_SOCKET_BUFFER_BYTES = 256 * 1024;

    private static void tuneTunnelSocketBuffers(Socket socket) {
        try {
            socket.setReceiveBufferSize(TUNNEL_SOCKET_BUFFER_BYTES);
            socket.setSendBufferSize(TUNNEL_SOCKET_BUFFER_BYTES);
        } catch (Exception ignored) {
            // Some platforms reject this on an unconnected socket, or cap it silently - either
            // way, falling back to the OS default is fine, not fatal.
        }
    }

    private static void readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) throw new IOException("Stream ended prematurely");
            total += n;
        }
    }

    private static void pipe(InputStream src, OutputStream dst) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = src.read(buf)) > 0) {
            dst.write(buf, 0, n);
            dst.flush();
        }
    }

    /**
     * SSL factory without certificate validation (matches upstream tg-ws-proxy's own
     * {@code backend.py} {@code _ssl_ctx}, {@code check_hostname = False} / {@code verify_mode =
     * ssl.CERT_NONE}) - used for every socket this service opens, tunnel included.
     *
     * <p>A real, validating factory was tried instead at one point (strict-security improvement
     * on paper). It was reverted after real user reports: on the exact hostile/DPI-filtered
     * networks this whole feature exists to work on, some candidate domains are apparently behind
     * active TLS interception, and strict validation correctly - but unhelpfully - refused those
     * connections outright, where trust-all silently accepted the interception and kept working.
     * For this specific tool, connectivity over a technically-untrusted transport beats a hard
     * failure: the payload carried over this tunnel is itself already separately encrypted by
     * MTProto's own auth_key, which a MITM'd outer TLS layer does not expose - the actual message
     * content isn't what strict TLS here was protecting anyway, just metadata/traffic-analysis
     * resistance on networks where that fight is already lost the moment DPI is actively
     * intercepting TLS at all. Do not re-flip this without a fresh, confirmed report tying real
     * breakage specifically to it - the same swap was tried and reverted for this exact reason.
     */
    private static SSLSocketFactory buildTrustAllSslFactory() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            FileLog.e(TAG + ": SSL factory error", e);
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "PrimeGram Proxy", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Прокси-сервис для обхода блокировок");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    // ─── Static start/stop helpers ─────────────────────────────────────────

    public static void startService(Context context) {
        Intent intent = new Intent(context, TgWsProxyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopService(Context context) {
        Intent intent = new Intent(context, TgWsProxyService.class);
        context.stopService(intent);
    }

    private InetAddress[] resolveWithFallbackDns(String host) {
        // Try system DNS first
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses != null && addresses.length > 0) {
                logInfo("System DNS resolved " + host + " to " + addresses.length + " addresses");
                return addresses;
            }
        } catch (java.net.UnknownHostException e) {
            logError("System DNS failed for " + host + ", trying fallback methods", e);
        }

        // Try DNS-over-HTTPS in parallel via multiple providers
        String[] dohProviders = {
            "https://dns.google/resolve?name=" + host + "&type=A",
            "https://cloudflare-dns.com/dns-query?name=" + host + "&type=A",
            "https://dns.quad9.net/dns-query?name=" + host + "&type=A"
        };

        final List<InetAddress[]> results = java.util.Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(dohProviders.length);

        for (final String dohUrl : dohProviders) {
            executor.submit(() -> {
                try {
                    InetAddress[] addresses = resolveDnsOverHttps(dohUrl, host);
                    if (addresses != null && addresses.length > 0) {
                        results.add(addresses);
                        logInfo("DNS-over-HTTPS resolved " + host + " via " + dohUrl);
                    }
                } catch (Exception e) {
                    logError("DNS-over-HTTPS failed for " + host + " via " + dohUrl, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            long start = System.currentTimeMillis();
            while (latch.getCount() > 0 && System.currentTimeMillis() - start < 5000) {
                if (!results.isEmpty()) {
                    break;
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException ignored) {}

        if (!results.isEmpty()) {
            return results.get(0);
        }

        // Last resort: try hardcoded Cloudflare anycast IPs
        logError("All DNS methods failed for " + host + ", using hardcoded Cloudflare IPs", null);
        List<InetAddress> fallbackAddresses = new ArrayList<>();
        for (String ip : FALLBACK_IPS) {
            try {
                fallbackAddresses.add(InetAddress.getByName(ip));
            } catch (java.net.UnknownHostException ignored) {}
        }
        if (!fallbackAddresses.isEmpty()) {
            return fallbackAddresses.toArray(new InetAddress[0]);
        }

        return null;
    }

    private InetAddress[] resolveDnsOverHttps(String dohUrl, String host) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(dohUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/dns-json");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("DNS-over-HTTPS returned code: " + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Parse JSON response manually to avoid dependency on JSON library
            String json = response.toString();
            List<InetAddress> addresses = new ArrayList<>();

            // Look for "Answer" array or "answer" array (different providers use different casing)
            int answerStart = json.indexOf("\"Answer\"");
            if (answerStart == -1) {
                answerStart = json.indexOf("\"answer\"");
            }
            if (answerStart == -1) {
                return null;
            }

            // Find all "data" fields in the Answer array
            int searchPos = answerStart;
            int dataPos = json.indexOf("\"data\"", searchPos);
            while (dataPos != -1) {
                int colonPos = json.indexOf(':', dataPos + 6);
                if (colonPos == -1) break;

                int quoteStart = json.indexOf('"', colonPos + 1);
                if (quoteStart == -1) break;

                int quoteEnd = json.indexOf('"', quoteStart + 1);
                if (quoteEnd == -1) break;

                String ip = json.substring(quoteStart + 1, quoteEnd);
                if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    try {
                        addresses.add(InetAddress.getByName(ip));
                    } catch (java.net.UnknownHostException ignored) {}
                }

                dataPos = json.indexOf("\"data\"", quoteEnd);
            }

            return addresses.isEmpty() ? null : addresses.toArray(new InetAddress[0]);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Socket connectWithIpv4Preference(String host, int port, int timeoutMs) throws IOException {
        InetAddress[] addresses = null;

        // If it's a proxy subdomain request, reuse this SAME host's previously-resolved IP (not
        // any other subdomain's) to avoid a redundant DNS round trip and keep repeated connections
        // to it landing on the same Cloudflare edge - see cachedBaseAddresses' own doc for why this
        // is keyed per-hostname now, not one address shared across every kwsN.<domain> subdomain.
        if (currentBaseDomain != null && host.endsWith(currentBaseDomain)) {
            synchronized (domainLock) {
                java.net.InetAddress cached = cachedBaseAddresses.get(host);
                if (cached == null) {
                    try {
                        InetAddress[] resolved = resolveWithFallbackDns(host);
                        if (resolved != null && resolved.length > 0) {
                            cached = resolved[0];
                            cachedBaseAddresses.put(host, cached);
                            logInfo("Resolved and cached base IP for " + host + ": " + cached);
                        }
                    } catch (Exception e) {
                        logError("Failed to resolve host " + host, e);
                    }
                }
                if (cached != null) {
                    addresses = new InetAddress[]{cached};
                }
            }
        }

        if (addresses == null) {
            // Try system DNS first, then fallback DNS servers
            addresses = resolveWithFallbackDns(host);
        }

        if (addresses == null || addresses.length == 0) {
            throw new java.net.UnknownHostException("Could not resolve host by any method: " + host);
        }

        List<InetAddress> prioritized = new ArrayList<>();
        // Prefer IPv4
        for (InetAddress addr : addresses) {
            if (addr instanceof java.net.Inet4Address) {
                prioritized.add(addr);
            }
        }
        // Then IPv6
        for (InetAddress addr : addresses) {
            if (addr instanceof java.net.Inet6Address) {
                prioritized.add(addr);
            }
        }

        if (prioritized.isEmpty()) {
            throw new java.net.UnknownHostException("No IP addresses found for host: " + host);
        }

        IOException lastEx = null;
        for (InetAddress addr : prioritized) {
            Socket socket = new Socket();
            tuneTunnelSocketBuffers(socket);
            try {
                logInfo("Connecting to resolved address " + addr + " for host " + host);
                final long tcpStart = System.currentTimeMillis();
                socket.connect(new java.net.InetSocketAddress(addr, port), timeoutMs);
                logInfo("TCP connected to " + addr + " in " + (System.currentTimeMillis() - tcpStart) + "ms, local=" + socket.getLocalSocketAddress());
                return socket;
            } catch (IOException e) {
                logError("Failed to connect to address " + addr + ": " + e.getMessage(), e);
                lastEx = e;
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        if (lastEx != null) {
            throw lastEx;
        }
        throw new IOException("Could not connect to any address for host: " + host);
    }

    private SSLSocket createTlsSocketWithIpv4Preference(String host, int port, int timeoutMs) throws IOException {
        Socket plainSocket = connectWithIpv4Preference(host, port, timeoutMs);
        SSLSocket tlsSocket = (SSLSocket) sslSocketFactory.createSocket(plainSocket, host, port, true);
        tlsSocket.setUseClientMode(true);
        tlsSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        tlsSocket.setTcpNoDelay(true);
        tlsSocket.setSoTimeout(timeoutMs);
        return tlsSocket;
    }
}
