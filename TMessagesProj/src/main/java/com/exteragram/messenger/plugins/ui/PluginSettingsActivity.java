package com.exteragram.messenger.plugins.ui;

import android.content.Context;
import android.widget.FrameLayout;

import com.exteragram.messenger.plugins.Plugin;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.PrimePluginsActivity;

/**
 * PrimeGram: compatibility shim for
 * {@code com.exteragram.messenger.plugins.ui.PluginSettingsActivity} - see
 * {@link PluginsActivity} for the reasoning. PrimeGram has no per-plugin settings screen
 * addressed by exteraGram's own {@link Plugin} identity, so this redirects to the plugins list
 * rather than a specific plugin's settings - closer than a crash, not the same screen.
 */
public class PluginSettingsActivity extends BaseFragment {

    public PluginSettingsActivity(Plugin plugin) {
        super();
    }

    public PluginSettingsActivity(Object plugin) {
        super();
    }

    @Override
    public android.view.View createView(Context context) {
        fragmentView = new FrameLayout(context);
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            try {
                if (getParentActivity() != null) {
                    presentFragment(new PrimePluginsActivity(), true);
                }
            } catch (Throwable ignore) {
            }
        });
        return fragmentView;
    }
}
