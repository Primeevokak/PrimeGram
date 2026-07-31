package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrimeGram: sending a file larger than Telegram will accept, as several files that a PrimeGram on
 * the other side puts back together.
 *
 * <p>This class is the format and the arithmetic - what a chunk is called, how many there are, how
 * big each one is. The sending and the reassembly live elsewhere; keeping the naming in one place
 * means both ends read the same rules from the same file, and a desktop client can be written
 * against this description alone.
 *
 * <h3>The name</h3>
 *
 * <pre>[PG1 a7f3k2c9 03-17 7947285432] Название файла.mkv</pre>
 *
 * <p>Every chunk carries the whole header, not just the first. The first-chunk-only arrangement
 * looks tempting - it is fewer bytes - and it fails in the ordinary case: someone who opens the
 * chat halfway through the upload, or scrolls into the middle of it later, holds parts that
 * reference an alias they have never seen. With the header repeated, any single chunk is enough to
 * know what the file is called, how big it will be, and how much is still coming, which is exactly
 * what the interface needs in order to show one file instead of seventeen.
 *
 * <p>The total size is in there for the same reason: without it a receiver can say "3 of 17" but
 * not "18%", and the sizes of individual chunks deliberately vary, so it cannot be inferred.
 *
 * <p>Sizes of individual chunks are <em>not</em> in the header. They do not need to be - a chunk's
 * size is a property of the file that arrived - and a number that can disagree with reality is a
 * number that eventually will.
 */
public final class PrimeBigFile {

    /** Format marker. Bumped only if the header's shape changes, so old clients can refuse. */
    public static final String MARKER = "PG1";

    private static final String KEY_ENABLED = "primegram_bigfile_enabled";
    private static final String KEY_EXPERIMENTAL = "primegram_bigfile_experimental";

    /** Telegram accepts 2 GB from everyone and 4 GB with Premium; we stay just under both. */
    public static final long CHUNK_FREE = 1990L * 1024 * 1024;
    public static final long CHUNK_PREMIUM = 3990L * 1024 * 1024;

    /** The ordinary ceiling, and the one behind the experimental switch. */
    public static final long MAX_NORMAL = 8L * 1024 * 1024 * 1024;
    public static final long MAX_EXPERIMENTAL = 50L * 1024 * 1024 * 1024;

    /** How much is shaved off a chunk so that no two are the same size. */
    private static final long JITTER_MIN = 10L * 1024 * 1024;
    private static final long JITTER_MAX = 70L * 1024 * 1024;

    /** Pause between chunks, so a set of parts looks like someone sending several files. */
    public static final int DELAY_MIN_MS = 5000;
    public static final int DELAY_MAX_MS = 15000;

    private static final Random RANDOM = new Random();

    private static final Pattern HEADER = Pattern.compile(
            "^\\[" + MARKER + " ([A-Za-z0-9]{4,16}) (\\d+)-(\\d+) (\\d+)M\\] (.+)$");

    /**
     * The total size travels in whole megabytes, not bytes.
     *
     * <p>Six characters shorter in every name, and nothing needs the exact figure: a transfer is
     * complete when every part has arrived, counted, not weighed. The number exists so the
     * receiver can draw a percentage before the last part lands, and a percentage does not care
     * about the final megabyte.
     */
    private static final long MB = 1024 * 1024;

    private PrimeBigFile() {
    }

    // region settings

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    /**
     * Whether this device may <em>send</em> oversized files. Off until asked for.
     *
     * <p>Receiving is never gated: a file arriving in parts is something that happened to the
     * user, not something they chose, and refusing to reassemble it would only mean they see
     * seventeen mysterious fragments instead of their file.
     */
    public static boolean isSendingEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setSendingEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isExperimentalEnabled() {
        return prefs().getBoolean(KEY_EXPERIMENTAL, false);
    }

    public static void setExperimentalEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_EXPERIMENTAL, enabled).apply();
    }

    public static long maxSendableSize() {
        return isExperimentalEnabled() ? MAX_EXPERIMENTAL : MAX_NORMAL;
    }

    /**
     * The chunk ceiling for this account.
     *
     * <p>Real Premium, not the local one. The limit being worked around here is the server's, and
     * the server is not consulting our settings screen - offering 4 GB chunks on an account that
     * cannot upload them would produce a transfer that fails on its first part.
     */
    public static long chunkLimitFor(int account) {
        try {
            return UserConfig.getInstance(account).hasRealPremium() ? CHUNK_PREMIUM : CHUNK_FREE;
        } catch (Throwable e) {
            return CHUNK_FREE;
        }
    }

    // endregion

    // region planning

    /** A file split into parts, worked out before anything is sent. */
    public static final class Plan {
        public final String alias;
        public final String fileName;
        public final long totalSize;
        /** Byte offsets and lengths, in order. */
        public final List<long[]> parts;

        Plan(String alias, String fileName, long totalSize, List<long[]> parts) {
            this.alias = alias;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.parts = parts;
        }

        public int count() {
            return parts.size();
        }

        public String nameFor(int index) {
            return header(alias, index + 1, parts.size(), totalSize, fileName);
        }
    }

    /**
     * Works out the whole split in advance.
     *
     * <p>In advance because the header of the very first chunk has to say how many there will be,
     * and because the sender's interface promises a percentage from the start.
     *
     * <p>Each chunk but the last takes the limit minus a random 10-70 MB, so no two parts are the
     * same size. The remainder simply falls into the last chunk - the shaved bytes do not need to
     * be moved anywhere, they arrive there by arithmetic.
     */
    public static Plan plan(String fileName, long totalSize, long chunkLimit) {
        final List<long[]> parts = new ArrayList<>();
        long offset = 0;
        while (offset < totalSize) {
            final long remaining = totalSize - offset;
            long length;
            if (remaining <= chunkLimit) {
                length = remaining;
            } else {
                final long jitter = JITTER_MIN + (long) (RANDOM.nextDouble() * (JITTER_MAX - JITTER_MIN));
                length = chunkLimit - jitter;
                if (length <= 0 || length >= remaining) {
                    length = Math.min(chunkLimit, remaining);
                }
            }
            parts.add(new long[]{offset, length});
            offset += length;
        }
        if (parts.isEmpty()) {
            parts.add(new long[]{0, 0});
        }
        return new Plan(newAlias(), fileName, totalSize, parts);
    }

    /** How long to wait before the next chunk. */
    public static int nextDelayMs() {
        return DELAY_MIN_MS + RANDOM.nextInt(DELAY_MAX_MS - DELAY_MIN_MS + 1);
    }

    /**
     * A token identifying one transfer.
     *
     * <p>Two files being sent at once - from two devices of the same account, even - must not have
     * their parts confused for each other, and nothing else in the message carries that
     * distinction.
     */
    public static String newAlias() {
        final StringBuilder builder = new StringBuilder(8);
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 8; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    // endregion

    // region the header

    /** Android caps a file name at 255 bytes, and the header has to fit inside that with it. */
    private static final int MAX_NAME_BYTES = 250;

    public static String header(String alias, int index, int total, long totalSize, String fileName) {
        final int width = String.valueOf(total).length();
        final long megabytes = Math.max(1, (totalSize + MB - 1) / MB);
        final String prefix = String.format(Locale.US, "[%s %s %0" + width + "d-%d %dM] ",
                MARKER, alias, index, total, megabytes);
        String name = fileName == null ? "file" : fileName;
        // Trimmed from the middle of the base name, never from the extension: the extension is
        // what every player and file manager reads to decide what the file is.
        final int budget = MAX_NAME_BYTES - prefix.getBytes().length;
        if (name.getBytes().length > budget) {
            final int dot = name.lastIndexOf('.');
            final String extension = dot > 0 ? name.substring(dot) : "";
            final int keep = Math.max(4, budget - extension.getBytes().length - 1);
            String base = dot > 0 ? name.substring(0, dot) : name;
            while (base.getBytes().length > keep && base.length() > 1) {
                base = base.substring(0, base.length() - 1);
            }
            name = base + extension;
        }
        return prefix + name;
    }

    /** What a chunk's name says about it, or null when the name is not one of ours. */
    public static final class Header {
        public final String alias;
        public final int index;
        public final int total;
        public final long totalSize;
        public final String fileName;

        Header(String alias, int index, int total, long totalSize, String fileName) {
            this.alias = alias;
            this.index = index;
            this.total = total;
            this.totalSize = totalSize;
            this.fileName = fileName;
        }
    }

    public static Header parse(String chunkName) {
        if (chunkName == null || !chunkName.startsWith("[" + MARKER + " ")) {
            return null;
        }
        final Matcher matcher = HEADER.matcher(chunkName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            final int index = Integer.parseInt(matcher.group(2));
            final int total = Integer.parseInt(matcher.group(3));
            final long size = Long.parseLong(matcher.group(4)) * MB;
            if (index < 1 || total < 1 || index > total) {
                return null;
            }
            return new Header(matcher.group(1), index, total, size, matcher.group(5));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // endregion
}
