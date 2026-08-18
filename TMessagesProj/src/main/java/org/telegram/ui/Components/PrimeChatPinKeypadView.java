package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.PrimePinContract;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PrimeGram: a reusable shuffled-digit PIN keypad, factored out fresh for the new
 * per-chat PIN dialogs ({@link PrimeChatPinDialog}) - same anti-shoulder-surfing shape
 * {@code PrimePinGateActivity} uses for the app-wide PIN (digits reshuffled every render,
 * only filled-dot count shown, never which digits were typed), but as a small standalone
 * component rather than touching that existing, working, monolithic Activity.
 */
public class PrimeChatPinKeypadView extends LinearLayout {

    public interface Listener {
        /** {@code pin} is only valid for the duration of this call - the view wipes its
         *  own copy right after invoking this, callers must not hold onto the reference
         *  past this call without copying it themselves. */
        void onSubmit(char[] pin);
    }

    private static final int PANEL_COLOR = Color.rgb(30, 42, 54);
    private static final int TEXT_COLOR = Color.WHITE;

    private final char[] input = new char[PrimePinContract.MAX_PIN_LENGTH];
    private int inputLength;
    private final TextView dotsView;
    private final LinearLayout keypadColumn;
    private final java.util.Random keypadRandom = new java.util.Random();
    private Listener listener;

    public PrimeChatPinKeypadView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        dotsView = new TextView(context);
        dotsView.setTextColor(TEXT_COLOR);
        dotsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        dotsView.setGravity(Gravity.CENTER);
        dotsView.setLetterSpacing(0.3f);
        addView(dotsView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        // Explicit rows of 3 instead of GridLayout's auto-placement - a real device
        // rendered the GridLayout version as one column of full-width, stretched keys
        // (an auto-placement/spec quirk not worth chasing further); this shape can't
        // misbehave the same way, each row is just three fixed-size keys side by side.
        keypadColumn = new LinearLayout(context);
        keypadColumn.setOrientation(VERTICAL);
        addView(keypadColumn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        renderKeypad();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Clears the current entry and reshuffles the keypad - call between attempts (a
     *  mismatch on confirm, a rejected unlock) so a shoulder-surfer watching key
     *  *positions* across attempts still can't infer anything. */
    public void reset() {
        java.util.Arrays.fill(input, '\0');
        inputLength = 0;
        setDots(0);
        renderKeypad();
    }

    private void setDots(int filled) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filled; i++) {
            if (i > 0) sb.append(' ');
            sb.append('●');
        }
        dotsView.setText(sb.toString());
    }

    private void renderKeypad() {
        keypadColumn.removeAllViews();
        List<Integer> digits = new ArrayList<>();
        for (int i = 0; i <= 9; i++) digits.add(i);
        Collections.shuffle(digits, keypadRandom);

        List<Runnable> keys = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            final int digit = digits.get(i);
            keys.add(() -> appendDigit(digit));
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            labels.add(String.valueOf(digits.get(i)));
        }
        labels.add("✕");
        keys.add(this::clearInput);
        final int lastDigit = digits.get(9);
        labels.add(String.valueOf(lastDigit));
        keys.add(() -> appendDigit(lastDigit));
        labels.add("✓");
        keys.add(this::submitIfValid);

        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(HORIZONTAL);
            keypadColumn.addView(rowLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                rowLayout.addView(makeKey(labels.get(index), keys.get(index)), LayoutHelper.createLinear(68, 52, 3, 3, 3, 3));
            }
        }
    }

    private TextView makeKey(String label, Runnable onClick) {
        TextView key = new TextView(getContext());
        key.setText(label);
        key.setTextColor(TEXT_COLOR);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL_COLOR);
        bg.setCornerRadius(dp(10));
        android.graphics.drawable.Drawable ripple = Theme.createSelectorDrawable(0x22FFFFFF, Theme.RIPPLE_MASK_ROUNDRECT_6DP);
        key.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, ripple}));
        key.setGravity(Gravity.CENTER);
        key.setSoundEffectsEnabled(false);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        key.setOnClickListener(v -> onClick.run());
        return key;
    }

    private void appendDigit(int digit) {
        if (inputLength >= PrimePinContract.MAX_PIN_LENGTH) {
            return;
        }
        input[inputLength++] = Character.forDigit(digit, 10);
        setDots(inputLength);
        if (inputLength >= PrimePinContract.MAX_PIN_LENGTH) {
            submitIfValid();
        }
    }

    private void clearInput() {
        java.util.Arrays.fill(input, '\0');
        inputLength = 0;
        setDots(0);
    }

    private void submitIfValid() {
        if (inputLength < PrimePinContract.MIN_PIN_LENGTH || listener == null) {
            return;
        }
        char[] copy = new char[inputLength];
        System.arraycopy(input, 0, copy, 0, inputLength);
        listener.onSubmit(copy);
        java.util.Arrays.fill(copy, '\0');
    }
}
