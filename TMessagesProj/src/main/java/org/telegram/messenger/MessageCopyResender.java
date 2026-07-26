package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * PrimeGram grey zone: re-sends messages as brand-new content instead of forwarding them.
 *
 * <p>Telegram refuses a real forward of protected content server-side, so the only way to
 * move it is to take the media that is already on the device and upload it again as the
 * user's own message. The result carries no "forwarded from" attribution — it is a copy,
 * not a forward, and that difference is visible to everyone who sees it.
 */
public class MessageCopyResender {

    /** Longest we'll wait for one missing file to arrive before giving up on it. */
    private static final long DOWNLOAD_TIMEOUT_MS = 60_000;
    private static final long POLL_STEP_MS = 250;

    public interface Callback {
        /** Delivered on the UI thread once every message has been handled. */
        void onFinished(int sent, int skipped);
    }

    /**
     * @param messages source messages, in the order they should appear
     * @param toDialogId destination chat
     */
    public static void resend(int account, List<MessageObject> messages, long toDialogId, Callback callback) {
        final ArrayList<MessageObject> copy = new ArrayList<>(messages);
        Executors.newSingleThreadExecutor().submit(() -> {
            int sent = 0, skipped = 0;
            for (MessageObject message : copy) {
                try {
                    if (resendOne(account, message, toDialogId)) {
                        sent++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    FileLog.e("MessageCopyResender", e);
                    skipped++;
                }
            }
            final int finalSent = sent, finalSkipped = skipped;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onFinished(finalSent, finalSkipped);
                }
            });
        });
    }

    private static boolean resendOne(int account, MessageObject message, long toDialogId) {
        if (message == null) {
            return false;
        }
        final AccountInstance accountInstance = AccountInstance.getInstance(account);

        // Plain text needs no file at all.
        if (message.type == MessageObject.TYPE_TEXT && TextUtils.isEmpty(message.messageOwner.attachPath) && message.getDocument() == null && message.photoThumbs == null) {
            final String text = message.messageOwner.message;
            if (TextUtils.isEmpty(text)) {
                return false;
            }
            AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account)
                    .sendMessage(SendMessagesHelper.SendMessageParams.of(text, toDialogId)));
            return true;
        }

        File file = ensureDownloaded(account, message);
        if (file == null) {
            return false;
        }

        final String caption = message.caption != null ? message.caption.toString() : null;
        final String path = file.getAbsolutePath();

        if (message.isPhoto() && message.getDocument() == null) {
            AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingPhoto(
                    accountInstance, path, null, toDialogId,
                    null, null, null, caption, null, null, null, 0,
                    null, true, 0, 0, null, 0
            ));
            return true;
        }

        String mime = null;
        TLRPC.Document document = message.getDocument();
        if (document != null) {
            mime = document.mime_type;
        }
        if (TextUtils.isEmpty(mime)) {
            mime = "application/octet-stream";
        }
        final String finalMime = mime;
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingDocument(
                accountInstance, path, path, null, caption, finalMime, toDialogId,
                null, null, null, null, null, true, 0, null, null, 0, false
        ));
        return true;
    }

    /**
     * Returns the message's file, pulling it down first if it isn't cached yet — the whole
     * point of this path is that the content must exist locally before it can be re-uploaded.
     */
    private static File ensureDownloaded(int account, MessageObject message) {
        File file = existingFile(account, message);
        if (file != null) {
            return file;
        }
        try {
            FileLoader.getInstance(account).loadFile(message.getDocument(), message, FileLoader.PRIORITY_HIGH, 0);
        } catch (Exception e) {
            FileLog.e("MessageCopyResender.loadFile", e);
        }
        final long deadline = System.currentTimeMillis() + DOWNLOAD_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_STEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            file = existingFile(account, message);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    private static File existingFile(int account, MessageObject message) {
        try {
            if (message.messageOwner != null && !TextUtils.isEmpty(message.messageOwner.attachPath)) {
                File attach = new File(message.messageOwner.attachPath);
                if (attach.exists() && attach.length() > 0) {
                    return attach;
                }
            }
            File file = FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
            if (file != null && file.exists() && file.length() > 0) {
                return file;
            }
        } catch (Exception e) {
            FileLog.e("MessageCopyResender.existingFile", e);
        }
        return null;
    }
}
