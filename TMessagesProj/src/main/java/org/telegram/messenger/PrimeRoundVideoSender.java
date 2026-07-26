package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.Collection;

/**
 * PrimeGram: sends an ordinary gallery video as a round "video message".
 *
 * <p>Telegram only produces round videos from its own camera. The wire format, though, is
 * just a square muted video with the round-video document attribute, so an existing file can
 * be re-encoded into one — which is exactly what this does: it takes the encoder settings
 * Telegram would have chosen, adds a centre square crop to 640×640, and marks the result as a
 * round video before handing it to the normal send path.
 *
 * <p>The server rejects anything that breaks the format's rules, so the two hard limits
 * (60 seconds, no GIFs) are checked up front and reported to the user rather than failing
 * silently mid-upload.
 */
public class PrimeRoundVideoSender {

    /** Round videos are square; this is the size Telegram's own recorder produces. */
    private static final int ROUND_SIZE = 640;
    private static final int MAX_DURATION_SECONDS = 60;

    /** @return true when every entry qualifies and sending has started */
    public static boolean send(ChatActivity chatActivity, Collection<MediaController.PhotoEntry> entries, VideoEditedInfo providedInfo) {
        if (chatActivity == null || entries == null || entries.isEmpty()) {
            return false;
        }
        try {
            for (MediaController.PhotoEntry entry : entries) {
                if (entry == null || !entry.isVideo || TextUtils.isEmpty(entry.path)) {
                    showError("Кружком можно отправить только видео");
                    return false;
                }
                if (entry.path.toLowerCase().endsWith(".gif")) {
                    showError("GIF нельзя отправить кружком");
                    return false;
                }
                if (entry.duration > MAX_DURATION_SECONDS) {
                    showError("Кружок не может быть длиннее 60 секунд");
                    return false;
                }
            }
            for (MediaController.PhotoEntry entry : entries) {
                VideoEditedInfo info = providedInfo;
                if (info == null) {
                    try {
                        info = SendMessagesHelper.primeCreateCompressionSettings(entry.path);
                    } catch (Throwable t) {
                        FileLog.e("PrimeRoundVideoSender.compression", t);
                    }
                }
                if (info == null) {
                    info = new VideoEditedInfo();
                    info.originalPath = entry.path;
                    info.originalWidth = entry.width;
                    info.originalHeight = entry.height;
                    info.rotationValue = entry.orientation;
                    info.originalDuration = (long) entry.duration * 1000L;
                    info.estimatedDuration = info.originalDuration;
                    info.framerate = 30;
                    info.bitrate = 1000000;
                    info.estimatedSize = (long) (info.estimatedDuration / 1000.0 * (info.bitrate / 8.0));
                }

                int sourceWidth = info.originalWidth > 0 ? info.originalWidth : entry.width;
                int sourceHeight = info.originalHeight > 0 ? info.originalHeight : entry.height;
                if (sourceWidth <= 0 || sourceHeight <= 0) {
                    showError("Не удалось прочитать размеры видео");
                    return false;
                }
                int square = Math.min(sourceWidth, sourceHeight);

                MediaController.CropState crop = new MediaController.CropState();
                crop.cropPx = 0.0f;
                crop.cropPy = 0.0f;
                crop.cropPw = square / (float) sourceWidth;
                crop.cropPh = square / (float) sourceHeight;
                crop.cropScale = 1.0f;
                crop.cropRotate = 0.0f;
                crop.transformWidth = ROUND_SIZE;
                crop.transformHeight = ROUND_SIZE;
                crop.transformRotation = 0;
                crop.mirrored = false;

                info.cropState = crop;
                info.roundVideo = true;
                info.muted = true;
                info.resultWidth = ROUND_SIZE;
                info.resultHeight = ROUND_SIZE;

                SendMessagesHelper.prepareSendingVideo(
                        chatActivity.getAccountInstance(),
                        entry.path,
                        info,
                        null,
                        null,
                        chatActivity.getDialogId(),
                        chatActivity.getReplyMessage(),
                        chatActivity.getThreadMessage(),
                        null,
                        chatActivity.getReplyQuote(),
                        entry.entities,
                        0,
                        null,
                        true,
                        0,
                        0,
                        false,
                        entry.hasSpoiler,
                        entry.caption,
                        chatActivity.quickReplyShortcut,
                        chatActivity.getQuickReplyId(),
                        0,
                        0
                );
            }
            return true;
        } catch (Throwable t) {
            FileLog.e("PrimeRoundVideoSender.send", t);
            showError("Не удалось отправить кружок");
            return false;
        }
    }

    private static void showError(String text) {
        try {
            AndroidUtilities.runOnUIThread(() -> BulletinFactory.global().createErrorBulletin(text).show());
        } catch (Throwable ignore) {}
    }
}
