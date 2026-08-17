package org.telegram.messenger;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;

import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

/**
 * PrimeGram E2E: the Android Keystore-backed AES-256-GCM key that seals the identity
 * vault ({@link PrimeE2EIdentity}) and session store ({@link PrimeE2EVault}). Same
 * StrongBox-first/TEE-fallback shape as {@link PrimePinKeyStore} (a proven, already
 * production pattern in this fork) under its own alias - a separate key so deleting one
 * feature's Keystore entry (e.g. disabling the PIN gate) can never affect the other's
 * sealed state.
 */
final class PrimeE2EKeyStore {

    static final String KEY_ALIAS = "primegram.e2e.identity.v1";
    private static final String PROVIDER = "AndroidKeyStore";

    enum ProtectionLevel {
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

    private PrimeE2EKeyStore() {
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

    /** Creates the key if missing, tries StrongBox first on API 28+, falls back to TEE -
     *  same fallback shape as {@link PrimePinKeyStore#getOrCreate()}, including treating
     *  any StrongBox failure (not just the documented {@link StrongBoxUnavailableException})
     *  as "fall back to TEE," since real StrongBox HALs routinely throw a plain
     *  ProviderException instead. */
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

    static ProtectionLevel inspectSecurityLevel(SecretKey key) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(key.getAlgorithm(), PROVIDER);
            KeyInfo info = (KeyInfo) factory.getKeySpec(key, KeyInfo.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                switch (info.getSecurityLevel()) {
                    case KeyProperties.SECURITY_LEVEL_STRONGBOX:
                        return ProtectionLevel.STRONGBOX;
                    case KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                        return ProtectionLevel.TRUSTED_ENVIRONMENT;
                    case KeyProperties.SECURITY_LEVEL_SOFTWARE:
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
