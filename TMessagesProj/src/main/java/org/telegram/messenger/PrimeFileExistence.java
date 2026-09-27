package org.telegram.messenger;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrimeGram: "is this file on disk yet?" without the UI thread touching the disk.
 *
 * A message cell asks this on every bind of a photo it has not loaded, which used to be a
 * synchronous File.exists() on the UI thread. Answers come from memory; an unknown path is checked on
 * a background thread and the caller is told on the UI thread if the file turned out to exist. A file
 * that exists is remembered as existing; a missing one is re-checked after a short while, since it
 * may be downloaded at any moment.
 */
public final class PrimeFileExistence {

    private static final long MISSING_TTL_MS = 3000;
    private static final int MAX_ENTRIES = 4000;

    /** path -> Long.MAX_VALUE when present, else the time it was last seen missing. */
    private static final ConcurrentHashMap<String, Long> known = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>();

    private PrimeFileExistence() {
    }

    /** TRUE / FALSE when known, null when it has to be checked. */
    public static Boolean peek(File file) {
        if (file == null) {
            return Boolean.FALSE;
        }
        final Long state = known.get(file.getPath());
        if (state == null) {
            return null;
        }
        if (state == Long.MAX_VALUE) {
            return Boolean.TRUE;
        }
        return System.currentTimeMillis() - state < MISSING_TTL_MS ? Boolean.FALSE : null;
    }

    /** Checks off the UI thread; {@code onExists} runs on the UI thread if the file is there. */
    public static void check(File file, Runnable onExists) {
        if (file == null) {
            return;
        }
        final String path = file.getPath();
        if (pending.putIfAbsent(path, Boolean.TRUE) != null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            boolean exists = false;
            try {
                exists = file.exists();
            } catch (Throwable ignore) {
            }
            if (known.size() > MAX_ENTRIES) {
                known.clear();
            }
            known.put(path, exists ? Long.MAX_VALUE : System.currentTimeMillis());
            pending.remove(path);
            if (exists && onExists != null) {
                AndroidUtilities.runOnUIThread(onExists);
            }
        });
    }
}
