package org.telegram.messenger;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * PrimeGram E2E: thin wrapper around BouncyCastle's lightweight crypto API (never
 * registered as a JCE {@link java.security.Provider}, so it can't conflict with Android's
 * own Conscrypt) plus {@code javax.crypto} primitives already used elsewhere in this fork
 * (e.g. {@link PrimePinVault}'s AES-GCM envelope). This class is pure math/crypto calls -
 * no Android dependency, no disk I/O, no session state - so it's directly unit-testable
 * against RFC 7748/RFC 8032/RFC 5869 vectors.
 */
public final class PrimeE2ECrypto {

    public static final int KEY_LEN = 32;
    public static final int SIG_LEN = 64;
    public static final int GCM_NONCE_LEN = 12;
    public static final int GCM_TAG_LEN = 16;

    private PrimeE2ECrypto() {
    }

    public static final class KeyPair {
        public final byte[] priv;
        public final byte[] pub;
        public KeyPair(byte[] priv, byte[] pub) {
            this.priv = priv;
            this.pub = pub;
        }
    }

    // ---- X25519 (Diffie-Hellman) ----

    public static KeyPair generateX25519(SecureRandom random) {
        X25519KeyPairGenerator gen = new X25519KeyPairGenerator();
        gen.init(new X25519KeyGenerationParameters(random));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        byte[] priv = ((X25519PrivateKeyParameters) pair.getPrivate()).getEncoded();
        byte[] pub = ((X25519PublicKeyParameters) pair.getPublic()).getEncoded();
        return new KeyPair(priv, pub);
    }

    public static byte[] x25519PublicFromPrivate(byte[] priv) {
        X25519PrivateKeyParameters p = new X25519PrivateKeyParameters(priv, 0);
        return p.generatePublicKey().getEncoded();
    }

    /** Raw X25519 scalar multiplication - the caller is responsible for HKDF'ing the
     *  result before using it as a symmetric key (a raw DH output must never be used
     *  directly as key material). */
    public static byte[] x25519(byte[] priv, byte[] pub) {
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(new X25519PrivateKeyParameters(priv, 0));
        byte[] out = new byte[agreement.getAgreementSize()];
        agreement.calculateAgreement(new X25519PublicKeyParameters(pub, 0), out, 0);
        return out;
    }

    // ---- Ed25519 (signing) ----

    public static KeyPair generateEd25519(SecureRandom random) {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(random));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        byte[] priv = ((Ed25519PrivateKeyParameters) pair.getPrivate()).getEncoded();
        byte[] pub = ((Ed25519PublicKeyParameters) pair.getPublic()).getEncoded();
        return new KeyPair(priv, pub);
    }

    public static byte[] ed25519Sign(byte[] priv, byte[] message) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, new Ed25519PrivateKeyParameters(priv, 0));
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }

    public static boolean ed25519Verify(byte[] pub, byte[] message, byte[] signature) {
        if (signature == null || signature.length != SIG_LEN) {
            return false;
        }
        try {
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(pub, 0));
            verifier.update(message, 0, message.length);
            return verifier.verifySignature(signature);
        } catch (RuntimeException e) {
            // Malformed public key material - never crash the caller (e.g. a corrupt/
            // malicious handshake message) over this, just fail closed.
            return false;
        }
    }

    // ---- HKDF-SHA256 (RFC 5869), for root-key/session-key derivation ----

    public static byte[] hkdfSha256(byte[] salt, byte[] ikm, byte[] info, int length) {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(ikm, salt, info));
        byte[] out = new byte[length];
        hkdf.generateBytes(out, 0, length);
        return out;
    }

    // ---- HMAC-SHA256, for the Double Ratchet's symmetric chain-key derivation ----

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is guaranteed present on every Android API level - this can't
            // actually happen outside of a broken device/provider, which nothing here
            // could recover from anyway.
            throw new IllegalStateException(e);
        }
    }

    // ---- AES-256-GCM, for per-message encryption (same primitive PrimePinVault uses) ----

    public static byte[] aesGcmEncrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key32, "AES"),
                    new GCMParameterSpec(GCM_TAG_LEN * 8, nonce12));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return null on authentication failure (wrong key / tampered ciphertext / wrong
     *  AAD) - callers must treat this as "cannot decrypt," never propagate the exception
     *  up through message-list rendering. */
    public static byte[] aesGcmDecrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key32, "AES"),
                    new GCMParameterSpec(GCM_TAG_LEN * 8, nonce12));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ciphertext);
        } catch (BadPaddingException e) {
            // AEADBadTagException (GCM auth-tag mismatch) is itself a BadPaddingException
            // subclass - this single catch covers both "wrong key/tampered ciphertext"
            // outcomes.
            return null;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
