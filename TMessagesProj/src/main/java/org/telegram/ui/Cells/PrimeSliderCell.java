package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

/**
 * PrimeGram: a labelled slider over an integer range.
 *
 * <p>Cards answer "which of these three", a slider answers "how much". Both belong on the same
 * screen for the same setting: the cards land you near what you want in one tap, and the slider
 * covers everything between them without a dialog full of numbers.
 *
 * <p>Reports every drag, not only the end of one, so whatever is showing a preview can follow the
 * finger. Only the release is worth storing.
 */
public class PrimeSliderCell extends FrameLayout {

    public interface Listener {
        /** {@code stop} marks the release, which is when the value should be persisted. */
        void onChanged(int value, boolean stop);
    }

    private final TextView titleView;
    private final TextView valueView;
    private final SeekBarView seekBar;
    private final int min;
    private final int max;

    private int value;
    private Listener listener;
    private ValueFormatter formatter;

    public interface ValueFormatter {
        CharSequence format(int value);
    }

    public PrimeSliderCell(Context context, CharSequence title, int min, int max, int value,
                           Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.value = Math.max(min, Math.min(this.max, value));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setText(title);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT, 21, 13, 100, 0));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valueView.setTypeface(AndroidUtilities.bold());
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
        valueView.setGravity(Gravity.RIGHT);
        addView(valueView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT, 21, 13, 21, 0));

        seekBar = new SeekBarView(context, resourcesProvider);
        seekBar.setReportChanges(true);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                final int wanted = PrimeSliderCell.this.min
                        + Math.round(progress * (PrimeSliderCell.this.max - PrimeSliderCell.this.min));
                PrimeSliderCell.this.value = wanted;
                updateValueText();
                if (listener != null) {
                    listener.onChanged(wanted, stop);
                }
            }

            @Override
            public int getStepsCount() {
                // Discrete steps, so the thumb snaps to values the setting can actually hold
                // instead of drifting between two that round to the same thing.
                return PrimeSliderCell.this.max - PrimeSliderCell.this.min;
            }
        });
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38,
                Gravity.TOP | Gravity.LEFT, 6, 40, 6, 0));

        updateValueText();
        seekBar.setProgress((this.value - min) / (float) (this.max - min));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Overrides how the number is shown, for values that mean something other than themselves. */
    public void setFormatter(ValueFormatter formatter) {
        this.formatter = formatter;
        updateValueText();
    }

    /** Moves the slider from outside - for when a card next to it picks a preset. */
    public void setValue(int value) {
        final int clamped = Math.max(min, Math.min(max, value));
        if (this.value == clamped) {
            return;
        }
        this.value = clamped;
        updateValueText();
        seekBar.setProgress((clamped - min) / (float) (max - min));
        invalidate();
    }

    public int getValue() {
        return value;
    }

    private void updateValueText() {
        valueView.setText(formatter != null ? formatter.format(value) : String.valueOf(value));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(84), MeasureSpec.EXACTLY));
    }
}
