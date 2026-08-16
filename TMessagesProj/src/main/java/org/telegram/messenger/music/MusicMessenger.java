package org.telegram.messenger.music;

import android.graphics.Bitmap;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the current track and sends it into a chat — as a rendered card, as the actual
 * audio file, or as plain text.
 *
 * <p>Every entry point reports back through {@link Callback} with a human-readable reason
 * on failure: these paths depend on third-party services and on whether the file is even
 * obtainable, so failing silently leaves the user staring at a button that "did nothing".
 */
public class MusicMessenger {

    private static final Pattern YOUTUBE_LINK = Pattern.compile("https://(www\\.)?youtube\\.com/[^\"]+=[^\"]+");
    private static final Pattern SOUNDCLOUD_LINK = Pattern.compile("https://(www\\.)?soundcloud\\.com/[^\"]+");

    public interface Callback {
        /** Always delivered on the UI thread. {@code error} is null on success. */
        void onResult(boolean success, String error);
    }

    private final int account;
    private final MusicCardRenderer.Style cardStyle;

    public MusicMessenger(int account, MusicCardRenderer.Style cardStyle) {
        this.account = account;
        this.cardStyle = cardStyle;
    }

    public void sendCard(long dialogId, Track track, Callback callback) {
        try {
            Bitmap card = MusicCardRenderer.renderHorizontalCard(track, cardStyle);
            if (card == null) {
                finish(callback, false, "Не удалось нарисовать карточку");
                return;
            }
            File file = new File(getTempDir(), "now_playing_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                card.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            if (!file.exists() || file.length() == 0) {
                finish(callback, false, "Файл карточки не записался");
                return;
            }

            final CharSequence caption = buildAttachmentCaption(track, false);
            final AccountInstance accountInstance = AccountInstance.getInstance(account);
            final String path = file.getAbsolutePath();
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    SendMessagesHelper.prepareSendingPhoto(
                            accountInstance, path, null, dialogId,
                            null, null, null, caption, null, null, null, 0,
                            null, true, 0, 0, null, 0
                    );
                    if (callback != null) callback.onResult(true, null);
                } catch (Exception e) {
                    FileLog.e("MusicMessenger.sendCard", e);
                    if (callback != null) callback.onResult(false, String.valueOf(e.getMessage()));
                }
            });
        } catch (Exception e) {
            FileLog.e("MusicMessenger.sendCard", e);
            finish(callback, false, String.valueOf(e.getMessage()));
        }
    }

    /** Progress while a card goes out to several chats at once - matches reSwaga's "Share
     *  with..." (its own {@code send_to_bottom_sheet}), which reports "Sent to X of Y" as it
     *  works through a picked chat list rather than going quiet until the very end. */
    public interface MultiCallback {
        /** Called on the UI thread after each attempted send, {@code dialogId} being the one
         *  just finished (success or not) - lets the caller show a running "X of Y" count. */
        void onProgress(long dialogId, boolean success, int done, int total);

        /** Called on the UI thread once every chat has been attempted. */
        void onFinished(int succeeded, int total);
    }

    /** Renders the card once, then sends the same file to every chat in {@code dialogIds} in
     *  turn - one render, many sends, rather than re-rendering per chat. */
    public void sendCardToMultiple(java.util.List<Long> dialogIds, Track track, MultiCallback callback) {
        if (dialogIds == null || dialogIds.isEmpty()) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onFinished(0, 0));
            }
            return;
        }
        try {
            Bitmap card = MusicCardRenderer.renderHorizontalCard(track, cardStyle);
            if (card == null) {
                AndroidUtilities.runOnUIThread(() -> callback.onFinished(0, dialogIds.size()));
                return;
            }
            File file = new File(getTempDir(), "now_playing_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                card.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            if (!file.exists() || file.length() == 0) {
                AndroidUtilities.runOnUIThread(() -> callback.onFinished(0, dialogIds.size()));
                return;
            }

            final CharSequence caption = buildAttachmentCaption(track, false);
            final AccountInstance accountInstance = AccountInstance.getInstance(account);
            final String path = file.getAbsolutePath();
            final int total = dialogIds.size();
            AndroidUtilities.runOnUIThread(() -> {
                int[] succeeded = {0};
                int[] done = {0};
                for (long dialogId : dialogIds) {
                    boolean ok;
                    try {
                        SendMessagesHelper.prepareSendingPhoto(
                                accountInstance, path, null, dialogId,
                                null, null, null, caption, null, null, null, 0,
                                null, true, 0, 0, null, 0
                        );
                        ok = true;
                    } catch (Exception e) {
                        FileLog.e("MusicMessenger.sendCardToMultiple", e);
                        ok = false;
                    }
                    done[0]++;
                    if (ok) succeeded[0]++;
                    if (callback != null) {
                        callback.onProgress(dialogId, ok, done[0], total);
                    }
                }
                if (callback != null) {
                    callback.onFinished(succeeded[0], total);
                }
            });
        } catch (Exception e) {
            FileLog.e("MusicMessenger.sendCardToMultiple", e);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onFinished(0, dialogIds.size()));
            }
        }
    }

    public void sendText(long dialogId, Track track, Callback callback) {
        final String text = buildCaption(track, true).toString();
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.getInstance(account).sendMessage(
                        SendMessagesHelper.SendMessageParams.of(text, dialogId)
                );
                if (callback != null) callback.onResult(true, null);
            } catch (Exception e) {
                FileLog.e("MusicMessenger.sendText", e);
                if (callback != null) callback.onResult(false, String.valueOf(e.getMessage()));
            }
        });
    }

    /** Blocking: downloads/locates the audio, then hands it to the send pipeline. */
    public void sendAudio(long dialogId, Track track, TrackProvider provider, MusicPlatform platform, String cobaltApiUrl, Callback callback) {
        try {
            if (track == null || !track.active) {
                finish(callback, false, "Сейчас ничего не играет");
                return;
            }

            File audioFile = null;

            // A track playing inside Telegram is already downloaded — sending it through
            // song.link + Cobalt would be absurd (and impossible: there's no song.link code
            // for Telegram), so take the local file straight from the player.
            if (platform == MusicPlatform.TG_MUSIC) {
                audioFile = getLocalTelegramAudioFile();
                if (audioFile == null) {
                    finish(callback, false, "Файл трека ещё не загружен в Telegram");
                    return;
                }
            } else if (track.downloadUrl != null && !track.downloadUrl.isEmpty()) {
                audioFile = new File(getTempDir(), safeFileName(track.title) + guessExtension(track.downloadUrl));
                if (!MusicHttp.downloadToFile(track.downloadUrl, audioFile)) {
                    finish(callback, false, "Не удалось скачать файл с сервиса");
                    return;
                }
            } else {
                if (!provider.canDownloadTrack()) {
                    finish(callback, false, "Для этой платформы скачивание недоступно");
                    return;
                }
                if (platform.songlinkCode == null) {
                    finish(callback, false, "Для этой платформы нет ссылки на скачивание");
                    return;
                }
                String resolved = resolveViaSonglink(platform, track.id);
                if (resolved == null) {
                    finish(callback, false, "song.link не нашёл этот трек");
                    return;
                }
                audioFile = CobaltDownloader.download(cobaltApiUrl, resolved, getTempDir(), safeFileName(track.title));
                if (audioFile == null) {
                    finish(callback, false, "Cobalt не смог скачать трек");
                    return;
                }
            }

            if (!audioFile.exists() || audioFile.length() == 0) {
                finish(callback, false, "Скачанный файл пуст");
                return;
            }

            final String mime = audioFile.getName().endsWith(".opus") ? "audio/opus" : "audio/mpeg";
            final String caption = buildAttachmentCaption(track, true).toString();
            final AccountInstance accountInstance = AccountInstance.getInstance(account);
            final String path = audioFile.getAbsolutePath();
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    SendMessagesHelper.prepareSendingDocument(
                            accountInstance, path, path, null, caption, mime, dialogId,
                            null, null, null, null, null, true, 0, null, null, 0, false
                    );
                    if (callback != null) callback.onResult(true, null);
                } catch (Exception e) {
                    FileLog.e("MusicMessenger.sendAudio", e);
                    if (callback != null) callback.onResult(false, String.valueOf(e.getMessage()));
                }
            });
        } catch (Exception e) {
            FileLog.e("MusicMessenger.sendAudio", e);
            finish(callback, false, String.valueOf(e.getMessage()));
        }
    }

    /** The file backing the message Telegram is currently playing, if it's on disk. */
    private File getLocalTelegramAudioFile() {
        try {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing == null) {
                return null;
            }
            File file = FileLoader.getInstance(account).getPathToMessage(playing.messageOwner);
            if (file != null && file.exists() && file.length() > 0) {
                return file;
            }
            if (playing.messageOwner != null && playing.messageOwner.attachPath != null) {
                File attach = new File(playing.messageOwner.attachPath);
                if (attach.exists() && attach.length() > 0) {
                    return attach;
                }
            }
        } catch (Exception e) {
            FileLog.e("MusicMessenger.getLocalTelegramAudioFile", e);
        }
        return null;
    }

    /** Saves the track's cover art (as reported by the provider, {@link Track#thumbUrl}) into the
     *  device's own Downloads/Telegram folder as a real file - separate from the "Карточка" image
     *  (a rendered now-playing card), this is just the raw artwork the platform itself serves. */
    public void downloadArtwork(Track track, Callback callback) {
        if (track == null || track.thumbUrl == null || track.thumbUrl.isEmpty()) {
            finish(callback, false, "У трека нет обложки");
            return;
        }
        try {
            File dir = getTempDir();
            String ext = guessExtension(track.thumbUrl);
            File dest = new File(dir, "artwork_" + System.currentTimeMillis() + ext);
            if (!MusicHttp.downloadToFile(track.thumbUrl, dest)) {
                finish(callback, false, "Не удалось скачать обложку");
                return;
            }
            final String mime = ext.equals(".png") ? "image/png" : "image/jpeg";
            final String displayName = safeFileName(track.title == null ? "cover" : track.title) + ext;
            AndroidUtilities.runOnUIThread(() -> MediaController.saveFile(
                    dest.getAbsolutePath(), org.telegram.messenger.ApplicationLoader.applicationContext,
                    2, displayName, mime,
                    uri -> {
                        if (callback != null) {
                            callback.onResult(uri != null, uri != null ? null : "Не удалось сохранить файл");
                        }
                    }));
        } catch (Exception e) {
            FileLog.e("MusicMessenger.downloadArtwork", e);
            finish(callback, false, String.valueOf(e.getMessage()));
        }
    }

    private void finish(Callback callback, boolean success, String error) {
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(success, error));
        }
    }

    private String resolveViaSonglink(MusicPlatform platform, String trackId) {
        if (platform.songlinkCode == null || trackId == null) {
            return null;
        }
        try {
            java.net.URL url = new java.net.URL("https://song.link/" + platform.songlinkCode + "/" + trackId);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            String html;
            try (java.io.InputStream is = conn.getInputStream()) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
                html = baos.toString("UTF-8");
            }
            Matcher sc = SOUNDCLOUD_LINK.matcher(html);
            if (sc.find()) return sc.group();
            Matcher yt = YOUTUBE_LINK.matcher(html);
            if (yt.find()) return yt.group();
            return null;
        } catch (Exception e) {
            FileLog.e("MusicMessenger.resolveViaSonglink", e);
            return null;
        }
    }

    /** Used by sendCard/sendAudio, where the caption is incidental metadata riding along with the
     *  actual attachment - respects the "attach caption" setting. sendText's own explicit action
     *  IS the text, so it calls {@link #buildCaption(Track, boolean)} directly, unaffected by
     *  that toggle - turning it off would leave "send as text" sending nothing at all. */
    private CharSequence buildAttachmentCaption(Track track, boolean withLink) {
        if (!org.telegram.messenger.music.MusicSettingsStore.isSendCaptionEnabled()) {
            return "";
        }
        return buildCaption(track, withLink);
    }

    private CharSequence buildCaption(Track track, boolean withLink) {
        if (track == null || !track.active || track.title == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("🎵 ").append(track.title).append(" — ").append(track.artistsJoined());
        if (withLink && track.link != null && !track.link.isEmpty()) {
            sb.append("\n").append(track.link);
        }
        return sb.toString();
    }

    private static String guessExtension(String url) {
        int dot = url.lastIndexOf('.');
        int slash = url.lastIndexOf('/');
        if (dot > slash && dot != -1) {
            String ext = url.substring(dot);
            if (ext.length() <= 5) return ext;
        }
        return ".mp3";
    }

    private static String safeFileName(String title) {
        if (title == null) return "track";
        return title.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static File getTempDir() {
        // Telegram's own cache dir, rather than external cache: the send pipeline reads
        // from here reliably regardless of scoped-storage behaviour.
        File dir = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "music_temp");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
