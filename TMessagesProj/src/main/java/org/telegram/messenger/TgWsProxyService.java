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
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.ConnectionsManager;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
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
        DC_WS_DOMAINS.put(1, new String[]{"149.154.175.50"});
        DC_WS_DOMAINS.put(2, new String[]{"149.154.167.220"});
        DC_WS_DOMAINS.put(3, new String[]{"149.154.175.100"});
        DC_WS_DOMAINS.put(4, new String[]{"149.154.167.91"});
        DC_WS_DOMAINS.put(5, new String[]{"149.154.171.5"});
        DC_WS_DOMAINS.put(203, new String[]{"91.105.192.100"});
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

    // Singleton for external access
    private static TgWsProxyService instance;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PrimeGram Proxy")
                .setContentText("Прокси активен (порт " + PROXY_PORT + ")")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        if (!running.getAndSet(true)) {
            executor.submit(this::runProxyServer);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running.set(false);
        instance = null;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        if (executor != null) executor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Proxy server loop ─────────────────────────────────────────────────

    private void runProxyServer() {
        try {
            serverSocket = new ServerSocket(PROXY_PORT, 50, InetAddress.getByName("127.0.0.1"));
            serverSocket.setReuseAddress(true);
            logInfo("listening on 127.0.0.1:" + PROXY_PORT);

            AndroidUtilities.runOnUIThread(() -> {
                try {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                    if (preferences.getBoolean("proxy_enabled", false)) {
                        String proxyAddress = preferences.getString("proxy_ip", "");
                        int proxyPort = preferences.getInt("proxy_port", 1080);
                        if ("127.0.0.1".equals(proxyAddress) && proxyPort == 1080) {
                            String proxyUsername = preferences.getString("proxy_user", "");
                            String proxyPassword = preferences.getString("proxy_pass", "");
                            String proxySecret = preferences.getString("proxy_secret", "");
                            ConnectionsManager.setProxySettings(true, proxyAddress, proxyPort, proxyUsername, proxyPassword, proxySecret);
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });

            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    executor.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running.get()) {
                        logError("accept error", e);
                    }
                }
            }
        } catch (IOException e) {
            logError("server error", e);
        } finally {
            running.set(false);
        }
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

        public void update(byte[] in, int inOff, int len, byte[] out, int outOff) throws Exception {
            cipher.update(in, inOff, len, out, outOff);
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

    // ─── MsgSplitter ───────────────────────────────────────────────────────

    private static class MsgSplitter {
        private final AESCTR dec;
        private final int proto;
        private final byte[] cipherBuf = new byte[256 * 1024];
        private final byte[] plainBuf = new byte[256 * 1024];
        private int bufLen = 0;
        private boolean disabled = false;

        public MsgSplitter(byte[] relayInit, int proto) throws Exception {
            byte[] key = new byte[32];
            byte[] iv = new byte[16];
            System.arraycopy(relayInit, 8, key, 0, 32);
            System.arraycopy(relayInit, 40, iv, 0, 16);
            this.dec = new AESCTR(key, iv);
            this.dec.update(new byte[64]); // advance 64 bytes
            this.proto = proto;
        }

        public synchronized List<byte[]> split(byte[] chunk) throws Exception {
            if (chunk == null || chunk.length == 0) {
                return new ArrayList<>();
            }
            if (disabled) {
                List<byte[]> res = new ArrayList<>();
                res.add(chunk);
                return res;
            }

            if (bufLen + chunk.length > cipherBuf.length) {
                disabled = true;
                List<byte[]> res = new ArrayList<>();
                res.add(chunk);
                return res;
            }

            System.arraycopy(chunk, 0, cipherBuf, bufLen, chunk.length);
            dec.update(chunk, 0, chunk.length, plainBuf, bufLen);
            bufLen += chunk.length;

            List<byte[]> parts = new ArrayList<>();
            int offset = 0;

            while (offset < bufLen) {
                Integer packetLen = nextPacketLen(offset, bufLen - offset);
                if (packetLen == null) {
                    break;
                }
                if (packetLen <= 0) {
                    byte[] tail = new byte[bufLen - offset];
                    System.arraycopy(cipherBuf, offset, tail, 0, tail.length);
                    parts.add(tail);
                    offset = bufLen;
                    disabled = true;
                    break;
                }
                byte[] pkt = new byte[packetLen];
                System.arraycopy(cipherBuf, offset, pkt, 0, packetLen);
                parts.add(pkt);
                offset += packetLen;
            }

            if (offset > 0) {
                int remaining = bufLen - offset;
                if (remaining > 0) {
                    System.arraycopy(cipherBuf, offset, cipherBuf, 0, remaining);
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

    private void handleClient(Socket client) {
        try {
            client.setTcpNoDelay(true);
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
            int[] dcInfo = IP_TO_DC.get(destIp);
            int dcId = -1;
            boolean isMedia = false;

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

            // Подключаемся к Telegram DC через WebSocket (через обходные Cloudflare домены)
            SSLSocket tlsSocket = null;
            String chosenDomain = null;

            // Добавляем случайную задержку (jitter) для сглаживания параллельных соединений (избегаем 429 лимитов)
            try {
                Thread.sleep(20 + RANDOM.nextInt(280));
            } catch (InterruptedException ignored) {}

            // Перебираем наши домены-обходчики
            for (String baseDomain : BASE_DOMAINS) {
                String wsDomain = "kws" + dcId + "." + baseDomain;
                logInfo("Connecting to CF proxy " + wsDomain + ":443");
                try {
                    tlsSocket = (SSLSocket) sslSocketFactory.createSocket(wsDomain, 443);
                    tlsSocket.setUseClientMode(true);
                    tlsSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                    tlsSocket.setTcpNoDelay(true);
                    tlsSocket.setSoTimeout(10_000); // 10s timeout for handshake

                    // WebSocket handshake
                    wsHandshake(tlsSocket, wsDomain);
                    chosenDomain = wsDomain;
                    logInfo("Successfully connected to " + wsDomain);
                    break; // Успешно подключились!
                } catch (Exception e) {
                    logError("Failed to connect to " + wsDomain, e);
                    // Если словили 429 Too Many Requests, делаем паузу перед следующим доменом
                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        try {
                            Thread.sleep(300 + RANDOM.nextInt(200));
                        } catch (InterruptedException ignored) {}
                    }
                    if (tlsSocket != null) {
                        try { tlsSocket.close(); } catch (IOException ignored) {}
                        tlsSocket = null;
                    }
                }
            }

            if (tlsSocket == null || chosenDomain == null) {
                logInfo("All CF proxy domains failed!");
                client.close();
                return;
            }

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
                splitter = new MsgSplitter(relayInit, protoVal);
            } catch (Exception e) {
                logError("MsgSplitter initialization failed", e);
                client.close();
                tlsSocket.close();
                return;
            }

            InputStream wsIn = tlsSocket.getInputStream();
            OutputStream wsOut = tlsSocket.getOutputStream();

            // Отправляем relay init в WebSocket бинарном фрейме
            sendWsFrame(wsOut, relayInit);
            logInfo("Sent obfuscation handshake to remote WS");

            // Убираем timeout — теперь работаем постоянно
            client.setSoTimeout(0);
            tlsSocket.setSoTimeout(0);

            logInfo("Session established, entering active relay bridge...");

            // Запускаем двунаправленный мост с ре-шифрованием
            bridgeConnections(client, in, out, tlsSocket, wsIn, wsOut, cryptoCtx, splitter);

        } catch (Exception e) {
            logError("handleClient error", e);
        } finally {
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
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        String wsKey = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP);

        String req =
                "GET /apiws HTTP/1.1\r\n" +
                "Host: " + domain + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Sec-WebSocket-Protocol: binary\r\n" +
                "Origin: https://web.telegram.org\r\n" +
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

    private void sendWsFrame(OutputStream out, byte[] data) throws IOException {
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);

        int length = data.length;
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

        byte[] masked = new byte[length];
        for (int i = 0; i < length; i++) {
            masked[i] = (byte) (data[i] ^ mask[i % 4]);
        }

        out.write(header.array());
        out.write(masked);
        out.flush();
    }

    private byte[] recvWsFrame(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        if (b1 < 0 || b2 < 0) return null;

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
        return payload;
    }

    private byte[] generateRelayInit(byte[] clientHandshake, int dcId, boolean isMedia) {
        int dcIdx = isMedia ? -dcId : dcId;
        byte[] protoTag = PROTO_TAG_ABRIDGED;

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

        // Thread: client -> WebSocket
        Thread toWs = new Thread(() -> {
            try {
                byte[] buf = new byte[65536];
                int n;
                while (!closed.get() && (n = in.read(buf)) > 0) {
                    byte[] plain = ctx.cltDec.update(Arrays.copyOf(buf, n));
                    byte[] enc = ctx.tgEnc.update(plain);
                    List<byte[]> parts = splitter.split(enc);
                    if (!parts.isEmpty()) {
                        synchronized (wsOut) {
                            for (byte[] part : parts) {
                                sendWsFrame(wsOut, part);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                closed.set(true);
                try { tlsSocket.close(); } catch (IOException ignored) {}
            }
        });
        toWs.setDaemon(true);
        toWs.start();

        // Thread: WebSocket -> client
        try {
            while (!closed.get()) {
                byte[] frame = recvWsFrame(wsIn);
                if (frame == null) break;
                byte[] plain = ctx.tgDec.update(frame);
                byte[] enc = ctx.cltEnc.update(plain);
                out.write(enc);
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            closed.set(true);
            try { client.close(); } catch (IOException ignored) {}
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
}
