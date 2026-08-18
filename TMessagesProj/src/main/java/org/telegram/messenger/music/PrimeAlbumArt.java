package org.telegram.messenger.music;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.PrimeMusicCardCell;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * PrimeGram Music: real embedded cover art, extracted once per file and cached to disk -
 * the same job namida's {@code thumbnail_manager.dart}/{@code ffmpeg_controller.dart}
 * do, done here with Android's own {@code MediaMetadataRetriever.getEmbeddedPicture()}
 * (already imported elsewhere in this feature, {@code PrimeMusicDownloader}, for
 * duration probing - no new dependency). The procedural gradient the previous visual
 * pass always drew ({@link PrimeMusicCardCell#createProceduralCoverDrawable}) becomes
 * the honest fallback for files with no embedded picture (most Telegram audio messages),
 * not the default for everything.
 */
public final class PrimeAlbumArt {

    private PrimeAlbumArt() {
    }

    private static File coversDir() {
        File dir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "primegram_music/covers");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Extracts the embedded picture from {@code audioPath} and caches it to disk,
     *  keyed by a hash of the path so a re-scan of the same file reuses the cached
     *  image instead of re-decoding it. Returns the cached file's path, or null if the
     *  file has no embedded picture (or extraction failed - never throws). Blocking -
     *  call off the main thread. */
    public static String extractAndCache(String audioPath) {
        if (audioPath == null) {
            return null;
        }
        File cached = new File(coversDir(), hash(audioPath) + ".jpg");
        if (cached.exists() && cached.length() > 0) {
            return cached.getAbsolutePath();
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(audioPath);
            byte[] art = retriever.getEmbeddedPicture();
            if (art == null || art.length == 0) {
                return null;
            }
            try (FileOutputStream out = new FileOutputStream(cached)) {
                out.write(art);
            }
            return cached.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignore) {
            }
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

    public interface BitmapCallback {
        void onBitmap(Bitmap bitmap);
    }

    /** Decodes {@code path} off {@code Utilities.globalQueue} and delivers the result back
     *  on the UI thread - the building block every async cover-bind call below uses.
     *  Callers are responsible for their own staleness check (a fast-scrolling list can
     *  easily rebind a row to a different track before a decode from the old one lands). */
    public static void decodeAsync(String path, int targetSizePx, BitmapCallback callback) {
        if (path == null) {
            callback.onBitmap(null);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = decodeSampled(path, targetSizePx);
            AndroidUtilities.runOnUIThread(() -> callback.onBitmap(bitmap));
        });
    }

    /** Shared cover-bind helper for every cover-showing view in the feature. Paints the
     *  procedural gradient fallback immediately (cheap, no disk I/O) so a list scrolls
     *  smoothly, then decodes the real embedded cover off the main thread and swaps it in
     *  once ready - synchronously decoding a JPEG per row on every bind() was the actual
     *  cause of "opening the player lags" on any library bigger than a handful of tracks.
     *  {@code frame} is the container whose background carries the fallback gradient (and
     *  gets cleared once a real bitmap lands); {@code icon} is the ImageView the real
     *  bitmap (or the placeholder note glyph) is drawn into - also used as the staleness
     *  tag (via {@link ImageView#setTag}) so a view recycled onto a different track before
     *  its decode finishes doesn't paint the wrong cover. */
    public static void applyCover(FrameLayout frame, ImageView icon, String coverPath, String seed, int cornerRadiusDp) {
        frame.setBackground(PrimeMusicCardCell.createProceduralCoverDrawable(seed, cornerRadiusDp));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageResource(org.telegram.messenger.R.drawable.msg_filled_data_music);
        icon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        icon.setTag(coverPath);
        if (coverPath == null) {
            return;
        }
        decodeAsync(coverPath, AndroidUtilities.dp(Math.max(cornerRadiusDp * 4, 48)), bitmap -> {
            if (bitmap == null || !coverPath.equals(icon.getTag())) {
                return;
            }
            frame.setBackground(null);
            icon.setColorFilter(null);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.setImageDrawable(new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap));
        });
    }

    /** @return a downscaled bitmap decoded to roughly {@code targetSizePx}, or null if
     *  the file is missing/corrupt/not a real image. */
    public static Bitmap decodeSampled(String path, int targetSizePx) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetSizePx && bounds.outHeight / (sample * 2) >= targetSizePx) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }
}
