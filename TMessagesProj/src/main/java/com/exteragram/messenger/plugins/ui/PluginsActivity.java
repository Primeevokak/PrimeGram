package com.exteragram.messenger.plugins.ui;

import android.content.Context;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.PrimePluginsActivity;

/**
 * PrimeGram: compatibility shim for
 * {@code com.exteragram.messenger.plugins.ui.PluginsActivity} - the exteraGram plugins list
 * screen. A plugin that presents one of these gets PrimeGram's own plugins screen instead of a
 * crash; the two aren't the same UI, but they're the same job.
 */
public class PluginsActivity extends BaseFragment {

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public android.view.View createView(Context context) {
        fragmentView = new FrameLayout(context);
        AndroidUtilitiesRedirect.redirect(this, new PrimePluginsActivity());
        return fragmentView;
    }

    /** Isolated so a redirect failure (fragment already detached, etc.) can't take createView
     *  down with it - the empty frame is still a valid, if useless, view to return. */
    private static final class AndroidUtilitiesRedirect {
        static void redirect(BaseFragment from, BaseFragment to) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (from.getParentActivity() != null) {
                        from.presentFragment(to, true);
                    }
                } catch (Throwable ignore) {
                }
            });
        }
    }
}
