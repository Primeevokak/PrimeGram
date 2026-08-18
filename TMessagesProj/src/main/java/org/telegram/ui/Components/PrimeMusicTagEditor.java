package org.telegram.ui.Components;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.EditText;

import org.telegram.messenger.music.PrimeMusicLibrary;

import java.util.ArrayList;
import java.util.List;

/** PrimeGram Music: shared mood-tag editor dialog, factored out of
 *  {@code PrimeMusicNowPlayingActivity} so the track context menu can offer the same
 *  action from any list screen, not just while a track is actively playing. */
public final class PrimeMusicTagEditor {

    private PrimeMusicTagEditor() {
    }

    public static void show(Activity activity, long trackId) {
        if (activity == null) {
            return;
        }
        List<String> currentTags = PrimeMusicLibrary.getTags(trackId);
        EditText input = new EditText(activity);
        input.setText(TextUtils.join(", ", currentTags));
        input.setHint("настроение, жанр, повод…");
        int pad = org.telegram.messenger.AndroidUtilities.dp(20);
        input.setPadding(pad, pad, pad, pad);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Теги трека")
                .setMessage("Через запятую — например: спокойное, вечер, тренировка")
                .setView(input)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String raw = input.getText().toString();
                    List<String> tags = new ArrayList<>();
                    for (String part : raw.split(",")) {
                        if (!part.trim().isEmpty()) {
                            tags.add(part.trim());
                        }
                    }
                    PrimeMusicLibrary.setTags(trackId, tags);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
