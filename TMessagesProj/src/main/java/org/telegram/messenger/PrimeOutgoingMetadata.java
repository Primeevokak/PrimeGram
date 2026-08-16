package org.telegram.messenger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * PrimeGram: strips EXIF/GPS and other embedded metadata from an outgoing JPEG/PNG before it's
 * uploaded - byte-level marker/chunk surgery, never a re-encode, so the actual pixel data is
 * bit-identical to the source (no quality loss, no re-compression artifacts).
 *
 * <p>Writes a cleaned COPY under a temp cache subfolder; the caller's original file on disk is
 * never modified (a user who later wants their original, EXIF and all, back on their own device
 * still has it). Malformed or unrecognized input returns the ORIGINAL bytes unchanged - a photo
 * that fails to parse must still send, not disappear into a silent no-op or a broken file.
 */
public final class PrimeOutgoingMetadata {

    private static final String PREFS = "mainconfig";
    private static final String KEY_ENABLED = "primegram_strip_metadata";
    /** How long a cleaned temp copy is allowed to sit before a sweep can remove it - age-based,
     *  not count-based: count-based sweeping can delete a copy still mid-upload if enough other
     *  sends happened to push it off the end of a fixed-size list. */
    private static final long TEMP_MAX_AGE_MS = 6L * 60 * 60 * 1000;

    private PrimeOutgoingMetadata() {
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    private static File tempDir() {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "prime_clean_media");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Best-effort periodic cleanup of old temp copies - call opportunistically (e.g. from a
     *  send-preparation site), not on any fixed schedule of its own. */
    public static void sweepOldTempFiles() {
        try {
            File dir = tempDir();
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (File f : files) {
                if (now - f.lastModified() > TEMP_MAX_AGE_MS) {
                    f.delete();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * @return a cleaned copy's path, or {@code originalPath} unchanged if this is disabled, not a
     * JPEG/PNG, or cleaning failed/found nothing to change for any reason.
     */
    public static String cleanIfNeeded(String originalPath) {
        if (!isEnabled() || originalPath == null) {
            return originalPath;
        }
        try {
            File source = new File(originalPath);
            if (!source.exists() || source.length() == 0 || source.length() > 64L * 1024 * 1024) {
                return originalPath; // absurdly large "photo" is not something to buffer fully into memory here
            }
            byte[] data = readAll(source);
            byte[] cleaned;
            String ext;
            if (isJpeg(data)) {
                cleaned = stripJpeg(data);
                ext = ".jpg";
            } else if (isPng(data)) {
                cleaned = stripPng(data);
                ext = ".png";
            } else {
                return originalPath;
            }
            if (cleaned == null) {
                return originalPath; // parser bailed - safer to send the untouched original than guess
            }
            File dir = tempDir();
            File out = new File(dir, "clean_" + System.currentTimeMillis() + "_" + Math.abs(originalPath.hashCode()) + ext);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(cleaned);
            }
            sweepOldTempFiles();
            return out.getAbsolutePath();
        } catch (Throwable t) {
            FileLog.e(t);
            return originalPath;
        }
    }

    private static byte[] readAll(File f) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream((int) f.length());
        try (InputStream in = new FileInputStream(f)) {
            byte[] chunk = new byte[65536];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
        }
        return buf.toByteArray();
    }

    private static boolean isJpeg(byte[] d) {
        return d.length > 4 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
    }

    private static final byte[] PNG_SIG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static boolean isPng(byte[] d) {
        if (d.length < PNG_SIG.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIG.length; i++) {
            if (d[i] != PNG_SIG[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Walks JPEG markers up to (and including) SOS, dropping metadata-bearing APPn/COM segments
     * and passing everything else through untouched; from SOS onward the rest of the file
     * (entropy-coded scan data, any RSTn markers inside it, EOI) is copied verbatim rather than
     * reparsed - that region isn't where metadata lives, and reparsing it risks corrupting a
     * scan that happens to contain byte sequences resembling markers.
     */
    private static byte[] stripJpeg(byte[] d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(d.length);
            int pos = 0;
            out.write(d, 0, 2); // SOI
            pos = 2;
            while (pos + 1 < d.length) {
                if ((d[pos] & 0xFF) != 0xFF) {
                    return null; // not a marker where one was expected - bail, don't guess
                }
                int marker = d[pos + 1] & 0xFF;
                if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    out.write(d, pos, 2);
                    pos += 2;
                    continue;
                }
                if (marker == 0xD9) { // EOI with no scan - unusual, but pass through and stop
                    out.write(d, pos, 2);
                    return out.toByteArray();
                }
                if (pos + 3 >= d.length) {
                    return null;
                }
                int segLen = ((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF);
                if (segLen < 2 || pos + 2 + segLen > d.length) {
                    return null;
                }
                boolean keep;
                if (marker == 0xE0 /* APP0 JFIF */ || marker == 0xEE /* APP14 Adobe */) {
                    keep = true;
                } else if (marker == 0xE2 /* APP2 */) {
                    keep = hasAsciiPrefixAt(d, pos + 4, "ICC_PROFILE");
                } else if (marker >= 0xE1 && marker <= 0xEF /* other APPn */) {
                    keep = false;
                } else if (marker == 0xFE /* COM */) {
                    keep = false;
                } else {
                    keep = true; // SOF*, DHT, DQT, DRI, etc. - structural, not metadata
                }
                if (keep) {
                    out.write(d, pos, 2 + segLen);
                }
                pos += 2 + segLen;
                if (marker == 0xDA) { // SOS - always "keep" (falls into the structural else above),
                    // its header is already written; everything from here to EOF is scan data
                    // plus any RSTn markers and the final EOI - copied verbatim, not reparsed.
                    out.write(d, pos, d.length - pos);
                    return out.toByteArray();
                }
            }
            return null; // fell off the end without ever hitting SOS - not a structure worth guessing about
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasAsciiPrefixAt(byte[] d, int offset, String prefix) {
        if (offset < 0 || offset + prefix.length() > d.length) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (d[offset + i] != (byte) prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static final java.util.Set<String> DROP_PNG_CHUNKS = new java.util.HashSet<>(java.util.Arrays.asList(
            "tEXt", "zTXt", "iTXt", "tIME", "eXIf"));

    private static byte[] stripPng(byte[] d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(d.length);
            out.write(d, 0, PNG_SIG.length);
            int pos = PNG_SIG.length;
            boolean sawIend = false;
            while (pos + 8 <= d.length) {
                long len = ((long) (d[pos] & 0xFF) << 24) | ((d[pos + 1] & 0xFF) << 16)
                        | ((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF);
                if (len < 0 || len > Integer.MAX_VALUE - 12 || pos + 12 + len > d.length) {
                    return null;
                }
                String type = new String(d, pos + 4, 4, StandardCharsets.US_ASCII);
                int total = (int) (12 + len);
                if (!DROP_PNG_CHUNKS.contains(type)) {
                    out.write(d, pos, total);
                }
                if ("IEND".equals(type)) {
                    sawIend = true;
                    pos += total;
                    break;
                }
                pos += total;
            }
            if (!sawIend) {
                return null;
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
