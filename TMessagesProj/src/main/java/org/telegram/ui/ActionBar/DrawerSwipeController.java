package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Property;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.util.Collections;
import java.util.List;

/**
 * PrimeGram: old-layout side drawer mechanics for {@link DrawerLayoutContainer} - swipe tracking,
 * open/close animation, scrim + edge-shadow rendering. Ported from inugram's Kotlin
 * {@code DrawerSwipeController}, itself an extraction of stock pre-redesign
 * {@code DrawerLayoutContainer}'s inline state machine, so the flat-nav patch stays a thin set of
 * delegating overrides instead of carrying the whole thing.
 */
public class DrawerSwipeController {

    private static final int EDGE_SAFE_ZONE_DP = 25;
    private static final int EXCLUSION_HEIGHT_DP = 200;

    public static final Property<DrawerSwipeController, Float> DRAWER_POSITION =
        new Property<DrawerSwipeController, Float>(Float.class, "drawerPosition") {
            @Override
            public Float get(DrawerSwipeController o) {
                return o.drawerPosition;
            }

            @Override
            public void set(DrawerSwipeController o, Float v) {
                o.setDrawerPosition(v);
            }
        };

    private final DrawerLayoutContainer host;

    private FrameLayout drawerLayout;
    private View drawerListView;
    private float drawerPosition;
    private boolean drawerOpened;
    private boolean allowOpenDrawer;
    private boolean maybeStartTracking;
    private boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId;
    private VelocityTracker velocityTracker;
    private boolean beginTrackingSent;
    private AnimatorSet currentAnimation;
    private float scrimOpacity;
    private final Paint scrimPaint = new Paint();
    private Drawable shadowLeft;
    private final Rect exclusionRect = new Rect();
    private final List<Rect> exclusionRects = Collections.singletonList(exclusionRect);

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        updateGestureExclusion();
        return true;
    };

    private final View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {
            v.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            ViewTreeObserver observer = v.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(preDrawListener);
        }
    };

    public DrawerSwipeController(DrawerLayoutContainer host) {
        this.host = host;
    }

    public boolean isDrawerOpened() {
        return drawerOpened;
    }

    public int getDrawerWidth() {
        return drawerLayout != null ? drawerLayout.getMeasuredWidth() : 0;
    }

    public boolean canOpenFromBackGesture() {
        return allowOpenDrawer && !drawerOpened && drawerPosition == 0f
            && getDrawerWidth() > 0 && canTrackGesture();
    }

    public void dragDrawerTo(float value) {
        cancelCurrentAnimation();
        setDrawerPosition(value);
    }

    public void setDrawerLayout(FrameLayout layout, View listView, FrameLayout.LayoutParams lp) {
        drawerLayout = layout;
        drawerListView = listView;
        host.addView(drawerLayout, lp);
        drawerLayout.setVisibility(View.INVISIBLE);
        if (shadowLeft == null) {
            try {
                shadowLeft = host.getResources().getDrawable(R.drawable.header_shadow);
            } catch (Exception ignore) {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            host.addOnAttachStateChangeListener(attachListener);
            if (host.isAttachedToWindow()) host.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        }
    }

    public boolean isDrawerChild(View child) {
        return child != null && child == drawerLayout;
    }

    public void setAllowOpenDrawer(boolean value, boolean animated) {
        allowOpenDrawer = value;
        if (!allowOpenDrawer && drawerPosition != 0f) {
            if (!animated) {
                setDrawerPosition(0f);
                onDrawerAnimationEnd(false);
            } else {
                closeDrawer(true);
            }
        }
        updateGestureExclusion();
    }

    public boolean isAllowOpenDrawer() {
        return allowOpenDrawer;
    }

    public void setDrawerPosition(float value) {
        FrameLayout layout = drawerLayout;
        if (layout == null) return;
        drawerPosition = Math.max(0f, Math.min(value, layout.getMeasuredWidth()));
        layout.setTranslationX(drawerPosition);
        if (drawerPosition > 0 && drawerListView != null && drawerListView.getVisibility() != View.VISIBLE) {
            drawerListView.setVisibility(View.VISIBLE);
        }
        layout.setVisibility(drawerPosition > 0 ? View.VISIBLE : View.INVISIBLE);
        scrimOpacity = drawerPosition / layout.getMeasuredWidth();
        host.invalidate();
    }

    public float getDrawerPosition() {
        return drawerPosition;
    }

    public void openDrawer(boolean fast) {
        FrameLayout layout = drawerLayout;
        if (layout == null || !allowOpenDrawer) return;
        cancelCurrentAnimation();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, DRAWER_POSITION, (float) layout.getMeasuredWidth()));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(fast ? Math.max((int) (200f / layout.getMeasuredWidth() * (layout.getMeasuredWidth() - drawerPosition)), 50) : 250);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                onDrawerAnimationEnd(true);
            }
        });
        animatorSet.start();
        currentAnimation = animatorSet;
    }

    public void closeDrawer(boolean fast) {
        FrameLayout layout = drawerLayout;
        if (layout == null) return;
        cancelCurrentAnimation();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, DRAWER_POSITION, 0f));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(fast ? Math.max((int) (200f / layout.getMeasuredWidth() * drawerPosition), 50) : 250);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                onDrawerAnimationEnd(false);
            }
        });
        animatorSet.start();
        currentAnimation = animatorSet;
    }

    public void cancelCurrentAnimation() {
        if (currentAnimation != null) currentAnimation.cancel();
        currentAnimation = null;
    }

    private void onDrawerAnimationEnd(boolean opened) {
        startedTracking = false;
        currentAnimation = null;
        drawerOpened = opened;
        host.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        syncStatusBar(opened);
        updateGestureExclusion();
    }

    private void syncStatusBar(boolean opened) {
        if (!(host.getContext() instanceof LaunchActivity)) return;
        LaunchActivity activity = (LaunchActivity) host.getContext();
        if (opened) {
            int bgKey = (Theme.hasThemeKey(Theme.key_chats_menuTopBackground) && Theme.getColor(Theme.key_chats_menuTopBackground) != 0)
                ? Theme.key_chats_menuTopBackground : Theme.key_chats_menuTopBackgroundCats;
            AndroidUtilities.setLightStatusBar(activity.getWindow(), ColorUtils.calculateLuminance(Theme.getColor(bgKey)) > 0.7);
        } else {
            activity.checkSystemBarColors(false, true, false);
        }
    }

    private boolean canTrackGesture() {
        if (drawerOpened || drawerPosition > 0) return true;
        if (host.parentActionBarLayout.getFragmentStack().size() != 1) return false;
        BaseFragment top = host.parentActionBarLayout.getLastFragment();
        if (top instanceof DialogsActivity) {
            DialogsActivity dialogsActivity = (DialogsActivity) top;
            if (dialogsActivity.searchIsShowed) return false;
            if (dialogsActivity.rightSlidingDialogContainer != null && dialogsActivity.rightSlidingDialogContainer.hasFragment()) return false;
        }
        return true;
    }

    private boolean tabsOwnHorizontalSwipe() {
        BaseFragment top = host.parentActionBarLayout.getLastFragment();
        if (!(top instanceof DialogsActivity)) return false;
        org.telegram.ui.Components.FilterTabsView tabs = ((DialogsActivity) top).filterTabsView;
        return tabs != null && tabs.getVisibility() == View.VISIBLE && !tabs.isFirstTabSelected();
    }

    private void updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        int height = host.getHeight();
        if (!allowOpenDrawer || height <= 0 || !canTrackGesture()) {
            host.setSystemGestureExclusionRects(Collections.emptyList());
            return;
        }
        int bottom = Math.max(0, height - AndroidUtilities.navigationBarHeight);
        exclusionRect.set(
            0,
            Math.max(0, bottom - AndroidUtilities.dp(EXCLUSION_HEIGHT_DP)),
            AndroidUtilities.dp(EDGE_SAFE_ZONE_DP),
            bottom
        );
        host.setSystemGestureExclusionRects(exclusionRects);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        FrameLayout layout = drawerLayout;
        if (layout == null || host.parentActionBarLayout.checkTransitionAnimation()) {
            if (startedTracking || maybeStartTracking) {
                startedTracking = false;
                maybeStartTracking = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
            return false;
        }
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && maybeStartTracking) {
            maybeStartTracking = false;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
        if (drawerOpened && ev != null && ev.getX() > drawerPosition && !startedTracking) {
            if (ev.getAction() == MotionEvent.ACTION_UP) closeDrawer(false);
            return true;
        }
        if (allowOpenDrawer && canTrackGesture()) {
            if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE)
                && !startedTracking && !maybeStartTracking) {
                startedTrackingX = (int) ev.getX();
                startedTrackingY = (int) ev.getY();
                startedTrackingPointerId = ev.getPointerId(0);
                maybeStartTracking = true;
                cancelCurrentAnimation();
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(ev);
            } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE
                && ev.getPointerId(0) == startedTrackingPointerId) {
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(ev);
                float dx = ev.getX() - startedTrackingX;
                float dy = Math.abs(ev.getY() - startedTrackingY);
                boolean inEdgeZone = startedTrackingX <= AndroidUtilities.dp(EDGE_SAFE_ZONE_DP);
                boolean openAngleOk = inEdgeZone || dx / 3f > dy;
                boolean openSwipe = dx > 0 && openAngleOk && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.2f, true)
                    && (!tabsOwnHorizontalSwipe() || inEdgeZone);
                boolean closeSwipe = drawerOpened && dx < 0 && Math.abs(dx) >= dy && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.4f, true);
                if (maybeStartTracking && !startedTracking && (openSwipe || closeSwipe)) {
                    maybeStartTracking = false;
                    startedTracking = true;
                    beginTrackingSent = false;
                    setDrawerPosition(drawerPosition + dx);
                    startedTrackingX = (int) ev.getX();
                    host.requestDisallowInterceptTouchEvent(true);
                } else if (startedTracking) {
                    if (!beginTrackingSent) {
                        beginTrackingSent = true;
                    }
                    setDrawerPosition(drawerPosition + dx);
                    startedTrackingX = (int) ev.getX();
                }
            } else if (ev == null || (ev.getPointerId(0) == startedTrackingPointerId
                && (ev.getAction() == MotionEvent.ACTION_CANCEL
                    || ev.getAction() == MotionEvent.ACTION_UP
                    || ev.getAction() == MotionEvent.ACTION_POINTER_UP))) {
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                if (ev != null) velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000);
                if (startedTracking || (drawerPosition != 0f && drawerPosition != layout.getMeasuredWidth())) {
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    boolean back = (drawerPosition < layout.getMeasuredWidth() / 2f
                        && (velX < 3500 || Math.abs(velX) < Math.abs(velY)))
                        || (velX < 0 && Math.abs(velX) >= 3500);
                    if (!back) openDrawer(!drawerOpened && Math.abs(velX) >= 3500);
                    else closeDrawer(drawerOpened && Math.abs(velX) >= 3500);
                }
                startedTracking = false;
                maybeStartTracking = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
        } else {
            if (ev == null || (ev.getPointerId(0) == startedTrackingPointerId
                && (ev.getAction() == MotionEvent.ACTION_CANCEL
                    || ev.getAction() == MotionEvent.ACTION_UP
                    || ev.getAction() == MotionEvent.ACTION_POINTER_UP))) {
                startedTracking = false;
                maybeStartTracking = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
        }
        return startedTracking;
    }

    public void onParentDisallowIntercept() {
        if (startedTracking) return;
        onTouchEvent(null);
    }

    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (drawerLayout == null) {
            return host.inu_superDrawChild(canvas, child, drawingTime);
        }
        int height = host.getHeight();
        boolean drawingContent = child != drawerLayout;
        int lastVisibleChild = 0;
        int clipLeft = 0;
        int clipRight = host.getWidth();

        int restoreCount = canvas.save();
        if (drawingContent) {
            int childCount = host.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View v = host.getChildAt(i);
                if (v.getVisibility() == View.VISIBLE && v != drawerLayout) {
                    lastVisibleChild = i;
                }
                if (v == child || v.getVisibility() != View.VISIBLE || v != drawerLayout || v.getHeight() < height) {
                    continue;
                }
                int vright = (int) Math.ceil(v.getX()) + v.getMeasuredWidth();
                if (vright > clipLeft) clipLeft = vright;
            }
            if (clipLeft != 0) {
                canvas.clipRect(clipLeft - AndroidUtilities.dp(1), 0, clipRight, host.getHeight());
            }
        }
        boolean result = host.inu_superDrawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);

        if (scrimOpacity > 0 && drawingContent) {
            if (host.indexOfChild(child) == lastVisibleChild) {
                scrimPaint.setColor((int) (0x99 * scrimOpacity) << 24);
                canvas.drawRect(clipLeft, 0, clipRight, host.getHeight(), scrimPaint);
            }
        } else if (shadowLeft != null && drawerPosition > 0) {
            float alpha = Math.max(0f, Math.min(drawerPosition / AndroidUtilities.dp(20), 1f));
            if (alpha != 0f) {
                shadowLeft.setBounds((int) drawerPosition, child.getTop(), (int) drawerPosition + shadowLeft.getIntrinsicWidth(), child.getBottom());
                shadowLeft.setAlpha((int) (0xff * alpha));
                shadowLeft.draw(canvas);
            }
        }
        return result;
    }
}
