package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

/**
 * PrimeGram: finding a peer by its numeric id.
 *
 * <p>The one lookup the stock search box cannot do at all. Usernames it resolves, contacts it
 * matches by phone, links it opens - but a bare number is just a number to it, and people are
 * handed bare numbers constantly: by bots, by moderation logs, by each other.
 *
 * <p>The catch is the protocol's, not ours. MTProto has no "fetch user by id": every peer
 * reference carries an {@code access_hash} that the client only holds for peers it has already
 * met. So this looks in the local cache first, and only then asks the server with a zero hash -
 * which the server answers for peers the account has some relationship with, and refuses for
 * strangers. There is no request that would do better.
 */
public final class PrimeIdentitySearch {

    /** Telegram ids are well under this; anything longer is a phone number or a typo. */
    private static final int MAX_DIGITS = 15;

    private PrimeIdentitySearch() {
    }

    /**
     * The id in this query, or null when the query is not one.
     *
     * <p>Deliberately strict: digits only, optionally with a leading minus for groups and
     * channels. A query with spaces or a plus is a name or a phone number, and answering those
     * here would put a second, worse result under the search box's own.
     */
    public static Long parseId(String query) {
        if (query == null) {
            return null;
        }
        final String text = query.trim();
        if (text.isEmpty()) {
            return null;
        }
        final boolean negative = text.charAt(0) == '-';
        final String digits = negative ? text.substring(1) : text;
        if (digits.isEmpty() || digits.length() > MAX_DIGITS) {
            return null;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return null;
            }
        }
        try {
            final long value = Long.parseLong(digits);
            if (value <= 0) {
                return null;
            }
            return negative ? -value : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves an id to a {@link TLRPC.User} or {@link TLRPC.Chat}, or null.
     *
     * <p>The callback always runs on the main thread, and always runs exactly once.
     */
    public static void resolve(int account, long id, Utilities.Callback<TLObject> callback) {
        if (callback == null) {
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);

        final TLRPC.User cachedUser = controller.getUser(id > 0 ? id : -id);
        if (cachedUser != null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(cachedUser));
            return;
        }

        long chatId = id < 0 ? -id : id;
        if (chatId > 1000000000000L) {
            // The -100… form channels are written in.
            chatId -= 1000000000000L;
        }
        final TLRPC.Chat cachedChat = controller.getChat(chatId);
        if (cachedChat != null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(cachedChat));
            return;
        }

        final TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        final TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = id > 0 ? id : -id;
        inputUser.access_hash = 0;
        req.id.add(inputUser);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (error != null || !(response instanceof Vector)) {
                        callback.run(null);
                        return;
                    }
                    final ArrayList<TLRPC.User> users = new ArrayList<>();
                    for (Object object : ((Vector<?>) response).objects) {
                        if (object instanceof TLRPC.User) {
                            users.add((TLRPC.User) object);
                        }
                    }
                    if (users.isEmpty()) {
                        callback.run(null);
                        return;
                    }
                    controller.putUsers(users, false);
                    MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);
                    callback.run(users.get(0));
                }));
    }
}
