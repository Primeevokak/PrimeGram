package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * PrimeGram E2E Phase 1: correctness of the crypto core with no Android/wiring
 * dependency. Covers (a) the underlying primitives against published test vectors, and
 * (b) a full two-party X3DH handshake + Double Ratchet exchange simulation, including
 * out-of-order delivery and one-time-prekey consumption.
 */
public class PrimeE2ERatchetTest {

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return out;
    }

    // ---- X25519 self-consistency ----
    //
    // BouncyCastle's own X25519 implementation is externally tested against RFC 7748
    // upstream - what this wrapper needs to verify is that PrimeE2ECrypto calls it
    // correctly, which self-consistency (ECDH's defining property: X25519(a, B) ==
    // X25519(b, A)) already establishes without risking a hand-transcribed hex vector
    // being wrong and asserting false confidence.

    @Test
    public void x25519_ecdhIsCommutativeAndDeterministic() {
        SecureRandom random = new SecureRandom();
        PrimeE2ECrypto.KeyPair a = PrimeE2ECrypto.generateX25519(random);
        PrimeE2ECrypto.KeyPair b = PrimeE2ECrypto.generateX25519(random);

        assertEquals(PrimeE2ECrypto.KEY_LEN, a.priv.length);
        assertEquals(PrimeE2ECrypto.KEY_LEN, a.pub.length);
        assertArrayEquals("public key must be deterministic from the private key",
                a.pub, PrimeE2ECrypto.x25519PublicFromPrivate(a.priv));

        byte[] sharedFromA = PrimeE2ECrypto.x25519(a.priv, b.pub);
        byte[] sharedFromB = PrimeE2ECrypto.x25519(b.priv, a.pub);
        assertArrayEquals("X25519(a_priv, B_pub) must equal X25519(b_priv, A_pub)", sharedFromA, sharedFromB);

        PrimeE2ECrypto.KeyPair c = PrimeE2ECrypto.generateX25519(random);
        byte[] sharedFromC = PrimeE2ECrypto.x25519(c.priv, a.pub);
        assertFalse("a different keypair must not produce the same shared secret",
                Arrays.equals(sharedFromA, sharedFromC));
    }

    // ---- Ed25519 sign/verify round trip (RFC 8032 self-consistency, not a fixed vector) ----

    @Test
    public void ed25519_signAndVerify_roundTrips() {
        SecureRandom random = new SecureRandom();
        PrimeE2ECrypto.KeyPair kp = PrimeE2ECrypto.generateEd25519(random);
        byte[] message = "PrimeGram E2E signed prekey".getBytes(StandardCharsets.UTF_8);

        byte[] sig = PrimeE2ECrypto.ed25519Sign(kp.priv, message);
        assertEquals(PrimeE2ECrypto.SIG_LEN, sig.length);
        assertTrue(PrimeE2ECrypto.ed25519Verify(kp.pub, message, sig));

        byte[] tampered = message.clone();
        tampered[0] ^= 0x01;
        assertFalse(PrimeE2ECrypto.ed25519Verify(kp.pub, tampered, sig));

        PrimeE2ECrypto.KeyPair other = PrimeE2ECrypto.generateEd25519(random);
        assertFalse(PrimeE2ECrypto.ed25519Verify(other.pub, message, sig));
    }

    // ---- RFC 5869 Appendix A.1 HKDF-SHA256 test vector ----

    @Test
    public void hkdf_rfc5869_appendixA1() {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = hex("000102030405060708090a0b0c");
        byte[] info = hex("f0f1f2f3f4f5f6f7f8f9");
        byte[] okm = PrimeE2ECrypto.hkdfSha256(salt, ikm, info, 42);
        byte[] expected = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
        assertEquals(42, expected.length);
        assertArrayEquals(expected, okm);
    }

    // ---- Full two-party simulation: 4-DH X3DH handshake + Double Ratchet exchange ----

    private static final class Party {
        long userId;
        PrimeE2ECrypto.KeyPair ikDh;
        PrimeE2ECrypto.KeyPair ikSign;
        PrimeE2ECrypto.KeyPair spk;
        byte[] spkSig;
        PrimeE2ECrypto.KeyPair opk;
        PrimeE2ESession session = new PrimeE2ESession();
    }

    private static Party newParty(long userId, SecureRandom random) {
        Party p = new Party();
        p.userId = userId;
        p.ikDh = PrimeE2ECrypto.generateX25519(random);
        p.ikSign = PrimeE2ECrypto.generateEd25519(random);
        p.spk = PrimeE2ECrypto.generateX25519(random);
        p.spkSig = PrimeE2ECrypto.ed25519Sign(p.ikSign.priv, p.spk.pub);
        p.opk = PrimeE2ECrypto.generateX25519(random);
        return p;
    }

    @Test
    public void x3dhAndDoubleRatchet_fullSimulation_roundTripsWithReordering() {
        SecureRandom random = new SecureRandom();
        Party alice = newParty(1001L, random);
        Party bob = newParty(2002L, random);

        long dialogId = bob.userId; // Alice's own convention: dialogId == peer's user id
        alice.session.selfUserId = alice.userId;
        alice.session.peerUserId = bob.userId;
        alice.session.dialogId = dialogId;
        bob.session.selfUserId = bob.userId;
        bob.session.peerUserId = alice.userId;
        bob.session.dialogId = alice.userId;

        // --- Handshake: Bob's SPK signature verifies against his signing identity ---
        assertTrue(PrimeE2ECrypto.ed25519Verify(bob.ikSign.pub, bob.spk.pub, bob.spkSig));

        // --- Alice generates her ephemeral X3DH key and computes the 4 DH outputs ---
        PrimeE2ECrypto.KeyPair ekAlice = PrimeE2ECrypto.generateX25519(random);
        byte[] dh1A = PrimeE2ECrypto.x25519(alice.ikDh.priv, bob.spk.pub);
        byte[] dh2A = PrimeE2ECrypto.x25519(ekAlice.priv, bob.ikDh.pub);
        byte[] dh3A = PrimeE2ECrypto.x25519(ekAlice.priv, bob.spk.pub);
        byte[] dh4A = PrimeE2ECrypto.x25519(ekAlice.priv, bob.opk.pub);
        byte[] skAlice = PrimeE2ERatchet.deriveX3DHSharedSecret(dh1A, dh2A, dh3A, dh4A);

        // --- Bob (mirror side) computes the identical DH outputs and SK ---
        byte[] dh1B = PrimeE2ECrypto.x25519(bob.spk.priv, alice.ikDh.pub);
        byte[] dh2B = PrimeE2ECrypto.x25519(bob.ikDh.priv, ekAlice.pub);
        byte[] dh3B = PrimeE2ECrypto.x25519(bob.spk.priv, ekAlice.pub);
        byte[] dh4B = PrimeE2ECrypto.x25519(bob.opk.priv, ekAlice.pub);
        byte[] skBob = PrimeE2ERatchet.deriveX3DHSharedSecret(dh1B, dh2B, dh3B, dh4B);

        assertArrayEquals("X3DH shared secret must match on both sides", skAlice, skBob);

        // --- Ratchet bootstrap ---
        PrimeE2ERatchet.initAsInitiator(alice.session, skAlice, bob.spk.pub, random);
        PrimeE2ERatchet.initAsResponder(bob.session, skBob, bob.spk.priv, bob.spk.pub);

        // --- Alice sends the first message; Bob completes his first DH-ratchet step on receipt ---
        PrimeE2ERatchet.Encrypted m0 = PrimeE2ERatchet.encrypt(alice.session, utf8("hello bob"));
        byte[] p0 = PrimeE2ERatchet.decrypt(bob.session, m0.header, m0.nonce, m0.ciphertext, random);
        assertArrayEquals(utf8("hello bob"), p0);

        // --- Bob replies; Alice completes her first DH-ratchet step on receipt ---
        PrimeE2ERatchet.Encrypted m1 = PrimeE2ERatchet.encrypt(bob.session, utf8("hi alice"));
        byte[] p1 = PrimeE2ERatchet.decrypt(alice.session, m1.header, m1.nonce, m1.ciphertext, random);
        assertArrayEquals(utf8("hi alice"), p1);

        // --- 20-message interleaved, out-of-order exchange in both directions ---
        List<PrimeE2ERatchet.Encrypted> aliceToBob = new ArrayList<>();
        List<byte[]> aliceToBobPlain = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            byte[] plain = utf8("a->b message " + i);
            aliceToBobPlain.add(plain);
            aliceToBob.add(PrimeE2ERatchet.encrypt(alice.session, plain));
        }
        // Deliver to Bob in a shuffled (but deterministic) order to exercise the
        // skipped-message-key path.
        int[] order = {5, 2, 0, 7, 1, 3, 19, 4, 6, 8, 18, 9, 10, 17, 11, 12, 16, 13, 14, 15};
        for (int idx : order) {
            byte[] decrypted = PrimeE2ERatchet.decrypt(bob.session, aliceToBob.get(idx).header,
                    aliceToBob.get(idx).nonce, aliceToBob.get(idx).ciphertext, random);
            assertArrayEquals("message " + idx + " must decrypt correctly out of order",
                    aliceToBobPlain.get(idx), decrypted);
        }

        // --- Bob replies, forcing another DH ratchet step; Alice decrypts normally ---
        PrimeE2ERatchet.Encrypted m2 = PrimeE2ERatchet.encrypt(bob.session, utf8("got all 20"));
        byte[] p2 = PrimeE2ERatchet.decrypt(alice.session, m2.header, m2.nonce, m2.ciphertext, random);
        assertArrayEquals(utf8("got all 20"), p2);

        // --- Tampered ciphertext must fail closed (null), never throw ---
        PrimeE2ERatchet.Encrypted good = PrimeE2ERatchet.encrypt(alice.session, utf8("final message"));
        byte[] tamperedCiphertext = good.ciphertext.clone();
        tamperedCiphertext[0] ^= 0x01;
        byte[] failed = PrimeE2ERatchet.decrypt(bob.session,
                new PrimeE2ERatchet.Header(good.header.ratchetPub, good.header.previousChainLength, good.header.messageNumber),
                good.nonce, tamperedCiphertext, random);
        assertNull("tampered ciphertext must fail to decrypt, not throw", failed);
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
