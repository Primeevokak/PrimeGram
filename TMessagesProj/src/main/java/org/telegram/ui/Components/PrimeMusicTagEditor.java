package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.LinearLayout;

import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicTheme;

import java.util.ArrayList;
import java.util.List;

/** PrimeGram Music: shared mood-tag editor dialog, factored out of
 *  {@code PrimeMusicNowPlayingActivity} so the track context menu can offer the same
 *  action from any list screen, not just while a track is actively playing. Built on
 *  the same dark card shell every other Music dialog uses ({@link PrimeMusicDialog}),
 *  not the stock white {@code AlertDialog} this used to be. */
public final class PrimeMusicTagEditor {

    private PrimeMusicTagEditor() {
    }

    public static void show(Activity activity, long trackId) {
        if (activity == null) {
            return;
        }
        List<String> currentTags = PrimeMusicLibrary.getTags(trackId);

        LinearLayout card = PrimeMusicDialog.newTitledCard(activity, "Теги трека");

        card.addView(PrimeMusicDialog.bodyText(activity, "Через запятую — например: спокойное, вечер, тренировка"),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        EditTextBoldCursor input = new EditTextBoldCursor(activity);
        input.setText(TextUtils.join(", ", currentTags));
        input.setHint("настроение, жанр, повод…");
        input.setHintTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        input.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        int accent = PrimeMusicTheme.currentAccent;
        input.setCursorColor(accent | 0xFF000000);
        input.setCursorWidth(1.5f);
        input.setLineColors(0x33FFFFFF, accent | 0xFF000000, 0xFFFF3B5C);
        input.setPadding(0, dp(4), 0, dp(10));
        input.setSingleLine(true);
        input.setBackground(null);
        card.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));

        android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        LinearLayout buttons = PrimeMusicDialog.buttonRow(activity, dialogRef, "Сохранить", false, () -> {
            String raw = input.getText().toString();
            List<String> tags = new ArrayList<>();
            for (String part : raw.split(",")) {
                if (!part.trim().isEmpty()) {
                    tags.add(part.trim());
                }
            }
            PrimeMusicLibrary.setTags(trackId, tags);
        });
        card.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        dialogRef[0] = PrimeMusicDialog.showCustomCard(activity, card);
    }
}
