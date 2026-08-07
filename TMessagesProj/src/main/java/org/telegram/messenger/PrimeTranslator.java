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
    /** Splits a batch across both keyless providers by index parity and runs the whole batch
     *  concurrently instead of one request at a time - see {@link #translate}. */
    public static final int PROVIDER_MULTIPLAY = 3;

    /** Bounded so a large batch (translating an entire loaded chat history at once) can't open
     *  dozens of sockets at the same two hosts simultaneously - 4 in flight is enough to get the
     *  parallelism win without looking like a burst to either provider. */
    private static final java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(4);

    /** Whether translation should bypass Telegram entirely. */
    public static boolean isExternal() {
        return PrimeTweaks.translateProvider() != PROVIDER_TELEGRAM;
    }

    private static volatile boolean warmed;

    /**
     * The very first {@link #translate} call after the process starts pays for a DNS lookup and a
     * fresh TLS handshake to the provider host - that one-time cost is what makes the first
     * translated send visibly slower than every one after it, which just reuses the pooled
     * connection {@link HttpURLConnection} keeps open. Call this as soon as the feature is turned
     * on for a chat (rather than waiting for the first real send) so that cost is already paid by
     * the time the user actually sends something.
     */
    public static void prewarm() {
        if (warmed || !isExternal()) {
            return;
        }
        warmed = true;
        final int provider = PrimeTweaks.translateProvider();
        pool.submit(() -> {
            try {
                if (provider == PROVIDER_YANDEX || provider == PROVIDER_MULTIPLAY) {
                    yandex("hi", "en");
                }
            } catch (Throwable ignore) {
            }
        });
        if (provider != PROVIDER_YANDEX) {
            pool.submit(() -> {
                try {
                    google("hi", "en");
                } catch (Throwable ignore) {
                }
            });
        }
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
        final int count = sources.size();
        // Each text used to go out one at a time on a single background thread - translating an
        // entire loaded chat history serially meant N sequential round trips. Firing them onto the
        // pool instead means they're actually in flight together; PROVIDER_MULTIPLAY additionally
        // splits the batch by index parity across both keyless providers, so neither host sees the
        // full batch and a lull in one doesn't stall texts that could have gone to the other.
        final TLRPC.TL_textWithEntities[] resultsArr = new TLRPC.TL_textWithEntities[count];
        final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(count);
        final java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        for (int i = 0; i < count; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    final String source = sources.get(index);
                    final TLRPC.TL_textWithEntities out = new TLRPC.TL_textWithEntities();
                    out.entities = new ArrayList<>();
                    if (source.isEmpty()) {
                        out.text = "";
                    } else if (provider == PROVIDER_MULTIPLAY) {
                        // Index parity still splits the load across both hosts for a real batch,
                        // but with only one text (every "translate before send" call - the
                        // outgoing path only ever has one) that split had no second provider to
                        // fall back to: index 0 always meant Google, so on a network that can't
                        // reach Google specifically (common enough on this fork's actual
                        // audience, where Yandex is often the one that still works), Multiplay
                        // was indistinguishable from plain Google and always failed. Trying the
                        // other provider on failure, here, is what actually earns the name.
                        final int primary = index % 2 == 0 ? PROVIDER_GOOGLE : PROVIDER_YANDEX;
                        try {
                            out.text = primary == PROVIDER_YANDEX ? yandex(source, target) : google(source, target);
                        } catch (Throwable primaryError) {
                            out.text = primary == PROVIDER_YANDEX ? google(source, target) : yandex(source, target);
                        }
                    } else {
                        out.text = provider == PROVIDER_YANDEX ? yandex(source, target) : google(source, target);
                    }
                    resultsArr[index] = out;
                } catch (Throwable t) {
                    FileLog.e(t);
                    failed.set(true);
                }
                if (remaining.decrementAndGet() == 0) {
                    if (failed.get()) {
                        AndroidUtilities.runOnUIThread(() -> callback.run(null, error(500, "PRIME_TRANSLATE_FAILED")));
                    } else {
                        final TLRPC.TL_messages_translateResult result = new TLRPC.TL_messages_translateResult();
                        for (TLRPC.TL_textWithEntities out : resultsArr) {
                            result.result.add(out);
                        }
                        AndroidUtilities.runOnUIThread(() -> callback.run(result, null));
                    }
                }
            });
        }
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
