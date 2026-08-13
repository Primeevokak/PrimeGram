package org.telegram.messenger.music;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.music.providers.LastFmProvider;
import org.telegram.messenger.music.providers.SoundCloudProvider;
import org.telegram.messenger.music.providers.SpotifyProvider;
import org.telegram.messenger.music.providers.TgMusicProvider;
import org.telegram.messenger.music.providers.VkMusicProvider;
import org.telegram.messenger.music.providers.YandexMusicProvider;

/** Reads/writes "Музыка" plugin settings and builds the currently-configured {@link TrackProvider}. */
public class MusicSettingsStore {

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean isTabEnabled() {
        return prefs().getBoolean("music_tab_enabled", false);
    }

    public static void setTabEnabled(boolean enabled) {
        prefs().edit().putBoolean("music_tab_enabled", enabled).apply();
    }

    public static MusicPlatform getSelectedPlatform() {
        return MusicPlatform.fromId(prefs().getInt("music_selected_platform", 0));
    }

    public static void setSelectedPlatform(MusicPlatform platform) {
        prefs().edit().putInt("music_selected_platform", platform.id).apply();
    }

    public static String getPlatformValue(MusicPlatform platform) {
        return prefs().getString("music_value_" + platform.id, "");
    }

    public static void setPlatformValue(MusicPlatform platform, String value) {
        prefs().edit().putString("music_value_" + platform.id, value).apply();
    }

    public static String getLastFmApiKey() {
        return prefs().getString("music_lastfm_api_key", "");
    }

    public static void setLastFmApiKey(String key) {
        prefs().edit().putString("music_lastfm_api_key", key).apply();
    }

    public static String getYandexCustomApiUrl() {
        return prefs().getString("music_yandex_api_url", "");
    }

    public static void setYandexCustomApiUrl(String url) {
        prefs().edit().putString("music_yandex_api_url", url).apply();
    }

    public static String getCobaltApiUrl() {
        return prefs().getString("music_cobalt_api_url", CobaltDownloader.DEFAULT_API_URL);
    }

    public static void setCobaltApiUrl(String url) {
        prefs().edit().putString("music_cobalt_api_url", url).apply();
    }

    public static String getFontFamily() {
        return prefs().getString("music_font_family", "System");
    }

    public static void setFontFamily(String family) {
        prefs().edit().putString("music_font_family", family).apply();
    }

    public static MusicCardRenderer.Style buildCardStyle() {
        MusicCardRenderer.Style style = new MusicCardRenderer.Style();
        style.fontFamily = getFontFamily();
        return style;
    }

    /** Builds the provider for the currently selected platform, or null if none selected. */
    public static TrackProvider createCurrentProvider() {
        MusicPlatform platform = getSelectedPlatform();
        switch (platform) {
            case SPOTIFY:
                return new SpotifyProvider(getPlatformValue(platform));
            case YANDEX_MUSIC:
                return new YandexMusicProvider(getPlatformValue(platform), getYandexCustomApiUrl());
            case SOUNDCLOUD:
                return new SoundCloudProvider(getPlatformValue(platform));
            case VK_MUSIC:
                return new VkMusicProvider(getPlatformValue(platform));
            case TG_MUSIC:
                return new TgMusicProvider();
            case LASTFM:
                return new LastFmProvider(getPlatformValue(platform), getLastFmApiKey());
            case YOUTUBE_MUSIC:
                return new org.telegram.messenger.music.providers.YoutubeMusicProvider(getPlatformValue(platform));
            default:
                return null;
        }
    }
}
