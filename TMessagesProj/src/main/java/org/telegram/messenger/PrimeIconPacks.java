package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import com.caverock.androidsvg.SVG;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * PrimeGram: user-installed icon packs - a folder of images, one per drawable resource name, that
 * {@link PrimeResources} checks before falling back to the app's own icon.
 *
 * <p>exteraGram's icon packs are Android launcher icon packs - archives built to reskin other apps'
 * home-screen icons, with an {@code appfilter.xml} mapping package names to drawables. That format
 * solves a problem we do not have: we are only ever reskinning our own app's internal icons, never
 * someone else's launcher. So the format here is the plain version of the same idea - a zip of
 * images named after the drawable they replace ({@code msg_delete.png}, {@code msg_delete.svg}) -
 * which is both simpler to write a pack for and simpler to parse.
 *
 * <p>A pack does not have to cover every icon. {@link #getIcon} returns {@code null} for anything
 * it has no file for, and {@link PrimeResources} draws the app's own icon in that case - a pack is
 * a set of overrides, not a replacement wardrobe that has to be complete before it is usable.
 */
public final class PrimeIconPacks {

    private static final String KEY_ACTIVE = "primegram_icon_pack_active";
    private static final String META_FILE = "pack.json";

    public static final class Pack {
        public final String id;
        public final String name;
        public final File dir;
        public final int iconCount;

        Pack(String id, String name, File dir, int iconCount) {
            this.id = id;
            this.name = name;
            this.dir = dir;
            this.iconCount = iconCount;
        }
    }

    private static final LruCache<String, Drawable> cache = new LruCache<>(200);

    private PrimeIconPacks() {
    }

    private static File packsDir() {
        final File dir = new File(ApplicationLoader.getFilesDirFixed(), "prime_icon_packs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static String getActivePackId() {
        return MessagesController.getGlobalMainSettings().getString(KEY_ACTIVE, null);
    }

    public static void setActivePackId(String id) {
        MessagesController.getGlobalMainSettings().edit().putString(KEY_ACTIVE, id).apply();
        cache.evictAll();
    }

    public static List<Pack> listPacks() {
        final List<Pack> result = new ArrayList<>();
        final File[] dirs = packsDir().listFiles();
        if (dirs == null) {
            return result;
        }
        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            final Pack pack = readPack(dir);
            if (pack != null) {
                result.add(pack);
            }
        }
        return result;
    }

    private static Pack readPack(File dir) {
        String name = dir.getName();
        final File meta = new File(dir, META_FILE);
        if (meta.exists()) {
            try (FileInputStream in = new FileInputStream(meta)) {
                final byte[] bytes = readAll(in);
                final JSONObject json = new JSONObject(new String(bytes, "UTF-8"));
                name = json.optString("name", name);
            } catch (Throwable ignore) {
            }
        }
        final File[] files = dir.listFiles();
        int count = 0;
        if (files != null) {
            for (File f : files) {
                if (isIconFile(f)) {
                    count++;
                }
            }
        }
        return new Pack(dir.getName(), name, dir, count);
    }

    private static boolean isIconFile(File f) {
        if (!f.isFile()) {
            return false;
        }
        final String name = f.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".svg") || name.endsWith(".webp");
    }

    /**
     * Unpacks {@code zip} into a new pack directory. Every entry is checked against the target
     * directory before being written - a zip is untrusted input, and an entry named
     * {@code ../../../something} is a real attack, not a hypothetical one.
     */
    public static Pack installFromZip(File zip, String displayName) throws Exception {
        final String id = UUID.randomUUID().toString();
        final File dir = new File(packsDir(), id);
        if (!dir.mkdirs()) {
            throw new Exception("не удалось создать папку пака");
        }
        final String dirPath = dir.getCanonicalPath() + File.separator;
        int extracted = 0;
        try (ZipInputStream zis = new ZipInputStream(new java.io.BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                final int slash = name.lastIndexOf('/');
                if (slash >= 0) {
                    name = name.substring(slash + 1);
                }
                if (name.isEmpty() || name.equals(META_FILE)) {
                    continue;
                }
                final File target = new File(dir, name);
                if (!target.getCanonicalPath().startsWith(dirPath)) {
                    continue;
                }
                if (!isIconFile(target)) {
                    continue;
                }
                try (FileOutputStream out = new FileOutputStream(target)) {
                    final byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                extracted++;
            }
        }
        if (extracted == 0) {
            deleteRecursive(dir);
            throw new Exception("в архиве не нашлось ни одной иконки (.png/.svg/.webp)");
        }
        final JSONObject meta = new JSONObject();
        meta.put("name", displayName == null || displayName.isEmpty() ? id : displayName);
        try (FileOutputStream out = new FileOutputStream(new File(dir, META_FILE))) {
            out.write(meta.toString().getBytes("UTF-8"));
        }
        return readPack(dir);
    }

    public static void deletePack(String id) {
        if (id == null) {
            return;
        }
        deleteRecursive(new File(packsDir(), id));
        if (id.equals(getActivePackId())) {
            setActivePackId(null);
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** {@code null} when there is no active pack, or the active pack has nothing for {@code
     *  resourceName} - either way, the caller draws the app's own icon instead. */
    public static Drawable getIcon(String resourceName) {
        final String activeId = getActivePackId();
        if (activeId == null || resourceName == null) {
            return null;
        }
        final String cacheKey = activeId + "/" + resourceName;
        final Drawable cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        final File dir = new File(packsDir(), activeId);
        Drawable drawable = tryLoad(new File(dir, resourceName + ".png"));
        if (drawable == null) {
            drawable = tryLoad(new File(dir, resourceName + ".webp"));
        }
        if (drawable == null) {
            drawable = tryLoadSvg(new File(dir, resourceName + ".svg"));
        }
        if (drawable != null) {
            cache.put(cacheKey, drawable);
        }
        return drawable;
    }

    private static Drawable tryLoad(File file) {
        if (!file.exists()) {
            return null;
        }
        try {
            final Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            return bitmap == null ? null : new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static Drawable tryLoadSvg(File file) {
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            final SVG svg = SVG.getFromInputStream(in);
            final Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new android.graphics.Canvas(bitmap));
            return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
