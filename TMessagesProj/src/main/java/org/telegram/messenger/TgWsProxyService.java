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

    public interface LogListener {
        void onLogAdded(String line);
    }
    private static final List<String> logBuffer = new ArrayList<>();
    private static LogListener logListener;

    public static synchronized void addLog(String line) {
        String formatted = String.format("[%tT] %s", System.currentTimeMillis(), line);
        logBuffer.add(formatted);
        if (logBuffer.size() > 200) {
            logBuffer.remove(0);
        }
        if (logListener != null) {
            logListener.onLogAdded(formatted);
        }
    }

    public static synchronized List<String> getLogBuffer() {
        return new ArrayList<>(logBuffer);
    }

    public static synchronized void setLogListener(LogListener listener) {
        logListener = listener;
    }

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

    // Cloudflare Worker bypass domains from tg-ws-proxy
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
        "pyatdesyatodin.co.uk"
    };

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
    private SSLSocketFactory sslSocketFactory;
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.Semaphore> dcSemaphores = new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.concurrent.Semaphore getSemaphoreForDc(int dcId, boolean isMedia) {
        String key = dcId + "_" + isMedia;
        java.util.concurrent.Semaphore sem = dcSemaphores.get(key);
        if (sem == null) {
            synchronized (dcSemaphores) {
                sem = dcSemaphores.get(key);
                if (sem == null) {
                    sem = new java.util.concurrent.Semaphore(20);
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
    public static String getCurrentBaseDomain() {
        return currentBaseDomain;
    }
    private static volatile java.net.InetAddress cachedBaseAddress = null;
    private static volatile long lastDomainSelectionTime = 0;

    private final Object socketLock = new Object();
    private final List<Socket> activeClientSockets = new ArrayList<>();
    private ConnectivityManager.NetworkCallback networkCallback;

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
        executor.submit(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(15_000); // Check every 15 seconds
                    if (running.get()) {
                        if (serverSocket == null || serverSocket.isClosed() || !serverSocket.isBound()) {
                            logInfo("Watchdog detected server socket is closed/unbound! Restarting server socket...");
                            restartProxySockets();
                        }
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
        executor = Executors.newCachedThreadPool();
        sslSocketFactory = buildTrustAllSslFactory();

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    logInfo("Network connection changed: available.");
                    // closeActiveClientSockets(); // Disabled: causes JNI/SOCKS5 SIGPIPE crash
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

        if (!running.getAndSet(true)) {
            currentBaseDomain = null;
            cachedBaseAddress = null;
            executor.submit(this::runProxyServer);
            startWatchdog();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running.set(false);
        instance = null;
        isSocketBound = false;
        currentBaseDomain = null;
        cachedBaseAddress = null;
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        restartProxySockets();
        if (executor != null) executor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Proxy server loop ─────────────────────────────────────────────────

    private void runProxyServer() {
        while (running.get()) {
            try {
                int port = PROXY_PORT;
                for (int i = 0; i < 10; i++) {
                    try {
                        serverSocket = new ServerSocket(port, 50, null);
                        serverSocket.setReuseAddress(true);
                        activeProxyPort = port;
                        break;
                    } catch (IOException e) {
                        logInfo("Port " + port + " is in use, trying next...");
                        port++;
                    }
                }
                if (serverSocket == null || serverSocket.isClosed()) {
                    serverSocket = new ServerSocket(0, 50, null);
                    activeProxyPort = serverSocket.getLocalPort();
                }
                isSocketBound = true;

                logInfo("Listening on wildcard address (IPv4/IPv6 loopback allowed) port: " + activeProxyPort);
                updateNotification();

                int finalPort = activeProxyPort;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                        if (preferences.getBoolean("proxy_enabled", false)) {
                            String proxyAddress = preferences.getString("proxy_ip", "");
                            if ("127.0.0.1".equals(proxyAddress)) {
                                String proxyUsername = preferences.getString("proxy_user", "");
                                String proxyPassword = preferences.getString("proxy_pass", "");
                                String proxySecret = preferences.getString("proxy_secret", "");
                                
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("proxy_port", finalPort);
                                editor.apply();
                                
                                ConnectionsManager.setProxySettings(true, proxyAddress, finalPort, proxyUsername, proxyPassword, proxySecret);
                            }
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
                        executor.submit(() -> handleClient(client));
                    } catch (IOException e) {
                        isSocketBound = false;
                        if (running.get()) {
                            logInfo("Accept interrupted or socket closed, will re-bind if running.");
                        }
                    }
                }
            } catch (IOException e) {
                isSocketBound = false;
                logError("server error, resting before retry...", e);
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
        private final byte[] plainBuf = new byte[256 * 1024];
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
                disabled = true;
                List<byte[]> res = new ArrayList<>();
                byte[] copy = new byte[len];
                System.arraycopy(chunk, off, copy, 0, len);
                res.add(copy);
                return res;
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

    private WsConnection getPooledWsConnection(int dcId, boolean isMedia) {
        String key = dcId + "_" + isMedia;
        java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q = wsPoolMap.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
        WsConnection conn = null;
        while ((conn = q.poll()) != null) {
            long age = System.currentTimeMillis() - conn.createdAt;
            if (age > 100_000 || conn.tlsSocket.isClosed()) { // 100 seconds max age
                try { conn.tlsSocket.close(); } catch (Exception ignored) {}
                continue;
            }
            logInfo("WsPool hit for DC" + dcId + " (media=" + isMedia + "), age=" + age + "ms");
            break;
        }
        refillWsPoolAsync(dcId, isMedia);
        return conn;
    }

    private void refillWsPoolAsync(int dcId, boolean isMedia) {
        String key = dcId + "_" + isMedia;
        java.util.concurrent.atomic.AtomicBoolean refilling = wsPoolRefilling.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicBoolean(false));
        if (refilling.compareAndSet(false, true)) {
            executor.submit(() -> {
                try {
                    java.util.concurrent.ConcurrentLinkedQueue<WsConnection> q = wsPoolMap.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
                    while (q.size() < 1) { // Maintain 1 ready background connection per active DC
                        WsConnection conn = connectToWebSocket(dcId, isMedia);
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

    private WsConnection connectToWebSocket(int dcId, boolean isMedia) {
        SSLSocket tlsSocket = null;
        String chosenDomain = null;

        try {
            Thread.sleep(20 + RANDOM.nextInt(280));
        } catch (InterruptedException ignored) {}

        String baseDomain;
        synchronized (TgWsProxyService.class) {
            if (currentBaseDomain == null) {
                if (System.currentTimeMillis() - lastDomainSelectionTime > 30_000) {
                    logInfo("Selecting base domain from candidates using pair-wise latency tests...");
                    List<String> candidates = new ArrayList<>(Arrays.asList(BASE_DOMAINS));
                    Collections.shuffle(candidates);

                    final String[] selected = new String[1];
                    final int targetDcId = dcId;

                    for (int i = 0; i < candidates.size() && selected[0] == null; i += 2) {
                        final String dom1 = candidates.get(i);
                        final String dom2 = (i + 1 < candidates.size()) ? candidates.get(i + 1) : null;

                        int count = (dom2 != null) ? 2 : 1;
                        final java.util.concurrent.CountDownLatch pairLatch = new java.util.concurrent.CountDownLatch(count);
                        final String[] pairSelected = new String[1];

                        Runnable runTest1 = () -> {
                            SSLSocket testSocket = null;
                            try {
                                String testHost = "kws" + targetDcId + "." + dom1;
                                InetAddress[] testResolve = resolveWithFallbackDns(testHost);
                                if (testResolve == null || testResolve.length == 0) {
                                    testHost = "kws." + dom1;
                                    testResolve = resolveWithFallbackDns(testHost);
                                    if (testResolve == null || testResolve.length == 0) {
                                        testHost = dom1;
                                    }
                                }
                                testSocket = (SSLSocket) sslSocketFactory.createSocket();
                                testSocket.connect(new java.net.InetSocketAddress(testHost, 443), 1200);
                                testSocket.setSoTimeout(1200);
                                testSocket.startHandshake();
                                synchronized (pairSelected) {
                                    if (pairSelected[0] == null) {
                                        pairSelected[0] = dom1;
                                    }
                                }
                            } catch (Exception ignored) {
                            } finally {
                                if (testSocket != null) {
                                    try { testSocket.close(); } catch (IOException ignored) {}
                                }
                                pairLatch.countDown();
                            }
                        };
                        executor.submit(runTest1);

                        if (dom2 != null) {
                            Runnable runTest2 = () -> {
                                SSLSocket testSocket = null;
                                try {
                                    String testHost = "kws" + targetDcId + "." + dom2;
                                    InetAddress[] testResolve = resolveWithFallbackDns(testHost);
                                    if (testResolve == null || testResolve.length == 0) {
                                        testHost = "kws." + dom2;
                                        testResolve = resolveWithFallbackDns(testHost);
                                        if (testResolve == null || testResolve.length == 0) {
                                            testHost = dom2;
                                        }
                                    }
                                    testSocket = (SSLSocket) sslSocketFactory.createSocket();
                                    testSocket.connect(new java.net.InetSocketAddress(testHost, 443), 1200);
                                    testSocket.setSoTimeout(1200);
                                    testSocket.startHandshake();
                                    synchronized (pairSelected) {
                                        if (pairSelected[0] == null) {
                                            pairSelected[0] = dom2;
                                        }
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    if (testSocket != null) {
                                        try { testSocket.close(); } catch (IOException ignored) {}
                                    }
                                    pairLatch.countDown();
                                }
                            };
                            executor.submit(runTest2);
                        }

                        try {
                            pairLatch.await(1400, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ignored) {}

                        if (pairSelected[0] != null) {
                            selected[0] = pairSelected[0];
                            break;
                        }
                    }

                    if (selected[0] == null) {
                        selected[0] = BASE_DOMAINS[RANDOM.nextInt(BASE_DOMAINS.length)];
                        logInfo("All pair-wise latency tests failed. Selected fallback: " + selected[0]);
                    } else {
                        logInfo("Fastest base domain selected: " + selected[0]);
                    }
                    currentBaseDomain = selected[0];
                    lastDomainSelectionTime = System.currentTimeMillis();
                } else {
                    currentBaseDomain = BASE_DOMAINS[RANDOM.nextInt(BASE_DOMAINS.length)];
                    logInfo("Quick failover (cooldown active): selected random domain: " + currentBaseDomain);
                }
                baseDomain = currentBaseDomain;
            } else {
                baseDomain = currentBaseDomain;
            }
        }
        boolean isUnified = false;
        String wsDomain = "kws" + dcId + "." + baseDomain;
        InetAddress[] checkResolve = resolveWithFallbackDns(wsDomain);
        if (checkResolve == null || checkResolve.length == 0) {
            wsDomain = baseDomain;
        }

        logInfo("Connecting to CF proxy " + wsDomain + ":443 for DC" + dcId + " (unified=" + isUnified + ")");
        try {
            tlsSocket = createTlsSocketWithIpv4Preference(wsDomain, 443, 10_000);
            tlsSocket.setUseClientMode(true);
            tlsSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            tlsSocket.setTcpNoDelay(true);
            tlsSocket.setSoTimeout(10_000); // 10s timeout for handshake

            wsHandshake(tlsSocket, wsDomain, dcId, isUnified);
            chosenDomain = wsDomain;
            logInfo("Successfully connected to " + wsDomain + " (DC" + dcId + ")");
            return new WsConnection(tlsSocket, chosenDomain);
        } catch (Exception e) {
            logError("Failed to connect to proxy " + wsDomain + ", resetting currentBaseDomain", e);
            synchronized (TgWsProxyService.class) {
                if (baseDomain.equals(currentBaseDomain)) {
                    currentBaseDomain = null;
                    cachedBaseAddress = null;
                    logInfo("Resetting currentBaseDomain (failover triggered)");
                }
            }
            if (tlsSocket != null) {
                try { tlsSocket.close(); } catch (IOException ignored) {}
            }
            return null;
        }
    }

    private void handleClient(Socket client) {
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

            logInfo("New SOCKS5 connection from " + client.getRemoteSocketAddress());

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
                logInfo("Unsupported SOCKS5 command: " + cmd);
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

            logInfo("SOCKS5 Request to " + destIp + ":" + destPort);

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

            logInfo("Matched DC" + dcId + " (media=" + isMedia + ")");

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
            try {
                if (!sem.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    logInfo("Timeout waiting for connection semaphore for DC" + dcId + " (media=" + isMedia + "), rejecting duplicate connection");
                    client.close();
                    return;
                }
                semaphoreAcquired = true;
            } catch (InterruptedException e) {
                logInfo("Interrupted waiting for connection semaphore for DC" + dcId);
                return;
            }

            WsConnection wsConn = getPooledWsConnection(dcId, isMedia);
            if (wsConn == null) {
                wsConn = connectToWebSocket(dcId, isMedia);
            }
            if (wsConn == null) {
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

            InputStream wsIn = new BufferedInputStream(tlsSocket.getInputStream(), 65536);
            OutputStream wsOut = tlsSocket.getOutputStream();

            // Отправляем relay init в WebSocket бинарном фрейме
            sendWsFrame(wsOut, relayInit, 0, relayInit.length);
            logInfo("Sent obfuscation handshake to remote WS");

            client.setKeepAlive(true);
            tlsSocket.setKeepAlive(true);
            client.setSoTimeout(120_000); // 2 minutes read timeout
            tlsSocket.setSoTimeout(120_000); // 2 minutes read timeout

            logInfo("Session established, entering active relay bridge...");

            // Запускаем двунаправленный мост с ре-шифрованием
            bridgeConnections(client, in, out, tlsSocket, wsIn, wsOut, cryptoCtx, splitter);

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

    private void wsHandshake(SSLSocket socket, String domain) throws IOException {
        wsHandshake(socket, domain, 2, false);
    }

    private void wsHandshake(SSLSocket socket, String domain, int dcId) throws IOException {
        wsHandshake(socket, domain, dcId, false);
    }

    private void wsHandshake(SSLSocket socket, String domain, int dcId, boolean isUnified) throws IOException {
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        String wsKey = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP);

        String path = isUnified ? ("/apiws?dc=" + dcId) : "/apiws";

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

        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

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

        String firstLine = response.toString().split("\r\n")[0];
        if (!firstLine.contains("101")) {
            throw new IOException("WebSocket upgrade failed: " + firstLine);
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

    private byte[] recvWsFrame(InputStream in, OutputStream wsOut) throws IOException {
        while (true) {
            int b1 = in.read();
            int b2 = in.read();
            if (b1 < 0 || b2 < 0) return null;

            int opcode = b1 & 0x0F;
            long payloadLen = b2 & 0x7F;
            if (payloadLen == 126) {
                payloadLen = ((in.read() & 0xFFL) << 8) | (in.read() & 0xFFL);
            } else if (payloadLen == 127) {
                payloadLen = 0;
                for (int i = 0; i < 8; i++) payloadLen = (payloadLen << 8) | (in.read() & 0xFFL);
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
                logInfo("Received WS CLOSE frame");
                return null;
            }

            return payload; // Binary/Text frame data
        }
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

    private void bridgeConnections(Socket client, InputStream in, OutputStream out,
                                   SSLSocket tlsSocket, InputStream wsIn, OutputStream wsOut,
                                   CryptoCtx ctx, MsgSplitter splitter) {
        AtomicBoolean closed = new AtomicBoolean(false);
        MsgSplitter wsSplitter = new MsgSplitter(PROTO_INTERMEDIATE_INT);

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

                    if (n <= 500) {
                        StringBuilder hex = new StringBuilder();
                        for (int i = 0; i < n; i++) hex.append(String.format("%02X ", buf[i]));
                        logInfo("Decrypted client packet, hex: " + hex.toString());
                    }

                    List<byte[]> packets = splitter.split(decBuf, 0, decLen);
                    for (byte[] plain : packets) {
                        // Repackage Abridged -> Intermediate for the Server
                        int headerLen = (plain[0] == 0x7F) ? 4 : 1;
                        int payloadLen = plain.length - headerLen;
                        
                        byte[] intermediate = new byte[4 + payloadLen];
                        intermediate[0] = (byte) (payloadLen & 0xFF);
                        intermediate[1] = (byte) ((payloadLen >> 8) & 0xFF);
                        intermediate[2] = (byte) ((payloadLen >> 16) & 0xFF);
                        intermediate[3] = (byte) ((payloadLen >> 24) & 0xFF);
                        System.arraycopy(plain, headerLen, intermediate, 4, payloadLen);

                        int encLen = ctx.tgEnc.update(intermediate, 0, intermediate.length, encBuf, 0);
                        if (encLen > 0) {
                            synchronized (wsOut) {
                                sendWsFrame(wsOut, encBuf, 0, encLen);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logError("Error in client-to-ws thread", e);
            } finally {
                closed.set(true);
                try { tlsSocket.close(); } catch (IOException ignored) {}
            }
        });
        toWs.setDaemon(true);
        toWs.start();

        // Thread: WebSocket -> client
        final long sessionStartTime = System.currentTimeMillis();
        try {
            byte[] decBuf = new byte[65536 + 64];
            byte[] encBuf = new byte[65536 + 64];
            while (!closed.get()) {
                byte[] frame = recvWsFrame(wsIn, wsOut);
                if (frame == null) break;
                
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
                }
                out.flush();
            }
        } catch (Exception e) {
            logError("Error in ws-to-client thread", e);
        } finally {
            closed.set(true);
            try { client.close(); } catch (IOException ignored) {}
            
            if (System.currentTimeMillis() - sessionStartTime < 5000) {
                synchronized (TgWsProxyService.class) {
                    currentBaseDomain = null;
                    cachedBaseAddress = null;
                }
            }
        }
    }

    // ─── Utilities ─────────────────────────────────────────────────────────

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
     * SSL factory без проверки сертификата (как в backend.py _ssl_ctx).
     */
    private SSLSocketFactory buildTrustAllSslFactory() {
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

        // If it's a proxy subdomain request, reuse the cached base IP to ensure all DC connections
        // route through the exact same Cloudflare edge server IP (avoiding geographic "impossible travel" IP mismatch).
        if (currentBaseDomain != null && host.endsWith(currentBaseDomain)) {
            synchronized (TgWsProxyService.class) {
                if (cachedBaseAddress == null) {
                    try {
                        InetAddress[] resolved = resolveWithFallbackDns(host);
                        if (resolved != null && resolved.length > 0) {
                            cachedBaseAddress = resolved[0];
                            logInfo("Resolved and cached base IP for " + host + ": " + cachedBaseAddress);
                        }
                    } catch (Exception e) {
                        logError("Failed to resolve host " + host, e);
                    }
                }
                if (cachedBaseAddress != null) {
                    addresses = new InetAddress[]{cachedBaseAddress};
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
            try {
                logInfo("Connecting to resolved address " + addr + " for host " + host);
                socket.connect(new java.net.InetSocketAddress(addr, port), timeoutMs);
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
