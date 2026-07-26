package org.telegram.messenger.music.providers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.music.MusicHttp;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads "currently playing" via stats.fm's public API (no OAuth needed —
 * just the user's public stats.fm/Spotify username), mirroring reSwaga's
 * approach of avoiding a full Spotify OAuth flow.
 */
public class SpotifyProvider implements TrackProvider {

    private final String username;
    private final Map<String, String> headers = new HashMap<>();

    public SpotifyProvider(String usernameOrUrl) {
        String u = usernameOrUrl == null ? "" : usernameOrUrl.trim();
        if (u.contains("/")) {
            u = u.substring(u.lastIndexOf('/') + 1);
        }
        this.username = u;
        headers.put("User-Agent", "Mozilla/5.0");
        headers.put("Accept", "application/json");
    }

    @Override
    public boolean isConfigured() {
        return !username.isEmpty();
    }

    @Override
    public Track getTrack() {
        if (!isConfigured()) {
            return Track.inactive();
        }
        JSONObject resp = MusicHttp.getJson("https://api.stats.fm/api/v1/users/" + username + "/streams/current", headers);
        if (resp == null) {
            return Track.inactive();
        }
        JSONObject item = resp.optJSONObject("item");
        if (item == null) {
            return Track.inactive();
        }
        JSONObject t = item.optJSONObject("track");
        if (t == null) {
            return Track.inactive();
        }

        Track track = new Track();
        track.active = true;
        JSONObject ids = t.optJSONObject("externalIds");
        String spotifyId = null;
        if (ids != null) {
            JSONArray sp = ids.optJSONArray("spotify");
            if (sp != null && sp.length() > 0) {
                spotifyId = sp.optString(0, null);
            }
        }
        track.id = spotifyId;
        track.title = t.optString("name");
        JSONArray artists = t.optJSONArray("artists");
        if (artists != null) {
            for (int i = 0; i < artists.length(); i++) {
                track.artists.add(artists.optJSONObject(i).optString("name"));
            }
        }
        JSONArray albums = t.optJSONArray("albums");
        if (albums != null && albums.length() > 0) {
            track.thumbUrl = albums.optJSONObject(0).optString("image", null);
        }
        track.durationSec = t.optInt("durationMs", 0) / 1000;
        track.progressSec = item.optInt("progressMs", 0) / 1000;
        track.link = spotifyId != null ? ("https://open.spotify.com/track/" + spotifyId) : null;
        track.device = item.optString("deviceName", null);
        return track;
    }
}
