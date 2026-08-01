package org.telegram.messenger;

import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * PrimeGram: voice-message transcription for accounts without Telegram Premium.
 *
 * <p>Telegram gates transcription behind Premium (with a small weekly trial). Users who
 * actually have Premium keep the native path untouched — it is better integrated, it is
 * already paid for, and it does not hand anyone's voice to a third party. This class only
 * takes over when the account has no Premium.
 *
 * <p>The endpoint is OpenAI-compatible (<code>POST /audio/transcriptions</code>, multipart with
 * a <code>file</code> and a <code>model</code> field), which is the de-facto standard: Groq,
 * OpenAI, Cloudflare's gateway and any self-hosted whisper server all speak it. The default is
 * Groq's free tier — no card, and its daily allowance is far past what a human sends in voice
 * messages.
 *
 * <p><b>Privacy.</b> This uploads the voice message to a server that is not Telegram. That is
 * a real cost and the reason the feature ships off by default and has to be switched on by
 * hand, with the warning next to the switch.
 */
public class PrimeTranscription {

    public static final String KEY_ENABLED = "primegram_stt_enabled";
    public static final String KEY_ENDPOINT = "primegram_stt_endpoint";
    public static final String KEY_TOKEN = "primegram_stt_token";
    public static final String KEY_MODEL = "primegram_stt_model";
    public static final String KEY_LANGUAGE = "primegram_stt_language";

    public static final String KEY_SMART_DNS = "primegram_stt_smart_dns";

    public static final String DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions";
    public static final String DEFAULT_MODEL = "whisper-large-v3-turbo";

    /**
     * A resolver that answers for services which refuse whole countries by returning its own
     * gateway, which then forwards by SNI. Used for this one request and nothing else - the
     * client's own traffic keeps going wherever it was going.
     */
    public static final String SMART_DNS_ENDPOINT = "https://xbox-dns.ru/dns-query";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    /** Bigger than any voice message Telegram will produce; a guard, not a real limit. */
    private static final long MAX_FILE_BYTES = 24L * 1024 * 1024;

    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    public static boolean isEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String getEndpoint() {
        String value = MessagesController.getGlobalMainSettings().getString(KEY_ENDPOINT, "");
        return TextUtils.isEmpty(value) ? DEFAULT_ENDPOINT : value;
    }

    public static void setEndpoint(String value) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_ENDPOINT, value == null ? "" : value.trim()).apply();
    }

    public static String getToken() {
        return MessagesController.getGlobalMainSettings().getString(KEY_TOKEN, "");
    }

    public static void setToken(String value) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_TOKEN, value == null ? "" : value.trim()).apply();
    }

    public static String getModel() {
        String value = MessagesController.getGlobalMainSettings().getString(KEY_MODEL, "");
        return TextUtils.isEmpty(value) ? DEFAULT_MODEL : value;
    }

    public static void setModel(String value) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_MODEL, value == null ? "" : value.trim()).apply();
    }

    /** Optional ISO-639-1 hint. Empty means "let the model decide", which is usually right. */
    public static String getLanguage() {
        return MessagesController.getGlobalMainSettings().getString(KEY_LANGUAGE, "");
    }

    public static void setLanguage(String value) {
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_LANGUAGE, value == null ? "" : value.trim()).apply();
    }

    public static boolean isSmartDnsEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(KEY_SMART_DNS, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setSmartDnsEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY_SMART_DNS, enabled).apply();
    }

    public static boolean isConfigured() {
        return isEnabled() && !TextUtils.isEmpty(getEndpoint()) && !TextUtils.isEmpty(getToken());
    }

    /**
     * True when this account should use our service instead of Telegram's. Premium accounts
     * never come here: they already have a better path that costs them nothing extra.
     */
    public static boolean shouldHandle(int account) {
        try {
            return !UserConfig.getInstance(account).hasRealPremium() && isConfigured();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Locates the downloaded voice file, or null when it isn't on disk yet. */
    public static File resolveFile(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        File file = null;
        if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
            file = new File(messageObject.messageOwner.attachPath);
        }
        if (file == null || !file.exists()) {
            file = FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner);
        }
        return file != null && file.exists() && file.length() > 0 ? file : null;
    }

    public static void transcribe(MessageObject messageObject, Callback callback) {
        final File file = resolveFile(messageObject);
        if (file == null) {
            // The voice message has to be downloaded before anyone can transcribe it; the
            // native path gets it from the server, we can only read what is already here.
            AndroidUtilities.runOnUIThread(() -> callback.onError("Сначала загрузите голосовое"));
            return;
        }
        if (file.length() > MAX_FILE_BYTES) {
            AndroidUtilities.runOnUIThread(() -> callback.onError("Файл слишком большой"));
            return;
        }
        final String fileName = TextUtils.isEmpty(messageObject.getFileName()) ? "voice.ogg" : messageObject.getFileName();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String text = upload(file, fileName);
                AndroidUtilities.runOnUIThread(() -> callback.onResult(text));
            } catch (Throwable t) {
                FileLog.e(t);
                final String message = t.getMessage() == null ? "Ошибка расшифровки" : t.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onError(message));
            }
        });
    }

    private static String upload(File file, String fileName) throws Exception {
        final String boundary = "----PrimeGram" + System.currentTimeMillis();
        if (isSmartDnsEnabled()) {
            final String text = uploadThroughSmartDns(file, fileName, boundary);
            if (text != null) {
                return text;
            }
            // The resolver had nothing to say, so this falls through to the ordinary path rather
            // than failing: a service that was reachable all along should not stop working
            // because a resolver was down.
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(getEndpoint()).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + getToken());
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setFixedLengthStreamingMode(bodyLength(file, fileName, boundary));

            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            writeBody(out, file, fileName, boundary);
            out.close();

            int code = connection.getResponseCode();
            String body = readAll(code / 100 == 2 ? connection.getInputStream() : connection.getErrorStream());
            if (code / 100 != 2) {
                // Logged as well as shown: a refusal that arrives as an HTML page from something
                // in front of the service says nothing in the interface, and the difference
                // between "wrong key" and "we do not serve your country" lives in this body.
                FileLog.e("PrimeTranscription: " + getEndpoint() + " answered " + code + ": "
                        + (body == null ? "" : body.substring(0, Math.min(400, body.length()))));
                throw new IllegalStateException(describeError(code, body));
            }
            String text = new JSONObject(body).optString("text", "").trim();
            if (TextUtils.isEmpty(text)) {
                throw new IllegalStateException("Сервис вернул пустой ответ");
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * The same request, sent to the address the smart resolver gave for this host.
     *
     * @return the transcript, or null when the resolver could not be used and the caller should
     *         fall back to an ordinary connection
     */
    private static String uploadThroughSmartDns(File file, String fileName, String boundary) throws Exception {
        final java.net.URL url = new URL(getEndpoint());
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            return null;
        }
        final org.telegram.messenger.browser.PrimeDns.Result resolved =
                org.telegram.messenger.browser.PrimeDns.resolveVia(SMART_DNS_ENDPOINT, url.getHost());
        if (resolved == null || resolved.blocked || resolved.addresses.isEmpty()) {
            return null;
        }

        // Whether the resolver actually sent us somewhere else. These gateways only substitute for
        // the services they carry; for anything else they answer with the ordinary address, and
        // then this whole path is an elaborate way of making the same refused connection. Worth
        // knowing, because "did not help" and "did not apply" look identical from the outside.
        final java.net.InetAddress address = resolved.addresses.get(0);
        boolean redirected = true;
        try {
            for (java.net.InetAddress system : java.net.InetAddress.getAllByName(url.getHost())) {
                if (system.equals(address)) {
                    redirected = false;
                    break;
                }
            }
        } catch (Throwable ignore) {
        }
        FileLog.d("PrimeTranscription (smart dns): " + url.getHost() + " -> " + address.getHostAddress()
                + (redirected ? " (шлюз)" : " (обычный адрес, подмены нет)"));

        final java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + getToken());
        headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);

        final PrimeDirectHttps.Response response = PrimeDirectHttps.post(url, address, headers,
                bodyLength(file, fileName, boundary),
                out -> writeBody(new DataOutputStream(out), file, fileName, boundary));

        if (response.code / 100 != 2) {
            FileLog.e("PrimeTranscription (smart dns): " + url.getHost() + " answered " + response.code
                    + ": " + response.body.substring(0, Math.min(400, response.body.length())));
            final String reason = describeError(response.code, response.body);
            throw new IllegalStateException(redirected ? reason
                    : reason + "\n\nОбход не применился: резолвер не обслуживает " + url.getHost()
                            + " и вернул обычный адрес.");
        }
        final String text = new JSONObject(response.body).optString("text", "").trim();
        if (TextUtils.isEmpty(text)) {
            throw new IllegalStateException("Сервис вернул пустой ответ");
        }
        return text;
    }

    /**
     * Turns the provider's error into something a user can act on. The two that actually
     * happen are a bad key and a spent quota, and "HTTP 401" tells nobody anything.
     */
    private static String describeError(int code, String body) {
        // The provider's own words first, whatever the code. "Сервис отклонил ключ" covered both
        // an invalid key and a region the provider refuses to serve - which are the same HTTP
        // status and completely different problems, one of which no amount of retyping fixes.
        String message = "";
        try {
            final JSONObject json = new JSONObject(body);
            final JSONObject error = json.optJSONObject("error");
            message = error != null ? error.optString("message", "") : json.optString("message", "");
        } catch (Throwable ignore) {
        }
        if (!TextUtils.isEmpty(message)) {
            return message.length() > 200 ? message.substring(0, 200) : message;
        }
        if (code == 401 || code == 403) {
            return "Сервис отклонил ключ или отказал в доступе (" + code + ")";
        }
        if (code == 429) {
            return "Лимит сервиса исчерпан, попробуйте позже";
        }
        return "Сервис ответил ошибкой " + code;
    }

    /** The multipart body, written the same way whichever connection carries it. */
    private static void writeBody(DataOutputStream out, File file, String fileName, String boundary) throws Exception {
        writeField(out, boundary, "model", getModel());
        if (!TextUtils.isEmpty(getLanguage())) {
            writeField(out, boundary, "language", getLanguage());
        }
        writeField(out, boundary, "response_format", "json");
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] chunk = new byte[16 * 1024];
            int read;
            while ((read = in.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        out.writeBytes("\r\n--" + boundary + "--\r\n");
        out.flush();
    }

    private static long bodyLength(File file, String fileName, String boundary) throws Exception {
        ByteArrayOutputStream counter = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(counter);
        writeField(out, boundary, "model", getModel());
        if (!TextUtils.isEmpty(getLanguage())) {
            writeField(out, boundary, "language", getLanguage());
        }
        writeField(out, boundary, "response_format", "json");
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
        out.flush();
        long head = counter.size();
        long tail = ("\r\n--" + boundary + "--\r\n").getBytes("UTF-8").length;
        return head + file.length() + tail;
    }

    private static void writeField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes("UTF-8"));
        out.writeBytes("\r\n");
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > 1024 * 1024) {
                break;
            }
        }
        stream.close();
        return buffer.toString("UTF-8");
    }
}
