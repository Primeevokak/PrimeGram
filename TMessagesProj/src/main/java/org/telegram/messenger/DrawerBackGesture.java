package org.telegram.messenger;

import android.os.Build;
import android.view.animation.DecelerateInterpolator;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;

import androidx.annotation.RequiresApi;

import org.telegram.ui.ActionBar.DrawerSwipeController;
import org.telegram.ui.LaunchActivity;

/**
 * PrimeGram: turns a left-edge system back gesture into a drawer open when
 * {@link DrawerHelper#isEnabled()}, covering the part of the edge that
 * {@link DrawerSwipeController}'s gesture exclusion band can't reach - the platform caps
 * exclusion at 200dp per edge. The right edge keeps plain back. Ported from inugram's Kotlin
 * {@code DrawerBackGesture}.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public final class DrawerBackGesture {

    private static final DecelerateInterpolator INTERPOLATOR = new DecelerateInterpolator();

    private DrawerBackGesture() {
    }

    public static OnBackAnimationCallback wrap(LaunchActivity activity, OnBackAnimationCallback inner) {
        return DrawerHelper.isEnabled() ? new Callback(activity, inner) : inner;
    }

    private static class Callback implements OnBackAnimationCallback {
        private final LaunchActivity activity;
        private final OnBackAnimationCallback inner;
        private boolean latched;

        Callback(LaunchActivity activity, OnBackAnimationCallback inner) {
            this.activity = activity;
            this.inner = inner;
        }

        private DrawerSwipeController getController() {
            return activity.drawerLayoutContainer != null ? activity.drawerLayoutContainer.inu_drawer : null;
        }

        @Override
        public void onBackStarted(BackEvent backEvent) {
            DrawerSwipeController controller = getController();
            latched = backEvent.getSwipeEdge() == BackEvent.EDGE_LEFT && controller != null && controller.canOpenFromBackGesture();
            if (!latched) inner.onBackStarted(backEvent);
        }

        @Override
        public void onBackProgressed(BackEvent backEvent) {
            if (!latched) {
                inner.onBackProgressed(backEvent);
                return;
            }
            DrawerSwipeController controller = getController();
            if (controller == null) return;
            float progress = INTERPOLATOR.getInterpolation(Math.max(0f, Math.min(1f, backEvent.getProgress())));
            controller.dragDrawerTo(progress * controller.getDrawerWidth());
        }

        @Override
        public void onBackInvoked() {
            if (!latched) {
                inner.onBackInvoked();
                return;
            }
            latched = false;
            DrawerSwipeController controller = getController();
            if (controller != null) controller.openDrawer(false);
        }

        @Override
        public void onBackCancelled() {
            if (!latched) {
                inner.onBackCancelled();
                return;
            }
            latched = false;
            DrawerSwipeController controller = getController();
            if (controller != null) controller.closeDrawer(false);
        }
    }
}
