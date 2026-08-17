package org.telegram.messenger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PrimeGram E2E: per-peer Double Ratchet session state. Pure data (plus one bounded
 * cache with its own eviction policy) - no Android dependency, no I/O. One instance per
 * (accountId, peerUserId), held by {@code PrimeE2EVault} (sealed-at-rest storage, added
 * in a later phase) and mutated in place by {@link PrimeE2ERatchet}.
 */
public final class PrimeE2ESession {

    public enum Mode {
        NONE,
        /** This device sent a bundle-request and is waiting for the peer's reply. */
        REQUESTED,
        /** This device received a bundle-request and is waiting for the local user to
         *  hold "Подтвердить"/"Отменить" - no DH math has run yet, see
         *  {@link #incomingBundleRaw}. */
        INCOMING_PENDING,
        ESTABLISHED
    }

    /** Bounded so a broken or malicious peer flooding gaps in the message-number
     *  sequence can't grow this without limit; Telegram permits multi-day-delayed
     *  delivery, so this needs real headroom, not a tiny cap. */
    public static final int MAX_SKIPPED_KEYS = 200;

    /** This account's own user id - together with {@link #peerUserId}, forms the AAD
     *  context binding a ciphertext to this specific conversation. Both must be present
     *  (not derived from "current account") because AAD is computed identically on
     *  encrypt and decrypt, and encrypt/decrypt happen from each side's own perspective
     *  where "self" and "peer" are swapped - the AAD itself is built from a canonical
     *  (order-independent) pairing of the two, see {@code PrimeE2ERatchet.aad()}. */
    public long selfUserId;
    public long peerUserId;
    public long dialogId;
    public Mode mode = Mode.NONE;

    /** The peer's long-term Ed25519 signing identity, captured once the handshake
     *  completes (from whichever bundle message carried it) - the safety-number
     *  fingerprint shown for out-of-band verification is computed from this plus this
     *  device's own signing identity, never from anything session-specific, so it stays
     *  stable across a session reset. */
    public byte[] peerIkSignPub;

    /** 32-byte root key - re-derived on every DH ratchet step. */
    public byte[] rootKey;

    /** Sending chain: null until this side has sent (or is ready to send) at least once. */
    public byte[] sendChainKey;
    public int sendMessageNumber;

    /** Receiving chain: null until the first message from the remote ratchet key arrives. */
    public byte[] recvChainKey;
    public int recvMessageNumber;

    /** "PN" in the Double Ratchet spec - length of the previous sending chain, carried
     *  in the header so the receiver knows how many trailing skipped keys to derive
     *  from the OLD receiving chain before switching to the new one. */
    public int previousSendChainLength;

    /** This side's current ratchet keypair. */
    public byte[] myRatchetPriv;
    public byte[] myRatchetPub;

    /** The most recent ratchet public key seen from the remote side; null until the
     *  first message (Alice) / until Bob receives Alice's first message (Bob). */
    public byte[] theirRatchetPub;

    /** Only populated while {@code mode == REQUESTED}: the exact signed-prekey keypair
     *  this device advertised in its outstanding bundle-request. Whoever *sends* a
     *  bundle-request is, cryptographically, the X3DH "bundle owner" (see
     *  {@code PrimeE2EFraming.Bundle.ephemeralPub}'s doc) - completing the handshake
     *  once a reply arrives requires DH-ing with THIS exact SPK private key, not
     *  whatever {@link PrimeE2EIdentity} considers "current" by then (the identity's
     *  own SPK rotates independently every 7 days, and the peer may take arbitrarily
     *  long to reply - a snapshot avoids the two silently drifting apart). */
    public byte[] pendingSpkPriv;
    public byte[] pendingSpkPub;

    /** Only populated while {@code mode == INCOMING_PENDING}: the raw, still-encoded
     *  `[PGE2E1:H ...]` bundle-request text this device received but hasn't acted on
     *  yet - kept encoded (not re-parsed into fields) since {@link PrimeE2EFraming.Bundle}
     *  isn't itself part of this class's serialized shape; re-decoding it on confirm is
     *  cheap and keeps this class's own (de)serializer from needing to know the wire
     *  format. Surviving process death here (sealed alongside the rest of this session)
     *  is what lets the confirm/cancel prompt still work correctly even if the app was
     *  killed and relaunched before the user acted on it. */
    public String incomingBundleRaw;

    /** Skipped-message-key cache, keyed by "ratchetPubKeyHex:msgNum". LinkedHashMap in
     *  access order would evict the wrong thing here (skipped keys are consumed once
     *  then deleted, not re-touched) - insertion order is what we want: oldest-first
     *  eviction once {@link #MAX_SKIPPED_KEYS} is exceeded. */
    public final LinkedHashMap<String, byte[]> skippedKeys = new LinkedHashMap<>();

    public static String skippedKeyId(byte[] ratchetPub, int messageNumber) {
        StringBuilder sb = new StringBuilder(ratchetPub.length * 2 + 8);
        for (byte b : ratchetPub) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        sb.append(':').append(messageNumber);
        return sb.toString();
    }

    void storeSkippedKey(byte[] ratchetPub, int messageNumber, byte[] messageKey) {
        if (skippedKeys.size() >= MAX_SKIPPED_KEYS) {
            Map.Entry<String, byte[]> oldest = skippedKeys.entrySet().iterator().next();
            skippedKeys.remove(oldest.getKey());
        }
        skippedKeys.put(skippedKeyId(ratchetPub, messageNumber), messageKey);
    }

    /** Consumes (removes) the skipped key if present - forward secrecy: a skipped key
     *  is usable exactly once, same as any other message key. */
    byte[] takeSkippedKey(byte[] ratchetPub, int messageNumber) {
        return skippedKeys.remove(skippedKeyId(ratchetPub, messageNumber));
    }
}
