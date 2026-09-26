package com.exteragram.messenger.plugins.ui;

import android.content.Context;
import android.widget.FrameLayout;

import com.exteragram.messenger.plugins.Plugin;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.PrimePluginsActivity;

/**
 * PrimeGram: compatibility shim for {@code com.exteragram.messenger.plugins.ui.PluginSettingsActivity}.
 * Plugins open another plugin's settings with {@link #of(String)} (or construct this with its
 * {@link Plugin}); either way it becomes PrimeGram's own settings screen for that plugin id, and
 * falls back to the plugins list only when no such plugin is installed.
 */
public class PluginSettingsActivity extends BaseFragment {

    private final String pluginId;

    public PluginSettingsActivity(Plugin plugin) {
        super();
        this.pluginId = plugin == null ? null : plugin.getId();
    }

    public PluginSettingsActivity(Object plugin) {
        super();
        this.pluginId = plugin instanceof Plugin ? ((Plugin) plugin).getId() : plugin instanceof String ? (String) plugin : null;
    }

    /** exteraGram: the settings screen of the plugin with this id, ready to present. */
    public static BaseFragment of(String pluginId) {
        final org.telegram.messenger.plugins.PrimePlugin plugin = pluginId == null ? null
                : org.telegram.messenger.plugins.PrimePluginsController.getInstance().findById(pluginId);
        return plugin != null ? new org.telegram.ui.PrimePluginSettingsActivity(plugin) : new PrimePluginsActivity();
    }

    @Override
    public android.view.View createView(Context context) {
        fragmentView = new FrameLayout(context);
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            try {
                if (getParentActivity() != null) {
                    presentFragment(of(pluginId), true);
                }
            } catch (Throwable ignore) {
            }
        });
        return fragmentView;
    }
}
