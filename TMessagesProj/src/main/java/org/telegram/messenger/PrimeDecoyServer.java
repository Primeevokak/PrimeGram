package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * PrimeGram: answers a small, fixed set of TL requests from {@link PrimeDecoyPersona}'s fake
 * data instead of ever reaching the network - the actual mechanism that makes decoy mode show
 * something instead of an empty/broken app once real network access is cut off at
 * {@code ConnectionsManager.sendRequest}.
 *
 * <p>Everything NOT explicitly handled here returns {@code null}, and the caller must then drop
 * the request silently (never invoke its callback) rather than answer with an error - an error
 * makes real UI retry forever (a spinner that never stops), where silence just leaves that one
 * optional feature not working, which is the honest state of a screen this MVP doesn't fake yet
 * (profile "full info" screens, search, stickers, and everything else outside the core
 * dialogs/history/contacts/send flow).
 *
 * <p>Every response object here is real, hand-built {@code TLRPC} data, handed directly to the
 * caller as a Java object graph - it is never serialized to wire bytes, so it only has to satisfy
 * what downstream Java code (mainly {@code MessagesController}) actually reads, not the wire
 * format's own flag bookkeeping.
 */
public final class PrimeDecoyServer {

    private static volatile PrimeDecoyPersona persona;

    private PrimeDecoyServer() {
    }

    private static PrimeDecoyPersona persona() {
        PrimeDecoyPersona p = persona;
        if (p == null) {
            synchronized (PrimeDecoyServer.class) {
                p = persona;
                if (p == null) {
                    p = persona = PrimeDecoyPersona.generate(PrimeDecoyState.seed());
                }
            }
        }
        return p;
    }

    /** @return a response object to hand back as-if from the server, or {@code null} if this
     *  request type isn't one decoy mode fakes - callers must treat {@code null} as "silently
     *  drop," not as an empty/error response. */
    public static TLObjectOrNull answer(TLObject request) {
        if (request == null) {
            return null;
        }
        final PrimeDecoyPersona p = persona();

        if (request instanceof TLRPC.TL_messages_getDialogs) {
            return wrap(buildDialogsResponse(p));
        }
        if (request instanceof TLRPC.TL_messages_getHistory) {
            TLRPC.TL_messages_getHistory req = (TLRPC.TL_messages_getHistory) request;
            return wrap(buildHistoryResponse(p, DialogObject.getPeerDialogId(req.peer)));
        }
        if (request instanceof TLRPC.TL_messages_readHistory || request instanceof TLRPC.TL_channels_readHistory) {
            TLRPC.TL_messages_affectedMessages r = new TLRPC.TL_messages_affectedMessages();
            r.pts = 1;
            r.pts_count = 0;
            return wrap(r);
        }
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            TLRPC.TL_messages_sendMessage req = (TLRPC.TL_messages_sendMessage) request;
            TLRPC.TL_updateShortSentMessage r = new TLRPC.TL_updateShortSentMessage();
            r.out = true;
            r.id = nextFakeMessageId();
            r.pts = 1;
            r.pts_count = 1;
            r.date = (int) (System.currentTimeMillis() / 1000L);
            return wrap(r);
        }
        if (request instanceof TLRPC.TL_contacts_getContacts) {
            TLRPC.TL_contacts_contacts r = new TLRPC.TL_contacts_contacts();
            for (TLRPC.TL_user u : p.contacts) {
                TLRPC.TL_contact c = new TLRPC.TL_contact();
                c.user_id = u.id;
                c.mutual = true;
                r.contacts.add(c);
                r.users.add(u);
            }
            r.saved_count = r.contacts.size();
            return wrap(r);
        }
        if (request instanceof TLRPC.TL_updates_getState) {
            TLRPC.TL_updates_state r = new TLRPC.TL_updates_state();
            r.pts = 1;
            r.qts = 0;
            r.date = (int) (System.currentTimeMillis() / 1000L);
            r.seq = 1;
            r.unread_count = 0;
            return wrap(r);
        }
        if (request instanceof TLRPC.TL_updates_getDifference) {
            TLRPC.TL_updates_differenceEmpty r = new TLRPC.TL_updates_differenceEmpty();
            r.date = (int) (System.currentTimeMillis() / 1000L);
            r.seq = 1;
            return wrap(r);
        }
        return null;
    }

    private static volatile int fakeMessageIdCounter = 900_000_000;

    private static synchronized int nextFakeMessageId() {
        return ++fakeMessageIdCounter;
    }

    private static TLRPC.TL_messages_dialogs buildDialogsResponse(PrimeDecoyPersona p) {
        TLRPC.TL_messages_dialogs r = new TLRPC.TL_messages_dialogs();
        r.dialogs.addAll(p.dialogs);
        r.messages.addAll(latestPerDialog(p));
        r.chats.add(p.group);
        r.chats.add(p.channel);
        r.users.addAll(p.contacts);
        r.users.add(p.self);
        r.count = r.dialogs.size();
        return r;
    }

    private static ArrayList<TLRPC.Message> latestPerDialog(PrimeDecoyPersona p) {
        ArrayList<TLRPC.Message> latest = new ArrayList<>();
        for (TLRPC.TL_dialog d : p.dialogs) {
            long dialogId = DialogObject.getPeerDialogId(d.peer);
            for (int i = p.allMessages.size() - 1; i >= 0; i--) {
                TLRPC.TL_message m = p.allMessages.get(i);
                if (m.id == d.top_message && messageDialogId(m) == dialogId) {
                    latest.add(m);
                    break;
                }
            }
        }
        return latest;
    }

    private static TLRPC.TL_messages_messages buildHistoryResponse(PrimeDecoyPersona p, long dialogId) {
        TLRPC.TL_messages_messages r = new TLRPC.TL_messages_messages();
        for (TLRPC.TL_message m : p.allMessages) {
            if (messageDialogId(m) == dialogId) {
                r.messages.add(m);
            }
        }
        r.chats.add(p.group);
        r.chats.add(p.channel);
        r.users.addAll(p.contacts);
        r.users.add(p.self);
        return r;
    }

    private static long messageDialogId(TLRPC.TL_message m) {
        return DialogObject.getPeerDialogId(m.peer_id);
    }

    /** Distinguishes "handled, here's the (possibly null-content) response" from "not handled at
     *  all" without a sentinel object, since a real response is itself sometimes legitimately an
     *  empty-but-valid object. */
    public static final class TLObjectOrNull {
        public final TLObject value;
        TLObjectOrNull(TLObject value) {
            this.value = value;
        }
    }

    private static TLObjectOrNull wrap(TLObject value) {
        return new TLObjectOrNull(value);
    }
}
