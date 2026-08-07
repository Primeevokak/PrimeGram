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

    /** The extension exteraGram's own icon packs ship under - a plain zip with a {@code
     *  metadata.json} describing the pack and mapping each drawable name to an arbitrarily-named
     *  image inside it, rather than the image being named after the drawable directly. See
     *  {@link #installFromZip} for where that mapping gets translated into our own layout. */
    public static final String EXTENSION = ".icons";

    private static final String KEY_ACTIVE = "primegram_icon_pack_active";
    private static final String META_FILE = "pack.json";
    private static final String SOURCE_META_FILE = "metadata.json";

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

    /** What {@code metadata.json} says about a {@code .icons} pack, read without installing
     *  anything - the counterpart to {@link org.telegram.messenger.plugins.PrimePluginsController#inspect},
     *  for the same reason: a confirmation sheet should be able to name what it is about to
     *  install before committing to it. */
    public static final class SourceMetadata {
        public final String packName;
        public final String author;
        public final String version;
        public final int iconCount;

        SourceMetadata(String packName, String author, String version, int iconCount) {
            this.packName = packName;
            this.author = author;
            this.version = version;
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

    /** Pure filename check, no disk access - safe to call on a target that has not been written
     *  yet. {@link #isIconFile(File)} is for the case that genuinely needs "does this file exist
     *  and have the right extension"; installation only ever needs the second half, and calling
     *  the file-existence one on a not-yet-extracted target made {@code isFile()} return false
     *  unconditionally, which meant a zip's icons were always rejected regardless of what was
     *  actually inside it. */
    private static boolean isIconFileName(String name) {
        final String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".svg") || lower.endsWith(".webp");
    }

    private static boolean isIconFile(File f) {
        return f.isFile() && isIconFileName(f.getName());
    }

    /**
     * Reads {@code metadata.json} from a {@code .icons} zip without installing anything, so a
     * confirmation sheet can name what it is about to install. Returns {@code null} for a zip
     * that has no {@code metadata.json} - our own plain format, which has nothing to preview
     * beyond "N image files", left to the caller to phrase.
     */
    public static SourceMetadata inspect(File zip) throws Exception {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip)) {
            final ZipEntry metaEntry = zf.getEntry(SOURCE_META_FILE);
            if (metaEntry == null) {
                return null;
            }
            final JSONObject json = new JSONObject(new String(readAll(zf.getInputStream(metaEntry)), "UTF-8"));
            final JSONObject icons = json.optJSONObject("icons");
            return new SourceMetadata(
                    json.optString("packName", null),
                    json.optString("author", null),
                    json.optString("version", null),
                    icons == null ? 0 : icons.length());
        }
    }

    /**
     * Unpacks {@code zip} into a new pack directory. Every entry is checked against the target
     * directory before being written - a zip is untrusted input, and an entry named
     * {@code ../../../something} is a real attack, not a hypothetical one.
     *
     * <p>Two source layouts are understood. Our own plain one names each image after the drawable
     * it replaces directly ({@code msg_delete.png}) and is copied across as-is. exteraGram's own
     * {@code .icons} format names images arbitrarily and carries a {@code metadata.json} mapping
     * each drawable name to one of them - those get written out under the drawable's own name
     * instead, so {@link #getIcon} never needs to know which layout a given pack came from.
     */
    public static Pack installFromZip(File zip, String displayName) throws Exception {
        final String id = UUID.randomUUID().toString();
        final File dir = new File(packsDir(), id);
        if (!dir.mkdirs()) {
            throw new Exception("не удалось создать папку пака");
        }
        final String dirPath = dir.getCanonicalPath() + File.separator;
        int extracted = 0;
        String sourcePackName = null;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip)) {
            // resourceName -> the exact entry name metadata.json says holds it, only populated
            // when metadata.json exists at all - an empty map here still means "plain layout",
            // not "no icons declared".
            final java.util.HashMap<String, String> byResourceName = new java.util.HashMap<>();
            boolean hasMetadata = false;
            final ZipEntry metaEntry = zf.getEntry(SOURCE_META_FILE);
            if (metaEntry != null) {
                hasMetadata = true;
                final JSONObject json = new JSONObject(new String(readAll(zf.getInputStream(metaEntry)), "UTF-8"));
                sourcePackName = json.optString("packName", null);
                final JSONObject icons = json.optJSONObject("icons");
                if (icons != null) {
                    final java.util.Iterator<String> keys = icons.keys();
                    while (keys.hasNext()) {
                        final String resourceName = keys.next();
                        final String fileName = icons.optString(resourceName, null);
                        if (resourceName != null && !resourceName.isEmpty() && fileName != null && !fileName.isEmpty()) {
                            byResourceName.put(fileName, resourceName);
                        }
                    }
                }
            }

            final java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                final int slash = entryName.lastIndexOf('/');
                if (slash >= 0) {
                    entryName = entryName.substring(slash + 1);
                }
                if (entryName.isEmpty() || entryName.equals(META_FILE) || entryName.equals(SOURCE_META_FILE)) {
                    continue;
                }

                final String targetName;
                if (hasMetadata) {
                    // Only what metadata.json actually mapped gets written out - an image in the
                    // zip that no drawable name points to is not one of our icons, whatever it is.
                    final String resourceName = byResourceName.get(entryName);
                    if (resourceName == null) {
                        continue;
                    }
                    final int dot = entryName.lastIndexOf('.');
                    final String ext = dot >= 0 ? entryName.substring(dot) : ".png";
                    targetName = resourceName + ext;
                } else {
                    targetName = entryName;
                }

                final File target = new File(dir, targetName);
                if (!target.getCanonicalPath().startsWith(dirPath)) {
                    continue;
                }
                if (!isIconFileName(targetName)) {
                    continue;
                }
                try (InputStream in = zf.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(target)) {
                    final byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
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
        final String resolvedName = sourcePackName != null && !sourcePackName.isEmpty() ? sourcePackName
                : (displayName == null || displayName.isEmpty() ? id : displayName);
        meta.put("name", resolvedName);
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
     *  resourceName} - either way, the caller draws the app's own icon instead.
     *
     *  <p>Unscaled - prefer {@link #getIcon(String, int, int)} wherever the size the icon is
     *  meant to fill is known, which is everywhere {@link PrimeResources} calls this from. A
     *  pack's own source image resolution has no reason to match this app's asset density grid -
     *  a pack built for a phone with a different scale, or just exported at a round number like
     *  512x512, decodes to a {@link BitmapDrawable} whose intrinsic size is whatever that is. Code
     *  that draws through {@code Drawable.setBounds(0, 0, expectedSize, expectedSize)} (as most of
     *  this codebase's manual-draw icon paths do) never resizes the bitmap itself to match those
     *  bounds - the drawable just paints small, anchored at the bounds' origin, which reads as
     *  "shrunk into the top-left corner" rather than "wrong size" at a glance. */
    public static Drawable getIcon(String resourceName) {
        return getIcon(resourceName, -1, -1);
    }

    /** Same as {@link #getIcon(String)}, scaled to exactly {@code targetWidthPx}x{@code
     *  targetHeightPx} - the pixel size {@code id}'s own (unreplaced) drawable would have reported,
     *  so a pack icon behaves identically to the resource it stands in for regardless of whether
     *  the caller relies on intrinsic size or sets explicit bounds. Either {@code <= 0} skips
     *  scaling, same as the unscaled overload. */
    public static Drawable getIcon(String resourceName, int targetWidthPx, int targetHeightPx) {
        final String activeId = getActivePackId();
        if (activeId == null || resourceName == null) {
            return null;
        }
        final boolean scale = targetWidthPx > 0 && targetHeightPx > 0;
        final String cacheKey = activeId + "/" + resourceName + (scale ? "/" + targetWidthPx + "x" + targetHeightPx : "");
        final Drawable cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        final File dir = new File(packsDir(), activeId);
        Drawable drawable = tryLoad(new File(dir, resourceName + ".png"), targetWidthPx, targetHeightPx);
        if (drawable == null) {
            drawable = tryLoad(new File(dir, resourceName + ".webp"), targetWidthPx, targetHeightPx);
        }
        if (drawable == null) {
            drawable = tryLoadSvg(new File(dir, resourceName + ".svg"), targetWidthPx, targetHeightPx);
        }
        if (drawable != null) {
            cache.put(cacheKey, drawable);
        }
        return drawable;
    }

    private static Drawable tryLoad(File file, int targetWidthPx, int targetHeightPx) {
        if (!file.exists()) {
            return null;
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                return null;
            }
            if (targetWidthPx > 0 && targetHeightPx > 0 && (bitmap.getWidth() != targetWidthPx || bitmap.getHeight() != targetHeightPx)) {
                bitmap = Bitmap.createScaledBitmap(bitmap, targetWidthPx, targetHeightPx, true);
            }
            return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static Drawable tryLoadSvg(File file, int targetWidthPx, int targetHeightPx) {
        if (!file.exists()) {
            return null;
        }
        final int size = targetWidthPx > 0 ? targetWidthPx : 96;
        final int sizeH = targetHeightPx > 0 ? targetHeightPx : 96;
        try (InputStream in = new FileInputStream(file)) {
            final SVG svg = SVG.getFromInputStream(in);
            final Bitmap bitmap = Bitmap.createBitmap(size, sizeH, Bitmap.Config.ARGB_8888);
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
