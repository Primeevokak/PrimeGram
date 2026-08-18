package org.telegram.messenger.music;

import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.Components.VideoPlayer;

/**
 * PrimeGram Music: a persistent equalizer for music playback, attached to whatever
 * {@code VideoPlayer} instance {@code MediaController} is currently using - the same
 * audio-session-id mechanism {@code VideoPlayer}'s own volume-boost feature already
 * uses for its {@code LoudnessEnhancer}, just for {@code android.media.audiofx.Equalizer}
 * instead. Re-attaches every time a new track starts (a fresh session id is possible
 * per {@code ExoPlayer} instance), since there's no single stable session id to attach
 * to once and forget.
 */
public final class PrimeEqualizer implements NotificationCenter.NotificationCenterDelegate {

    private static final PrimeEqualizer instance = new PrimeEqualizer();

    private static final String PREFS = "mainconfig";
    private static final String KEY_ENABLED = "prime_music_eq_enabled";
    private static final String KEY_BAND_PREFIX = "prime_music_eq_band_";

    private Equalizer equalizer;
    private short bandCount;

    public static void register() {
        NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.messagePlayingDidStart);
    }

    private PrimeEqualizer() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
        instance.applyEnabledState();
    }

    /** @return band count, or 0 if no equalizer is currently attached (nothing playing
     *  yet) - the settings screen should re-check after playback starts. */
    public static short getBandCount() {
        return instance.equalizer != null ? instance.bandCount : 0;
    }

    public static short[] getBandLevelRange() {
        if (instance.equalizer == null) {
            return new short[]{-1500, 1500};
        }
        try {
            return instance.equalizer.getBandLevelRange();
        } catch (Throwable t) {
            return new short[]{-1500, 1500};
        }
    }

    public static int getCenterFreqHz(short band) {
        if (instance.equalizer == null) {
            return 0;
        }
        try {
            return instance.equalizer.getCenterFreq(band) / 1000;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static short getBandLevel(short band) {
        int saved = prefs().getInt(KEY_BAND_PREFIX + band, Integer.MIN_VALUE);
        if (saved != Integer.MIN_VALUE) {
            return (short) saved;
        }
        return 0;
    }

    public static void setBandLevel(short band, short level) {
        prefs().edit().putInt(KEY_BAND_PREFIX + band, level).apply();
        if (instance.equalizer != null && isEnabled()) {
            try {
                instance.equalizer.setBandLevel(band, level);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private void applyEnabledState() {
        if (equalizer == null) {
            return;
        }
        try {
            equalizer.setEnabled(isEnabled());
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void attach() {
        try {
            release();
            VideoPlayer player = MediaController.getInstance().getAudioPlayer();
            if (player == null) {
                return;
            }
            int sessionId = player.getAudioSessionIdForEffects();
            if (sessionId == 0) {
                return;
            }
            equalizer = new Equalizer(0, sessionId);
            bandCount = equalizer.getNumberOfBands();
            for (short band = 0; band < bandCount; band++) {
                equalizer.setBandLevel(band, getBandLevel(band));
            }
            equalizer.setEnabled(isEnabled());
        } catch (Throwable t) {
            // Some devices/ROMs don't provide the effect at all - music still plays
            // fine, just without EQ, mirroring VideoPlayer's own LoudnessEnhancer
            // degrade-gracefully precedent.
            FileLog.e(t);
            equalizer = null;
        }
    }

    private void release() {
        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (Throwable ignore) {
            }
            equalizer = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart) {
            attach();
        }
    }
}
