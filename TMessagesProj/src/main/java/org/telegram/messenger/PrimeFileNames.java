package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * PrimeGram: replaces a downloaded document's original filename with a deterministic,
 * meaningless one on disk - "vacation_photo_final2.jpg" tells anyone who sees the file (a
 * gallery app, a backup tool, another user of a shared device) something about its content;
 * "3f9a1c2b8e7d0a5f.jpg" doesn't.
 *
 * <p>Deterministic (same original name -&gt; always the same output name), not random per call -
 * a partial-download resume looks the file up again by its final name, so a fresh random name on
 * a retry would orphan the partially-written file. The salt is what keeps it from being a plain,
 * reversible hash: without it, anyone could brute-force common filenames against the hash to
 * confirm a specific file was downloaded; with a per-install random salt they can't, even knowing
 * this algorithm.
 *
 * <p>Does NOT touch {@link MediaController}'s "save to gallery" / explicit "save as" path - a
 * user who explicitly chooses where a file goes and what it's called gets exactly that; this
 * only affects the app's own internal on-disk copy for a document with a real filename.
 */
public final class PrimeFileNames {

    private static final String PREFS = "mainconfig";
    private static final String KEY_ENABLED = "primegram_anon_filenames";
    private static final String KEY_SALT = "primegram_filename_salt";

    private static volatile byte[] cachedSalt;

    private PrimeFileNames() {
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** {@code SHA-256(salt || originalName)}, first 8 bytes as 16 lowercase hex chars, plus the
     *  original extension (needed for correct MIME/app-open behavior later - the hash covers the
     *  meaningful part, the base name, not the file type). Falls back to the original name on
     *  any failure - a slightly-less-private filename beats a failed download. */
    public static String anonymize(String originalName) {
        if (originalName == null || originalName.isEmpty()) {
            return originalName;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(getOrCreateSalt());
            digest.update(originalName.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            String ext = "";
            int dot = originalName.lastIndexOf('.');
            // Sane extension only - not e.g. a dotted name with no real extension, and not an
            // absurdly long "extension" that's actually just text with a period in it.
            if (dot > 0 && dot < originalName.length() - 1 && originalName.length() - dot <= 12) {
                ext = originalName.substring(dot).toLowerCase(java.util.Locale.ROOT);
            }
            return hex + ext;
        } catch (Exception e) {
            FileLog.e(e);
            return originalName;
        }
    }

    private static byte[] getOrCreateSalt() {
        byte[] local = cachedSalt;
        if (local != null) {
            return local;
        }
        synchronized (PrimeFileNames.class) {
            if (cachedSalt != null) {
                return cachedSalt;
            }
            SharedPreferences p = prefs();
            String existing = p.getString(KEY_SALT, null);
            byte[] result;
            if (existing != null) {
                try {
                    result = Base64.decode(existing, Base64.NO_WRAP);
                } catch (Exception e) {
                    result = null;
                }
            } else {
                result = null;
            }
            if (result == null || result.length != 32) {
                result = new byte[32];
                new SecureRandom().nextBytes(result);
                p.edit().putString(KEY_SALT, Base64.encodeToString(result, Base64.NO_WRAP)).apply();
            }
            cachedSalt = result;
            return result;
        }
    }
}
