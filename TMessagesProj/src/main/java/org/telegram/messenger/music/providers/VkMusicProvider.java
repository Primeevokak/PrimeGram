package org.telegram.messenger.music.providers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.music.MusicHttp;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;

public class VkMusicProvider implements TrackProvider {

    private final String token;

    public VkMusicProvider(String token) {
        this.token = token == null ? "" : token.trim();
    }

    @Override
    public boolean isConfigured() {
        return !token.isEmpty();
    }

    /** VK doesn't expose a stable download URL through this endpoint. */
    @Override
    public boolean canDownloadTrack() {
        return false;
    }

    @Override
    public Track getTrack() {
        if (!isConfigured()) {
            return Track.inactive();
        }
        String url = "https://api.vk.ru/method/users.get?fields=status&v=5.199&access_token=" + token;
        JSONObject data = MusicHttp.getJson(url, null);
        if (data == null) {
            return Track.inactive();
        }
        JSONArray responseArr = data.optJSONArray("response");
        if (responseArr == null || responseArr.length() == 0) {
            return Track.inactive();
        }
        JSONObject user = responseArr.optJSONObject(0);
        JSONObject t = user != null ? user.optJSONObject("status_audio") : null;
        if (t == null) {
            return Track.inactive();
        }

        Track track = new Track();
        track.active = true;
        track.id = String.valueOf(t.optLong("id", 0));

        JSONArray mainArtists = t.optJSONArray("main_artists");
        if (mainArtists != null && mainArtists.length() > 0) {
            for (int i = 0; i < mainArtists.length(); i++) {
                track.artists.add(mainArtists.optJSONObject(i).optString("name", "Artist w/out name"));
            }
        } else {
            track.artists.add(t.optString("artist", "Artist w/out name"));
        }

        long ownerId = t.optLong("owner_id", 0);
        long audioId = t.optLong("id", 0);
        track.link = "https://vk.ru/audio" + ownerId + "_" + audioId;

        JSONObject album = t.optJSONObject("album");
        String thumb = null;
        if (album != null) {
            track.albumLink = "https://vk.ru/music/album/" + album.optLong("owner_id", 0) + "_" + album.optLong("id", 0) + "_" + album.optString("access_key", "");
            JSONObject thumbObj = album.optJSONObject("thumb");
            if (thumbObj != null) {
                thumb = thumbObj.optString("photo_1200", null);
            }
        } else {
            track.albumLink = track.link;
        }
        track.thumbUrl = thumb;
        track.title = t.optString("title", "No title provided");
        return track;
    }
}
