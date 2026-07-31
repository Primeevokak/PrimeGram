package org.telegram.messenger.plugins;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * PrimeGram: sending, as a plugin sees it.
 *
 * <p>The SDK's {@code send_text}, {@code send_photo} and friends could be written in Python by
 * calling {@code SendMessagesHelper} directly, and exteraGram does exactly that. Here they go
 * through Java instead, because those methods take between eighteen and twenty-eight arguments of
 * types that change with every Telegram release. Written in Python, a change upstream produces a
 * plugin that fails at run time in a stranger's chat; written here, it produces a compile error in
 * our own build. The narrow methods below are the whole point - a plugin passes a chat and a
 * string, and the sprawl stays on this side of the wall.
 */
public final class PrimePluginSend {

    private PrimePluginSend() {
    }

    private static AccountInstance account(int account) {
        return AccountInstance.getInstance(account);
    }

    public static void sendText(int account, long dialogId, String text, ArrayList<TLRPC.MessageEntity> entities) {
        final SendMessagesHelper.SendMessageParams params =
                SendMessagesHelper.SendMessageParams.of(text, dialogId);
        if (entities != null && !entities.isEmpty()) {
            params.entities = entities;
        }
        account(account).getSendMessagesHelper().sendMessage(params);
    }

    public static void sendPhoto(int account, long dialogId, String path, String caption,
                                 ArrayList<TLRPC.MessageEntity> entities) {
        SendMessagesHelper.prepareSendingPhoto(
                account(account), path, null, dialogId, null, null, null, caption, entities,
                null, null, 0, null, true, 0, 0, null, 0);
    }

    public static void sendVideo(int account, long dialogId, String path, String caption,
                                 ArrayList<TLRPC.MessageEntity> entities) {
        SendMessagesHelper.prepareSendingVideo(
                account(account), path, null, null, null, dialogId, null, null, null, null,
                entities, 0, null, true, 0, 0, false, false, caption, null, 0, 0, 0);
    }

    /**
     * Sends any file as a document. Audio arrives here too: the sender reads the file's own
     * metadata and attaches audio attributes when it finds them, so a track sent this way still
     * shows up as a track rather than a blob - and a file that only claims to be audio does not.
     */
    public static void sendDocument(int account, long dialogId, String path, String caption) {
        SendMessagesHelper.prepareSendingDocument(
                account(account), path, path, null, caption, null, dialogId, null, null, null,
                null, null, true, 0, null, null, 0, false);
    }

    /**
     * Edits the text of an existing message. Media edits are not offered: they need the original
     * upload machinery and a plugin that wants one is better served sending a new message.
     */
    public static void editText(int account, long dialogId, int messageId, String text,
                                ArrayList<TLRPC.MessageEntity> entities) {
        final TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
        req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        req.id = messageId;
        req.message = text == null ? "" : text;
        req.flags |= 1 << 11;
        if (entities != null && !entities.isEmpty()) {
            req.entities = entities;
            req.flags |= 1 << 3;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.Updates) {
                MessagesController.getInstance(account).processUpdates((TLRPC.Updates) response, false);
            } else if (error != null) {
                FileLog.e("plugin editText failed: " + error.text);
            }
        });
    }
}
