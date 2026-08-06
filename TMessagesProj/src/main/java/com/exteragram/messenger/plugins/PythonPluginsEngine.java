package com.exteragram.messenger.plugins;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.plugins.PrimePluginStore;
import org.telegram.messenger.plugins.PrimePluginsController;
import org.telegram.messenger.plugins.PrimePythonEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PrimeGram: compatibility shim for {@code com.exteragram.messenger.plugins.PythonPluginsEngine}.
 * The real class's source was not recoverable (not present in the decompile PrimeGram's stub
 * work is based on); this implements the {@link PluginsController.PluginsEngine} contract its
 * call sites require, backed by {@link PrimePluginStore} wherever the operation is settings, and
 * as a safe no-op wherever it is exteraGram-backend-specific (external-app opening, sharing).
 */
public final class PythonPluginsEngine implements PluginsController.PluginsEngine {

    public static final PythonPluginsEngine INSTANCE = new PythonPluginsEngine();

    public PythonPluginsEngine() {
    }

    public String getSDK_VERSION() {
        return "1.4.5.0";
    }

    @Override
    public boolean isPlugin(File file, Object messageObject) {
        return file != null && file.getName().endsWith(PrimePluginsController.EXTENSION);
    }

    @Override
    public boolean isEngineAvailable() {
        return true;
    }

    @Override
    public void init(Runnable callback) {
        if (callback != null) callback.run();
    }

    @Override
    public void checkDevServer() {
    }

    @Override
    public void shutdown(Runnable callback) {
        if (callback != null) callback.run();
    }

    @Override
    public void setPluginEnabled(String pluginId, boolean enabled, Utilities.Callback<String> callback) {
        PrimePluginStore.setEnabled(pluginId, enabled);
        if (callback != null) callback.run(null);
    }

    @Override
    public void deletePlugin(String pluginId, Utilities.Callback<String> callback) {
        PrimePluginStore.forget(pluginId);
        if (callback != null) callback.run(null);
    }

    @Override
    public String getPluginPath(String id) {
        return new File(PrimePluginsController.pluginsDir(), id + PrimePluginsController.EXTENSION).getAbsolutePath();
    }

    @Override
    public boolean canOpenInExternalApp() {
        return false;
    }

    @Override
    public void openInExternalApp(String id) {
        FileLog.d("PythonPluginsEngine.openInExternalApp (compat, no-op): " + id);
    }

    @Override
    public void sharePlugin(String id) {
    }

    @Override
    public List<Object> loadPluginSettings(String id) {
        return new ArrayList<>();
    }

    @Override
    public Object getPluginSetting(String pluginId, String key, Object defaultValue) {
        if (defaultValue instanceof Boolean) {
            return PrimePluginStore.getBoolean(pluginId, key, (Boolean) defaultValue);
        }
        if (defaultValue instanceof Integer) {
            return PrimePluginStore.getInt(pluginId, key, (Integer) defaultValue);
        }
        return PrimePluginStore.getString(pluginId, key, defaultValue == null ? null : defaultValue.toString());
    }

    @Override
    public void setPluginSetting(String pluginId, String key, Object value) {
        PrimePluginStore.put(pluginId, key, value);
    }

    @Override
    public void clearPluginSettings(String pluginId) {
        PrimePluginStore.forget(pluginId);
    }

    @Override
    public Map<String, ?> getAllPluginSettings(String pluginId) {
        return new HashMap<>();
    }

    @Override
    public void executeOnAppEvent(String eventType) {
    }

    /**
     * The one method the real exteraGram class exposes that a plugin actually needs to *hook*
     * (as opposed to call) - zwylib's own cleanup relies on knowing the instant any plugin gets
     * unloaded, and Xposed can only intercept a call that genuinely happens through this exact
     * method, not one that merely exists. So unlike everything else in this class, which is
     * either backed by {@link PrimePluginStore} or a safe no-op, this is now
     * {@link PrimePluginsController}'s real, single unload path - it forwards into
     * {@code _prime_loader.unload_plugin} itself, and {@code PrimePluginsController.unloadFromPython}
     * calls this rather than duplicating the call, so every unload - whichever of our own code
     * paths triggered it - is visible to a hook installed here.
     */
    public void unloadPlugin(String pluginId) {
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
}
