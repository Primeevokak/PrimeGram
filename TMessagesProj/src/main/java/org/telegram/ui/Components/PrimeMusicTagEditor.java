package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PrimeGram Music: marking a track's moods, and creating or editing a mood.
 *
 * <p>Moods used to be typed as comma-separated tags in a text field, and the wave's mood chips
 * filtered by fixed words nobody knew to type - so picking a mood on the wave played nothing.
 * Now a mood is a category the user owns (name + emoji), and a track is put in one with a tap.
 * Free-form tags are still there underneath, for anyone who uses them.
 */
public final class PrimeMusicTagEditor {

    private PrimeMusicTagEditor() {
    }

    /** The mood picker for one track. Mood taps apply at once; free tags apply on "Сохранить". */
    public static void show(Activity activity, long trackId) {
        show(activity, trackId, null);
    }

    public static void show(Activity activity, long trackId, Runnable onChanged) {
        if (activity == null) {
            return;
        }
        final LinearLayout card = PrimeMusicDialog.newTitledCard(activity, "Настроение трека");
        card.addView(PrimeMusicDialog.bodyText(activity, "Отметьте, под какое настроение подходит трек — «Моя волна» будет подбирать его для этого настроения."),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        final PrimeFlowLayout moods = new PrimeFlowLayout(activity, 8, 8);
        card.addView(moods, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));
        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            moods.removeAllViews();
            final Set<Long> on = PrimeMusicLibrary.getTagIds(trackId);
            for (PrimeMusicLibrary.Mood mood : PrimeMusicLibrary.getMoods()) {
                final boolean selected = on.contains(mood.id);
                final TextView chip = chip(activity, mood.label(), selected);
                chip.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    PrimeMusicLibrary.setTrackTag(trackId, mood.id, !selected);
                    rebuild[0].run();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
                moods.addView(chip);
            }
            final TextView add = chip(activity, "＋ Новое настроение", false);
            add.setOnClickListener(v -> showMoodEditor(activity, null, () -> {
                rebuild[0].run();
                if (onChanged != null) {
                    onChanged.run();
                }
            }));
            moods.addView(add);
        };
        rebuild[0].run();

        final TextView tagsTitle = PrimeMusicDialog.bodyText(activity, "Свои теги — через запятую");
        card.addView(tagsTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));
        final EditTextBoldCursor input = input(activity, "тренировка, дорога, фон…");
        input.setText(TextUtils.join(", ", PrimeMusicLibrary.getPlainTags(trackId)));
        card.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        final LinearLayout buttons = PrimeMusicDialog.buttonRow(activity, dialogRef, "Сохранить", false, () -> {
            final List<String> tags = new ArrayList<>();
            for (String part : input.getText().toString().split(",")) {
                if (!part.trim().isEmpty()) {
                    tags.add(part.trim());
                }
            }
            PrimeMusicLibrary.setPlainTags(trackId, tags);
            if (onChanged != null) {
                onChanged.run();
            }
        });
        card.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));
        dialogRef[0] = PrimeMusicDialog.showCustomCard(activity, card);
    }

    /**
     * Create ({@code mood == null}) or edit a mood: an emoji and a name. Editing also offers
     * deleting it; the tracks stay, only the category goes.
     */
    public static void showMoodEditor(Activity activity, PrimeMusicLibrary.Mood mood, Runnable onDone) {
        if (activity == null) {
            return;
        }
        final LinearLayout card = PrimeMusicDialog.newTitledCard(activity, mood == null ? "Новое настроение" : "Настроение");
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditTextBoldCursor emoji = input(activity, "🎧");
        emoji.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        emoji.setGravity(Gravity.CENTER);
        if (mood != null && mood.emoji != null) {
            emoji.setText(mood.emoji);
        }
        row.addView(emoji, LayoutHelper.createLinear(56, LayoutHelper.WRAP_CONTENT, 0, 0, 12, 0));
        final EditTextBoldCursor name = input(activity, "Например: Дорога");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        if (mood != null) {
            name.setText(mood.name);
        }
        row.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        if (mood != null) {
            final TextView delete = PrimeMusicDialog.bodyText(activity, "Удалить настроение");
            delete.setTextColor(0xFFFF5C6E);
            delete.setPadding(0, dp(12), 0, dp(4));
            delete.setOnClickListener(v -> {
                dialogRef[0].dismiss();
                PrimeMusicDialog.confirm(activity, "Удалить «" + mood.name + "»?",
                        "Треки останутся в медиатеке, пропадёт только само настроение.", "Удалить", true, () -> {
                            PrimeMusicLibrary.deleteMood(mood.id);
                            if (onDone != null) {
                                onDone.run();
                            }
                        });
            });
            card.addView(delete, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }
        final LinearLayout buttons = PrimeMusicDialog.buttonRow(activity, dialogRef, mood == null ? "Создать" : "Сохранить", false, () -> {
            final String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                return;
            }
            final String e = emoji.getText().toString().trim();
            if (mood == null) {
                PrimeMusicLibrary.createMood(n, e.isEmpty() ? null : e);
            } else if (!PrimeMusicLibrary.renameMood(mood.id, n, e.isEmpty() ? null : e)) {
                android.widget.Toast.makeText(activity, "Такое название уже занято", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (onDone != null) {
                onDone.run();
            }
        });
        card.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));
        dialogRef[0] = PrimeMusicDialog.showCustomCard(activity, card);
        AndroidUtilities.runOnUIThread(() -> {
            name.requestFocus();
            AndroidUtilities.showKeyboard(name);
        }, 250);
    }

    static TextView chip(Context context, CharSequence text, boolean selected) {
        final TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        if (selected) {
            bg.setColor(PrimeMusicTheme.currentAccent | 0xFF000000);
            chip.setTextColor(0xFF000000);
        } else {
            bg.setColor(0x1FFFFFFF);
            bg.setStroke(dp(1), 0x26FFFFFF);
            chip.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        }
        chip.setBackground(bg);
        return chip;
    }

    private static EditTextBoldCursor input(Context context, String hint) {
        final EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setHint(hint);
        input.setHintTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        input.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        final int accent = PrimeMusicTheme.currentAccent;
        input.setCursorColor(accent | 0xFF000000);
        input.setCursorWidth(1.5f);
        input.setLineColors(0x33FFFFFF, accent | 0xFF000000, 0xFFFF3B5C);
        input.setPadding(0, dp(4), 0, dp(10));
        input.setSingleLine(true);
        input.setBackground(null);
        return input;
    }
}
