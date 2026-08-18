package org.telegram.messenger.music;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * PrimeGram Music: fetches song lyrics from <a href="https://lrclib.net">lrclib.net</a>
 * (free, no API key, no rate-limit auth) - nothing in this fork fetches lyrics today,
 * this is a genuinely new external call mirroring the existing precedent of
 * {@code MusicMessenger} already reaching out to free external services (song.link, a
 * public Cobalt instance) for a similar "enrich a track with more data" need.
 */
public final class PrimeLyricsFetcher {

    public interface Callback {
        /** @param lyrics plain lyrics text, or null if none were found/the request failed. */
        void onResult(String lyrics);
    }

    private PrimeLyricsFetcher() {
    }

    public static void fetch(String title, String artist, int durationSeconds, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            String lyrics = fetchBlocking(title, artist, durationSeconds);
            callback.onResult(lyrics);
        });
    }

    private static String fetchBlocking(String title, String artist, int durationSeconds) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        try {
            StringBuilder url = new StringBuilder("https://lrclib.net/api/get?track_name=")
                    .append(URLEncoder.encode(title, "UTF-8"));
            if (artist != null && !artist.isEmpty()) {
                url.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"));
            }
            if (durationSeconds > 0) {
                url.append("&duration=").append(durationSeconds);
            }
            JSONObject response = MusicHttp.getJson(url.toString(), null);
            if (response == null) {
                return null;
            }
            String plain = response.optString("plainLyrics", null);
            if (plain != null && !plain.isEmpty()) {
                return plain;
            }
            String synced = response.optString("syncedLyrics", null);
            if (synced != null && !synced.isEmpty()) {
                return stripTimestamps(synced);
            }
            return null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** lrclib's "synced" lyrics are LRC-format (`[mm:ss.xx] line`) - strip the
     *  timestamps for the plain-text fallback display; a real synced-scroll view is a
     *  possible future refinement, not needed for this feature's first cut. */
    private static String stripTimestamps(String synced) {
        StringBuilder out = new StringBuilder();
        for (String line : synced.split("\n")) {
            out.append(line.replaceFirst("^\\[\\d+:\\d+\\.\\d+]\\s*", "")).append("\n");
        }
        return out.toString().trim();
    }
}
