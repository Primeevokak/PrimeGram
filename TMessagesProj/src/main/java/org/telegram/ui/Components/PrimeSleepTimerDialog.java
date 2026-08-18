package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.music.PrimeMusicTheme;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram Music: the sleep-timer picker, rebuilt around {@link PrimeCircularTimerView}
 * - a rotary dial instead of a flat preset list, matching a reference design the user
 * supplied (drag clockwise to add minutes, laps stack into nested rings past 60 minutes).
 */
public final class PrimeSleepTimerDialog {

    public interface Callback {
        /** {@code minutes == 0 && !stopAtTrackEnd} means "just cancel the running timer". */
        void onConfirm(int minutes, boolean stopAtTrackEnd);
    }

    private PrimeSleepTimerDialog() {
    }

    public static void show(Activity activity, int initialMinutes, boolean initialStopAtTrackEnd, Callback callback) {
        if (activity == null) {
            return;
        }
        Context context = activity;
        LinearLayout card = PrimeMusicDialog.newTitledCard(context, "Таймер сна");

        LinearLayout toggleRow = new LinearLayout(context);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(toggleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 4));

        TextView toggleLabel = new TextView(context);
        toggleLabel.setText("Конец трека или выпуска");
        toggleLabel.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        toggleLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        toggleRow.addView(toggleLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Switch toggleSwitch = new Switch(context);
        toggleSwitch.setChecked(initialStopAtTrackEnd, false);
        toggleRow.addView(toggleSwitch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        PrimeCircularTimerView dial = new PrimeCircularTimerView(context);
        dial.setMinutes(initialMinutes > 0 ? initialMinutes : 30);
        LinearLayout.LayoutParams dialParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 12);
        dialParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(dial, dialParams);

        toggleSwitch.setOnClickListener(v -> {
            boolean checked = !toggleSwitch.isChecked();
            toggleSwitch.setChecked(checked, true);
            dial.setAlpha(checked ? 0.35f : 1f);
            dial.setEnabled(!checked);
        });

        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];

        int accent = PrimeMusicTheme.currentAccent;
        TextView confirm = new TextView(context);
        confirm.setText("Запустить");
        confirm.setTextColor(0xFF000000);
        confirm.setTypeface(AndroidUtilities.bold());
        confirm.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        confirm.setGravity(Gravity.CENTER);
        confirm.setPadding(0, dp(14), 0, dp(14));
        android.graphics.drawable.GradientDrawable confirmBg = new android.graphics.drawable.GradientDrawable();
        confirmBg.setColor(accent | 0xFF000000);
        confirmBg.setCornerRadius(dp(28));
        confirm.setBackground(confirmBg);
        confirm.setClickable(true);
        confirm.setOnClickListener(v -> {
            dialogRef[0].dismiss();
            if (callback != null) {
                callback.onConfirm(toggleSwitch.isChecked() ? 0 : dial.getMinutes(), toggleSwitch.isChecked());
            }
        });
        card.addView(confirm, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        TextView cancelTimer = new TextView(context);
        cancelTimer.setText("Выключить таймер");
        cancelTimer.setTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        cancelTimer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        cancelTimer.setGravity(Gravity.CENTER);
        cancelTimer.setPadding(0, dp(14), 0, dp(4));
        cancelTimer.setClickable(true);
        cancelTimer.setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        cancelTimer.setOnClickListener(v -> {
            dialogRef[0].dismiss();
            if (callback != null) {
                callback.onConfirm(0, false);
            }
        });
        card.addView(cancelTimer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        dialogRef[0] = PrimeMusicDialog.showCustomCard(activity, card);
    }
}
