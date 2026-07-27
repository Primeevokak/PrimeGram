package org.telegram.messenger;

import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: translation through a provider other than Telegram's own.
 *
 * <p>Telegram's translate call goes over the same connection as everything else and is subject to
 * whatever limits the account has. In a fork whose whole point is working where Telegram is
 * blocked, that is a poor single point of failure: the tunnel can be struggling while
 * translate.googleapis.com answers in a hundred milliseconds over the ordinary network.
 *
 * <p>The result is handed back as a {@link TLRPC.TL_messages_translateResult}, the same object the
 * server would have returned, so nothing downstream - caching, the poll and story paths, the
 * translate bar - needs to know which provider produced it.
 *
 * <p>Known limitation: entities are not preserved. Google returns plain text, so bold, links and
 * mentions are dropped from a translated message. Telegram's own translation keeps them, which is
 * why it stays the default.
 */
public class PrimeTranslator {

    public static final int PROVIDER_TELEGRAM = 0;
    public static final int PROVIDER_GOOGLE = 1;
    public static final int PROVIDER_YANDEX = 2;

    /** Whether translation should bypass Telegram entirely. */
    public static boolean isExternal() {
        return PrimeTweaks.translateProvider() != PROVIDER_TELEGRAM;
    }

    /**
     * Translates {@code texts} and calls {@code callback} exactly once, on the main thread.
     *
     * <p>{@code texts} rather than the request itself, because the request that translates real
     * messages carries only peer and message ids - the server looks the text up. An outside
     * provider cannot, so the caller passes the copies it already holds.
     */
    public static void translate(List<TLRPC.TL_textWithEntities> texts, String toLang, RequestDelegate callback) {
        if (callback == null) {
            return;
        }
        if (texts == null || texts.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> callback.run(null, error(400, "PRIME_NOTHING_TO_TRANSLATE")));
            return;
        }
        // Snapshot the strings now: the caller's list belongs to a pending-translation record that
        // may be rewritten while this runs on another thread.
        final ArrayList<String> sources = new ArrayList<>(texts.size());
        for (TLRPC.TL_textWithEntities text : texts) {
            sources.add(text == null || text.text == null ? "" : text.text);
        }
        final String target = toLang == null || toLang.isEmpty() ? "en" : toLang;

        final int provider = PrimeTweaks.translateProvider();
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<TLRPC.TL_textWithEntities> results = new ArrayList<>(sources.size());
            try {
                for (String source : sources) {
                    final TLRPC.TL_textWithEntities out = new TLRPC.TL_textWithEntities();
                    out.entities = new ArrayList<>();
                    out.text = source.isEmpty() ? ""
                            : provider == PROVIDER_YANDEX ? yandex(source, target) : google(source, target);
                    results.add(out);
                }
            } catch (Throwable t) {
                FileLog.e(t);
                AndroidUtilities.runOnUIThread(() -> callback.run(null, error(500, "PRIME_TRANSLATE_FAILED")));
                return;
            }
            final TLRPC.TL_messages_translateResult result = new TLRPC.TL_messages_translateResult();
            result.result.addAll(results);
            AndroidUtilities.runOnUIThread(() -> callback.run(result, null));
        });
    }

    private static TLRPC.TL_error error(int code, String text) {
        TLRPC.TL_error err = new TLRPC.TL_error();
        err.code = code;
        err.text = text;
        return err;
    }

    /**
     * The endpoint the Google Translate web widget uses. No key, no account, and it answers with
     * the sentences already split, which is why the reply has to be stitched back together.
     */
    private static String google(String source, String toLang) throws Exception {
        final String url = "https://translate.googleapis.com/translate_a/single?dj=1&q="
                + URLEncoder.encode(source, "UTF-8")
                + "&sl=auto&tl=" + URLEncoder.encode(toLang, "UTF-8")
                + "&ie=UTF-8&oe=UTF-8&client=at&dt=t&otf=2";

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
            final int code = connection.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }
            return joinSentences(read(connection.getInputStream()));
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /**
     * The endpoint the Yandex Translate mobile app uses. Also keyless, and it reaches a different
     * network than Google does - useful precisely when one of the two is unreachable.
     */
    private static String yandex(String source, String toLang) throws Exception {
        // The id is the app's install identifier; the service only checks that one is present and
        // well-formed, so a fresh one per request is both valid and the least identifying choice.
        final String id = java.util.UUID.randomUUID().toString().replace("-", "") + "-0-0";
        final String url = "https://translate.yandex.net/api/v1/tr.json/translate?srv=android&id=" + id;
        final byte[] body = ("lang=" + URLEncoder.encode(toLang, "UTF-8")
                + "&text=" + URLEncoder.encode(source, "UTF-8")).getBytes("UTF-8");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "ru.yandex.translate/21.15.4.21402814 (Android 13)");
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            final int code = connection.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }
            return joinTextArray(read(connection.getInputStream()));
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private static String read(InputStream stream) throws Exception {
        final StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 16384)) {
            final char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) > 0) {
                body.append(buffer, 0, count);
            }
        }
        return body.toString();
    }

    /** Yandex answers with {@code {"text":["..."],"lang":"en-ru"}} - a plain array of strings. */
    private static String joinTextArray(String json) throws Exception {
        final org.json.JSONObject root = new org.json.JSONObject(json);
        final org.json.JSONArray texts = root.optJSONArray("text");
        if (texts == null) {
            throw new Exception("no text in response");
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < texts.length(); i++) {
            final String piece = texts.optString(i, null);
            if (piece != null) {
                out.append(piece);
            }
        }
        return out.toString();
    }

    /**
     * Pulls the translated halves out of {@code {"sentences":[{"trans":"..","orig":".."}, ..]}}.
     *
     * <p>The response also carries a {@code src} field and, for longer input, sentences that hold
     * only transliteration - taking every {@code trans} in order and concatenating is what
     * reassembles the original paragraph.
     */
    private static String joinSentences(String json) throws Exception {
        final org.json.JSONObject root = new org.json.JSONObject(json);
        final org.json.JSONArray sentences = root.optJSONArray("sentences");
        if (sentences == null) {
            throw new Exception("no sentences in response");
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < sentences.length(); i++) {
            final org.json.JSONObject sentence = sentences.optJSONObject(i);
            if (sentence == null) {
                continue;
            }
            final String piece = sentence.optString("trans", null);
            if (piece != null) {
                out.append(piece);
            }
        }
        return out.toString();
    }
}
