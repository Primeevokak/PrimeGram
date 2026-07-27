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

    public static final String DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions";
    public static final String DEFAULT_MODEL = "whisper-large-v3-turbo";

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

    public static boolean isConfigured() {
        return isEnabled() && !TextUtils.isEmpty(getEndpoint()) && !TextUtils.isEmpty(getToken());
    }

    /**
     * True when this account should use our service instead of Telegram's. Premium accounts
     * never come here: they already have a better path that costs them nothing extra.
     */
    public static boolean shouldHandle(int account) {
        try {
            return !UserConfig.getInstance(account).isPremium() && isConfigured();
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
            writeField(out, boundary, "model", getModel());
            if (!TextUtils.isEmpty(getLanguage())) {
                writeField(out, boundary, "language", getLanguage());
            }
            writeField(out, boundary, "response_format", "json");
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
            out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            InputStream in = new BufferedInputStream(new FileInputStream(file));
            byte[] chunk = new byte[16 * 1024];
            int read;
            while ((read = in.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }
            in.close();
            out.writeBytes("\r\n--" + boundary + "--\r\n");
            out.flush();
            out.close();

            int code = connection.getResponseCode();
            String body = readAll(code / 100 == 2 ? connection.getInputStream() : connection.getErrorStream());
            if (code / 100 != 2) {
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
     * Turns the provider's error into something a user can act on. The two that actually
     * happen are a bad key and a spent quota, and "HTTP 401" tells nobody anything.
     */
    private static String describeError(int code, String body) {
        if (code == 401 || code == 403) {
            return "Сервис отклонил ключ";
        }
        if (code == 429) {
            return "Лимит сервиса исчерпан, попробуйте позже";
        }
        try {
            String message = new JSONObject(body).getJSONObject("error").optString("message", "");
            if (!TextUtils.isEmpty(message)) {
                return message;
            }
        } catch (Throwable ignore) {
        }
        return "Сервис ответил ошибкой " + code;
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
