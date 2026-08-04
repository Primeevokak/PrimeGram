package com.exteragram.messenger.utils;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/**
 * PrimeGram: compatibility shim for {@code com.exteragram.messenger.utils.ChatUtils} - a plugin
 * dependency (`cactuslib`, among others) reaches for directly. Real implementations for the
 * operations that map cleanly onto stock Telegram API calls; the exteraGram-app-specific ones
 * (path-to-message deep links into their own UI) are not meaningful here and are omitted rather
 * than faked.
 */
public final class ChatUtils {

    private static final ChatUtils INSTANCE = new ChatUtils();

    private ChatUtils() {
    }

    public static ChatUtils getInstance() {
        return INSTANCE;
    }

    private int currentAccount() {
        return UserConfig.selectedAccount;
    }

    /** Resolves a public username to its chat/channel, same as tapping a t.me link. */
    public void resolveChannel(String username, Utilities.Callback<TLRPC.Chat> callback) {
        resolveChannel(username, currentAccount(), callback);
    }

    public void resolveChannel(String username, int account, Utilities.Callback<TLRPC.Chat> callback) {
        if (username == null || username.isEmpty()) {
            if (callback != null) callback.run(null);
            return;
        }
        final String cleaned = username.startsWith("@") ? username.substring(1) : username;
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = cleaned;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            TLRPC.Chat chat = null;
            if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                MessagesController.getInstance(account).putUsers(resolved.users, false);
                MessagesController.getInstance(account).putChats(resolved.chats, false);
                final long chatId = MessageObject.getPeerId(resolved.peer);
                if (chatId != 0) {
                    for (TLRPC.Chat c : resolved.chats) {
                        if (c.id == chatId) {
                            chat = c;
                            break;
                        }
                    }
                }
            }
            final TLRPC.Chat result = chat;
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.run(result);
            });
        });
    }

    /** Best-effort deep link to a message inside PrimeGram's own chat screen. */
    public String getPathToMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        final long dialogId = messageObject.getDialogId();
        final int messageId = messageObject.getId();
        return "tg://openmessage?chat_id=" + dialogId + "&message_id=" + messageId;
    }
}
