package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PrimeMusicHomeView;

/**
 * PrimeGram Music: the custom "Музыка" tab's root fragment. Phase 3 (this class):
 * empty shell wired into {@code MainTabsActivity}'s tab system. Phase 4 fleshes out
 * the actual home screen (recommendations/playlists/liked songs, dark visual style)
 * inside {@link #createView}.
 */
public class PrimeMusicActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);

        PrimeMusicHomeView homeView = new PrimeMusicHomeView(context, this);
        fragmentView = homeView;
        return fragmentView;
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }
}
