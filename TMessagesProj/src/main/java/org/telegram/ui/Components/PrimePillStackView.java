package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.plugins.PrimePillStack;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;

/**
 * PrimeGram: the row of plugin-supplied pills above the chat list - empty and gone (zero height)
 * until at least one plugin calls {@code add_pill}, so an install with no such plugin sees no
 * trace of this ever existing.
 */
public class PrimePillStackView extends HorizontalScrollView {

    private final LinearLayout row;
    private List<PrimePillStack.Pill> shown = java.util.Collections.emptyList();

    public PrimePillStackView(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        addView(row);
        rebuild();
        PrimePillStack.setOnChangedListener(this::rebuild);
    }

    private void rebuild() {
        final List<PrimePillStack.Pill> pills = PrimePillStack.getAll();
        shown = pills;
        row.removeAllViews();
        setVisibility(pills.isEmpty() ? GONE : VISIBLE);
        for (PrimePillStack.Pill pill : pills) {
            row.addView(makeChip(pill), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6, 0));
        }
        requestLayout();
    }

    private View makeChip(PrimePillStack.Pill pill) {
        final TextView chip = new TextView(getContext());
        chip.setText(pill.text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        chip.setTextColor(Color.WHITE);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        int color;
        try {
            color = pill.colorHex != null ? Color.parseColor(pill.colorHex) : Theme.getColor(Theme.key_chats_actionBackground);
        } catch (Throwable t) {
            color = Theme.getColor(Theme.key_chats_actionBackground);
        }
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setColor(color);
        chip.setBackground(bg);

        if (pill.icon != null) {
            final int iconRes = ApplicationLoader.applicationContext.getResources()
                    .getIdentifier(pill.icon, "drawable", ApplicationLoader.applicationContext.getPackageName());
            if (iconRes != 0) {
                chip.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
                chip.setCompoundDrawablePadding(AndroidUtilities.dp(6));
            }
        }

        chip.setOnClickListener(v -> PrimePillStack.click(pill));
        return chip;
    }
}
