package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicTheme;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram Music: a real dark-themed playlist-creation dialog matching the rest of
 * the Music tab, instead of a plain stock {@code AlertDialog} with a white input field
 * dropped onto it (the previous shape, which visually clashed with everything around
 * it). Built on a plain {@code android.app.AlertDialog} shell with its default window
 * chrome stripped (transparent background) - the standard way to get a fully custom
 * dialog card without needing a whole BottomSheet subclass for a single text field.
 */
public final class PrimeMusicPlaylistDialog {

    private PrimeMusicPlaylistDialog() {
    }

    public interface Callback {
        void onCreated(long playlistId);
    }

    public static void show(Activity activity, Callback callback) {
        showInternal(activity, null, "Новый плейлист", "Создать", name -> {
            long playlistId = PrimeMusicLibrary.createPlaylist(name);
            if (callback != null) {
                callback.onCreated(playlistId);
            }
        });
    }

    /** Same card, prefilled with the playlist's current name and wired to rename
     *  instead of create - the entry point for {@link PrimeMusicPlaylistMenu}'s
     *  "Переименовать" action. */
    public static void showRename(Activity activity, org.telegram.messenger.music.PrimePlaylist playlist, Runnable onRenamed) {
        showInternal(activity, playlist.name, "Переименовать плейлист", "Сохранить", name -> {
            PrimeMusicLibrary.renamePlaylist(playlist.id, name);
            if (onRenamed != null) {
                onRenamed.run();
            }
        });
    }

    private interface NameCallback {
        void onConfirmed(String name);
    }

    private static void showInternal(Activity activity, String initialName, String titleText, String buttonText, NameCallback callback) {
        if (activity == null) {
            return;
        }
        Context context = activity;
        int accent = PrimeMusicTheme.currentAccent;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(PrimeMusicTheme.card(accent));
        cardBg.setCornerRadius(dp(PrimeMusicTheme.CARD_RADIUS_DP));
        card.setBackground(cardBg);
        card.setPadding(dp(22), dp(22), dp(22), dp(18));

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_LARGE);
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        input.setHintTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        input.setHint("Название плейлиста");
        if (initialName != null) {
            input.setText(initialName);
            input.setSelection(initialName.length());
        }
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        input.setCursorColor(accent | 0xFF000000);
        input.setCursorWidth(1.5f);
        input.setLineColors(0x33FFFFFF, accent | 0xFF000000, 0xFFFF3B5C);
        input.setPadding(0, dp(4), 0, dp(10));
        input.setSingleLine(true);
        input.setBackground(null);
        card.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        card.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));

        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];

        TextView cancel = pillButton(context, "Отмена", 0x00000000, PrimeMusicTheme.TEXT_TERTIARY);
        cancel.setOnClickListener(v -> dialogRef[0].dismiss());
        buttons.addView(cancel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        TextView create = pillButton(context, buttonText, accent | 0xFF000000, 0xFF000000);
        create.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                input.requestFocus();
                return;
            }
            dialogRef[0].dismiss();
            callback.onConfirmed(name);
        });
        buttons.addView(create);

        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(dp(24), 0, dp(24), 0);
        wrapper.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                .setView(wrapper)
                .create();
        dialogRef[0] = dialog;
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        input.requestFocus();
        AndroidUtilities.showKeyboard(input);
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
}
