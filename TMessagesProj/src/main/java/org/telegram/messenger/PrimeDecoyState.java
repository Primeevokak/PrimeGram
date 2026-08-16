package org.telegram.messenger;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * PrimeGram: the marker that says "this device is showing the decoy, not the real account."
 *
 * <p>Deliberately plaintext (Base64 in a flat file, not sealed under the PIN Keystore key like
 * {@link PrimePinVault}): it has to be readable at the very first moment of a cold start, before
 * any Keystore/unlock ceremony has happened, so {@link ApplicationLoader} can decide whether to
 * even attempt a real network/account init this launch. This is a known, intentional trade - the
 * marker's mere existence and its seed are metadata a sophisticated attacker could find, exactly
 * as NovaGram's own docs say about the same design. The thing it protects (the real account data)
 * is not in this file and is not recoverable from it; the marker only ever says "render the
 * deterministic decoy," nothing more.
 *
 * <p>Lives in the no-backup files dir, same as {@link PrimePinVault}, so it never round-trips
 * through Android's app-data backup - a restored backup silently reactivating a decoy on a
 * different device would be a strange and bad surprise.
 */
public final class PrimeDecoyState {

    private static final String FILE_NAME = "primegram/security/decoy_marker.b64";

    private static volatile Boolean cachedActive;
    private static volatile long cachedSeed;

    private PrimeDecoyState() {
    }

    private static File file(Context context) {
        return new File(context.getNoBackupFilesDir(), FILE_NAME);
    }

    public static boolean isActive() {
        Boolean cached = cachedActive;
        if (cached != null) {
            return cached;
        }
        return isActive(ApplicationLoader.applicationContext);
    }

    public static boolean isActive(Context context) {
        if (context == null) {
            return false;
        }
        long seed = readSeed(context);
        boolean active = seed != Long.MIN_VALUE;
        cachedActive = active;
        cachedSeed = active ? seed : 0L;
        return active;
    }

    /** The seed the decoy persona was generated from - stable for as long as the marker exists,
     *  so repeated cold starts (and repeated getDialogs/getHistory calls within one run) always
     *  render the exact same fake chats rather than a new random set every time. */
    public static long seed() {
        if (cachedActive != null && cachedActive) {
            return cachedSeed;
        }
        Context context = ApplicationLoader.applicationContext;
        long seed = context != null ? readSeed(context) : Long.MIN_VALUE;
        cachedActive = seed != Long.MIN_VALUE;
        cachedSeed = cachedActive ? seed : 0L;
        return cachedSeed;
    }

    /** Writes the marker. Idempotent seed choice: callers that already have one (re-arming after
     *  a storage wipe touched this same file) pass it back in rather than getting a new persona. */
    public static void arm(Context context, long seed) {
        try {
            File f = file(context);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            String encoded = android.util.Base64.encodeToString(
                    Long.toString(seed).getBytes(StandardCharsets.US_ASCII), android.util.Base64.NO_WRAP);
            android.util.AtomicFile atomicFile = new android.util.AtomicFile(f);
            java.io.FileOutputStream out = atomicFile.startWrite();
            try {
                out.write(encoded.getBytes(StandardCharsets.US_ASCII));
                atomicFile.finishWrite(out);
            } catch (Exception e) {
                atomicFile.failWrite(out);
                throw e;
            }
            cachedActive = true;
            cachedSeed = seed;
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** A fresh, unpredictable seed for a brand-new decoy - SecureRandom here (unlike the persona
     *  generator itself, which must be deterministic FROM this seed), since this is the one
     *  moment where actual unpredictability matters. */
    public static long newSeed() {
        return new java.security.SecureRandom().nextLong();
    }

    private static long readSeed(Context context) {
        try {
            File f = file(context);
            if (!f.exists()) {
                return Long.MIN_VALUE;
            }
            byte[] raw = new android.util.AtomicFile(f).readFully();
            byte[] decoded = android.util.Base64.decode(raw, android.util.Base64.NO_WRAP);
            return Long.parseLong(new String(decoded, StandardCharsets.US_ASCII).trim());
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }
}
