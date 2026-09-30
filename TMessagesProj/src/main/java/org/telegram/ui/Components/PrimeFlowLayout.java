package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;

/**
 * PrimeGram: children laid out in rows, wrapping to the next row when one is full - chips of
 * moods, tags and the like. Android has no such layout built in, and a horizontal scroller hides
 * every chip past the edge of the screen.
 */
public class PrimeFlowLayout extends ViewGroup {

    private final int hGap;
    private final int vGap;

    public PrimeFlowLayout(Context context, int hGapDp, int vGapDp) {
        super(context);
        hGap = AndroidUtilities.dp(hGapDp);
        vGap = AndroidUtilities.dp(vGapDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            final int w = child.getMeasuredWidth();
            if (x > 0 && x + w > width) {
                x = 0;
                y += rowHeight + vGap;
                rowHeight = 0;
            }
            x += w + hGap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + rowHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l - getPaddingLeft() - getPaddingRight();
        final boolean rtl = LocaleController.isRTL;
        int x = 0, y = 0, rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            final int w = child.getMeasuredWidth();
            if (x > 0 && x + w > width) {
                x = 0;
                y += rowHeight + vGap;
                rowHeight = 0;
            }
            final int left = rtl ? getPaddingLeft() + width - x - w : getPaddingLeft() + x;
            child.layout(left, getPaddingTop() + y, left + w, getPaddingTop() + y + child.getMeasuredHeight());
            x += w + hGap;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
    }
}
