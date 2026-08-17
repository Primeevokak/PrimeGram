package org.telegram.messenger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram E2E: per-account long-term identity - two long-term keypairs (
 * {@code IK_dh} for X3DH Diffie-Hellman, {@code IK_sign} for signing the SPK; kept
 * separate rather than one XEdDSA-hybrid key, since both are plain unmodified RFC 7748/
 * RFC 8032 primitives via BouncyCastle, not a hand-rolled Montgomery-to-Edwards
 * conversion), a periodically-rotated signed prekey, and a replenishing batch of
 * one-time prekeys - all generated once and sealed under {@link PrimeE2EKeyStore}'s
 * Keystore key via {@link PrimeE2ESealedFile}, mirroring {@link PrimePinVault}'s
 * envelope pattern.
 */
final class PrimeE2EIdentity {

    private static final int INNER_MAGIC = 0x50474531; // "PGE1"
    private static final int INNER_VERSION = 1;
    private static final byte[] AAD = "PrimeGram E2E identity v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** SPK rotates every 7 days - long enough that rotation is rare (each rotation
     *  invalidates any in-flight handshake still referencing the old SPK id), short
     *  enough to bound how long a single SPK compromise would matter. */
    private static final long SPK_ROTATION_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000;

    /** Batch size for one-time prekeys and the threshold at which more are generated -
     *  a handful of E2E handshakes per account is the expected usage, so this is
     *  generous headroom, not a tight budget. */
    private static final int OPK_BATCH_SIZE = 20;
    private static final int OPK_REPLENISH_THRESHOLD = 5;

    private static final Object LOCK = new Object();
    private static volatile State cached;

    private PrimeE2EIdentity() {
    }

    static final class OneTimePrekey {
        final int index;
        final byte[] priv;
        final byte[] pub;
        boolean consumed;

        OneTimePrekey(int index, byte[] priv, byte[] pub, boolean consumed) {
            this.index = index;
            this.priv = priv;
            this.pub = pub;
            this.consumed = consumed;
        }
    }

    private static final class State {
        byte[] ikDhPriv, ikDhPub;
        byte[] ikSignPriv, ikSignPub;
        byte[] spkPriv, spkPub, spkSig;
        long spkCreatedAt;
        int spkId;
        int nextOpkIndex;
        final List<OneTimePrekey> opks = new ArrayList<>();
    }

    /** Everything a peer needs to run their side of X3DH against this account - no
     *  private key material. */
    static final class PublicBundle {
        final byte[] ikDhPub;
        final byte[] ikSignPub;
        final byte[] spkPub;
        final byte[] spkSig;
        final int spkId;
        final List<byte[]> opkPubs;
        final List<Integer> opkIndices;

        PublicBundle(byte[] ikDhPub, byte[] ikSignPub, byte[] spkPub, byte[] spkSig, int spkId,
                     List<byte[]> opkPubs, List<Integer> opkIndices) {
            this.ikDhPub = ikDhPub;
            this.ikSignPub = ikSignPub;
            this.spkPub = spkPub;
            this.spkSig = spkSig;
            this.spkId = spkId;
            this.opkPubs = opkPubs;
            this.opkIndices = opkIndices;
        }
    }

    private static File dir() {
        return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "primegram/security");
    }

    private static PrimeE2ESealedFile sealedFile() {
        return new PrimeE2ESealedFile(
                new File(dir(), "e2e_identity.bin"),
                new File(dir(), "e2e_identity.lock"),
                AAD);
    }

    /** @return the current local public bundle, generating identity/SPK/OPKs on first
     *  use if none exist yet, rotating the SPK if it's stale, and replenishing OPKs if
     *  the unconsumed count has dropped below the threshold. Safe to call unconditionally
     *  whenever a handshake message needs to be built. */
    static PublicBundle getOrCreateLocalBundle() {
        synchronized (LOCK) {
            State state = loadOrGenerate();
            maybeRotateSpk(state);
            maybeReplenishOpks(state);
            persist(state);
            return toPublicBundle(state);
        }
    }

    /** Marks one-time prekey {@code opkIndex} as consumed (a peer's handshake told us
     *  which one they used) and persists immediately - an OPK must never be reused. */
    static void consumeOwnOpk(int opkIndex) {
        synchronized (LOCK) {
            State state = loadOrGenerate();
            for (OneTimePrekey opk : state.opks) {
                if (opk.index == opkIndex) {
                    opk.consumed = true;
                    break;
                }
            }
            maybeReplenishOpks(state);
            persist(state);
        }
    }

    static byte[] ikDhPriv() {
        synchronized (LOCK) {
            return loadOrGenerate().ikDhPriv;
        }
    }

    static final class SpkKeyPair {
        final byte[] priv;
        final byte[] pub;
        SpkKeyPair(byte[] priv, byte[] pub) {
            this.priv = priv;
            this.pub = pub;
        }
    }

    /** The exact SPK keypair currently being advertised - callers that are about to
     *  send a bundle-request must snapshot this into their session's pending state
     *  (see {@link PrimeE2ESession#pendingSpkPriv}) rather than re-reading "current"
     *  again later, since it can rotate independently while a handshake is outstanding. */
    static SpkKeyPair currentSpk() {
        synchronized (LOCK) {
            State state = loadOrGenerate();
            maybeRotateSpk(state);
            persist(state);
            return new SpkKeyPair(state.spkPriv, state.spkPub);
        }
    }

    /** @return the private half of a still-unconsumed OPK by index, or null if it was
     *  already consumed/never existed - a handshake referencing a stale OPK index must
     *  fail closed, not silently proceed with the wrong key. */
    static byte[] opkPrivIfUnconsumed(int opkIndex) {
        synchronized (LOCK) {
            State state = loadOrGenerate();
            for (OneTimePrekey opk : state.opks) {
                if (opk.index == opkIndex && !opk.consumed) {
                    return opk.priv;
                }
            }
            return null;
        }
    }

    private static State loadOrGenerate() {
        if (cached != null) {
            return cached;
        }
        try {
            PrimeE2EKeyStore.KeyMaterial keyMaterial = PrimeE2EKeyStore.getOrCreate();
            byte[] plain = sealedFile().read(keyMaterial.key);
            State state = plain != null ? deserialize(plain) : null;
            if (state == null) {
                state = generateFresh();
                persistWithKey(state, keyMaterial.key);
            }
            cached = state;
            return state;
        } catch (Exception e) {
            FileLog.e(e);
            // No usable Keystore/vault - fall back to an in-memory-only identity so the
            // feature degrades to "works this session, nothing persists" rather than
            // crashing every caller; PrimeE2EManager surfaces this as "E2E unavailable."
            State state = generateFresh();
            cached = state;
            return state;
        }
    }

    private static State generateFresh() {
        SecureRandom random = new SecureRandom();
        State state = new State();
        PrimeE2ECrypto.KeyPair ikDh = PrimeE2ECrypto.generateX25519(random);
        state.ikDhPriv = ikDh.priv;
        state.ikDhPub = ikDh.pub;
        PrimeE2ECrypto.KeyPair ikSign = PrimeE2ECrypto.generateEd25519(random);
        state.ikSignPriv = ikSign.priv;
        state.ikSignPub = ikSign.pub;
        rotateSpk(state, random);
        state.nextOpkIndex = 0;
        replenishOpks(state, random);
        return state;
    }

    private static void maybeRotateSpk(State state) {
        if (state.spkPriv == null || System.currentTimeMillis() - state.spkCreatedAt > SPK_ROTATION_INTERVAL_MS) {
            rotateSpk(state, new SecureRandom());
        }
    }

    private static void rotateSpk(State state, SecureRandom random) {
        PrimeE2ECrypto.KeyPair spk = PrimeE2ECrypto.generateX25519(random);
        state.spkPriv = spk.priv;
        state.spkPub = spk.pub;
        state.spkSig = PrimeE2ECrypto.ed25519Sign(state.ikSignPriv, spk.pub);
        state.spkCreatedAt = System.currentTimeMillis();
        state.spkId = (state.spkId + 1) & 0xFFFF;
    }

    private static void maybeReplenishOpks(State state) {
        int unconsumed = 0;
        for (OneTimePrekey opk : state.opks) {
            if (!opk.consumed) {
                unconsumed++;
            }
        }
        if (unconsumed < OPK_REPLENISH_THRESHOLD) {
            replenishOpks(state, new SecureRandom());
        }
        // Consumed entries older than one full batch back are pruned so this list
        // doesn't grow without bound over the life of the account.
        while (state.opks.size() > OPK_BATCH_SIZE * 2) {
            state.opks.remove(0);
        }
    }

    private static void replenishOpks(State state, SecureRandom random) {
        for (int i = 0; i < OPK_BATCH_SIZE; i++) {
            PrimeE2ECrypto.KeyPair opk = PrimeE2ECrypto.generateX25519(random);
            state.opks.add(new OneTimePrekey(state.nextOpkIndex++, opk.priv, opk.pub, false));
        }
    }

    private static PublicBundle toPublicBundle(State state) {
        List<byte[]> pubs = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (OneTimePrekey opk : state.opks) {
            if (!opk.consumed) {
                pubs.add(opk.pub);
                indices.add(opk.index);
            }
        }
        return new PublicBundle(state.ikDhPub, state.ikSignPub, state.spkPub, state.spkSig, state.spkId, pubs, indices);
    }

    private static void persist(State state) {
        try {
            persistWithKey(state, PrimeE2EKeyStore.getOrCreate().key);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void persistWithKey(State state, javax.crypto.SecretKey key) {
        try {
            sealedFile().write(serialize(state), key);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static byte[] serialize(State state) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(INNER_MAGIC);
        out.writeInt(INNER_VERSION);
        PrimeE2EIo.writeBytes(out, state.ikDhPriv);
        PrimeE2EIo.writeBytes(out, state.ikDhPub);
        PrimeE2EIo.writeBytes(out, state.ikSignPriv);
        PrimeE2EIo.writeBytes(out, state.ikSignPub);
        PrimeE2EIo.writeBytes(out, state.spkPriv);
        PrimeE2EIo.writeBytes(out, state.spkPub);
        PrimeE2EIo.writeBytes(out, state.spkSig);
        out.writeLong(state.spkCreatedAt);
        out.writeInt(state.spkId);
        out.writeInt(state.nextOpkIndex);
        out.writeInt(state.opks.size());
        for (OneTimePrekey opk : state.opks) {
            out.writeInt(opk.index);
            PrimeE2EIo.writeBytes(out, opk.priv);
            PrimeE2EIo.writeBytes(out, opk.pub);
            out.writeBoolean(opk.consumed);
        }
        return bytes.toByteArray();
    }

    private static State deserialize(byte[] plain) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain));
            if (in.readInt() != INNER_MAGIC || in.readInt() != INNER_VERSION) {
                return null;
            }
            State state = new State();
            state.ikDhPriv = PrimeE2EIo.readBytes(in);
            state.ikDhPub = PrimeE2EIo.readBytes(in);
            state.ikSignPriv = PrimeE2EIo.readBytes(in);
            state.ikSignPub = PrimeE2EIo.readBytes(in);
            state.spkPriv = PrimeE2EIo.readBytes(in);
            state.spkPub = PrimeE2EIo.readBytes(in);
            state.spkSig = PrimeE2EIo.readBytes(in);
            state.spkCreatedAt = in.readLong();
            state.spkId = in.readInt();
            state.nextOpkIndex = in.readInt();
            int count = in.readInt();
            if (count < 0 || count > OPK_BATCH_SIZE * 4) {
                return null;
            }
            for (int i = 0; i < count; i++) {
                int index = in.readInt();
                byte[] priv = PrimeE2EIo.readBytes(in);
                byte[] pub = PrimeE2EIo.readBytes(in);
                boolean consumed = in.readBoolean();
                state.opks.add(new OneTimePrekey(index, priv, pub, consumed));
            }
            return state;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}
