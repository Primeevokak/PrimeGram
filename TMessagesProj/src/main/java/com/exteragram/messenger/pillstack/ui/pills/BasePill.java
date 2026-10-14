package com.exteragram.messenger.pillstack.ui.pills;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;

/**
 * exteraGram API, PrimeGram implementation: the base of a View pill in the pill stack. Same
 * members and behaviour as exteraGram's - a plugin subclasses it (through {@code dynamic_proxy} or
 * a shipped DEX) and implements the abstract hooks.
 *
 * <p>Refresh: with {@link #getRefreshInterval()} above zero, {@link #onUpdateData(boolean)} runs
 * on attach (unless another instance of the same pill refreshed recently - the last-update time is
 * kept per pill id, not per View) and then every interval while the stack is visible. A pill calls
 * {@link #markDataUpdated()} when fresh data arrived.
 */
public abstract class BasePill extends FrameLayout {

    private static final SparseArray<Long> globalLastUpdateTimes = new SparseArray<>();

    private final Runnable autoRefreshRunnable = () -> {
        onUpdateData(false);
        scheduleNextUpdate();
    };
    private final RectF rectF = new RectF();
    private boolean stackVisible = true;

    protected boolean loading;
    protected LoadingDrawable loadingDrawable;
    protected View loadingTargetView;
    protected Theme.ResourcesProvider resourcesProvider;

    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
        setClipChildren(false);
        setClipToPadding(false);
    }

    public abstract int getPillId();

    /** Milliseconds between automatic {@link #onUpdateData} calls; 0 or less for none. */
    public abstract long getRefreshInterval();

    public abstract void onPillClicked();

    public abstract boolean onPillLongClicked();

    /** {@code force} is true when the user asked for the refresh (a tap, a settings change). */
    public abstract void onUpdateData(boolean force);

    public abstract void updateColors();

    public void onPillSelected() {
    }

    public void onPillUnselected() {
    }

    private void scheduleNextUpdate() {
        removeCallbacks(autoRefreshRunnable);
        if (stackVisible) {
            final long interval = getRefreshInterval();
            if (interval > 0) {
                postDelayed(autoRefreshRunnable, interval);
            }
        }
    }

    public boolean isRefreshDue() {
        final long interval = getRefreshInterval();
        if (interval <= 0) {
            return true;
        }
        final long last = globalLastUpdateTimes.get(getPillId(), 0L);
        return last == 0 || SystemClock.elapsedRealtime() - last >= interval;
    }

    public void markDataUpdated() {
        globalLastUpdateTimes.put(getPillId(), SystemClock.elapsedRealtime());
        scheduleNextUpdate();
    }

    public void onStackVisibilityChanged(boolean visible) {
        if (stackVisible == visible) {
            return;
        }
        stackVisible = visible;
        if (!visible) {
            removeCallbacks(autoRefreshRunnable);
        } else if (getRefreshInterval() > 0) {
            if (isRefreshDue()) {
                onUpdateData(false);
            }
            scheduleNextUpdate();
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!stackVisible) {
            return;
        }
        final long interval = getRefreshInterval();
        if (interval <= 0) {
            return;
        }
        final long last = globalLastUpdateTimes.get(getPillId(), 0L);
        if (last != 0) {
            final long since = SystemClock.elapsedRealtime() - last;
            if (since < interval) {
                postDelayed(autoRefreshRunnable, interval - since);
                return;
            }
        }
        autoRefreshRunnable.run();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(autoRefreshRunnable);
    }

    public void animateSizeChange() {
        if (isLaidOut() && getVisibility() == VISIBLE && getParent() != null && getParent().getParent() instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) getParent().getParent(),
                    new TransitionSet().addTransition(new ChangeBounds()).setDuration(300).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT));
        }
    }

    public int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public int getThemedColor(int key, float alpha) {
        return Theme.multAlpha(getThemedColor(key), alpha);
    }

    public void setLoadingTargetView(View view) {
        loadingTargetView = view;
    }

    public void startLoading() {
        loading = true;
        if (loadingDrawable == null) {
            loadingDrawable = new LoadingDrawable(resourcesProvider);
            loadingDrawable.setCallback(this);
            loadingDrawable.setGradientScale(2.0f);
            loadingDrawable.setRadiiDp(14.0f);
            updateLoadingColors();
        }
        loadingDrawable.reset();
        loadingDrawable.resetDisappear();
        loadingDrawable.setAlpha(255);
        invalidate();
    }

    public void stopLoading() {
        loading = false;
        if (loadingDrawable != null) {
            loadingDrawable.disappear();
        }
    }

    public void updateLoadingColors() {
        if (loadingDrawable != null) {
            final int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            loadingDrawable.setColors(Theme.multAlpha(color, 0.05f), Theme.multAlpha(color, 0.15f));
        }
    }

    @Override
    public void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (loadingDrawable != null && (loadingDrawable.getAlpha() > 0 || !loadingDrawable.isDisappearing())) {
            final View target = loadingTargetView != null ? loadingTargetView : this;
            rectF.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
            loadingDrawable.setBounds(rectF);
            loadingDrawable.draw(canvas);
            invalidate();
        }
    }

    @Override
    public boolean verifyDrawable(@NonNull Drawable drawable) {
        return drawable == loadingDrawable || super.verifyDrawable(drawable);
    }
}
