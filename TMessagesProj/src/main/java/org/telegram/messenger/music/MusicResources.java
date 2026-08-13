package org.telegram.messenger.music;

import android.graphics.Typeface;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Font pack for {@link MusicCardRenderer} - the same font families reSwaga's card renderer
 * offers (Onest/Circular/YS Text/YS Music/Noto Sans JP), bundled straight into the app under
 * {@code assets/music_fonts/} rather than fetched from a user-supplied URL. Card rendering still
 * works fine with just the system Typeface if a family's file is somehow missing.
 *
 * <p>A downloaded override in {@link #getResourceDir()} still takes priority when present, for
 * anyone who wants to swap in their own font file for a family without rebuilding the app - the
 * bundled asset is only the fallback once the download slot is empty, not the only source.
 */
public class MusicResources {

    public static final String[] FONT_FAMILIES = {"System", "Onest", "Circular", "YSText", "YSMusic", "NotoSansJP"};

    private static final String ASSET_DIR = "music_fonts";

    private static final String[] RESOURCE_FILES = {
            "Onest-Regular.ttf", "Onest-Bold.ttf",
            "Circular-Regular.ttf", "Circular-Bold.ttf",
            "YSText-Regular.ttf", "YSText-Bold.ttf",
            "YSMusic-Regular.ttf", "YSMusic-Bold.ttf",
            "NotoSansJP-Regular.ttf", "NotoSansJP-Bold.ttf",
    };

    public interface DownloadCallback {
        void onComplete(boolean success);
    }

    private static final Map<String, Typeface> cache = new HashMap<>();

    public static File getResourceDir() {
        File dir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "music_resources");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** All fonts ship bundled now, so this is always true - kept for callers that still gate UI
     *  on it (a downloaded-resources screen that no longer has anything to offer to download). */
    public static boolean areResourcesDownloaded() {
        return true;
    }

    /** Whether the user has fetched a downloaded override for at least one font file - distinct
     *  from {@link #areResourcesDownloaded}, which is about the bundled assets and is always true
     *  now. Used to decide whether "remove the override" has anything to actually remove. */
    public static boolean hasOverride() {
        File[] files = getResourceDir().listFiles();
        return files != null && files.length > 0;
    }

    /** Downloads override font files from {@code baseUrl} (expected to serve them at
     *  "{baseUrl}/{filename}"). Only needed if someone wants a different look than the bundled
     *  fonts - {@link #getTypeface} already works without ever calling this. */
    public static void downloadResources(String baseUrl, DownloadCallback callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            boolean allOk = true;
            File dir = getResourceDir();
            String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            for (String f : RESOURCE_FILES) {
                File dest = new File(dir, f);
                if (dest.exists()) continue;
                if (!MusicHttp.downloadToFile(base + f, dest)) {
                    allOk = false;
                    FileLog.e("MusicResources: failed to download " + f);
                }
            }
            cache.clear();
            boolean success = allOk;
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onComplete(success);
            });
        });
    }

    /** Removes only the downloaded overrides - the bundled fonts in assets are part of the app
     *  itself and were never something to "clear". */
    public static void clearResources() {
        File dir = getResourceDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        cache.clear();
    }

    /** Returns the requested family/weight - a downloaded override if present, otherwise the
     *  bundled asset, otherwise the system Typeface. */
    public static Typeface getTypeface(String family, boolean bold) {
        if (family == null || "System".equals(family)) {
            return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
        String fileName = family + (bold ? "-Bold.ttf" : "-Regular.ttf");
        Typeface cached = cache.get(fileName);
        if (cached != null) {
            return cached;
        }
        File overrideFile = new File(getResourceDir(), fileName);
        try {
            Typeface tf;
            if (overrideFile.exists()) {
                tf = Typeface.createFromFile(overrideFile);
            } else {
                tf = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), ASSET_DIR + "/" + fileName);
            }
            cache.put(fileName, tf);
            return tf;
        } catch (Exception e) {
            FileLog.e("MusicResources.getTypeface " + fileName, e);
            return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
    }
}
