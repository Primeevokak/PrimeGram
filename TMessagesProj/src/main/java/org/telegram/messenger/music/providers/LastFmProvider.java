package org.telegram.messenger.music.providers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.music.MusicHttp;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;

public class LastFmProvider implements TrackProvider {

    private final String username;
    private final String apiKey;

    /** Unlike reSwaga, there is no baked-in shared API key — get a free one at last.fm/api/account/create. */
    public LastFmProvider(String username, String apiKey) {
        this.username = username == null ? "" : username.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean isConfigured() {
        return !username.isEmpty() && !apiKey.isEmpty();
    }

    @Override
    public boolean canDownloadTrack() {
        return false;
    }

    @Override
    public Track getTrack() {
        if (!isConfigured()) {
            return Track.inactive();
        }
        String url = "http://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=" + username
                + "&api_key=" + apiKey + "&format=json&limit=1";
        JSONObject data = MusicHttp.getJson(url, null);
        if (data == null) {
            return Track.inactive();
        }
        JSONObject recent = data.optJSONObject("recenttracks");
        JSONArray tracks = recent != null ? recent.optJSONArray("track") : null;
        if (tracks == null || tracks.length() == 0) {
            return Track.inactive();
        }
        JSONObject t = tracks.optJSONObject(0);
        if (t == null) {
            return Track.inactive();
        }

        Track track = new Track();
        track.active = true;
        track.title = t.optString("name", "");
        track.id = track.title.replace(' ', '_').toLowerCase();

        Object artistObj = t.opt("artist");
        String artist = artistObj instanceof JSONObject ? ((JSONObject) artistObj).optString("#text", "") : String.valueOf(artistObj);
        track.artists.add(artist);

        Object albumObj = t.opt("album");
        track.album = albumObj instanceof JSONObject ? ((JSONObject) albumObj).optString("#text", "") : String.valueOf(albumObj);

        JSONArray images = t.optJSONArray("image");
        String imageUrl = null;
        if (images != null) {
            for (String size : new String[]{"mega", "extralarge", "large"}) {
                for (int i = 0; i < images.length() && imageUrl == null; i++) {
                    JSONObject img = images.optJSONObject(i);
                    if (img != null && size.equals(img.optString("size")) && !img.optString("#text", "").isEmpty()) {
                        imageUrl = img.optString("#text");
                    }
                }
                if (imageUrl != null) break;
            }
        }
        track.thumbUrl = imageUrl;
        track.link = t.optString("url", null);
        return track;
    }
}
