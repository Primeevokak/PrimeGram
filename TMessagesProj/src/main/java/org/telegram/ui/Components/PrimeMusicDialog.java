package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.music.PrimeMusicTheme;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;

/**
 * PrimeGram Music: the shared dark card-styled dialog shell every Music-tab dialog uses
 * now - confirmations, option lists, the playlist picker - instead of the stock white
 * {@code AlertDialog}, which the user explicitly called out as clashing with the rest of
 * the tab ("везде нужны кастомные плашки как у 'новый плейлист'"). Built the same way
 * {@link PrimeMusicPlaylistDialog} already was: a plain {@code AlertDialog} with its
 * default window chrome stripped, holding a real rounded card - not a new dialog
 * mechanism, just factored out so every call site gets the same look and the same small
 * entrance animation instead of a flat, static list.
 */
public final class PrimeMusicDialog {

    private PrimeMusicDialog() {
    }

    public interface ItemPick {
        void onPick(int index);
    }

    /** A titled card with a destructive/primary confirm button + a cancel button. */
    public static void confirm(Activity activity, String title, String message, String confirmLabel, boolean destructive, Runnable onConfirm) {
        if (activity == null) {
            return;
        }
        Context context = activity;
        int accent = PrimeMusicTheme.currentAccent;

        LinearLayout card = newCard(context, accent);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_LARGE);
        card.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextColor(PrimeMusicTheme.TEXT_SECONDARY);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        messageView.setLineSpacing(dp(2), 1f);
        card.addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];

        TextView cancel = pillButton(context, "Отмена", 0x00000000, PrimeMusicTheme.TEXT_TERTIARY);
        cancel.setOnClickListener(v -> dialogRef[0].dismiss());
        buttons.addView(cancel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        int confirmBg = destructive ? 0xFFFF3B5C : (accent | 0xFF000000);
        TextView confirm = pillButton(context, confirmLabel, confirmBg, destructive ? 0xFFFFFFFF : 0xFF000000);
        confirm.setOnClickListener(v -> {
            dialogRef[0].dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
        buttons.addView(confirm);

        dialogRef[0] = showCard(activity, card);
    }

    /** A titled card of tappable rows - each an icon (optional) + label - replacing a
     *  plain {@code AlertDialog.setItems(...)} list. */
    public static void list(Activity activity, String title, List<String> labels, int[] icons, ItemPick onPick) {
        if (activity == null) {
            return;
        }
        Context context = activity;
        int accent = PrimeMusicTheme.currentAccent;

        LinearLayout card = newCard(context, accent);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_LARGE);
        card.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(320), MeasureSpec.AT_MOST));
            }
        };
        card.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];

        for (int i = 0; i < labels.size(); i++) {
            int index = i;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ROUNDRECT_6DP));
            row.setPadding(dp(4), dp(12), dp(4), dp(12));
            row.setOnClickListener(v -> {
                dialogRef[0].dismiss();
                if (onPick != null) {
                    onPick.onPick(index);
                }
            });

            if (icons != null && icons[i] != 0) {
                ImageView icon = new ImageView(context);
                icon.setImageResource(icons[i]);
                icon.setColorFilter(new PorterDuffColorFilter(accent | 0xFF000000, PorterDuff.Mode.SRC_IN));
                row.addView(icon, LayoutHelper.createLinear(22, 22, 0, 0, 14, 0));
            }

            TextView label = new TextView(context);
            label.setText(labels.get(i));
            label.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
            row.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            rows.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        dialogRef[0] = showCard(activity, card);
    }

    private static LinearLayout newCard(Context context, int accent) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(PrimeMusicTheme.card(accent));
        cardBg.setCornerRadius(dp(PrimeMusicTheme.CARD_RADIUS_DP));
        card.setBackground(cardBg);
        card.setPadding(dp(22), dp(22), dp(22), dp(18));
        return card;
    }

    private static TextView pillButton(Context context, String text, int bgColor, int textColor) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(18), dp(9), dp(18), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(18));
        button.setBackground(bgColor == 0 ? Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ROUNDRECT_6DP) : bg);
        button.setClickable(true);
        return button;
    }

    /** Builds an empty titled card - {@code newCard} plus a bold title row - for a
     *  caller that needs its own custom body content (e.g. an {@code EditText}) rather
     *  than the fixed confirm/list shapes above; add content below the returned title,
     *  then pass the card to {@link #showCustomCard}. */
    public static LinearLayout newTitledCard(Context context, String title) {
        int accent = PrimeMusicTheme.currentAccent;
        LinearLayout card = newCard(context, accent);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_LARGE);
        card.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        return card;
    }

    public static TextView bodyText(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(PrimeMusicTheme.TEXT_SECONDARY);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    public static LinearLayout buttonRow(Context context, android.app.AlertDialog[] dialogRef, String confirmLabel, boolean destructive, Runnable onConfirm) {
        int accent = PrimeMusicTheme.currentAccent;
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        TextView cancel = pillButton(context, "Отмена", 0x00000000, PrimeMusicTheme.TEXT_TERTIARY);
        cancel.setOnClickListener(v -> dialogRef[0].dismiss());
        buttons.addView(cancel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        int confirmBg = destructive ? 0xFFFF3B5C : (accent | 0xFF000000);
        TextView confirm = pillButton(context, confirmLabel, confirmBg, destructive ? 0xFFFFFFFF : 0xFF000000);
        confirm.setOnClickListener(v -> {
            dialogRef[0].dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
        buttons.addView(confirm);
        return buttons;
    }

    /** Wraps {@code card} in the transparent-chrome dialog shell and shows it with a
     *  small scale+fade entrance - the touch a flat {@code AlertDialog.setItems} list
     *  never had. */
    public static android.app.AlertDialog showCustomCard(Activity activity, LinearLayout card) {
        return showCard(activity, card);
    }

    private static android.app.AlertDialog showCard(Activity activity, LinearLayout card) {
        FrameLayout wrapper = new FrameLayout(activity);
        wrapper.setPadding(dp(24), 0, dp(24), 0);
        wrapper.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                .setView(wrapper)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(new OvershootInterpolator(1.05f)).start();
        return dialog;
    }
}
