package org.telegram.messenger.music.providers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.music.MusicHttp;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;

import java.util.HashMap;
import java.util.Map;

public class SoundCloudProvider implements TrackProvider {

    private static final String CLIENT_ID = "1HxML01xkzWgtHfBreaeZfpANMe3ADjb";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final String token;

    public SoundCloudProvider(String token) {
        this.token = token == null ? "" : token.trim();
    }

    @Override
    public boolean isConfigured() {
        return !token.isEmpty();
    }

    @Override
    public Track getTrack() {
        if (!isConfigured()) {
            return Track.inactive();
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Authorization", "OAuth " + token);
        headers.put("Origin", "https://soundcloud.com");
        headers.put("Referer", "https://soundcloud.com/");

        String url = "https://api-v2.soundcloud.com/me/play-history/tracks"
                + "?client_id=" + CLIENT_ID
                + "&app_locale=en&limit=20&offset=0&linked_partitioning=1";

        JSONObject data = MusicHttp.getJson(url, headers);
        if (data == null) {
            return Track.inactive();
        }
        JSONArray collection = data.optJSONArray("collection");
        if (collection == null || collection.length() == 0) {
            return Track.inactive();
        }
        JSONObject entry = collection.optJSONObject(0);
        JSONObject t = entry != null ? entry.optJSONObject("track") : null;
        if (t == null) {
            return Track.inactive();
        }

        Track track = new Track();
        track.active = true;
        track.id = String.valueOf(entry.optLong("track_id", 0));
        track.title = t.optString("title", "No title provided");
        JSONObject metadata = t.optJSONObject("publisher_metadata");
        track.artists.add(metadata != null ? metadata.optString("artist", "Anonymous artist") : "Anonymous artist");
        track.album = metadata != null ? metadata.optString("album_title", "") : "";
        track.thumbUrl = t.optString("artwork_url", null);
        track.durationSec = t.optInt("duration", 0) / 1000;
        track.link = t.optString("permalink_url", null);
        return track;
    }
}
