package com.exteragram.messenger.preferences.components;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

/**
 * PrimeGram: exteraGram's labelled slider - a title with the current value on one line, the bar,
 * and the minimum/maximum labels under its ends. Plugins build their own settings rows with it
 * ({@code AltSeekbar(context, on_drag, min, max, title, left, right)}); PrimeGram's plugin
 * settings also use it for the SDK's {@code Slider} row, so both look the same.
 */
public class AltSeekbar extends FrameLayout {

    public interface OnDrag {
        void run(float value);
    }

    private final OnDrag onDrag;
    protected final int min;
    protected final int max;
    protected final TextView headerValue;
    protected final TextView leftTextView;
    protected final TextView rightTextView;
    public final SeekBarView seekBarView;
    protected float currentValue;
    protected int roundedValue;

    public AltSeekbar(Context context, OnDrag onDrag, int min, int max, String title, String left, String right) {
        super(context);
        this.onDrag = onDrag;
        this.min = min;
        this.max = Math.max(min, max);

        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        titleView.setText(title);
        header.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f));
        headerValue = new TextView(context);
        headerValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        header.addView(headerValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 21, 15, 21, 0));

        seekBarView = new SeekBarView(context);
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                final float value = AltSeekbar.this.min + (AltSeekbar.this.max - AltSeekbar.this.min) * progress;
                updateHeader(value);
                if (AltSeekbar.this.onDrag != null) {
                    try {
                        AltSeekbar.this.onDrag.run(value);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }
        });
        addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP, 6, 40, 6, 0));

        leftTextView = label(context, left, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        rightTextView = label(context, right, LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 21, 78, 21, 12));
        addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 21, 78, 21, 12));
        setProgress(min);
    }

    private static TextView label(Context context, String text, int gravity) {
        final TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        view.setGravity(gravity);
        view.setText(text);
        return view;
    }

    /** @param value a setting value in [min..max], not a fraction */
    public void setProgress(float value) {
        seekBarView.setProgress(max == min ? 0f : (value - min) / (float) (max - min));
        updateHeader(value);
    }

    /** Refresh only the displayed value; the bar is already where it should be. */
    public void updateHeader(float value) {
        currentValue = value;
        roundedValue = Math.round(value);
        headerValue.setText(getTextForHeader());
    }

    public CharSequence getTextForHeader() {
        return String.valueOf(roundedValue);
    }

    public float getValue() {
        return currentValue;
    }
}
