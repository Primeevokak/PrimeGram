package org.telegram.messenger.music;

import android.media.MediaMetadataRetriever;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * PrimeGram Music: pulls a track into the player's own permanent local library -
 * either from an external link (reuses {@link CobaltDownloader}, the same
 * Cobalt/song.link resolver {@code MusicMessenger} already uses to fetch "what's
 * playing" audio) or by copying an existing Telegram audio message's already-downloaded
 * file. Either way lands the bytes under a dedicated, non-cache folder under
 * {@code getExternalFilesDir(null)} - app-scoped storage that survives cache sweeps and
 * is removed only on uninstall, unlike every other Prime-authored media folder in this
 * fork (which are all explicitly temp/cache dirs) - then inserts a {@code tracks} row
 * via {@link PrimeMusicLibrary#addLocalTrack}.
 */
public final class PrimeMusicDownloader {

    public interface Callback {
        void onResult(boolean success, long trackId);
    }

    private PrimeMusicDownloader() {
    }

    private static File libraryDir() {
        File dir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "primegram_music");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void downloadFromUrl(String sourceUrl, String title, String artist, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String fileNameNoExt = "track_" + System.currentTimeMillis();
                File file = CobaltDownloader.download(CobaltDownloader.DEFAULT_API_URL, sourceUrl, libraryDir(), fileNameNoExt);
                if (file == null) {
                    postResult(callback, false, 0);
                    return;
                }
                int durationSeconds = probeDurationSeconds(file);
                long trackId = PrimeMusicLibrary.addLocalTrack(file.getAbsolutePath(),
                        title != null ? title : file.getName(), artist, durationSeconds, null);
                postResult(callback, true, trackId);
            } catch (Exception e) {
                FileLog.e(e);
                postResult(callback, false, 0);
            }
        });
    }

    /** Copies an already-downloaded Telegram audio message into the permanent library -
     *  the source stays untouched, this is a copy, not a move. */
    public static void downloadFromMessage(MessageObject message, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String sourcePath = FileLoaderResolvedPath(message);
                if (sourcePath == null) {
                    postResult(callback, false, 0);
                    return;
                }
                File source = new File(sourcePath);
                if (!source.exists()) {
                    postResult(callback, false, 0);
                    return;
                }
                String ext = sourcePath.contains(".") ? sourcePath.substring(sourcePath.lastIndexOf('.')) : ".mp3";
                File dest = new File(libraryDir(), "track_" + System.currentTimeMillis() + ext);
                copyFile(source, dest);
                int durationSeconds = probeDurationSeconds(dest);
                long trackId = PrimeMusicLibrary.addLocalTrack(dest.getAbsolutePath(),
                        message.getMusicTitle(), message.getMusicAuthor(), durationSeconds, null);
                postResult(callback, true, trackId);
            } catch (Exception e) {
                FileLog.e(e);
                postResult(callback, false, 0);
            }
        });
    }

    private static String FileLoaderResolvedPath(MessageObject message) {
        File path = org.telegram.messenger.FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        return path != null ? path.getAbsolutePath() : null;
    }

    private static void copyFile(File source, File dest) throws Exception {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static int probeDurationSeconds(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value != null ? (int) (Long.parseLong(value) / 1000) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignore) {
            }
        }
    }

    private static void postResult(Callback callback, boolean success, long trackId) {
        if (callback == null) {
            return;
        }
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onResult(success, trackId));
    }
}
