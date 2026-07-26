package org.telegram.messenger.music;

import java.util.ArrayList;
import java.util.List;

/** Currently-playing track snapshot, as reported by a {@link TrackProvider}. */
public class Track {

    public boolean active;
    public String id;
    public String title;
    public List<String> artists = new ArrayList<>();
    public String album;
    public String thumbUrl;
    public int durationSec;
    public int progressSec;
    public String link;
    public String albumLink;
    public String device;
    /** Direct download URL, if the provider already knows one (e.g. Yandex Music). Null otherwise. */
    public String downloadUrl;

    public static Track inactive() {
        Track t = new Track();
        t.active = false;
        return t;
    }

    public String artistsJoined() {
        return android.text.TextUtils.join(", ", artists);
    }
}
