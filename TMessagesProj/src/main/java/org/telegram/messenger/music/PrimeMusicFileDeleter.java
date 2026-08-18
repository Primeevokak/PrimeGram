package org.telegram.messenger.music;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.Collections;

/**
 * PrimeGram Music: physically deletes a track's underlying file, not just its library
 * row - the missing piece behind "an excluded/deleted device track can still come back
 * later": {@link PrimeMusicLibrary#deleteTrack} only ever removed the DB row, and the
 * next {@code PrimeDeviceMusicScanner} sync happily re-discovers the same file still
 * sitting on disk and re-inserts it. Real deletion needs to go through the actual
 * filesystem (or, for a device file on API 30+, through {@code MediaStore
 * .createDeleteRequest} - scoped storage means this app has no write access to another
 * app's media without the user explicitly approving a system prompt for it, unlike a
 * {@link PrimeTrack#SOURCE_LOCAL} file living in this app's own permanent library
 * folder, which needs no such approval since this app owns it outright).
 */
public final class PrimeMusicFileDeleter {

    private PrimeMusicFileDeleter() {
    }

    public interface Callback {
        void onResult(boolean deleted);
    }

    /** Deletes {@code track}'s file and its library row together - a track deleted
     *  this way genuinely cannot come back on the next device sync, unlike {@link
     *  PrimeMusicLibrary#deleteTrack} alone. Only meaningful for {@code SOURCE_LOCAL}/
     *  {@code SOURCE_DEVICE} tracks (a {@code SOURCE_TELEGRAM} track has no local file
     *  of its own to delete - it always plays straight from the chat). */
    public static void deletePermanently(Activity activity, PrimeTrack track, Callback callback) {
        if (track == null || track.localPath == null) {
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            if (track.source == PrimeTrack.SOURCE_LOCAL) {
                // This app's own permanent library folder - no scoped-storage
                // permission dance needed, we own the file outright.
                boolean deleted = new File(track.localPath).delete();
                finish(track, deleted, callback);
                return;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                Uri uri = resolveMediaStoreUri(track.localPath);
                if (uri == null) {
                    finish(track, false, callback);
                    return;
                }
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> requestSystemDelete(activity, track, uri, callback));
            } else {
                boolean deleted = new File(track.localPath).delete();
                finish(track, deleted, callback);
            }
        });
    }

    private static void requestSystemDelete(Activity activity, PrimeTrack track, Uri uri, Callback callback) {
        LaunchActivity launchActivity = activity instanceof LaunchActivity ? (LaunchActivity) activity : LaunchActivity.instance;
        if (launchActivity == null) {
            finish(track, false, callback);
            return;
        }
        try {
            PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                    launchActivity.getContentResolver(), Collections.singletonList(uri));
            launchActivity.whenPrimeMusicDeleteResultReady(approved ->
                    finish(track, approved, callback));
            launchActivity.startIntentSenderForResult(pendingIntent.getIntentSender(),
                    LaunchActivity.PRIME_MUSIC_DELETE_REQUEST_CODE, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            FileLog.e(e);
            finish(track, false, callback);
        }
    }

    private static void finish(PrimeTrack track, boolean deleted, Callback callback) {
        if (deleted) {
            PrimeMusicLibrary.deleteTrack(track.id);
        }
        if (callback != null) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onResult(deleted));
        }
    }

    private static Uri resolveMediaStoreUri(String path) {
        try (Cursor cursor = org.telegram.messenger.ApplicationLoader.applicationContext.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.DATA + "=?",
                new String[]{path}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }
}
