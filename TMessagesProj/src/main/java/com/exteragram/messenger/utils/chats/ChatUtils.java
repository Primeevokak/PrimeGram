package com.exteragram.messenger.utils.chats;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;

/**
 * PrimeGram: exteraGram's chat helpers that plugins call - chiefly {@link #resolveChannel} and
 * {@link #getPathToMessage}. One instance per account, like the Telegram controllers.
 */
public final class ChatUtils {

    private static final ChatUtils[] instances = new ChatUtils[UserConfig.MAX_ACCOUNT_COUNT];
    private final int account;

    private ChatUtils(int account) {
        this.account = account;
    }

    public static ChatUtils getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

    public static synchronized ChatUtils getInstance(int account) {
        if (account < 0 || account >= instances.length) {
            account = UserConfig.selectedAccount;
        }
        ChatUtils instance = instances[account];
        if (instance == null) {
            instances[account] = instance = new ChatUtils(account);
        }
        return instance;
    }

    /** Telegram's public names for its data centers. */
    public static String getDCName(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "MIA, Miami FL, USA";
            case 2:
            case 4:
                return "AMS, Amsterdam, NL";
            case 5:
                return "SIN, Singapore, SG";
            default:
                return "UNK";
        }
    }

    /** A channel/supergroup dialog id (-100…) or a chat id, as the bare positive owner id. */
    public static long extractOwnerId(long id) {
        final long abs = Math.abs(id);
        return abs > 1_000_000_000_000L ? abs - 1_000_000_000_000L : abs;
    }

    public String getPathToMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath) && new File(messageObject.messageOwner.attachPath).exists()) {
            return messageObject.messageOwner.attachPath;
        }
        final File file = FileLoader.getInstance(account).getPathToMessage(messageObject.messageOwner);
        return file == null ? null : file.getAbsolutePath();
    }

    public CharSequence getMessageText(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        if (!TextUtils.isEmpty(messageObject.caption)) {
            return messageObject.caption;
        }
        return messageObject.messageText;
    }

    /** The channel with this username if it is already known locally, else null. */
    public TLRPC.Chat resolveChannel(String username) {
        final TLObject object = MessagesController.getInstance(account).getUserOrChat(clean(username));
        return object instanceof TLRPC.Chat ? (TLRPC.Chat) object : null;
    }

    /** The channel with this username, asking the server if needed; null if there is none.
     *  The callback always runs on the main thread. */
    public void resolveChannel(String username, Utilities.Callback<TLRPC.Chat> callback) {
        final TLRPC.Chat known = resolveChannel(username);
        if (known != null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(known));
            return;
        }
        MessagesController.getInstance(account).getUserNameResolver().resolve(clean(username), peerId -> {
            final TLRPC.Chat chat = peerId == null || peerId >= 0 ? null
                    : MessagesController.getInstance(account).getChat(-peerId);
            AndroidUtilities.runOnUIThread(() -> callback.run(chat));
        });
    }

    private static String clean(String username) {
        if (username == null) {
            return "";
        }
        String u = username.trim();
        if (u.startsWith("@")) {
            u = u.substring(1);
        }
        final int slash = u.lastIndexOf('/');
        return slash >= 0 ? u.substring(slash + 1) : u;
    }
}
