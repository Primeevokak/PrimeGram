package org.telegram.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import io.flutter.embedding.android.FlutterView;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;

import org.telegram.messenger.PrimeFlutterSupport;
import org.telegram.messenger.music.PrimeMusicFlutterEngineHolder;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * PrimeGram Music: the "Музыка" tab's root fragment - Phase 1 of the Flutter rewrite (see
 * the session plan). Its content is now a {@link FlutterView} attached to
 * {@link PrimeMusicFlutterEngineHolder}'s dedicated engine
 * ({@code prime_flutter_module/lib/music_main.dart}), not a native
 * {@code PrimeMusicHomeView} - that class stays in the tree untouched for now (Phase 2/3
 * will move its remaining screens over one at a time), just no longer referenced here.
 *
 * <p>The engine outlives this fragment: {@link #onFragmentDestroy} detaches the view but
 * does not destroy the engine, so switching away from and back to the tab re-attaches
 * instantly instead of a cold Dart restart.
 */
public class PrimeMusicActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private FlutterView flutterView;

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);

        if (!PrimeFlutterSupport.isSupported()) {
            // No Flutter on this ABI (see PrimeFlutterSupport's own doc) - an empty fragment
            // view is an honest degrade until this tab gets a native fallback; the tab is
            // opt-in/hidden by default, so this is not a regression for the common case.
            fragmentView = new View(context);
            return fragmentView;
        }

        PrimeMusicFlutterEngineHolder.warmUp(context);
        FlutterEngine engine = FlutterEngineCache.getInstance().get(PrimeMusicFlutterEngineHolder.ENGINE_ID);

        flutterView = new FlutterView(context);
        if (engine != null) {
            flutterView.attachToFlutterEngine(engine);
        }
        fragmentView = flutterView;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        FlutterEngine engine = FlutterEngineCache.getInstance().get(PrimeMusicFlutterEngineHolder.ENGINE_ID);
        if (engine != null) {
            engine.getLifecycleChannel().appIsResumed();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        FlutterEngine engine = FlutterEngineCache.getInstance().get(PrimeMusicFlutterEngineHolder.ENGINE_ID);
        if (engine != null) {
            engine.getLifecycleChannel().appIsInactive();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (flutterView != null) {
            flutterView.detachFromFlutterEngine();
            flutterView = null;
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }

    // MainTabsActivity refuses a tab-swipe away from any fragment that doesn't opt in here
    // (canScrollInternal defaults to false for non-delegates). The Phase 1 Dart home screen
    // is a plain vertical list with no horizontal carousel, so there is nothing to fight the
    // outer tab-swipe for yet - unlike the old native PrimeMusicHomeView, which needed
    // isTouchOverHorizontalCarousel() here. Revisit once Phase 2 adds the wave card's own
    // horizontal mood-chip strip in Dart - Flutter's own touch consumption inside FlutterView
    // will need the same kind of negotiation, and this method is likely not enough on its
    // own to express it (flagged as an open risk in the plan, not solved here).
    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return true;
    }
}
