package org.telegram.messenger.plugins;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

/**
 * PrimeGram: notices a plugin that has not returned.
 *
 * <p>Plugin code runs on one serial queue - see {@link PrimePythonEngine} - which means a single
 * plugin hook that never returns does not just hang itself, it hangs every other plugin's turn
 * behind it, silently, with nothing on screen to say why. Nothing on that queue can report its own
 * hang: the thread that would report it is the one that is stuck. So this checks in from outside,
 * on the UI thread, on a timer {@link org.telegram.ui.LaunchActivity} drives while the app is in
 * the foreground - {@link #beginDispatch}/{@link #endDispatch} bracket one plugin's turn from the
 * Python side, and {@link #check} looks at how long the current bracket has been open.
 */
public final class PrimePluginWatchdog {

    private static final long THRESHOLD_MS = 12_000;

    private static volatile String currentPluginId;
    private static volatile long dispatchStartTime;
    private static volatile boolean warned;

    private PrimePluginWatchdog() {
    }

    public static void beginDispatch(String pluginId) {
        currentPluginId = pluginId;
        dispatchStartTime = SystemClock.elapsedRealtime();
        warned = false;
    }

    public static void endDispatch() {
        final String pluginId = currentPluginId;
        currentPluginId = null;
        if (warned && pluginId != null) {
            // It came back. The warning already shown stays shown - dismissing it here from a
            // background thread mid-tap would be its own kind of confusing - but the flag clears,
            // so the plugin list stops calling it stuck.
            warned = false;
            final PrimePlugin plugin = PrimePluginsController.getInstance().findById(pluginId);
            if (plugin != null) {
                plugin.setNotResponding(false);
            }
        }
    }

    /** Called every few seconds from the UI thread. Cheap when nothing is running - two field
     *  reads and a comparison - which is what it costs on every check that is not the one that
     *  finds something. */
    public static void check() {
        final String pluginId = currentPluginId;
        if (pluginId == null || warned) {
            return;
        }
        if (SystemClock.elapsedRealtime() - dispatchStartTime < THRESHOLD_MS) {
            return;
        }
        warned = true;
        final PrimePlugin plugin = PrimePluginsController.getInstance().findById(pluginId);
        if (plugin != null) {
            plugin.setNotResponding(true);
        }
        showDialog(pluginId, plugin);
    }

    private static void showDialog(String pluginId, PrimePlugin plugin) {
        final LaunchActivity activity = LaunchActivity.instance;
        if (activity == null) {
            return;
        }
        final String name = plugin != null ? plugin.name() : pluginId;
        new AlertDialog.Builder(activity)
                .setTitle("Плагин не отвечает")
                .setMessage("«" + name + "» не отвечает уже больше десяти секунд. Пока он не "
                        + "вернёт управление, остальные плагины тоже стоят в очереди за ним.")
                .setPositiveButton("Отключить", (dialog, which) -> {
                    if (plugin != null) {
                        PrimePluginsController.getInstance().setEnabled(activity, plugin, false);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }
}
