package org.telegram.ui;

import android.content.Context;
import android.view.MotionEvent;
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
public class PrimeMusicActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private PrimeMusicHomeView homeView;

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);

        homeView = new PrimeMusicHomeView(context, this);
        fragmentView = homeView;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Picks up anything changed on a pushed screen (a deleted/renamed playlist, a
        // synced library, a like toggled elsewhere) once the user comes back here -
        // reloadData() itself is cheap (async DB read, see its own doc).
        if (homeView != null) {
            homeView.reloadData();
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }

    // MainTabsActivity refuses a tab-swipe away from any fragment that doesn't opt in
    // here (canScrollInternal defaults to false for non-delegates) - every other real
    // tab (Chats/Feed/Settings/Profile) already implements this; Music never did, which
    // is why swiping INTO it worked (that's gated by canSwipeToPosition, not this) but
    // swiping back OUT never did. Unconditional true was too broad, though - the Home
    // screen has its own horizontal carousels (recommended tracks, playlists), and a
    // touch over either of those needs to stay theirs, not get claimed by the outer
    // tab-swipe fighting for the same horizontal drag.
    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        if (homeView != null && homeView.isTouchOverHorizontalCarousel(ev.getX(), ev.getY())) {
            return false;
        }
        return true;
    }
}
