package org.telegram.messenger;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * PrimeGram E2E: the Double Ratchet state machine (per the public Signal Double Ratchet
 * spec) plus the X3DH shared-secret derivation that seeds it. Pure functions over
 * {@link PrimeE2ESession} - no Android dependency, no I/O, unit-testable directly.
 *
 * <p>Root/chain KDF domain separation follows RFC 5869 (HKDF) for the root chain and the
 * standard two-constant HMAC construction for the symmetric chain-key ratchet, both
 * built on {@link PrimeE2ECrypto}.
 */
public final class PrimeE2ERatchet {

    private static final byte[] INFO_ROOT = utf8("PGE2E-DR-ROOT-v1");
    private static final byte[] INFO_X3DH = utf8("PGE2E-X3DH-v1");
    private static final byte[] ZERO_SALT = new byte[32];
    private static final byte CK_CONST_MESSAGE_KEY = 0x01;
    private static final byte CK_CONST_NEXT_CHAIN = 0x02;

    private PrimeE2ERatchet() {
    }

    private static byte[] utf8(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---- X3DH shared-secret derivation ----

    /** 3 or 4 DH outputs (DH4/the one-time-prekey combine is omitted when the
     *  responder's OPK batch was exhausted - X3DH's own documented degraded mode),
     *  concatenated and HKDF'd into the 32-byte root secret SK. */
    public static byte[] deriveX3DHSharedSecret(byte[] dh1, byte[] dh2, byte[] dh3, byte[] dh4 /* nullable */) {
        int len = dh1.length + dh2.length + dh3.length + (dh4 != null ? dh4.length : 0);
        byte[] ikm = new byte[len];
        int off = 0;
        System.arraycopy(dh1, 0, ikm, off, dh1.length); off += dh1.length;
        System.arraycopy(dh2, 0, ikm, off, dh2.length); off += dh2.length;
        System.arraycopy(dh3, 0, ikm, off, dh3.length); off += dh3.length;
        if (dh4 != null) {
            System.arraycopy(dh4, 0, ikm, off, dh4.length);
        }
        byte[] sk = PrimeE2ECrypto.hkdfSha256(ZERO_SALT, ikm, INFO_X3DH, 32);
        Arrays.fill(ikm, (byte) 0);
        return sk;
    }

    // ---- Ratchet bootstrap (RatchetInitAlice / RatchetInitBob per the Double Ratchet spec) ----

    /** Initiator side: generates a fresh ratchet keypair, treats the responder's SPK
     *  public key as the initial "their ratchet key" placeholder (the standard
     *  bootstrap technique the spec itself defines - Bob hasn't sent a real ratchet key
     *  yet, so his already-known SPK stands in for it), and derives the first sending
     *  chain immediately so the initiator can send right away. */
    public static void initAsInitiator(PrimeE2ESession session, byte[] sharedSecret,
                                        byte[] responderSpkPub, SecureRandom random) {
        PrimeE2ECrypto.KeyPair ratchet = PrimeE2ECrypto.generateX25519(random);
        session.myRatchetPriv = ratchet.priv;
        session.myRatchetPub = ratchet.pub;
        session.theirRatchetPub = responderSpkPub;

        byte[] dhOut = PrimeE2ECrypto.x25519(session.myRatchetPriv, session.theirRatchetPub);
        byte[][] rkAndCk = kdfRootChain(sharedSecret, dhOut);
        session.rootKey = rkAndCk[0];
        session.sendChainKey = rkAndCk[1];
        session.recvChainKey = null;
        session.sendMessageNumber = 0;
        session.recvMessageNumber = 0;
        session.previousSendChainLength = 0;
        session.mode = PrimeE2ESession.Mode.ESTABLISHED;
    }

    /** Responder side: reuses its own SPK keypair as the initial ratchet keypair (the
     *  same bootstrap trick, from the other side) - no sending chain yet, since a real
     *  DH-ratchet step only happens once the initiator's first message (carrying her
     *  real ratchet public key) arrives. */
    public static void initAsResponder(PrimeE2ESession session, byte[] sharedSecret,
                                        byte[] spkPriv, byte[] spkPub) {
        session.myRatchetPriv = spkPriv;
        session.myRatchetPub = spkPub;
        session.theirRatchetPub = null;
        session.rootKey = sharedSecret;
        session.sendChainKey = null;
        session.recvChainKey = null;
        session.sendMessageNumber = 0;
        session.recvMessageNumber = 0;
        session.previousSendChainLength = 0;
        session.mode = PrimeE2ESession.Mode.ESTABLISHED;
    }

    // ---- Root/chain KDFs ----

    /** KDF_RK: HKDF(salt=currentRootKey, ikm=dhOutput) -> 64 bytes, split into the new
     *  root key and a fresh chain key. */
    private static byte[][] kdfRootChain(byte[] currentRootKey, byte[] dhOutput) {
        byte[] out = PrimeE2ECrypto.hkdfSha256(currentRootKey, dhOutput, INFO_ROOT, 64);
        byte[] newRoot = Arrays.copyOfRange(out, 0, 32);
        byte[] newChain = Arrays.copyOfRange(out, 32, 64);
        Arrays.fill(out, (byte) 0);
        return new byte[][]{newRoot, newChain};
    }

    /** KDF_CK: two HMACs of the current chain key under distinct constants - one
     *  advances the chain, the other derives this message's key. Both are cheap HMACs,
     *  not full HKDF, matching the Double Ratchet spec (chain steps happen far more
     *  often than root steps and don't need HKDF's extract/expand separation). */
    private static byte[] nextChainKey(byte[] chainKey) {
        return PrimeE2ECrypto.hmacSha256(chainKey, new byte[]{CK_CONST_NEXT_CHAIN});
    }

    private static byte[] messageKeyFromChain(byte[] chainKey) {
        return PrimeE2ECrypto.hmacSha256(chainKey, new byte[]{CK_CONST_MESSAGE_KEY});
    }

    // ---- Message header ----

    public static final class Header {
        public final byte[] ratchetPub;
        public final int previousChainLength;
        public final int messageNumber;

        public Header(byte[] ratchetPub, int previousChainLength, int messageNumber) {
            this.ratchetPub = ratchetPub;
            this.previousChainLength = previousChainLength;
            this.messageNumber = messageNumber;
        }
    }

    public static final class Encrypted {
        public final Header header;
        public final byte[] nonce;
        public final byte[] ciphertext;

        public Encrypted(Header header, byte[] nonce, byte[] ciphertext) {
            this.header = header;
            this.nonce = nonce;
            this.ciphertext = ciphertext;
        }
    }

    /** Deterministic nonce derived from the chain position, not random - makes GCM
     *  nonce reuse structurally impossible (a repeated (key, nonce) pair is
     *  catastrophic for GCM) rather than relying on a CSPRNG being called correctly at
     *  exactly the right point. Each message key is used exactly once, so binding the
     *  nonce to the message key itself (rather than e.g. a counter that could in theory
     *  be replayed under a bug elsewhere) ties the two together directly. */
    private static byte[] nonceForMessageKey(byte[] messageKey) {
        return Arrays.copyOfRange(
                PrimeE2ECrypto.hkdfSha256(ZERO_SALT, messageKey, utf8("PGE2E-NONCE-v1"), PrimeE2ECrypto.GCM_NONCE_LEN),
                0, PrimeE2ECrypto.GCM_NONCE_LEN);
    }

    /** Canonical (order-independent) pairing of the two participants, so encrypt (from
     *  one side's perspective) and decrypt (from the other side's) compute the
     *  identical AAD despite "self" and "peer" being swapped between them. This is what
     *  actually binds a ciphertext to one specific conversation - dialogId alone isn't
     *  symmetric (it's the peer's own user id from each side's perspective, so it
     *  differs between Alice's and Bob's session objects for the very same chat). */
    private static byte[] aad(PrimeE2ESession session, int messageNumber) {
        long a = Math.min(session.selfUserId, session.peerUserId);
        long b = Math.max(session.selfUserId, session.peerUserId);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8 + 8 + 4);
        buf.putLong(a).putLong(b).putInt(messageNumber);
        return buf.array();
    }

    // ---- Encrypt / decrypt ----

    public static Encrypted encrypt(PrimeE2ESession session, byte[] plaintext) {
        if (session.sendChainKey == null) {
            throw new IllegalStateException("No sending chain - session not fully established as a sender yet");
        }
        byte[] messageKey = messageKeyFromChain(session.sendChainKey);
        session.sendChainKey = nextChainKey(session.sendChainKey);

        Header header = new Header(session.myRatchetPub, session.previousSendChainLength, session.sendMessageNumber);
        session.sendMessageNumber++;

        byte[] nonce = nonceForMessageKey(messageKey);
        byte[] ad = aad(session, header.messageNumber);
        byte[] ciphertext = PrimeE2ECrypto.aesGcmEncrypt(messageKey, nonce, ad, plaintext);
        Arrays.fill(messageKey, (byte) 0);
        return new Encrypted(header, nonce, ciphertext);
    }

    /** @return the decrypted plaintext, or null if the message cannot be decrypted
     *  (unknown skipped key, authentication failure) - never throws for a malformed/
     *  hostile message, since a crash here would break message-list rendering for the
     *  whole chat. */
    public static byte[] decrypt(PrimeE2ESession session, Header header, byte[] nonce, byte[] ciphertext,
                                  SecureRandom random) {
        byte[] skipped = session.takeSkippedKey(header.ratchetPub, header.messageNumber);
        if (skipped != null) {
            byte[] ad = aad(session, header.messageNumber);
            byte[] plaintext = PrimeE2ECrypto.aesGcmDecrypt(skipped, nonce, ad, ciphertext);
            Arrays.fill(skipped, (byte) 0);
            return plaintext;
        }

        if (session.theirRatchetPub == null || !Arrays.equals(header.ratchetPub, session.theirRatchetPub)) {
            // New DH ratchet key from the remote side - skip and cache any trailing
            // keys on the OLD receiving chain first, then step the ratchet.
            if (session.recvChainKey != null) {
                skipMessageKeys(session, session.theirRatchetPub, header.previousChainLength);
            }
            dhRatchetStep(session, header.ratchetPub, random);
        }

        skipMessageKeys(session, header.ratchetPub, header.messageNumber);

        byte[] messageKey = messageKeyFromChain(session.recvChainKey);
        session.recvChainKey = nextChainKey(session.recvChainKey);
        session.recvMessageNumber++;

        byte[] ad = aad(session, header.messageNumber);
        byte[] plaintext = PrimeE2ECrypto.aesGcmDecrypt(messageKey, nonce, ad, ciphertext);
        Arrays.fill(messageKey, (byte) 0);
        return plaintext;
    }

    private static void skipMessageKeys(PrimeE2ESession session, byte[] ratchetPub, int untilMessageNumber) {
        if (session.recvChainKey == null || ratchetPub == null) {
            return;
        }
        while (session.recvMessageNumber < untilMessageNumber) {
            byte[] messageKey = messageKeyFromChain(session.recvChainKey);
            session.recvChainKey = nextChainKey(session.recvChainKey);
            session.storeSkippedKey(ratchetPub, session.recvMessageNumber, messageKey);
            session.recvMessageNumber++;
        }
    }

    private static void dhRatchetStep(PrimeE2ESession session, byte[] newTheirRatchetPub, SecureRandom random) {
        session.previousSendChainLength = session.sendMessageNumber;
        session.sendMessageNumber = 0;
        session.recvMessageNumber = 0;
        session.theirRatchetPub = newTheirRatchetPub;

        byte[] dhRecv = PrimeE2ECrypto.x25519(session.myRatchetPriv, session.theirRatchetPub);
        byte[][] rkAndRecvCk = kdfRootChain(session.rootKey, dhRecv);
        session.rootKey = rkAndRecvCk[0];
        session.recvChainKey = rkAndRecvCk[1];

        PrimeE2ECrypto.KeyPair newRatchet = PrimeE2ECrypto.generateX25519(random);
        session.myRatchetPriv = newRatchet.priv;
        session.myRatchetPub = newRatchet.pub;

        byte[] dhSend = PrimeE2ECrypto.x25519(session.myRatchetPriv, session.theirRatchetPub);
        byte[][] rkAndSendCk = kdfRootChain(session.rootKey, dhSend);
        session.rootKey = rkAndSendCk[0];
        session.sendChainKey = rkAndSendCk[1];
    }
}
