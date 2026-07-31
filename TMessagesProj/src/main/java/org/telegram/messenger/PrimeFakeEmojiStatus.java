package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/**
 * PrimeGram: keeps the emoji status chosen without real Premium from being wiped by the next sync.
 *
 * <p>Setting a status on an account that is not actually Premium cannot be told to the server - it
 * would be refused - so the fork sets it only on the local user object. That works until the server
 * sends the user again, which it does constantly: after a reconnect, a contacts sync, a call to
 * getDifference. The fresh copy has no status, it replaces the local one, and the emoji the user
 * picked disappears. From the outside it looks like the setting does not stick.
 *
 * <p>So the choice is remembered here instead of on an object the server owns, and put back
 * whenever the self user is replaced. It remains what it always was - visible on this device only,
 * to this user - but it survives.
 */
public final class PrimeFakeEmojiStatus {

    private static final String KEY_DOCUMENT = "primegram_fake_emoji_status_";
    private static final String KEY_UNTIL = "primegram_fake_emoji_status_until_";

    private PrimeFakeEmojiStatus() {
    }

    private static android.content.SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    /** Remembers what the user picked, or forgets it when they cleared it. */
    public static void remember(int account, TLRPC.EmojiStatus status) {
        final long documentId = documentIdOf(status);
        final android.content.SharedPreferences.Editor editor = prefs().edit();
        if (documentId == 0) {
            editor.remove(KEY_DOCUMENT + account).remove(KEY_UNTIL + account);
        } else {
            editor.putLong(KEY_DOCUMENT + account, documentId);
            editor.putInt(KEY_UNTIL + account, untilOf(status));
        }
        editor.apply();
    }

    /**
     * Puts the remembered status back on a user object that has just arrived without one.
     *
     * <p>Only when the incoming object has none: a status the server does know about is the real
     * answer, and overwriting it with ours would break the account that later buys Premium.
     */
    public static void restore(int account, TLRPC.User user) {
        if (user == null || documentIdOf(user.emoji_status) != 0) {
            return;
        }
        final long documentId = prefs().getLong(KEY_DOCUMENT + account, 0);
        if (documentId == 0) {
            return;
        }
        final int until = prefs().getInt(KEY_UNTIL + account, 0);
        if (until != 0 && until <= ConnectionsManager.getInstance(account).getCurrentTime()) {
            // It had an expiry and the expiry has passed; forgetting it keeps this from
            // resurrecting a status the user set for an hour last week.
            prefs().edit().remove(KEY_DOCUMENT + account).remove(KEY_UNTIL + account).apply();
            return;
        }
        final TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
        status.document_id = documentId;
        if (until != 0) {
            status.until = until;
            status.flags |= 1;
        }
        user.emoji_status = status;
    }

    private static long documentIdOf(TLRPC.EmojiStatus status) {
        if (status instanceof TLRPC.TL_emojiStatus) {
            return ((TLRPC.TL_emojiStatus) status).document_id;
        }
        return 0;
    }

    private static int untilOf(TLRPC.EmojiStatus status) {
        if (status instanceof TLRPC.TL_emojiStatus) {
            final TLRPC.TL_emojiStatus emojiStatus = (TLRPC.TL_emojiStatus) status;
            return (emojiStatus.flags & 1) != 0 ? emojiStatus.until : 0;
        }
        return 0;
    }
}
