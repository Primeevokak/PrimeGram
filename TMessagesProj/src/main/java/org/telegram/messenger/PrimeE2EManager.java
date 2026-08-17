package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.security.SecureRandom;

/**
 * PrimeGram E2E: orchestration facade over the X3DH handshake and Double Ratchet
 * message flow for a single account - the one class every hook point (chat menu,
 * {@code SendMessagesHelper}, {@code MessageObject}, {@code ChatActivity}) calls into,
 * so those diffs stay a few lines each.
 *
 * <p><b>Handshake ceremony.</b> Neither side has a live directory to fetch a prekey
 * bundle from before the very first message, so the classic X3DH "initiator fetches
 * responder's published bundle" shape is inverted here: whoever taps "Enable E2E"
 * sends their OWN identity/SPK/OPK bundle as the request - cryptographically that makes
 * them the X3DH "bundle owner" (their prekeys are what get consumed), and whoever
 * *replies* is the actual X3DH initiator (generates the ephemeral, computes SK first,
 * and can start sending immediately). See {@code PrimeE2EFraming.Bundle.ephemeralPub}'s
 * doc for the same explanation from the wire-format side.
 */
public final class PrimeE2EManager {

    private PrimeE2EManager() {
    }

    /** Only ordinary 1-on-1 chats with another real user - Telegram dialog ids are
     *  positive exactly for that case (negative = chat/channel, encrypted/Secret Chat
     *  dialogs are a completely separate id space this fork never confuses with these). */
    static boolean isEligibleDialog(long dialogId) {
        return dialogId > 0;
    }

    public static boolean isSessionEstablished(long dialogId) {
        PrimeE2ESession s = PrimeE2EVault.getSession(dialogId);
        return s != null && s.mode == PrimeE2ESession.Mode.ESTABLISHED;
    }

    public static boolean isHandshakePending(long dialogId) {
        PrimeE2ESession s = PrimeE2EVault.getSession(dialogId);
        return s != null && s.mode == PrimeE2ESession.Mode.REQUESTED;
    }

    /** This device received a bundle-request and is waiting for the local user to hold
     *  "Подтвердить"/"Отменить" - no cryptography has run yet. */
    public static boolean isIncomingPending(long dialogId) {
        PrimeE2ESession s = PrimeE2EVault.getSession(dialogId);
        return s != null && s.mode == PrimeE2ESession.Mode.INCOMING_PENDING;
    }

    /** UI entry point ("Enable E2E Encryption" in the chat menu). */
    public static void beginHandshake(int account, long dialogId) {
        if (!isEligibleDialog(dialogId) || isSessionEstablished(dialogId)
                || isHandshakePending(dialogId) || isIncomingPending(dialogId)) {
            return;
        }
        PrimeE2EIdentity.PublicBundle local = PrimeE2EIdentity.getOrCreateLocalBundle();
        PrimeE2EIdentity.SpkKeyPair spkSnapshot = PrimeE2EIdentity.currentSpk();

        PrimeE2ESession session = new PrimeE2ESession();
        session.selfUserId = selfUserId(account);
        session.peerUserId = dialogId;
        session.dialogId = dialogId;
        session.mode = PrimeE2ESession.Mode.REQUESTED;
        session.pendingSpkPriv = spkSnapshot.priv;
        session.pendingSpkPub = spkSnapshot.pub;
        PrimeE2EVault.putSession(session);
        PrimeE2EState.setMode(dialogId, PrimeE2EState.MODE_REQUESTED);

        PrimeE2EFraming.Bundle wire = toWireBundle(local, PrimeE2EFraming.SUBTYPE_REQUEST);
        sendFramed(account, dialogId, PrimeE2EFraming.encodeHandshake(wire));
    }

    /** Hooked at the very top of {@code SendMessagesHelper.sendMessage(...)}, right
     *  after the existing plugin hook and before anything reads {@code
     *  sendMessageParams.message} into a local. For a plain-text send into an
     *  E2E-established dialog, mutates {@code params} in place - {@code params.message}
     *  becomes ciphertext (what actually reaches {@code reqSend.message}/the server),
     *  {@code params.primeDisplayMessage} becomes the original plaintext (what the
     *  sender's own bubble and local storage show, via the existing split this field
     *  already powers for the outgoing-translation feature) - and returns {@code false}
     *  (proceed with sending).
     *
     *  <p>Returns {@code true} ("block this send, do not call through to the network")
     *  in exactly one case that matters: the dialog is E2E-established but this side's
     *  own sending chain isn't ready yet (this device was the original bundle-owner and
     *  hasn't received the replier's first ratcheted message yet - a real, if usually
     *  brief, window). Falling through and sending the typed text as plain, unencrypted
     *  content in a chat the user was just told is "E2E active" would be a silent
     *  confidentiality failure worse than a message that has to be retried a moment
     *  later, so this blocks rather than degrades. */
    public static boolean tryEncryptOutgoingText(SendMessagesHelper.SendMessageParams params) {
        String text = params.message;
        if (text == null || text.isEmpty() || params.primeDisplayMessage != null) {
            return false;
        }
        if (PrimeE2EFraming.isHandshake(text) || PrimeE2EFraming.isCiphertext(text)) {
            return false; // our own protocol message - never re-encrypt or re-wrap it
        }
        if (params.photo != null || params.document != null || params.location != null
                || params.poll != null || params.todo != null || params.invoice != null || params.game != null) {
            return false; // media send - out of scope until Phase 6
        }
        long dialogId = params.peer;
        if (!isEligibleDialog(dialogId)) {
            return false;
        }
        PrimeE2ESession session = PrimeE2EVault.getSession(dialogId);
        if (session == null || session.mode != PrimeE2ESession.Mode.ESTABLISHED) {
            return false; // no established session for this dialog - send as ordinary text
        }
        if (session.sendChainKey == null) {
            // Established, but this side hasn't derived a sending chain yet - see this
            // method's own doc. Block rather than silently send plaintext.
            return true;
        }

        PrimeE2ERatchet.Encrypted encrypted = PrimeE2ERatchet.encrypt(session, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String framed = PrimeE2EFraming.encodeCiphertext(encrypted);
        if (framed == null) {
            return true; // encryption failed - block rather than fall back to plaintext
        }
        // Persist the advanced ratchet state BEFORE returning - if the app crashes
        // between here and the network call actually completing, the worst case is a
        // message that never reaches the server (retried normally), never a ratchet
        // that thinks it sent something it didn't.
        PrimeE2EVault.putSession(session);

        params.primeDisplayMessage = text;
        params.message = framed;
        params.entities = null; // entity offsets describe the plaintext - meaningless against ciphertext
        return false;
    }

    /** Discards any in-progress or established session and starts over - the user-facing
     *  "Reset Session" action, also the correct recovery if the ratchet ever desyncs.
     *  Does NOT notify the peer - use {@link #cancelHandshake} for the user-facing
     *  Cancel button, which does both. */
    public static void resetSession(long dialogId) {
        PrimeE2EVault.deleteSession(dialogId);
        PrimeE2EState.setMode(dialogId, PrimeE2EState.MODE_NONE);
    }

    /** User-facing "Отменить" action, from either side of a pending handshake
     *  (sender still {@code REQUESTED}, or receiver still {@code INCOMING_PENDING}):
     *  resets the local session and tells the other side via {@code [PGE2E1:C]} so
     *  their own prompt updates instead of sitting stale. Safe to call even if there's
     *  no session (e.g. a double-tap race) - {@link #sendFramed} and
     *  {@link #resetSession} are both no-ops/idempotent in that case. */
    public static void cancelHandshake(int account, long dialogId) {
        resetSession(dialogId);
        sendFramed(account, dialogId, PrimeE2EFraming.encodeCancel());
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.primeE2EStateChanged, dialogId);
    }

    /** User-facing "Сбросить сессию" action on an already-{@code ESTABLISHED} session
     *  (as opposed to {@link #cancelHandshake}, for a still-pending one): sends one
     *  final message through the still-valid ratchet telling the peer encryption is
     *  being turned off, THEN wipes the local session. Deliberately ratchet-encrypted
     *  rather than a bare {@code [PGE2E1:C]} marker - a plain marker is forgeable by
     *  anyone able to write into the chat (including, in principle, a compromised
     *  server), which is exactly why {@code handleIncomingCancel} refuses to tear down
     *  an established session on one; this notice can only have come from whoever
     *  actually holds the real ratchet key, so accepting it as authoritative is sound. */
    public static void disableEncryption(int account, long dialogId) {
        PrimeE2ESession session = PrimeE2EVault.getSession(dialogId);
        if (session != null && session.mode == PrimeE2ESession.Mode.ESTABLISHED && session.sendChainKey != null) {
            PrimeE2ERatchet.Encrypted encrypted = PrimeE2ERatchet.encrypt(session,
                    PrimeE2EFraming.DISABLE_SENTINEL.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String framed = PrimeE2EFraming.encodeCiphertext(encrypted);
            if (framed != null) {
                // Built manually (not via sendFramed) so primeDisplayMessage can be set
                // to the sentinel too - otherwise this device's own stored/shown copy
                // would be the raw ciphertext marker, same bug DialogCell's fix (device
                // testing this session) was for, just on the sending side this time.
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(framed, dialogId);
                params.primeDisplayMessage = PrimeE2EFraming.DISABLE_SENTINEL;
                SendMessagesHelper.getInstance(account).sendMessage(params);
            }
        }
        // Reset unconditionally, even if there was nothing to encrypt with (no
        // established session, or mid-handshake with no send chain yet) - the local
        // teardown must never be blocked on a network message actually going out.
        resetSession(dialogId);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.primeE2EStateChanged, dialogId);
    }

    /** Called from {@code MessageObject}'s constructor for every incoming message,
     *  right after {@code messageOwner = message} and before {@code updateMessageText()}
     *  runs - returns the text that should actually end up in
     *  {@code messageOwner.message}. Never throws: a malformed/hostile marked message
     *  must never break message-list construction for the whole chat. */
    static String processIncoming(int account, TLRPC.Message message) {
        String text = message.message;
        if (text == null || message.out) {
            // Our own sent copy already holds plaintext locally (the
            // SendMessagesHelper.sendMessage hook's primeDisplayMessage split writes
            // plaintext into newMsg.message before it's ever stored), so it never
            // carries an E2E marker - nothing to do for messages we sent ourselves.
            return text;
        }
        try {
            if (PrimeE2EFraming.isHandshake(text)) {
                handleIncomingHandshake(account, message);
                return text; // ChatActivity renders this via PrimeE2EHandshakeCell instead of hiding it
            }
            if (PrimeE2EFraming.isCancel(text)) {
                handleIncomingCancel(account, message.dialog_id);
                return text; // stays hidden - ChatActivity's protocol-marker filter covers it
            }
            if (PrimeE2EFraming.isCiphertext(text)) {
                return handleIncomingCiphertext(account, message);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return text;
    }

    /** Used by {@code DialogCell}'s preview-text guard and the delete-menu guard
     *  (`MessageObject.canDeleteMessage`) - true for ANY E2E protocol message (both
     *  handshake subtypes, request and response, plus a cancel marker), none of which
     *  must ever show as raw marker text or be user-deletable. Broader than
     *  {@link #isHandshakeRequestMarker} on purpose: the *response* half of a handshake
     *  never gets its own visible row (see that method's doc), but still must never leak
     *  as plaintext-looking garbage in the dialogs list. */
    public static boolean isProtocolMarker(MessageObject obj) {
        if (obj == null || obj.messageOwner == null) {
            return false;
        }
        String text = obj.messageOwner.message;
        // The disable-sentinel case is post-decryption: by the time ChatActivity/
        // DialogCell read messageOwner.message here, handleIncomingCiphertext has
        // already replaced the raw [PGE2E1:M ...] marker with the decrypted plaintext
        // (or, for exactly this sentinel, left the sentinel string itself in place) -
        // see that method's own doc.
        return PrimeE2EFraming.isHandshake(text) || PrimeE2EFraming.isCancel(text)
                || PrimeE2EFraming.isDisableSentinel(text);
    }

    /** True only for the *request* half of a handshake - the one message that renders
     *  as a real {@code PrimeE2EHandshakeCell} row (via {@link PrimeInteractiveCellRegistry}).
     *  The *response* message is real, normally-delivered content too, but carries no
     *  new information the UI needs to show on its own - the single request row already
     *  reflects current state (pending/established) whenever it rebinds, so response
     *  messages stay hidden from the timeline exactly like a cancel marker (see
     *  {@code ChatActivity}'s hidden-message filter: hidden whenever
     *  {@link #isProtocolMarker} is true but this one is false). */
    public static boolean isHandshakeRequestMarker(MessageObject obj) {
        if (obj == null || obj.messageOwner == null || !PrimeE2EFraming.isHandshake(obj.messageOwner.message)) {
            return false;
        }
        PrimeE2EFraming.Bundle bundle = PrimeE2EFraming.decodeHandshake(obj.messageOwner.message);
        return bundle != null && bundle.subtype == PrimeE2EFraming.SUBTYPE_REQUEST;
    }

    private static void handleIncomingHandshake(int account, TLRPC.Message message) {
        long dialogId = message.dialog_id;
        if (!isEligibleDialog(dialogId)) {
            return;
        }
        PrimeE2EFraming.Bundle bundle = PrimeE2EFraming.decodeHandshake(message.message);
        if (bundle == null) {
            return;
        }
        if (bundle.subtype == PrimeE2EFraming.SUBTYPE_REQUEST) {
            handleBundleRequest(account, dialogId, message.message, bundle);
        } else {
            handleBundleResponse(account, dialogId, bundle);
        }
    }

    /** We received a bundle-request. This does NOT run any cryptography or establish
     *  anything - it only verifies the SPK signature (fail closed on a forged bundle,
     *  never even show the prompt for one) and stashes the request for the local user
     *  to act on via {@link #confirmIncomingHandshake}/{@link #cancelHandshake}. See
     *  this class's own doc for why the eventual replier ends up as the cryptographic
     *  X3DH initiator regardless of who tapped "Enable E2E" first. */
    private static void handleBundleRequest(int account, long dialogId, String rawBundleText, PrimeE2EFraming.Bundle requesterBundle) {
        if (isSessionEstablished(dialogId) || isIncomingPending(dialogId)) {
            return; // already talking to this peer, or already have a pending prompt - ignore a stray/replayed request
        }
        if (!PrimeE2ECrypto.ed25519Verify(requesterBundle.ikSignPub, requesterBundle.spkPub, requesterBundle.spkSig)) {
            return; // forged/corrupt bundle - fail closed, never show a prompt for it
        }

        PrimeE2ESession session = new PrimeE2ESession();
        session.selfUserId = selfUserId(account);
        session.peerUserId = dialogId;
        session.dialogId = dialogId;
        session.mode = PrimeE2ESession.Mode.INCOMING_PENDING;
        session.incomingBundleRaw = rawBundleText;
        PrimeE2EVault.putSession(session);
        PrimeE2EState.setMode(dialogId, PrimeE2EState.MODE_INCOMING_PENDING);
    }

    /** The local user held "Подтвердить" on an {@code INCOMING_PENDING} prompt - runs
     *  the X3DH math that {@link #handleBundleRequest} deliberately deferred, exactly
     *  as it used to run automatically before this consent step existed. */
    public static void confirmIncomingHandshake(int account, long dialogId) {
        PrimeE2ESession pending = PrimeE2EVault.getSession(dialogId);
        if (pending == null || pending.mode != PrimeE2ESession.Mode.INCOMING_PENDING || pending.incomingBundleRaw == null) {
            return;
        }
        PrimeE2EFraming.Bundle requesterBundle = PrimeE2EFraming.decodeHandshake(pending.incomingBundleRaw);
        if (requesterBundle == null) {
            resetSession(dialogId);
            return;
        }

        SecureRandom random = new SecureRandom();
        PrimeE2ECrypto.KeyPair ek = PrimeE2ECrypto.generateX25519(random);
        byte[] myIkDhPriv = PrimeE2EIdentity.ikDhPriv();

        int consumedIndex = -1;
        byte[] opkPub = null;
        if (!requesterBundle.opkIndices.isEmpty()) {
            consumedIndex = requesterBundle.opkIndices.get(0);
            opkPub = requesterBundle.opkPubs.get(0);
        }

        byte[] dh1 = PrimeE2ECrypto.x25519(myIkDhPriv, requesterBundle.spkPub);
        byte[] dh2 = PrimeE2ECrypto.x25519(ek.priv, requesterBundle.ikDhPub);
        byte[] dh3 = PrimeE2ECrypto.x25519(ek.priv, requesterBundle.spkPub);
        byte[] dh4 = opkPub != null ? PrimeE2ECrypto.x25519(ek.priv, opkPub) : null;
        byte[] sk = PrimeE2ERatchet.deriveX3DHSharedSecret(dh1, dh2, dh3, dh4);

        PrimeE2ESession session = new PrimeE2ESession();
        session.selfUserId = selfUserId(account);
        session.peerUserId = dialogId;
        session.dialogId = dialogId;
        session.peerIkSignPub = requesterBundle.ikSignPub;
        PrimeE2ERatchet.initAsInitiator(session, sk, requesterBundle.spkPub, random);
        PrimeE2EVault.putSession(session);
        PrimeE2EState.setMode(dialogId, PrimeE2EState.MODE_ESTABLISHED);

        PrimeE2EIdentity.PublicBundle myBundle = PrimeE2EIdentity.getOrCreateLocalBundle();
        PrimeE2EFraming.Bundle response = toWireBundle(myBundle, PrimeE2EFraming.SUBTYPE_RESPONSE);
        response.consumedOpkIndex = consumedIndex;
        response.ephemeralPub = ek.pub;
        sendFramed(account, dialogId, PrimeE2EFraming.encodeHandshake(response));
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.primeE2EStateChanged, dialogId);
    }

    private static void handleIncomingCancel(int account, long dialogId) {
        if (!isEligibleDialog(dialogId)) {
            return;
        }
        // Only meaningful if we actually have a pending handshake for this dialog - an
        // ESTABLISHED session is never torn down by a bare cancel marker (that would be
        // a trivial DoS: anyone able to send a message into the chat could kill an
        // already-working encrypted session).
        if (isHandshakePending(dialogId) || isIncomingPending(dialogId)) {
            resetSession(dialogId);
        }
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.primeE2EStateChanged, dialogId);
    }

    /** We're the original requester - cryptographically the X3DH "bundle owner" whose
     *  prekeys just got consumed: mirror the same DH combination using the exact SPK
     *  (and, if one was consumed, OPK) we advertised, then bootstrap our ratchet as the
     *  receiving side - no send chain until the replier's first real message arrives. */
    private static void handleBundleResponse(int account, long dialogId, PrimeE2EFraming.Bundle responderBundle) {
        PrimeE2ESession session = PrimeE2EVault.getSession(dialogId);
        if (session == null || session.mode != PrimeE2ESession.Mode.REQUESTED || session.pendingSpkPriv == null) {
            return; // no matching outstanding request - stray/duplicate/replayed response
        }
        if (!PrimeE2ECrypto.ed25519Verify(responderBundle.ikSignPub, responderBundle.spkPub, responderBundle.spkSig)) {
            return;
        }
        if (responderBundle.ephemeralPub == null) {
            return;
        }

        byte[] myIkDhPriv = PrimeE2EIdentity.ikDhPriv();
        byte[] opkPriv = responderBundle.consumedOpkIndex >= 0
                ? PrimeE2EIdentity.opkPrivIfUnconsumed(responderBundle.consumedOpkIndex) : null;

        byte[] dh1 = PrimeE2ECrypto.x25519(session.pendingSpkPriv, responderBundle.ikDhPub);
        byte[] dh2 = PrimeE2ECrypto.x25519(myIkDhPriv, responderBundle.ephemeralPub);
        byte[] dh3 = PrimeE2ECrypto.x25519(session.pendingSpkPriv, responderBundle.ephemeralPub);
        byte[] dh4 = opkPriv != null ? PrimeE2ECrypto.x25519(opkPriv, responderBundle.ephemeralPub) : null;
        byte[] sk = PrimeE2ERatchet.deriveX3DHSharedSecret(dh1, dh2, dh3, dh4);

        if (responderBundle.consumedOpkIndex >= 0) {
            PrimeE2EIdentity.consumeOwnOpk(responderBundle.consumedOpkIndex);
        }

        session.peerIkSignPub = responderBundle.ikSignPub;
        PrimeE2ERatchet.initAsResponder(session, sk, session.pendingSpkPriv, session.pendingSpkPub);
        session.pendingSpkPriv = null;
        session.pendingSpkPub = null;
        PrimeE2EVault.putSession(session);
        PrimeE2EState.setMode(dialogId, PrimeE2EState.MODE_ESTABLISHED);
    }

    private static String handleIncomingCiphertext(int account, TLRPC.Message message) {
        long dialogId = message.dialog_id;
        PrimeE2ESession session = PrimeE2EVault.getSession(dialogId);
        if (session == null || session.mode != PrimeE2ESession.Mode.ESTABLISHED) {
            return "🔒"; // lock emoji - no session to decrypt with
        }
        PrimeE2EFraming.DecodedCiphertext decoded = PrimeE2EFraming.decodeCiphertext(message.message);
        if (decoded == null) {
            return "🔒";
        }
        byte[] plaintext = PrimeE2ERatchet.decrypt(session, decoded.header, decoded.nonce, decoded.ciphertext, new SecureRandom());
        PrimeE2EVault.putSession(session); // ratchet state advanced even on failure - persist either way
        if (plaintext == null) {
            return "🔒";
        }
        String decoded_text = new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        if (PrimeE2EFraming.isDisableSentinel(decoded_text)) {
            // The peer deliberately turned encryption off on their end and told us via
            // this authenticated (ratchet-encrypted) notice, not a forgeable plain
            // marker - mirror the teardown locally and let a visible chat refresh.
            resetSession(dialogId);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.primeE2EStateChanged, dialogId);
            return decoded_text; // hidden by isProtocolMarker's sentinel check, never shown as a bubble
        }
        return decoded_text;
    }

    private static final byte[] ZERO_SALT_32 = new byte[32];

    /** Out-of-band safety-number verification code, per this class's own doc and the
     *  implementation plan's §5-equivalent design: {@code FP} is derived from both
     *  sides' long-term signing identities (order-independent, so both compute the same
     *  value regardless of role), then folded together with the user-supplied seed
     *  phrase via HMAC into a short numeric code the two humans compare out of band.
     *  The seed phrase never touches any ratchet/message key - this is authentication
     *  only, exactly as specified. */
    public static String computeVerificationCode(long dialogId, String seedPhrase) {
        PrimeE2ESession session = PrimeE2EVault.getSession(dialogId);
        if (session == null || session.mode != PrimeE2ESession.Mode.ESTABLISHED || session.peerIkSignPub == null) {
            return null;
        }
        byte[] myIkSignPub = PrimeE2EIdentity.getOrCreateLocalBundle().ikSignPub;
        byte[] a = myIkSignPub, b = session.peerIkSignPub;
        if (compareUnsigned(a, b) > 0) {
            byte[] t = a; a = b; b = t;
        }
        byte[] ikm = new byte[a.length + b.length];
        System.arraycopy(a, 0, ikm, 0, a.length);
        System.arraycopy(b, 0, ikm, a.length, b.length);
        byte[] fp = PrimeE2ECrypto.hkdfSha256(ZERO_SALT_32, ikm,
                "PGE2E-FP-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 32);

        byte[] seedKey = PrimeE2ECrypto.hkdfSha256(ZERO_SALT_32,
                (seedPhrase == null ? "" : seedPhrase).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "PGE2E-SEED-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 32);
        byte[] code = PrimeE2ECrypto.hmacSha256(seedKey, fp);

        long numeric = ((code[0] & 0xFFL) << 24 | (code[1] & 0xFFL) << 16 | (code[2] & 0xFFL) << 8 | (code[3] & 0xFFL)) % 1_000_000L;
        return String.format(java.util.Locale.US, "%06d", numeric);
    }

    private static int compareUnsigned(byte[] a, byte[] b) {
        for (int i = 0; i < a.length && i < b.length; i++) {
            int x = a[i] & 0xFF, y = b[i] & 0xFF;
            if (x != y) {
                return x - y;
            }
        }
        return a.length - b.length;
    }

    private static PrimeE2EFraming.Bundle toWireBundle(PrimeE2EIdentity.PublicBundle local, int subtype) {
        PrimeE2EFraming.Bundle wire = new PrimeE2EFraming.Bundle();
        wire.subtype = subtype;
        wire.ikDhPub = local.ikDhPub;
        wire.ikSignPub = local.ikSignPub;
        wire.spkPub = local.spkPub;
        wire.spkSig = local.spkSig;
        wire.spkId = local.spkId;
        wire.opkIndices.addAll(local.opkIndices);
        wire.opkPubs.addAll(local.opkPubs);
        return wire;
    }

    private static void sendFramed(int account, long dialogId, String framedText) {
        if (framedText == null) {
            return;
        }
        SendMessagesHelper.getInstance(account).sendMessage(
                SendMessagesHelper.SendMessageParams.of(framedText, dialogId));
    }

    private static long selfUserId(int account) {
        return UserConfig.getInstance(account).getClientUserId();
    }
}
