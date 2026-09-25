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
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

public class DrawerLayoutContainer extends FrameLayout {

    public INavigationLayout parentActionBarLayout;
    private ActionBarLayout actionBarLayout;

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
    private boolean inLayout;

    public DrawerLayoutContainer(Context context) {
        super(context);

        ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public void setParentActionBarLayout(INavigationLayout layout) {
        parentActionBarLayout = layout;
    }

    public void setActionBarLayout(ActionBarLayout actionBarLayout) {
        this.actionBarLayout = actionBarLayout;
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
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);

        final int newDisplayWidth = widthSize
            - systemAndCutoutInsets.left
            - systemAndCutoutInsets.right;

        final int newDisplayHeight = heightSize
            - systemAndCutoutInsets.top
            - systemAndCutoutInsets.bottom;

        AndroidUtilities.displaySize.x = newDisplayWidth;
        AndroidUtilities.displaySize.y = newDisplayHeight;

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
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (actionBarLayout != null && actionBarLayout.getParent() == this) {
            actionBarLayout.parentDraw(this, canvas);
        }

        super.dispatchDraw(canvas);
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
    private @NonNull Insets systemAndCutoutInsets = Insets.NONE;
    private @NonNull Insets systemAndCutoutAndImeInsets = Insets.NONE;

    private void dispatchApplyWindowInsetsInternal(View child, WindowInsetsCompat insets) {
        boolean canApplyInsets = child instanceof ActionBarLayout || child.getTag() == null;
        if (canApplyInsets) {
            ViewCompat.dispatchApplyWindowInsets(child, insets);
        }
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        lastWindowInsetsCompat = insets;

        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        final Insets systemAndImeInsets = AndroidUtilities.getDefaultWindowInsets(insets, true);

        if (!systemAndCutoutInsets.equals(systemInsets) || !systemAndCutoutAndImeInsets.equals(systemAndImeInsets)) {
            AndroidUtilities.statusBarHeight = systemInsets.top;
            AndroidUtilities.navigationBarHeight = systemInsets.bottom;

            systemAndCutoutInsets = systemInsets;
            systemAndCutoutAndImeInsets = systemAndImeInsets;
            requestLayout();
        }

        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            dispatchApplyWindowInsetsInternal(child, insets);
        }

        invalidate();
        return WindowInsetsCompat.CONSUMED;
    }
}
