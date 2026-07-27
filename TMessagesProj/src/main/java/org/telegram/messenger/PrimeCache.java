package org.telegram.messenger;

import java.io.File;

/**
 * PrimeGram: cache sizes and cache clearing, without leaving our settings screen.
 *
 * <p>Telegram's own cache screen lives several taps away inside Data and Storage and mixes the
 * per-chat breakdown in with the totals. This is the short version: how much each kind of media
 * is holding, and a way to drop it.
 *
 * <p>Everything here walks the filesystem, so nothing may be called from the main thread. The
 * callers below all hop to {@link Utilities#globalQueue} first.
 */
public class PrimeCache {

    public static final int KIND_IMAGES = 0;
    public static final int KIND_VIDEO = 1;
    public static final int KIND_DOCUMENTS = 2;
    public static final int KIND_AUDIO = 3;
    public static final int KIND_OTHER = 4;
    public static final int KIND_COUNT = 5;

    public static final String[] KIND_NAMES = {"Фото", "Видео", "Документы", "Аудио", "Прочее"};

    /**
     * The directories behind each kind.
     *
     * <p>{@code MEDIA_DIR_FILES} and {@code MEDIA_DIR_IMAGE_PUBLIC} are deliberately absent: those
     * hold files the user asked to keep - saved downloads and anything copied to the gallery -
     * and a button labelled "clear cache" must not reach them.
     */
    private static int[] dirsFor(int kind) {
        switch (kind) {
            case KIND_IMAGES:
                return new int[]{FileLoader.MEDIA_DIR_IMAGE};
            case KIND_VIDEO:
                return new int[]{FileLoader.MEDIA_DIR_VIDEO};
            case KIND_DOCUMENTS:
                return new int[]{FileLoader.MEDIA_DIR_DOCUMENT};
            case KIND_AUDIO:
                return new int[]{FileLoader.MEDIA_DIR_AUDIO};
            case KIND_OTHER:
                return new int[]{FileLoader.MEDIA_DIR_CACHE, FileLoader.MEDIA_DIR_STORIES};
            default:
                return new int[0];
        }
    }

    /** Sizes in bytes, indexed by kind. Blocking; call off the main thread. */
    public static long[] measure() {
        final long[] sizes = new long[KIND_COUNT];
        for (int kind = 0; kind < KIND_COUNT; kind++) {
            long total = 0;
            for (int dir : dirsFor(kind)) {
                total += sizeOf(FileLoader.checkDirectory(dir));
            }
            sizes[kind] = total;
        }
        return sizes;
    }

    /** Deletes the contents of every selected kind. Blocking; call off the main thread. */
    public static void clear(boolean[] kinds) {
        if (kinds == null) {
            return;
        }
        for (int kind = 0; kind < KIND_COUNT && kind < kinds.length; kind++) {
            if (!kinds[kind]) {
                continue;
            }
            for (int dir : dirsFor(kind)) {
                deleteContents(FileLoader.checkDirectory(dir));
            }
        }
        // Thumbnails and decoded bitmaps still point at files that are gone; without this the
        // next scroll draws from a cache whose backing files no longer exist.
        try {
            ImageLoader.getInstance().clearMemory();
        } catch (Throwable ignore) {
        }
    }

    private static long sizeOf(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        long total = 0;
        final File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        for (File child : children) {
            if (child == null) {
                continue;
            }
            // Not recursed through symlinks: a link pointing outside the cache would make the
            // total meaningless and, worse, make "clear" delete something else.
            if (child.isDirectory()) {
                total += sizeOf(child);
            } else {
                total += child.length();
            }
        }
        return total;
    }

    private static void deleteContents(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child == null) {
                continue;
            }
            try {
                if (child.isDirectory()) {
                    deleteContents(child);
                    child.delete();
                } else {
                    child.delete();
                }
            } catch (Throwable ignore) {
            }
        }
    }
}
