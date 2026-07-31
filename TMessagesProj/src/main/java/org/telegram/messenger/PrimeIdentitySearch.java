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

    /** What kind of identifier a query turned out to be, for the heading above the result. */
    public static final int KIND_NONE = 0;
    public static final int KIND_ID = 1;
    public static final int KIND_PHONE = 2;
    public static final int KIND_LINK = 3;

    /**
     * Which of the three this query is, or {@link #KIND_NONE}.
     *
     * <p>A bare {@code @username} is deliberately not one of them: the ordinary search already
     * resolves those against the server, and answering it here would put a second copy of the same
     * person underneath the first.
     */
    public static int kindOf(String query) {
        if (parseId(query) != null) {
            return KIND_ID;
        }
        if (parseLink(query) != null) {
            return KIND_LINK;
        }
        return parsePhone(query) != null ? KIND_PHONE : KIND_NONE;
    }

    /** The username inside a t.me or tg:// link, or null. */
    public static String parseLink(String query) {
        if (query == null) {
            return null;
        }
        String value = query.trim();
        final int index = value.indexOf("t.me/");
        if (index >= 0) {
            value = value.substring(index + 5);
        } else if (value.startsWith("tg://resolve?domain=")) {
            value = value.substring("tg://resolve?domain=".length());
        } else {
            return null;
        }
        int cut = value.indexOf('?');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        cut = value.indexOf('/');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        // A "+"-prefixed link is a private invite, which is a different request and a different
        // kind of answer - a chat you have not joined, not a peer you can open.
        if (value.startsWith("+") || value.isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * The digits of a phone number, or null.
     *
     * <p>Requires the query to look dialled - a leading plus, or spacing of some kind. A run of
     * eleven digits with nothing else is far more likely to be an id, and is claimed by
     * {@link #parseId} above.
     */
    public static String parsePhone(String query) {
        if (query == null) {
            return null;
        }
        final String text = query.trim();
        if (!text.startsWith("+") && !text.contains(" ") && !text.contains("-") && !text.contains("(")) {
            return null;
        }
        final String digits = text.replaceAll("[^0-9]", "");
        return digits.length() >= 7 && digits.length() <= 15 ? digits : null;
    }

    /**
     * Resolves whatever kind of identifier this query is, or calls back with null.
     *
     * <p>The callback always runs on the main thread, and always runs exactly once.
     */
    public static void resolveQuery(int account, String query, Utilities.Callback<TLObject> callback) {
        if (callback == null) {
            return;
        }
        final Long id = parseId(query);
        if (id != null) {
            resolve(account, id, callback);
            return;
        }
        final String username = parseLink(query);
        if (username != null) {
            resolveUsername(account, username, callback);
            return;
        }
        final String phone = parsePhone(query);
        if (phone != null) {
            resolvePhone(account, phone, callback);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> callback.run(null));
    }

    private static void resolveUsername(int account, String username, Utilities.Callback<TLObject> callback) {
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> callback.run(firstPeer(account, response))));
    }

    private static void resolvePhone(int account, String phone, Utilities.Callback<TLObject> callback) {
        final TLRPC.TL_contacts_resolvePhone req = new TLRPC.TL_contacts_resolvePhone();
        req.phone = phone;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> callback.run(firstPeer(account, response))));
    }

    /** Stores what came back and returns the peer it names, so tapping the row can open it. */
    private static TLObject firstPeer(int account, TLObject response) {
        if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
            return null;
        }
        final TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
        MessagesController.getInstance(account).putUsers(resolved.users, false);
        MessagesController.getInstance(account).putChats(resolved.chats, false);
        MessagesStorage.getInstance(account).putUsersAndChats(resolved.users, resolved.chats, true, true);
        if (!resolved.users.isEmpty()) {
            return resolved.users.get(0);
        }
        return resolved.chats.isEmpty() ? null : resolved.chats.get(0);
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
