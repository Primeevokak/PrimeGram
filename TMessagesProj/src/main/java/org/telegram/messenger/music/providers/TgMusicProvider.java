package org.telegram.messenger.music.providers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.music.MusicHttp;
import org.telegram.messenger.music.Track;
import org.telegram.messenger.music.TrackProvider;

/** Reads Telegram's own "now playing" state — no external account needed. */
public class TgMusicProvider implements TrackProvider {

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Track getTrack() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null || MediaController.getInstance().isMessagePaused()) {
            return Track.inactive();
        }

        Track track = new Track();
        track.active = true;
        track.title = playing.getMusicTitle();
        track.artists.add(playing.getMusicAuthor());
        track.durationSec = (int) playing.getDuration();
        track.progressSec = playing.audioProgressSec;

        if (playing.isMusic() && !playing.isVoice() && !playing.isRoundVideo()) {
            // Best-effort cover art lookup via iTunes search, matching reSwaga's approach —
            // Telegram audio messages don't carry cover art of their own.
            try {
                String artwork = playing.getArtworkUrl(false);
                if (artwork != null) {
                    String lookupUrl = artwork.replace("athumb", "https");
                    JSONObject resp = MusicHttp.getJson(lookupUrl, null);
                    JSONArray results = resp != null ? resp.optJSONArray("results") : null;
                    if (results != null && results.length() > 0) {
                        JSONObject r = results.optJSONObject(0);
                        track.album = r.optString("collectionViewUrl", null);
                        track.link = r.optString("trackViewUrl", null);
                        String artworkUrl = r.optString("artworkUrl30", null);
                        if (artworkUrl != null) {
                            track.thumbUrl = artworkUrl.replace("30x30bb", "600x600bb");
                        }
                    }
                }
            } catch (Exception ignore) {}
        }
        return track;
    }

    @Override
    public boolean canDownloadTrack() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        return playing != null && playing.isMusic();
    }
}
