package org.telegram.messenger.plugins;

import android.view.View;

import com.chaquo.python.PyObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

/**
 * PrimeGram: the JSON/View bridge a plugin's settings screen talks to.
 *
 * <p>Split out of {@link PrimePluginsController} - nothing here touches the catalogue (what is
 * installed, enabled, or has crashed), it only ever shuttles a request to {@code _prime_loader}
 * on the engine queue and hands the reply back on the main thread. Stateless, so unlike the
 * controller there is nothing here worth a singleton beyond keeping the call sites the same shape.
 */
final class PrimePluginSettingsBridge {

    private static volatile PrimePluginSettingsBridge instance;

    private PrimePluginSettingsBridge() {
    }

    static PrimePluginSettingsBridge getInstance() {
        PrimePluginSettingsBridge local = instance;
        if (local == null) {
            synchronized (PrimePluginSettingsBridge.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimePluginSettingsBridge();
                }
            }
        }
        return local;
    }

    /**
     * Asks a plugin what settings it offers. The answer is JSON, described by the plugin's own
     * {@code create_settings}, and it arrives on the main thread because a settings screen is the
     * only thing that wants it.
     *
     * <p>A plugin that is switched off has no Python side and therefore no rows; the screen shows
     * its stored values through {@link PrimePluginStore} regardless, which is the point of keeping
     * storage in Java.
     */
    void requestSettings(String pluginId, org.telegram.messenger.Utilities.Callback<String> callback) {
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
     * The screen a {@code create_sub_fragment} row opens, as JSON: {@code {"title", "rows"}}.
     * {@code parentPath} is the screen the row itself lives on - {@code ""} for the plugin's own
     * screen, or whatever an earlier call here returned as a child path - because the same row
     * index means a different row on every screen.
     */
    void requestSubSettings(String pluginId, String parentPath, int index,
                             org.telegram.messenger.Utilities.Callback<String> callback) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            String json = "{\"title\":\"\",\"rows\":[]}";
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    json = loader.callAttr("build_sub_settings", pluginId, parentPath, index).toString();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final String result = json;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    /**
     * The live {@link View} for one {@code "custom"} row - the one piece of a settings screen that
     * cannot ride along in {@link #requestSettings}'s JSON, because JSON cannot hold a Java object.
     * Fetched separately, once the row is actually about to be drawn. {@code path} is the screen
     * the row lives on, same as in {@link #requestSubSettings}.
     */
    void requestCustomView(String pluginId, String path, int index, org.telegram.messenger.Utilities.Callback<View> callback) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            View view = null;
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    final PyObject result = loader.callAttr("build_custom_view", pluginId, path, index);
                    if (result != null && result.toJava(Object.class) != null) {
                        view = result.toJava(View.class);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final View result = view;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    /**
     * What the downloader has fetched, as a JSON object of name to version.
     *
     * <p>Worth showing because these arrive without the user asking: a plugin declares what it
     * needs and it appears. Somewhere has to answer "what did this app download onto my phone".
     */
    void requestLibraries(org.telegram.messenger.Utilities.Callback<String> callback) {
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
    void clearLibraries(Runnable done) {
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
    void notifySettingChanged(String pluginId, String path, int index, String valueJson) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("on_setting_changed", pluginId, path, index, valueJson);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    void notifySettingClicked(String pluginId, String path, int index) {
        PrimePythonEngine.getInstance().queue().postRunnable(() -> {
            try {
                final PyObject loader = PrimePythonEngine.getInstance().module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("on_setting_clicked", pluginId, path, index);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }
}
