package org.telegram.messenger.music;

public enum MusicPlatform {
    NOT_SELECTED(0, "Не выбрано", null),
    SPOTIFY(1, "Spotify", "s"),
    YANDEX_MUSIC(2, "Яндекс Музыка", "ya"),
    SOUNDCLOUD(3, "SoundCloud", "sc"),
    VK_MUSIC(4, "VK Музыка", null),
    TG_MUSIC(5, "Telegram", null),
    LASTFM(6, "Last.fm", null);

    public final int id;
    public final String displayName;
    /** song.link codename, used to resolve a cross-platform download link. Null if unsupported. */
    public final String songlinkCode;

    MusicPlatform(int id, String displayName, String songlinkCode) {
        this.id = id;
        this.displayName = displayName;
        this.songlinkCode = songlinkCode;
    }

    public static MusicPlatform fromId(int id) {
        for (MusicPlatform p : values()) {
            if (p.id == id) {
                return p;
            }
        }
        return NOT_SELECTED;
    }
}
