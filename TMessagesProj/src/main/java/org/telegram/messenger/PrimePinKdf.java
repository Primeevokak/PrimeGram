package org.telegram.messenger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * PrimeGram: PIN verifier derivation for {@link PrimePinVault}.
 *
 * <p>PBKDF2-HMAC-SHA256 derives a key from the PIN + salt, then that key is used as an
 * HMAC-SHA256 key over a fixed domain string - the stored "verifier" is that HMAC output,
 * never the derived key itself, so a leaked verifier can't be used directly as a
 * decryption key elsewhere. {@code char[]} is used throughout instead of {@link String} so
 * the PIN is never interned or left behind in string-pool memory; callers must wipe every
 * array this class touches when done ({@link PrimeSecretWiper}).
 */
public final class PrimePinKdf {

    public static final int ITERATIONS = 600_000;
    public static final int SALT_BYTES = 32;
    public static final int VERIFIER_BYTES = 32;
    private static final int DERIVED_KEY_BITS = 256;
    private static final String VERIFIER_DOMAIN = "PrimeGram PIN verifier v1";

    private PrimePinKdf() {
    }

    /** Refuses to derive below {@link #ITERATIONS} - a caller reading an old/tampered
     *  record with a lower iteration count must not silently accept the weaker security. */
    public static byte[] createVerifier(char[] pin, byte[] salt, int iterations) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (iterations < ITERATIONS) {
            throw new IllegalArgumentException("iterations below security floor");
        }
        byte[] derived = null;
        PBEKeySpec spec = new PBEKeySpec(pin, salt, iterations, DERIVED_KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            derived = factory.generateSecret(spec).getEncoded();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derived, "HmacSHA256"));
            return mac.doFinal(VERIFIER_DOMAIN.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (java.security.InvalidKeyException e) {
            throw new RuntimeException(e);
        } finally {
            spec.clearPassword();
            if (derived != null) {
                PrimeSecretWiper.wipe(derived);
            }
        }
    }

    /** Constant-time compare against a freshly-recomputed verifier. */
    public static boolean verify(char[] pin, byte[] salt, int iterations, byte[] expectedVerifier) {
        byte[] recomputed = null;
        try {
            recomputed = createVerifier(pin, salt, iterations);
            return MessageDigest.isEqual(recomputed, expectedVerifier);
        } catch (Exception e) {
            return false;
        } finally {
            if (recomputed != null) {
                PrimeSecretWiper.wipe(recomputed);
            }
        }
    }

    public static byte[] randomSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new java.security.SecureRandom().nextBytes(salt);
        return salt;
    }

    public static boolean constantTimeEquals(char[] a, char[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
