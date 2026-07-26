package org.telegram.messenger.music.providers;

import org.json.JSONObject;
import org.telegram.messenger.music.MusicHttp;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;

import java.util.HashMap;
import java.util.Map;

public class YandexMusicProvider implements TrackProvider {

    private static final String DEFAULT_API_URL = "https://track.mipoh.gay";

    private final String token;
    private final String apiUrl;

    public YandexMusicProvider(String token, String customApiUrl) {
        this.token = token == null ? "" : token.trim();
        this.apiUrl = (customApiUrl == null || customApiUrl.isEmpty()) ? DEFAULT_API_URL : customApiUrl;
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
        headers.put("User-Agent", "Mozilla/5.0");
        headers.put("Accept", "application/json");
        headers.put("ya-token", token);

        JSONObject data = MusicHttp.getJson(apiUrl + "/get_current_track_beta", headers);
        if (data == null || !data.has("track")) {
            return Track.inactive();
        }
        JSONObject t = data.optJSONObject("track");
        if (t == null) {
            return Track.inactive();
        }

        Track track = new Track();
        track.active = true;
        track.id = t.optString("track_id", null);
        track.title = t.optString("title", null);
        String rawArtist = t.optString("artist", "");
        for (String a : rawArtist.split(",")) {
            String trimmed = a.trim();
            if (!trimmed.isEmpty()) {
                track.artists.add(trimmed);
            }
        }
        track.album = t.optString("album", null);
        track.thumbUrl = t.optString("img", null);
        track.durationSec = t.optInt("duration", 0);
        track.progressSec = data.optInt("progress_ms", 0) / 1000;
        track.link = "https://music.yandex.ru/track/" + track.id;
        track.downloadUrl = t.optString("download_link", null);
        return track;
    }
}
