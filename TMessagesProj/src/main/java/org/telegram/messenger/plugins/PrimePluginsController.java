package org.telegram.messenger.plugins;

import android.content.Context;
import android.text.TextUtils;

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

    /** What a plugin file is called, and the only extension we will install. */
    public static final String EXTENSION = ".plugin";

    /** Anything larger is not a plugin; refusing early keeps a hostile file off the main thread. */
    private static final long MAX_SIZE = 8 * 1024 * 1024;

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
                if (!file.isFile() || !file.getName().endsWith(EXTENSION)) {
                    continue;
                }
                try {
                    final PluginManifest manifest = PluginManifest.parse(readHeader(file));
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
        if (file.length() > MAX_SIZE) {
            throw new IOException("too large");
        }
        return PluginManifest.parse(readHeader(file));
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
            try {
                manifest = inspect(source);
                if (!manifest.isCompatibleWithApp(BuildVars.BUILD_VERSION_STRING)) {
                    failure = "version";
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
            //noinspection ResultOfMethodCallIgnored
            plugin.file.delete();
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
            final PyObject result = loader.callAttr("load_plugin", plugin.id(), plugin.file.getAbsolutePath());
            final String error = result == null || result.toJava(Object.class) == null ? null : result.toString();
            if (TextUtils.isEmpty(error)) {
                plugin.setError(null);
            } else {
                plugin.setError(new PluginLoadException(error));
            }
        } catch (Throwable e) {
            FileLog.e(e);
            plugin.setError(e);
        }
        notifyChanged();
    }

    /** Must be called on the engine queue. */
    private void unloadFromPython(String pluginId) {
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader != null) {
                loader.callAttr("unload_plugin", pluginId);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
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
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            String json = "[]";
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    json = loader.callAttr("build_settings", pluginId).toString();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final String result = json;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    /**
     * What the downloader has fetched, as a JSON object of name to version.
     *
     * <p>Worth showing because these arrive without the user asking: a plugin declares what it
     * needs and it appears. Somewhere has to answer "what did this app download onto my phone".
     */
    public void requestLibraries(org.telegram.messenger.Utilities.Callback<String> callback) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            String json = "{}";
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    json = loader.callAttr("installed_libraries").toString();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final String result = json;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    /** Deletes them all. Anything still needed is fetched again the next time a plugin loads. */
    public void clearLibraries(Runnable done) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("clear_libraries");
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (done != null) {
                AndroidUtilities.runOnUIThread(done);
            }
        });
    }

    /** Tells the plugin a row moved. The value is already stored - this is only its chance to react. */
    public void notifySettingChanged(String pluginId, int index, String valueJson) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("on_setting_changed", pluginId, index, valueJson);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public void notifySettingClicked(String pluginId, int index) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("on_setting_clicked", pluginId, index);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    /** A plugin's own failure, as opposed to ours - its message is the Python traceback's last line. */
    public static final class PluginLoadException extends RuntimeException {
        public PluginLoadException(String message) {
            super(message);
        }
    }

    // endregion

    private void notifyChanged() {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pluginsDidUpdate));
    }
}
