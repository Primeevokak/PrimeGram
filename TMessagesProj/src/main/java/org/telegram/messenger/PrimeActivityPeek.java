package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrimeGram grey-zone feature: a shared group is enough to see that someone who hides "last
 * seen" from you is, right now, typing in a chat you're both in.
 *
 * <p>This reads {@link MessagesController#printingUsers} rather than tracking anything new -
 * that map is already kept live by every {@code TL_updateChatUserTyping} the client receives for
 * any group it belongs to, whether or not that group is the one currently open. Finding someone
 * is a reverse lookup over data the client already had, not a new source of information.
 */
public final class PrimeActivityPeek {

    public static final class Sighting {
        public final long dialogId;
        public final TLRPC.SendMessageAction action;
        public final long time;

        Sighting(long dialogId, TLRPC.SendMessageAction action, long time) {
            this.dialogId = dialogId;
            this.action = action;
            this.time = time;
        }
    }

    private PrimeActivityPeek() {
    }

    public static boolean isEnabled() {
        return GreyZone.isEnabled(GreyZone.ACTIVITY_PEEK);
    }

    /** The most recent sighting of {@code userId} typing in a group shared with this account, or
     *  {@code null} if there is none - stale entries are pruned by MessagesController itself, so
     *  anything still in the map is live. Private chats are skipped: a 1:1 typing status is
     *  already visible as-is and isn't what "hides online from you" is hiding. */
    public static Sighting find(int currentAccount, long userId) {
        if (!isEnabled()) {
            return null;
        }
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        Sighting best = null;
        for (Map.Entry<Long, ConcurrentHashMap<Integer, ArrayList<MessagesController.PrintingUser>>> dialogEntry : controller.printingUsers.entrySet()) {
            final long dialogId = dialogEntry.getKey();
            if (dialogId >= 0) {
                continue;
            }
            for (ArrayList<MessagesController.PrintingUser> arr : dialogEntry.getValue().values()) {
                for (MessagesController.PrintingUser pu : arr) {
                    if (pu.userId == userId && (best == null || pu.lastTime > best.time)) {
                        best = new Sighting(dialogId, pu.action, pu.lastTime);
                    }
                }
            }
        }
        return best;
    }

    public static String describe(Sighting sighting, MessagesController controller) {
        if (sighting == null) {
            return null;
        }
        final TLRPC.Chat chat = controller.getChat(-sighting.dialogId);
        final String chatName = chat != null ? chat.title : "общей группе";
        final String verb;
        final TLRPC.SendMessageAction action = sighting.action;
        if (action instanceof TLRPC.TL_sendMessageRecordAudioAction) {
            verb = "записывает голосовое";
        } else if (action instanceof TLRPC.TL_sendMessageRecordRoundAction) {
            verb = "записывает кружок";
        } else if (action instanceof TLRPC.TL_sendMessageRecordVideoAction) {
            verb = "записывает видео";
        } else if (action instanceof TLRPC.TL_sendMessageUploadPhotoAction) {
            verb = "отправляет фото";
        } else if (action instanceof TLRPC.TL_sendMessageUploadVideoAction || action instanceof TLRPC.TL_sendMessageUploadRoundAction) {
            verb = "отправляет видео";
        } else if (action instanceof TLRPC.TL_sendMessageUploadAudioAction) {
            verb = "отправляет голосовое";
        } else if (action instanceof TLRPC.TL_sendMessageUploadDocumentAction) {
            verb = "отправляет файл";
        } else if (action instanceof TLRPC.TL_sendMessageChooseStickerAction) {
            verb = "выбирает стикер";
        } else {
            verb = "печатает";
        }
        return verb + " в «" + chatName + "»";
    }
}
