package org.telegram.messenger.music;

import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
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
    // Band layout (count/frequencies/range) is fixed per device+build, not per
    // playback session - cached here the first time a real Equalizer attaches so the
    // settings screen keeps working (sliders visible, editable) even when nothing is
    // currently playing, instead of only right after a track started.
    private static final String KEY_CACHED_BAND_COUNT = "prime_music_eq_cached_band_count";
    private static final String KEY_CACHED_RANGE_MIN = "prime_music_eq_cached_range_min";
    private static final String KEY_CACHED_RANGE_MAX = "prime_music_eq_cached_range_max";
    private static final String KEY_CACHED_FREQ_PREFIX = "prime_music_eq_cached_freq_";

    private Equalizer equalizer;
    private short bandCount;

    public static void register() {
        // messagePlayingDidStart is posted per-account (MediaController posts via
        // NotificationCenter.getInstance(messageObject.currentAccount)), never on the
        // global instance - registering there (the original, wrong copy of a stale
        // precedent) meant attach() was never actually called on a real playback start,
        // so the equalizer looked entirely nonfunctional. Mirrors the working pattern
        // already used for this exact notification elsewhere (FragmentContextView,
        // AudioPlayerAlert, SharedAudioCell, etc.) - loop every account slot.
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(instance, NotificationCenter.messagePlayingDidStart);
        }
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

    /** @return band count - the live value while a real {@code Equalizer} is attached,
     *  otherwise whatever was cached the last time one was (band layout is fixed per
     *  device/build, not per playback session, so this is stable across app restarts).
     *  Probes a throwaway global-session {@code Equalizer} to seed the cache on first
     *  call if nothing has ever attached yet - the equalizer settings screen should be
     *  usable before the user has played a single track, not gated on one. */
    public static short getBandCount() {
        if (instance.equalizer != null) {
            return instance.bandCount;
        }
        int cached = prefs().getInt(KEY_CACHED_BAND_COUNT, 0);
        if (cached <= 0) {
            instance.probeBandMetadata();
            cached = prefs().getInt(KEY_CACHED_BAND_COUNT, 0);
        }
        return (short) cached;
    }

    /** Band count/range/center-frequencies are fixed per device+build, not per playback
     *  session - a throwaway {@code Equalizer} on the global output session (0) reads
     *  them without needing any track to actually be playing. Not kept attached or
     *  enabled (session 0 is the whole device's output mix, not this app's specific
     *  player instance) - {@link #attach()} still does the real per-track attach once
     *  something actually plays; this only exists to make the settings screen show real
     *  sliders immediately instead of an empty "start playback first" state. */
    private void probeBandMetadata() {
        Equalizer probe = null;
        try {
            probe = new Equalizer(0, 0);
            short count = probe.getNumberOfBands();
            SharedPreferences.Editor editor = prefs().edit();
            editor.putInt(KEY_CACHED_BAND_COUNT, count);
            short[] range = probe.getBandLevelRange();
            editor.putInt(KEY_CACHED_RANGE_MIN, range[0]);
            editor.putInt(KEY_CACHED_RANGE_MAX, range[1]);
            for (short band = 0; band < count; band++) {
                editor.putInt(KEY_CACHED_FREQ_PREFIX + band, probe.getCenterFreq(band) / 1000);
            }
            editor.apply();
        } catch (Throwable t) {
            // Some devices/ROMs don't provide the effect at all - the settings screen
            // falls back to its own "no equalizer available" state.
            FileLog.e(t);
        } finally {
            if (probe != null) {
                try {
                    probe.release();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    public static short[] getBandLevelRange() {
        if (instance.equalizer != null) {
            try {
                return instance.equalizer.getBandLevelRange();
            } catch (Throwable t) {
                // fall through to cache
            }
        }
        int min = prefs().getInt(KEY_CACHED_RANGE_MIN, Integer.MIN_VALUE);
        int max = prefs().getInt(KEY_CACHED_RANGE_MAX, Integer.MIN_VALUE);
        if (min != Integer.MIN_VALUE && max != Integer.MIN_VALUE) {
            return new short[]{(short) min, (short) max};
        }
        return new short[]{-1500, 1500};
    }

    public static int getCenterFreqHz(short band) {
        if (instance.equalizer != null) {
            try {
                return instance.equalizer.getCenterFreq(band) / 1000;
            } catch (Throwable t) {
                // fall through to cache
            }
        }
        return prefs().getInt(KEY_CACHED_FREQ_PREFIX + band, 0);
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
            SharedPreferences.Editor editor = prefs().edit();
            editor.putInt(KEY_CACHED_BAND_COUNT, bandCount);
            short[] range = equalizer.getBandLevelRange();
            editor.putInt(KEY_CACHED_RANGE_MIN, range[0]);
            editor.putInt(KEY_CACHED_RANGE_MAX, range[1]);
            for (short band = 0; band < bandCount; band++) {
                equalizer.setBandLevel(band, getBandLevel(band));
                editor.putInt(KEY_CACHED_FREQ_PREFIX + band, equalizer.getCenterFreq(band) / 1000);
            }
            editor.apply();
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
