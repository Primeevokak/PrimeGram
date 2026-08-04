package com.exteragram.messenger.plugins;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.plugins.PrimePluginStore;
import org.telegram.messenger.plugins.PrimePluginsController;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrimeGram: compatibility shim for {@code com.exteragram.messenger.plugins.PluginsController} -
 * the plugin-engine orchestrator some plugins (zwylib among them) reach for directly. Where a
 * real equivalent already exists in PrimeGram's own plugin system, this delegates to it
 * ({@link PrimePluginsController#pluginsDir()}, {@link PrimePluginStore} for settings); where the
 * real class does something exteraGram-backend-specific PrimeGram has no equivalent for (its
 * install-dialog UI, its own engine registration), the method is a safe no-op rather than a
 * crash - a plugin calling it gets nothing to react to, which is what "unsupported feature"
 * should look like, not a stack trace.
 */
public final class PluginsController {

    public static final int PLUGIN_FILE_ICON_ID_START = 101;
    public static final int PLUGIN_FILE_ICON_NONE = -1;

    private static volatile PluginsController instance;

    private final ConcurrentHashMap<String, PluginsEngine> engines = new ConcurrentHashMap<>();
    private File pluginsDir;
    private SharedPreferences preferences;

    private PluginsController() {
        pluginsDir = PrimePluginsController.pluginsDir();
        preferences = MessagesController.getGlobalMainSettings();
    }

    public static PluginsController getInstance() {
        PluginsController local = instance;
        if (local == null) {
            synchronized (PluginsController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PluginsController();
                }
            }
        }
        return local;
    }

    // region Companion-style static passthroughs

    public static void applyArtOpts() {
    }

    public static ConcurrentHashMap<String, PluginsEngine> getEngines() {
        return getInstance().engines;
    }

    public static int getFileIconId(String fileName) {
        return PLUGIN_FILE_ICON_NONE;
    }

    public static PluginsEngine getPluginEngineStatic(File file) {
        return null;
    }

    public static android.graphics.drawable.Drawable getPluginFileIconDrawable(int icon) {
        return null;
    }

    public static boolean isPlugin(File file, Object messageObject) {
        return file != null && file.getName().endsWith(PrimePluginsController.EXTENSION);
    }

    public static boolean isPlugin(Object messageObject) {
        return false;
    }

    public static boolean isPluginEngineAvailable() {
        return true;
    }

    public static boolean isPluginEngineSupported() {
        return true;
    }

    public static boolean isPluginFileIcon(int icon) {
        return false;
    }

    public static boolean isPluginPinned(String pluginId) {
        return PrimePluginStore.getBoolean(pluginId, "pinned", false);
    }

    public static void openPluginSettings(String pluginId) {
        openPluginSettings(pluginId, null);
    }

    public static void openPluginSettings(String pluginId, String linkAlias) {
        // No PrimeGram equivalent screen keyed by exteraGram's own plugin identity - a plugin
        // that calls this expecting its own settings fragment to open gets nothing to react to
        // rather than a crash pointing at code that isn't its own.
        FileLog.d("PluginsController.openPluginSettings (compat, no-op): " + pluginId);
    }

    public static int registerFileIcon(String extension, android.graphics.drawable.Drawable drawable) {
        return PLUGIN_FILE_ICON_NONE;
    }

    public static void runOnPluginsQueue(Runnable runnable) {
        if (runnable == null) return;
        Utilities.globalQueue.postRunnable(runnable);
    }

    public static void setPluginPinned(String pluginId, boolean isPinned) {
        PrimePluginStore.put(pluginId, "pinned", isPinned);
    }

    public static void unregisterFileIcon(String extension) {
    }

    // endregion

    // region instance API

    public ConcurrentHashMap<String, Plugin> getPlugins() {
        return new ConcurrentHashMap<>();
    }

    public ConcurrentHashMap<String, List<Object>> getSettings() {
        return new ConcurrentHashMap<>();
    }

    public File getPluginsDir() {
        return pluginsDir;
    }

    public void setPluginsDir(File file) {
        pluginsDir = file;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    public void setPreferences(SharedPreferences sp) {
        preferences = sp;
    }

    public boolean getInitialized() {
        return true;
    }

    public void init() {
    }

    public void init(Runnable onDone) {
        if (onDone != null) onDone.run();
    }

    public void init(boolean startWithSafeMode) {
    }

    public void init(boolean startWithSafeMode, Runnable onDone) {
        if (onDone != null) onDone.run();
    }

    public void restart() {
    }

    public void restart(boolean startWithSafeMode) {
    }

    public void shutdown(Runnable onDone) {
        if (onDone != null) onDone.run();
    }

    public void checkDevServers() {
    }

    public PluginsEngine getPluginEngine(String pluginId) {
        return engines.get(pluginId);
    }

    public boolean isPluginActive(Plugin plugin) {
        return plugin != null && isPluginActive(plugin.getId());
    }

    public boolean isPluginActive(String pluginId) {
        return pluginId != null && PrimePluginStore.isEnabled(pluginId);
    }

    public void setPluginEnabled(String pluginId, boolean enabled, Utilities.Callback<String> callback) {
        PrimePluginStore.setEnabled(pluginId, enabled);
        if (callback != null) callback.run(null);
    }

    public void deletePlugin(String pluginId, Utilities.Callback<String> callback) {
        PrimePluginStore.forget(pluginId);
        if (callback != null) callback.run(null);
    }

    public void cleanupPlugin(String pluginId) {
        PrimePluginStore.forget(pluginId);
    }

    public String getPluginPath(String id) {
        return new File(pluginsDir, id + PrimePluginsController.EXTENSION).getAbsolutePath();
    }

    public void showInstallDialog(Object fragment, String filePath, boolean trusted) {
        FileLog.d("PluginsController.showInstallDialog (compat, no-op): " + filePath);
    }

    public void showInstallDialog(Object fragment, Object messageObject) {
    }

    // region plugin settings - routed to PrimePluginStore

    public List<Object> getPluginSettingsList(String pluginId) {
        return new ArrayList<>();
    }

    public void loadPluginSettings() {
    }

    public void loadPluginSettings(String pluginId) {
    }

    public boolean hasPluginSettings(String pluginId) {
        return false;
    }

    public void invalidatePluginSettings(String pluginId) {
    }

    public void clearPluginSettingsPreferences(String pluginId) {
        clearPluginSettingsPreferences(pluginId, false);
    }

    public void clearPluginSettingsPreferences(String pluginId, boolean clearEnabledState) {
        PrimePluginStore.forget(pluginId);
        if (!clearEnabledState) {
            // forget() already drops the enabled flag along with everything else - there is no
            // partial-clear in PrimeGram's store, so the distinction this parameter draws in the
            // real class does not exist here.
        }
    }

    public Map<String, ?> getPluginSettingsPreferences(String pluginId) {
        return new HashMap<>();
    }

    public boolean hasPluginSettingsPreferences(String pluginId) {
        return false;
    }

    public boolean getPluginSettingBoolean(String pluginId, String key, boolean defaultValue) {
        return PrimePluginStore.getBoolean(pluginId, key, defaultValue);
    }

    public String getPluginSettingString(String pluginId, String key, String defaultValue) {
        return PrimePluginStore.getString(pluginId, key, defaultValue);
    }

    public int getPluginSettingInt(String pluginId, String key, int defaultValue) {
        return PrimePluginStore.getInt(pluginId, key, defaultValue);
    }

    public void setPluginSetting(String pluginId, String key, Object value) {
        PrimePluginStore.put(pluginId, key, value);
    }

    public void setPluginSettingAndTriggerOnChange(String pluginId, String key, Object value, Object onChangeCallback) {
        PrimePluginStore.put(pluginId, key, value);
    }

    // endregion

    public void addEventHook(String pluginId, String hookName, boolean matchSubstring, int priority) {
    }

    public void removeEventHook(String pluginId, String hookName) {
    }

    public void removeHooksByPluginId(String pluginId) {
    }

    public String addMenuItem(String pluginId, Object pyMenuItemData) {
        return null;
    }

    public boolean removeMenuItem(String pluginId, String itemId) {
        return false;
    }

    public void removeMenuItemsByPluginId(String pluginId) {
    }

    public void notifyPluginsChanged() {
    }

    public void executeOnAppEvent(String eventType) {
    }

    // endregion

    /**
     * PrimeGram: the real class's key extension point - an engine that can load and run plugins
     * of some kind. Only {@link PythonPluginsEngine} implements this here; PrimeGram has no other
     * engine.
     */
    public interface PluginsEngine {
        boolean isPlugin(File file, Object messageObject);

        boolean isEngineAvailable();

        void init(Runnable callback);

        void checkDevServer();

        void shutdown(Runnable callback);

        void setPluginEnabled(String pluginId, boolean enabled, Utilities.Callback<String> callback);

        void deletePlugin(String pluginId, Utilities.Callback<String> callback);

        String getPluginPath(String id);

        boolean canOpenInExternalApp();

        void openInExternalApp(String id);

        void sharePlugin(String id);

        List<Object> loadPluginSettings(String id);

        Object getPluginSetting(String pluginId, String key, Object defaultValue);

        void setPluginSetting(String pluginId, String key, Object value);

        void clearPluginSettings(String pluginId);

        Map<String, ?> getAllPluginSettings(String pluginId);

        void executeOnAppEvent(String eventType);
    }

    public static final class HookResult<T> {
        private T result;
        private boolean cancel;
        private boolean isFinal;

        public HookResult(T result, boolean cancel, boolean isFinal) {
            this.result = result;
            this.cancel = cancel;
            this.isFinal = isFinal;
        }

        public T getResult() {
            return result;
        }

        public void setResult(T v) {
            result = v;
        }

        public boolean getCancel() {
            return cancel;
        }

        public void setCancel(boolean v) {
            cancel = v;
        }

        public boolean getIsFinal() {
            return isFinal;
        }

        public void setFinal(boolean v) {
            isFinal = v;
        }
    }

    public static final class PluginValidationResult {
        private Plugin plugin;
        private String error;

        public PluginValidationResult(Plugin plugin, String error) {
            this.plugin = plugin;
            this.error = error;
        }

        public Plugin getPlugin() {
            return plugin;
        }

        public void setPlugin(Plugin p) {
            plugin = p;
        }

        public String getError() {
            return error;
        }

        public void setError(String e) {
            error = e;
        }
    }
}
