package org.telegram.messenger.music;

import android.Manifest;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.PermissionRequest;

/**
 * PrimeGram Music: scans the device's own {@code MediaStore.Audio.Media} index for
 * already-existing music - the same content provider the stock Files/Gallery/Music apps
 * read, and the Android-native equivalent of what namida's {@code on_audio_query}
 * dependency wraps on Flutter (confirmed by reading {@code indexer_controller.dart} in
 * the cloned reference checkout). Without this, {@link PrimeMusicLibrary} only ever
 * gains rows from a track being played once or explicitly downloaded - for a user who
 * hasn't done either yet, the whole player looks empty regardless of any visual polish.
 */
public final class PrimeDeviceMusicScanner {

    private static final String KEY_SCANNED_ONCE = "primegram_music_device_scanned";

    public interface Callback {
        void onComplete(int addedOrUpdated);
    }

    private static volatile boolean scanning;

    private PrimeDeviceMusicScanner() {
    }

    public static boolean hasScannedOnce() {
        return prefs().getBoolean(KEY_SCANNED_ONCE, false);
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private static String readPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasPermission() {
        return PermissionRequest.hasPermission(readPermission());
    }

    /** Requests the read permission if needed, then scans in the background. Safe to
     *  call repeatedly - re-scans just update/dedup existing rows, see
     *  {@link PrimeMusicLibrary#findOrAddDeviceTrack}. */
    public static void scan(Callback callback) {
        if (scanning) {
            return;
        }
        PermissionRequest.requestPermission(readPermission(), granted -> {
            if (granted == null || !granted) {
                if (callback != null) {
                    callback.onComplete(0);
                }
                return;
            }
            scanning = true;
            Utilities.globalQueue.postRunnable(() -> {
                int count = scanBlocking();
                prefs().edit().putBoolean(KEY_SCANNED_ONCE, true).apply();
                scanning = false;
                if (callback != null) {
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onComplete(count));
                }
            });
        });
    }

    private static int scanBlocking() {
        int count = 0;
        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)) {
            if (cursor == null) {
                return 0;
            }
            int idxTitle = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int idxArtist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int idxAlbum = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int idxDuration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int idxData = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            String genreColumn = genreColumnNameOrNull();
            int idxGenre = genreColumn != null ? cursor.getColumnIndex(genreColumn) : -1;

            while (cursor.moveToNext()) {
                String path = cursor.getString(idxData);
                if (path == null || path.isEmpty()) {
                    continue;
                }
                String title = cursor.getString(idxTitle);
                String artist = cursor.getString(idxArtist);
                String album = cursor.getString(idxAlbum);
                String genre = idxGenre >= 0 ? cursor.getString(idxGenre) : null;
                int durationMs = cursor.getInt(idxDuration);
                String coverPath = PrimeAlbumArt.extractAndCache(path);
                PrimeMusicLibrary.findOrAddDeviceTrack(path, title, artist, album, genre, durationMs / 1000, coverPath);
                count++;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return count;
    }

    /** {@code MediaStore.Audio.Media.GENRE} only exists as a direct column from API 30
     *  onward - older devices need the separate {@code Audio.Genres.Members} join this
     *  scanner deliberately doesn't do (real scope reduction, noted in the plan): genre
     *  grouping on those devices just buckets everything under "Без жанра". */
    private static String genreColumnNameOrNull() {
        if (Build.VERSION.SDK_INT < 30) {
            return null;
        }
        return MediaStore.Audio.Media.GENRE;
    }
}
