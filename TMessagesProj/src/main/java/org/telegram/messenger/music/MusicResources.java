package org.telegram.messenger.music;

import android.graphics.Typeface;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Optional downloadable font pack for {@link MusicCardRenderer}, mirroring
 * reSwaga's resource system: card rendering works fine with just the system
 * Typeface, but a user can point this at a resource host to fetch nicer
 * custom fonts (Onest/Circular/YS Text/YS Music/Noto Sans JP) for the card —
 * same font families the original offered, downloaded on demand rather than
 * bundled, with the source URL left to the user (no hardcoded third-party
 * repo dependency baked into the client).
 */
public class MusicResources {

    public static final String[] FONT_FAMILIES = {"System", "Onest", "Circular", "YSText", "YSMusic", "NotoSansJP"};

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

    public static boolean areResourcesDownloaded() {
        File dir = getResourceDir();
        for (String f : RESOURCE_FILES) {
            if (!new File(dir, f).exists()) {
                return false;
            }
        }
        return true;
    }

    /** Downloads all font files from {@code baseUrl} (expected to serve them at "{baseUrl}/{filename}"). */
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

    /** Returns the requested family/weight if downloaded, otherwise falls back to the system Typeface. */
    public static Typeface getTypeface(String family, boolean bold) {
        if (family == null || "System".equals(family)) {
            return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
        String fileName = family + (bold ? "-Bold.ttf" : "-Regular.ttf");
        String cacheKey = fileName;
        Typeface cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        File file = new File(getResourceDir(), fileName);
        if (!file.exists()) {
            return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
        try {
            Typeface tf = Typeface.createFromFile(file);
            cache.put(cacheKey, tf);
            return tf;
        } catch (Exception e) {
            FileLog.e("MusicResources.getTypeface " + fileName, e);
            return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
    }
}
