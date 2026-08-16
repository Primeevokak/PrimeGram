package org.telegram.messenger;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

/**
 * PrimeGram: the Android Keystore-backed AES-256-GCM key that seals {@link PrimePinVault}'s
 * state file. StrongBox (a separate secure chip, where the device has one) is tried first,
 * falling back to the TEE - either way the raw key material never enters process memory,
 * only wrap/unwrap operations cross that boundary.
 */
public final class PrimePinKeyStore {

    static final String KEY_ALIAS = "primegram.pin.state.v1";
    private static final String PROVIDER = "AndroidKeyStore";

    public enum ProtectionLevel {
        STRONGBOX(4, true),
        TRUSTED_ENVIRONMENT(3, true),
        UNKNOWN_SECURE_HARDWARE(2, true),
        SOFTWARE(1, false),
        UNKNOWN(0, false);

        final int storageId;
        final boolean secure;

        ProtectionLevel(int storageId, boolean secure) {
            this.storageId = storageId;
            this.secure = secure;
        }

        static ProtectionLevel fromStorageId(int id) {
            for (ProtectionLevel level : values()) {
                if (level.storageId == id) {
                    return level;
                }
            }
            return UNKNOWN;
        }
    }

    static final class KeyMaterial {
        final SecretKey key;
        final ProtectionLevel level;

        KeyMaterial(SecretKey key, ProtectionLevel level) {
            this.key = key;
            this.level = level;
        }
    }

    private PrimePinKeyStore() {
    }

    static boolean exists() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        return ks.containsAlias(KEY_ALIAS);
    }

    static void deleteQuietly() {
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception ignored) {
        }
    }

    /** Creates the key if missing, tries StrongBox first on API 28+, falls back to TEE. */
    static KeyMaterial getOrCreate() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            SecretKey existing = (SecretKey) ks.getKey(KEY_ALIAS, null);
            return new KeyMaterial(existing, inspectSecurityLevel(existing));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                SecretKey key = generate(true);
                return new KeyMaterial(key, inspectSecurityLevel(key));
            } catch (StrongBoxUnavailableException ignored) {
                deleteQuietly();
            } catch (Exception e) {
                // Real devices: a StrongBox HAL that's present but broken/misconfigured routinely
                // throws a plain ProviderException (or another undocumented subclass) instead of
                // the one type this API contract promises, StrongBoxUnavailableException - caught
                // narrowly above, this device's actual failure fell straight through it and killed
                // enrollment outright ("что-то пошло не так") rather than falling back to the TEE
                // like it was supposed to. Any failure at this stage means "StrongBox didn't work
                // on this device," full stop - the TEE fallback below is what StrongBoxUnavailable
                // was already routing to, so widening the catch just makes that same fallback
                // reachable for every real-world failure shape, not only the documented one.
                FileLog.e(e);
                deleteQuietly();
            }
        }
        SecretKey key = generate(false);
        return new KeyMaterial(key, inspectSecurityLevel(key));
    }

    static SecretKey load() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }

    private static SecretKey generate(boolean strongBox) throws NoSuchAlgorithmException, NoSuchProviderException,
            java.security.InvalidAlgorithmParameterException {
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false);
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true);
        }
        generator.init(builder.build());
        return generator.generateKey();
    }

    /** Re-checked on every read, not just at creation - a protection level that changed
     *  since the key was made (OS update, chip swap) is treated as corruption upstream. */
    static ProtectionLevel inspectSecurityLevel(SecretKey key) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(key.getAlgorithm(), PROVIDER);
            KeyInfo info = (KeyInfo) factory.getKeySpec(key, KeyInfo.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                switch (info.getSecurityLevel()) {
                    case android.security.keystore.KeyProperties.SECURITY_LEVEL_STRONGBOX:
                        return ProtectionLevel.STRONGBOX;
                    case android.security.keystore.KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                        return ProtectionLevel.TRUSTED_ENVIRONMENT;
                    case android.security.keystore.KeyProperties.SECURITY_LEVEL_SOFTWARE:
                        return ProtectionLevel.SOFTWARE;
                    default:
                        return info.isInsideSecureHardware() ? ProtectionLevel.UNKNOWN_SECURE_HARDWARE : ProtectionLevel.UNKNOWN;
                }
            }
            return info.isInsideSecureHardware() ? ProtectionLevel.UNKNOWN_SECURE_HARDWARE : ProtectionLevel.SOFTWARE;
        } catch (Exception e) {
            return ProtectionLevel.UNKNOWN;
        }
    }
}
