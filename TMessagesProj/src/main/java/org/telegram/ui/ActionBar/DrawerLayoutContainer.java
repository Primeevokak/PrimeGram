/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

public class DrawerLayoutContainer extends FrameLayout {

    public INavigationLayout parentActionBarLayout;

    /** PrimeGram: set by {@link org.telegram.messenger.DrawerHelper#setupMainFragment} when the
     *  classic drawer navigation is on - see {@link DrawerSwipeController}. */
    public DrawerSwipeController inu_drawer;

    public boolean inu_superDrawChild(Canvas canvas, View child, long drawingTime) {
        return super.drawChild(canvas, child, drawingTime);
    }

    public void setDrawerLayout(FrameLayout layout, View listView) {
        if (inu_drawer == null) inu_drawer = new DrawerSwipeController(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, android.view.Gravity.LEFT);
        inu_drawer.setDrawerLayout(layout, listView, lp);
    }

    public void moveDrawerByX(float dx) {
        if (inu_drawer != null) inu_drawer.dragDrawerTo(inu_drawer.getDrawerPosition() + dx);
    }

    public void setDrawerPosition(float value) {
        if (inu_drawer != null) inu_drawer.setDrawerPosition(value);
    }

    public float getDrawerPosition() {
        return inu_drawer != null ? inu_drawer.getDrawerPosition() : 0f;
    }

    public void cancelCurrentAnimation() {
        if (inu_drawer != null) inu_drawer.cancelCurrentAnimation();
    }

    public void openDrawer(boolean fast) {
        if (inu_drawer != null) inu_drawer.openDrawer(fast);
    }

    public void closeDrawer(boolean fast) {
        if (inu_drawer != null) inu_drawer.closeDrawer(fast);
    }

    public void closeDrawer() {
        closeDrawer(false);
    }

    public void setAllowOpenDrawer(boolean value, boolean animated) {
        if (inu_drawer != null) inu_drawer.setAllowOpenDrawer(value, animated);
    }

    public boolean isAllowOpenDrawer() {
        return inu_drawer != null && inu_drawer.isAllowOpenDrawer();
    }

    public boolean isDrawerOpened() {
        return inu_drawer != null && inu_drawer.isDrawerOpened();
    }

    public void presentFragment(BaseFragment fragment) {
        if (parentActionBarLayout != null) parentActionBarLayout.presentFragment(fragment);
    }

    public INavigationLayout getParentActionBarLayout() {
        return parentActionBarLayout;
    }

    private int behindKeyboardColor;

    public void setBehindKeyboardColor(int color) {
        behindKeyboardColor = color;
    }

    public int getBehindKeyboardColor() {
        return behindKeyboardColor;
    }

    private boolean hasCutout;

    private boolean inLayout;

    private boolean firstLayout = true;

    private boolean keyboardVisibility;
    private int imeHeight;

    /** @noinspection deprecation*/
    public DrawerLayoutContainer(Context context) {
        super(context);

        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                boolean newKeyboardVisibility = insets.isVisible(WindowInsetsCompat.Type.ime());
                int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                if (keyboardVisibility != newKeyboardVisibility || this.imeHeight != imeHeight) {
                    keyboardVisibility = newKeyboardVisibility;
                    this.imeHeight = imeHeight;
                    requestLayout();
                }
            }
            final DrawerLayoutContainer drawerLayoutContainer = (DrawerLayoutContainer) v;
            if (AndroidUtilities.statusBarHeight != insets.getSystemWindowInsetTop()) {
                drawerLayoutContainer.requestLayout();
            }
            int newTopInset = insets.getSystemWindowInsetTop();
            if ((newTopInset != 0 || AndroidUtilities.isInMultiwindow || firstLayout) && AndroidUtilities.statusBarHeight != newTopInset) {
                AndroidUtilities.statusBarHeight = newTopInset;
            }
            firstLayout = false;
            drawerLayoutContainer.setWillNotDraw(insets.getSystemWindowInsetTop() <= 0 && getBackground() == null);

            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutoutCompat cutout = insets.getDisplayCutout();
                hasCutout = cutout != null && !cutout.getBoundingRects().isEmpty();
            }
            invalidate();

            return onApplyWindowInsets(v, insets);
        });
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public void setParentActionBarLayout(INavigationLayout layout) {
        parentActionBarLayout = layout;
    }

    private boolean drawCurrentPreviewFragmentAbove;

    public boolean isDrawCurrentPreviewFragmentAbove() {
        return drawCurrentPreviewFragmentAbove;
    }

    public void setDrawCurrentPreviewFragmentAbove(boolean value) {
        drawCurrentPreviewFragmentAbove = value;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (inu_drawer != null) return inu_drawer.onTouchEvent(ev);
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return parentActionBarLayout.checkTransitionAnimation() || onTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (inu_drawer != null) inu_drawer.onParentDisallowIntercept();
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            try {
                if (inu_drawer != null && inu_drawer.isDrawerChild(child)) {
                    child.layout(-child.getMeasuredWidth(), lp.topMargin + getPaddingTop(), 0, lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
                } else {
                    child.layout(lp.leftMargin, lp.topMargin + getPaddingTop(), lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (BuildVars.DEBUG_VERSION) {
                    throw e;
                }
            }
        }
        inLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!inLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!BuildVars.USE_LEGACY_SYSTEM_INSETS) {
            final WindowInsetsCompat insetsCompat = ViewCompat.getRootWindowInsets(this);
            if (insetsCompat != null) {
                final Insets systemInsets = insetsCompat.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());

                AndroidUtilities.statusBarHeight = systemInsets.top;
                AndroidUtilities.navigationBarHeight = systemInsets.bottom;
            }
        }

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);
        final int newSize = heightSize
            - AndroidUtilities.statusBarHeight
            - AndroidUtilities.navigationBarHeight;

        if (newSize > 0 && newSize < 4096) {
            AndroidUtilities.displaySize.y = newSize;
        }

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int contentWidthSpec;
            if (inu_drawer != null && inu_drawer.isDrawerChild(child)) {
                contentWidthSpec = MeasureSpec.makeMeasureSpec(lp.width > 0 ? lp.width : widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
            } else {
                contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
            }
            final int contentHeightSpec;
            if (lp.height > 0) {
                contentHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            } else {
                contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
            }
            if (child instanceof ActionBarLayout) {
                ActionBarLayout actionBarLayout = (ActionBarLayout) child;
                //fix keyboard measuring
                if (actionBarLayout.storyViewerAttached()) {
                    child.forceLayout();
                }
            }
            child.measure(contentWidthSpec, contentHeightSpec);
        }
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (inu_drawer != null) {
            return inu_drawer.drawChild(canvas, child, drawingTime);
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (lastWindowInsetsCompat == null) {
            return;
        }

        final Insets insets = lastWindowInsetsCompat.getInsets(WindowInsetsCompat.Type.ime()
            | WindowInsetsCompat.Type.systemBars()
            | WindowInsetsCompat.Type.displayCutout());

        if (insets.bottom > 0) {
            canvas.drawRect(
                0,
                getMeasuredHeight() - insets.bottom,
                getMeasuredWidth(),
                getMeasuredHeight(),
                internalNavbarPaint
            );
        }

        if (hasCutout) {
            final int left = insets.left;
            if (left != 0) {
                canvas.drawRect(0, 0, left, getMeasuredHeight(), Theme.fillingPaint(Color.BLACK));
            }
            final int right = insets.right;
            if (right != 0) {
                canvas.drawRect(right, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.fillingPaint(Color.BLACK));
            }
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private final Paint internalNavbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Paint getInternalNavbarPaint() {
        return internalNavbarPaint;
    }

    public void setInternalNavigationBarColor(int color) {
        if (internalNavbarPaint.getColor() != color) {
            internalNavbarPaint.setColor(color);
            invalidate();

            for (int a = 0, N = getChildCount(); a < N; a++) {
                getChildAt(a).invalidate();
            }
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (lastWindowInsetsCompat != null) {
            dispatchApplyWindowInsetsInternal(child, lastWindowInsetsCompat);
        }
    }

    private @Nullable WindowInsetsCompat lastWindowInsetsCompat;

    private void dispatchApplyWindowInsetsInternal(View child, WindowInsetsCompat insets) {
        boolean canApplyInsets = child instanceof ActionBarLayout || child.getTag() == null;
        if (!canApplyInsets) {
            return;
        }

        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final Insets systemInsetsWithIme = insets.getInsets(WindowInsetsCompat.Type.ime()
                | WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());

        final boolean changed = lp.topMargin != 0 || lp.bottomMargin != 0
                || lp.leftMargin != systemInsetsWithIme.left
                || lp.rightMargin != systemInsetsWithIme.right;

        if (changed) {
            lp.leftMargin = systemInsetsWithIme.left;
            lp.topMargin = 0;
            lp.rightMargin = systemInsetsWithIme.right;
            lp.bottomMargin = 0;

            child.requestLayout();
        }

        final WindowInsetsCompat consumed = insets.inset(
                lp.leftMargin, lp.topMargin,
                lp.rightMargin, lp.bottomMargin);

        ViewCompat.dispatchApplyWindowInsets(child, consumed);
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        lastWindowInsetsCompat = insets;

        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            dispatchApplyWindowInsetsInternal(child, insets);
        }

        invalidate();
        return WindowInsetsCompat.CONSUMED;
    }
}
