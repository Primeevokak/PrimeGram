package org.telegram.messenger.plugins;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import com.chaquo.python.PyObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * PrimeGram: the catalogue of installed plugins, and the only thing that starts or stops one.
 *
 * <p>Two halves that deliberately do not share a thread. The catalogue - what is installed, what it
 * claims about itself, whether the user wants it on - is plain Java, read from disk and from
 * preferences, and any screen may ask it anything at any moment. Running a plugin is the other
 * half, and all of it happens on {@link PrimePythonEngine#queue()}.
 *
 * <p>The split matters because the interpreter is the slow, failable part. A user who has installed
 * nothing should never start Python; a user whose plugin hangs should still get a plugins screen
 * that draws. Keeping the list answerable without the engine is what makes both true.
 */
public final class PrimePluginsController {

    /** What a single-file plugin is called. */
    public static final String EXTENSION = ".plugin";
    /** What a packaged (elyxbuilder) plugin is called - a zip, extracted to a directory of the
     *  same suffixed name rather than copied whole. See PluginManifest.parseYaml and
     *  _prime_loader.load_elyx_plugin for the rest of this format's handling. */
    public static final String EXTENSION_ELYX = ".elyx";

    /** Anything larger is not a plugin; refusing early keeps a hostile file off the main thread. */
    private static final long MAX_SIZE = 8 * 1024 * 1024;
    /** A packaged {@code .elyx} plugin bundles real assets (fonts, images) alongside its code, so
     *  it earns a much larger allowance than a single source file. */
    private static final long MAX_SIZE_ELYX = 64 * 1024 * 1024;

    private static volatile PrimePluginsController instance;

    private final List<PrimePlugin> plugins = new ArrayList<>();
    private volatile boolean cataloguedOnce;

    private PrimePluginsController() {
    }

    public static PrimePluginsController getInstance() {
        PrimePluginsController local = instance;
        if (local == null) {
            synchronized (PrimePluginsController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimePluginsController();
                }
            }
        }
        return local;
    }

    // region catalogue

    /** Where installed plugins live. Inside our own files directory, so uninstalling takes them. */
    public static File pluginsDir() {
        final File dir = new File(ApplicationLoader.getFilesDirFixed(), "plugins");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** A snapshot of the catalogue, safe to hand to a list adapter. */
    public List<PrimePlugin> getPlugins() {
        synchronized (plugins) {
            return new ArrayList<>(plugins);
        }
    }

    public PrimePlugin findById(String id) {
        if (id == null) {
            return null;
        }
        synchronized (plugins) {
            for (int i = 0; i < plugins.size(); i++) {
                if (id.equals(plugins.get(i).id())) {
                    return plugins.get(i);
                }
            }
        }
        return null;
    }

    public int count() {
        synchronized (plugins) {
            return plugins.size();
        }
    }

    public boolean hasEnabledPlugins() {
        synchronized (plugins) {
            for (int i = 0; i < plugins.size(); i++) {
                if (plugins.get(i).isEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reads the plugins directory and rebuilds the catalogue, then starts whatever is switched on.
     *
     * <p>Safe to call repeatedly - the second call and later ones return immediately - because the
     * places that want plugins ready (app start, the plugins screen, a shared file arriving) have no
     * way of knowing which of them got there first.
     */
    public void loadIfNeeded() {
        if (cataloguedOnce) {
            return;
        }
        cataloguedOnce = true;
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            rescanLocked();
            startEnabled();
        });
    }

    private void rescanLocked() {
        final List<PrimePlugin> found = new ArrayList<>();
        final File[] files = pluginsDir().listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    final PluginManifest manifest;
                    if (file.isFile() && file.getName().endsWith(EXTENSION)) {
                        manifest = PluginManifest.parse(readHeader(file));
                    } else if (file.isDirectory() && file.getName().endsWith(EXTENSION_ELYX)) {
                        manifest = inspectElyxDirectory(file);
                    } else {
                        continue;
                    }
                    final PrimePlugin plugin = new PrimePlugin(manifest, file);
                    plugin.setEnabled(PrimePluginStore.isEnabled(manifest.id));
                    found.add(plugin);
                } catch (Throwable e) {
                    // A file we cannot read the header of is not shown at all. There is no id to
                    // list it under and nothing truthful to say about it beyond its filename.
                    FileLog.e(e);
                }
            }
        }
        Collections.sort(found, (a, b) -> a.name().compareToIgnoreCase(b.name()));
        synchronized (plugins) {
            plugins.clear();
            plugins.addAll(found);
        }
        notifyChanged();
    }

    /**
     * The first {@code 64 KB} of a plugin, which is where the header is by definition - the metadata
     * assignments come before any code. Reading the whole file to find its name would mean holding a
     * stranger's megabytes in memory to answer a question the first page always answers.
     */
    private static String readHeader(File file) throws IOException {
        final byte[] buffer = new byte[Math.min((int) Math.min(file.length(), 64 * 1024), 64 * 1024)];
        try (InputStream in = new FileInputStream(file)) {
            int read = 0;
            while (read < buffer.length) {
                final int n = in.read(buffer, read, buffer.length - read);
                if (n <= 0) {
                    break;
                }
                read += n;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    // endregion

    // region installing

    /** What went wrong with a file the user tapped, in words a user can act on. */
    public interface InstallCallback {
        void onInstalled(PrimePlugin plugin, boolean replacedExisting);

        void onFailed(String reason);
    }

    /**
     * Reads a candidate file's header without installing anything, so the confirmation sheet can
     * name what the user is about to run. Runs on whatever thread calls it - it is one small read.
     */
    public static PluginManifest inspect(File file) throws PluginManifest.MalformedException, IOException {
        if (file == null || !file.exists()) {
            throw new IOException("no file");
        }
        if (file.getName().endsWith(EXTENSION_ELYX)) {
            if (file.length() > MAX_SIZE_ELYX) {
                throw new IOException("too large");
            }
            return inspectElyx(file);
        }
        if (file.length() > MAX_SIZE) {
            throw new IOException("too large");
        }
        return PluginManifest.parse(readHeader(file));
    }

    /** Reads a path relative to an {@code .elyx} package's root - either straight out of the zip
     *  (before install, for the confirmation sheet) or out of an already-extracted directory
     *  (after install, for {@link #rescanLocked}) - so both share the same manifest-building code
     *  without caring which one it's talking to. */
    private interface ElyxEntryReader {
        /** Text content of the entry, or null if it does not exist. */
        String readText(String relativePath) throws IOException;
    }

    private static PluginManifest buildElyxManifest(ElyxEntryReader reader) throws PluginManifest.MalformedException, IOException {
        final String refmapSource = reader.readText("refmap.yml");
        if (refmapSource == null) {
            throw new PluginManifest.MalformedException("no refmap.yml");
        }
        final Map<String, String> refmap = readSimpleYaml(refmapSource);
        final String metaPath = refmap.get("metainfo");
        if (metaPath == null) {
            throw new PluginManifest.MalformedException("refmap.yml has no metainfo");
        }
        final String metaSource = reader.readText(metaPath);
        if (metaSource == null) {
            throw new PluginManifest.MalformedException("meta.yml missing: " + metaPath);
        }
        return PluginManifest.parseYaml(metaSource, readElyxLocale(reader, refmap.get("strings")));
    }

    /** {@code {key}} placeholders in meta.yml (elyxbuilder's own convention for
     *  {@code description: "{description}"}) resolve against the plugin's own locale file for the
     *  app's current language, falling back to English, and to nothing at all rather than fail
     *  the whole install over a missing translation. */
    private static Map<String, String> readElyxLocale(ElyxEntryReader reader, String localesDir) {
        if (localesDir == null) {
            return Collections.emptyMap();
        }
        final String lang = org.telegram.messenger.LocaleController.getLocaleStringIso639();
        String json = null;
        try {
            if (lang != null) {
                json = reader.readText(localesDir + "/strings_" + lang + ".json");
            }
        } catch (IOException ignore) {
        }
        if (json == null) {
            try {
                json = reader.readText(localesDir + "/strings_en.json");
            } catch (IOException ignore) {
            }
        }
        if (json == null) {
            return Collections.emptyMap();
        }
        final Map<String, String> result = new java.util.LinkedHashMap<>();
        try {
            final org.json.JSONObject obj = new org.json.JSONObject(json);
            final java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                final Object value = obj.opt(key);
                if (value instanceof String) {
                    result.put(key, (String) value);
                }
            }
        } catch (Throwable ignore) {
        }
        return result;
    }

    /** The same flat {@code key: value} shape {@link PluginManifest#parseYaml} reads for
     *  meta.yml - refmap.yml is just as simple, so this stays a tiny local reader rather than
     *  exposing that one publicly for a single other caller. */
    private static Map<String, String> readSimpleYaml(String source) {
        final Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String line : source.split("\n")) {
            final String trimmed = line.trim();
            final int colon = trimmed.indexOf(':');
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || colon <= 0) {
                continue;
            }
            result.put(trimmed.substring(0, colon).trim(), trimmed.substring(colon + 1).trim());
        }
        return result;
    }

    private static PluginManifest inspectElyx(File zipFile) throws PluginManifest.MalformedException, IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            return buildElyxManifest(relativePath -> {
                final java.util.zip.ZipEntry entry = zip.getEntry(relativePath);
                if (entry == null) {
                    return null;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    return readAll(in);
                }
            });
        }
    }

    private static PluginManifest inspectElyxDirectory(File dir) throws PluginManifest.MalformedException, IOException {
        return buildElyxManifest(relativePath -> {
            final File f = new File(dir, relativePath);
            if (!f.exists()) {
                return null;
            }
            try (InputStream in = new FileInputStream(f)) {
                return readAll(in);
            }
        });
    }

    /** The fields {@code load_elyx_plugin} needs but has no module-level dunders to read (unlike
     *  {@code .plugin}, {@code meta.yml} was already fully parsed on this side before the
     *  interpreter ever saw the plugin) - handed over as JSON since that is how every other
     *  Java<->Python call in this controller already talks. */
    private static String manifestToJson(PluginManifest manifest) {
        final org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("name", manifest.name);
            json.put("description", manifest.description == null ? "" : manifest.description);
            json.put("author", manifest.author == null ? "" : manifest.author);
            json.put("version", manifest.version);
            final String icon = manifest.icon();
            json.put("icon", icon == null ? "" : icon);
        } catch (org.json.JSONException ignore) {
        }
        return json.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }

    /**
     * Copies a plugin into our directory and switches it on. Overwriting an existing plugin of the
     * same id is the update path, and keeps its settings: the user is replacing a version of
     * something they already configured, not installing a stranger that happens to share a name.
     */
    public void install(Context context, File source, InstallCallback callback) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            PluginManifest manifest = null;
            String failure = null;
            boolean replaced = false;
            File target = null;
            final boolean isElyx = source.getName().endsWith(EXTENSION_ELYX);
            try {
                manifest = inspect(source);
                if (!manifest.isCompatibleWithApp(BuildVars.BUILD_VERSION_STRING)) {
                    failure = "version";
                } else if (isElyx) {
                    target = new File(pluginsDir(), manifest.id + EXTENSION_ELYX);
                    replaced = target.exists();
                    if (replaced) {
                        unloadFromPython(manifest.id);
                        deleteRecursive(target);
                    }
                    extractZip(source, target);
                } else {
                    target = new File(pluginsDir(), manifest.id + EXTENSION);
                    replaced = target.exists();
                    if (replaced) {
                        unloadFromPython(manifest.id);
                    }
                    copy(source, target);
                }
            } catch (PluginManifest.MalformedException e) {
                failure = "malformed";
            } catch (Throwable e) {
                FileLog.e(e);
                failure = "io";
            }

            final String reason = failure;
            if (reason != null) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onFailed(reason));
                }
                return;
            }

            final PrimePlugin plugin = new PrimePlugin(manifest, target);
            // Installed, not started. Nothing of the plugin runs and none of its libraries are
            // fetched until the user switches it on - which is the moment they agree to execute
            // it, as opposed to the moment they agreed to keep the file.
            //
            // An update keeps whatever the user had chosen for the previous version: a plugin they
            // had running should not go quiet because its author released a fix.
            final boolean keepRunning = replaced && PrimePluginStore.isEnabled(manifest.id);
            PrimePluginStore.setEnabled(manifest.id, keepRunning);
            plugin.setEnabled(keepRunning);
            synchronized (plugins) {
                plugins.remove(plugin);
                plugins.add(plugin);
                Collections.sort(plugins, (a, b) -> a.name().compareToIgnoreCase(b.name()));
            }
            if (keepRunning) {
                loadIntoPython(context, plugin);
            }
            notifyChanged();

            final boolean wasReplaced = replaced;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onInstalled(plugin, wasReplaced));
            }
        });
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            final byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    /** Unpacks an {@code .elyx} zip into {@code targetDir}, which must not already exist (the
     *  caller is responsible for clearing a previous install first). Guards against a zip entry
     *  whose name tries to escape {@code targetDir} ({@code ../../..}) - a hostile plugin file is
     *  not a reason to let it write anywhere outside its own directory. */
    private static void extractZip(File source, File targetDir) throws IOException {
        if (!targetDir.mkdirs() && !targetDir.isDirectory()) {
            throw new IOException("cannot create " + targetDir);
        }
        final String targetRoot = targetDir.getCanonicalPath() + File.separator;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(source))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final File outFile = new File(targetDir, entry.getName());
                if (!outFile.getCanonicalPath().startsWith(targetRoot)) {
                    throw new IOException("zip entry escapes target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    final File parent = outFile.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        final byte[] buffer = new byte[16 * 1024];
                        int n;
                        while ((n = zis.read(buffer)) > 0) {
                            out.write(buffer, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /** Removes a plugin and everything it remembered. Deleting is meant to leave nothing behind. */
    public void delete(PrimePlugin plugin) {
        if (plugin == null) {
            return;
        }
        synchronized (plugins) {
            plugins.remove(plugin);
        }
        notifyChanged();
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            unloadFromPython(plugin.id());
            if (plugin.file.isDirectory()) {
                deleteRecursive(plugin.file);
            } else {
                //noinspection ResultOfMethodCallIgnored
                plugin.file.delete();
            }
            PrimePluginStore.forget(plugin.id());
        });
    }

    // endregion

    // region running

    /**
     * Turns a plugin on or off. The flag is written straight away so the switch under the user's
     * finger does not wait for Python, and the loading happens behind it.
     */
    public void setEnabled(Context context, PrimePlugin plugin, boolean enabled) {
        if (plugin == null) {
            return;
        }
        if (enabled) {
            // Cleared before setEnabled, not after: PrimePlugin.setEnabled refuses to turn on a
            // plugin that still has an error on it, and that error is whatever the *last* attempt
            // left behind. Without this, asking to retry a failed plugin silently did nothing -
            // the flag went in, isEnabled() stayed false because the stale error was still there,
            // and loadIntoPython below ran without the UI ever reflecting that anything happened.
            plugin.setError(null);
        }
        PrimePluginStore.setEnabled(plugin.id(), enabled);
        plugin.setEnabled(enabled);
        notifyChanged();
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            if (enabled) {
                loadIntoPython(context, plugin);
            } else {
                unloadFromPython(plugin.id());
            }
        });
    }

    /**
     * Disables exactly {@code pluginId} and whatever is joined to it by an import - not every
     * installed plugin. See {@link PrimePluginCrashHandler} for the actual dependency-graph walk
     * and dialog; this stays here only because it is called by name from Python
     * ({@code _prime_loader.disable_crashed_plugin}), which needs the same {@code
     * PrimePluginsController.getInstance().disableAfterCrash(...)} shape it always has.
     */
    public void disableAfterCrash(String pluginId, String reason) {
        PrimePluginCrashHandler.getInstance().disableAfterCrash(pluginId, reason);
    }

    private void startEnabled() {
        final Context context = ApplicationLoader.applicationContext;
        final List<PrimePlugin> snapshot = getPlugins();
        boolean any = false;
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).isEnabled()) {
                any = true;
                break;
            }
        }
        if (!any) {
            // Nothing to run, so nothing starts the interpreter. This is the common case on this
            // fork and it is worth several megabytes and a good fraction of a second.
            return;
        }
        for (int i = 0; i < snapshot.size(); i++) {
            final PrimePlugin plugin = snapshot.get(i);
            if (plugin.isEnabled()) {
                loadIntoPython(context, plugin);
            }
        }
    }

    /** Must be called on the engine queue. */
    private void loadIntoPython(Context context, PrimePlugin plugin) {
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.ensureStarted(context == null ? ApplicationLoader.applicationContext : context)) {
            plugin.setError(engine.startError() != null
                    ? engine.startError() : new IllegalStateException("python unavailable"));
            notifyChanged();
            return;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader == null) {
                throw new IllegalStateException("loader missing");
            }
            // Whatever the plugin declared it needs, fetched before it runs. Cheap when they are
            // already there - the check is local - and the only chance to get them before the
            // first import fails.
            final List<String> requirements = plugin.manifest.requirements;
            if (requirements != null && !requirements.isEmpty()) {
                final org.json.JSONArray json = new org.json.JSONArray();
                for (String requirement : requirements) {
                    json.put(requirement);
                }
                final PyObject problems = loader.callAttr("install_requirements", json.toString());
                final String text = problems == null ? "" : problems.toString();
                if (!TextUtils.isEmpty(text)) {
                    FileLog.e("plugin " + plugin.id() + " requirements: " + text);
                }
            }
            final PyObject result = plugin.file.isDirectory()
                    ? loader.callAttr("load_elyx_plugin", plugin.id(), plugin.file.getAbsolutePath(), manifestToJson(plugin.manifest))
                    : loader.callAttr("load_plugin", plugin.id(), plugin.file.getAbsolutePath());
            final String error = result == null || result.toJava(Object.class) == null ? null : result.toString();
            if (TextUtils.isEmpty(error)) {
                plugin.setError(null);
            } else {
                String fullTraceback = null;
                try {
                    final PyObject traceback = loader.callAttr("get_load_traceback", plugin.id());
                    fullTraceback = traceback == null ? null : traceback.toString();
                } catch (Throwable ignore) {
                }
                plugin.setError(new PluginLoadException(error, fullTraceback));
            }
        } catch (Throwable e) {
            FileLog.e(e);
            plugin.setError(e);
        }
        notifyChanged();
    }

    /** Must be called on the engine queue. Package-private: {@link PrimePluginCrashHandler} also
     *  needs this to unload the connected component of a crashed plugin.
     *
     *  <p>Forwards to {@link com.exteragram.messenger.plugins.PythonPluginsEngine#unloadPlugin} -
     *  that compat class, not this one, is the method a plugin written against exteraGram's own
     *  API can actually attach an Xposed hook to by name, so it is the single real implementation
     *  now; every caller of this method (user toggling a plugin off, deleting it, a crash taking
     *  down its dependency chain) goes through it too, rather than a duplicate that a hook there
     *  would never see. */
    void unloadFromPython(String pluginId) {
        com.exteragram.messenger.plugins.PythonPluginsEngine.INSTANCE.unloadPlugin(pluginId);
    }

    // endregion

    // region settings

    /**
     * Asks a plugin what settings it offers. The answer is JSON, described by the plugin's own
     * {@code create_settings}, and it arrives on the main thread because a settings screen is the
     * only thing that wants it.
     *
     * <p>A plugin that is switched off has no Python side and therefore no rows; the screen shows
     * its stored values through {@link PrimePluginStore} regardless, which is the point of keeping
     * storage in Java.
     */
    public void requestSettings(String pluginId, org.telegram.messenger.Utilities.Callback<String> callback) {
        PrimePluginSettingsBridge.getInstance().requestSettings(pluginId, callback);
    }

    /**
     * The screen a {@code create_sub_fragment} row opens, as JSON: {@code {"title", "rows"}}.
     * {@code parentPath} is the screen the row itself lives on - {@code ""} for the plugin's own
     * screen, or whatever an earlier call here returned as a child path - because the same row
     * index means a different row on every screen.
     */
    public void requestSubSettings(String pluginId, String parentPath, int index,
                                    org.telegram.messenger.Utilities.Callback<String> callback) {
        PrimePluginSettingsBridge.getInstance().requestSubSettings(pluginId, parentPath, index, callback);
    }

    /**
     * The live {@link View} for one {@code "custom"} row - the one piece of a settings screen that
     * cannot ride along in {@link #requestSettings}'s JSON, because JSON cannot hold a Java object.
     * Fetched separately, once the row is actually about to be drawn. {@code path} is the screen
     * the row lives on, same as in {@link #requestSubSettings}.
     */
    public void requestCustomView(String pluginId, String path, int index, org.telegram.messenger.Utilities.Callback<View> callback) {
        PrimePluginSettingsBridge.getInstance().requestCustomView(pluginId, path, index, callback);
    }

    /**
     * What the downloader has fetched, as a JSON object of name to version.
     *
     * <p>Worth showing because these arrive without the user asking: a plugin declares what it
     * needs and it appears. Somewhere has to answer "what did this app download onto my phone".
     */
    public void requestLibraries(org.telegram.messenger.Utilities.Callback<String> callback) {
        PrimePluginSettingsBridge.getInstance().requestLibraries(callback);
    }

    /** Deletes them all. Anything still needed is fetched again the next time a plugin loads. */
    public void clearLibraries(Runnable done) {
        PrimePluginSettingsBridge.getInstance().clearLibraries(done);
    }

    /** Tells the plugin a row moved. The value is already stored - this is only its chance to react. */
    public void notifySettingChanged(String pluginId, String path, int index, String valueJson) {
        PrimePluginSettingsBridge.getInstance().notifySettingChanged(pluginId, path, index, valueJson);
    }

    public void notifySettingClicked(String pluginId, String path, int index) {
        PrimePluginSettingsBridge.getInstance().notifySettingClicked(pluginId, path, index);
    }

    /** A plugin's own failure, as opposed to ours - its message is the Python traceback's last line. */
    public static final class PluginLoadException extends RuntimeException {
        /** The Python-side traceback, if the Python loader captured one - null for the load
         *  failures that never reach a Python stack frame (unreadable file, missing library). */
        public final String fullTraceback;

        public PluginLoadException(String message) {
            this(message, null);
        }

        public PluginLoadException(String message, String fullTraceback) {
            super(message);
            this.fullTraceback = fullTraceback;
        }
    }

    // endregion

    /** Package-private: {@link PrimePluginCrashHandler} also fires this after disabling a
     *  crashed plugin's connected component. */
    void notifyChanged() {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pluginsDidUpdate));
    }
}
