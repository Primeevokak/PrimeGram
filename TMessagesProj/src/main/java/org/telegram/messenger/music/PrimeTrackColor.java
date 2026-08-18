package org.telegram.messenger.music;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.palette.graphics.Palette;
import androidx.palette.graphics.Target;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * PrimeGram Music: extracts a dominant accent color from a track's cover art, mirroring
 * namida's own {@code CurrentColor.extractPaletteFromImage} - the actual mechanism that
 * makes namida's whole UI recolor itself per-track. Built on {@code androidx.palette}
 * (already a Gradle dependency, already used the same way elsewhere in this fork by
 * {@code org.telegram.ui.Stories.recorder.DominantColors}) rather than namida's own
 * {@code PaletteGenerator} package, which this fork has no equivalent of - the fallback
 * chain and HSV legibility nudge below directly mirror {@code DominantColors}'s own
 * {@code getColorFromPalette}/{@code adapt}, the existing answer in this codebase to
 * "make an extracted swatch usable as UI chrome on a dark background."
 */
public final class PrimeTrackColor {

    private PrimeTrackColor() {
    }

    private static File cacheDir() {
        File dir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "primegram_music/covers");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public interface AccentCallback {
        void onAccent(int accentColor);
    }

    /** Resolves {@code coverPath}'s accent color (cache-file read first, extracts and
     *  caches on miss), and - if this is still the most recently requested track by the
     *  time the result lands - updates {@link PrimeMusicTheme#currentAccent}. Safe to
     *  call with a null {@code coverPath} (falls back to the default seed, e.g. a track
     *  with no embedded art). */
    public static void resolveForCurrentTrack(String coverPath, AccentCallback callback) {
        String key = coverPath != null ? coverPath : "__default__";
        PrimeMusicTheme.currentAccentKey = key;
        if (coverPath == null) {
            PrimeMusicTheme.currentAccent = PrimeMusicTheme.accentForUi(PrimeMusicTheme.DEFAULT_ACCENT_SEED);
            PrimeMusicTheme.notifyAccentChanged();
            if (callback != null) {
                callback.onAccent(PrimeMusicTheme.currentAccent);
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            int rawAccent = extractAndCache(coverPath);
            int uiAccent = PrimeMusicTheme.accentForUi(rawAccent);
            AndroidUtilities.runOnUIThread(() -> {
                if (key.equals(PrimeMusicTheme.currentAccentKey)) {
                    PrimeMusicTheme.currentAccent = uiAccent;
                    PrimeMusicTheme.notifyAccentChanged();
                }
                if (callback != null) {
                    callback.onAccent(uiAccent);
                }
            });
        });
    }

    /** Blocking - decode, extract (or read from cache), and cache the raw (pre-{@link
     *  PrimeMusicTheme#accentForUi}) swatch. Call off the main thread. */
    public static int extractAndCache(String coverPath) {
        File cacheFile = new File(cacheDir(), hash(coverPath) + ".color");
        Integer cached = readCached(cacheFile);
        if (cached != null) {
            return cached;
        }
        int color = extractBlocking(coverPath);
        writeCached(cacheFile, color);
        return color;
    }

    private static int extractBlocking(String coverPath) {
        try {
            Bitmap bitmap = PrimeAlbumArt.decodeSampled(coverPath, AndroidUtilities.dp(160));
            if (bitmap == null) {
                return PrimeMusicTheme.DEFAULT_ACCENT_SEED;
            }
            Palette palette = new Palette.Builder(bitmap).generate();
            return colorFromPalette(palette);
        } catch (Throwable t) {
            FileLog.e(t);
            return PrimeMusicTheme.DEFAULT_ACCENT_SEED;
        }
    }

    /** Same fallback order {@code DominantColors.getColorFromPalette} already uses
     *  elsewhere in this fork - muted first (namida's own palette-derived colors lean
     *  toward populous/muted swatches too, not the loudest one), then vibrant, then a
     *  lightness-constrained target, then dark variants, then the seed. */
    private static int colorFromPalette(Palette palette) {
        if (palette == null) {
            return PrimeMusicTheme.DEFAULT_ACCENT_SEED;
        }
        int color = palette.getMutedColor(0);
        if (Color.alpha(color) > 200) {
            return color;
        }
        color = palette.getVibrantColor(0);
        if (Color.alpha(color) > 200) {
            return color;
        }
        color = palette.getColorForTarget(new Target.Builder()
                .setMaximumLightness(0.8f).setMinimumLightness(0.1f).setSaturationWeight(0.4f).build(), 0);
        if (Color.alpha(color) > 200) {
            return color;
        }
        color = palette.getDarkVibrantColor(0);
        if (Color.alpha(color) > 200) {
            return color;
        }
        color = palette.getDarkMutedColor(0);
        if (Color.alpha(color) > 200) {
            return color;
        }
        return PrimeMusicTheme.DEFAULT_ACCENT_SEED;
    }

    private static Integer readCached(File file) {
        if (!file.exists()) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            return in.readInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCached(File file, int color) {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(color);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
