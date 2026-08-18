package org.telegram.messenger.music;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/**
 * PrimeGram Music: renders an "unknown artist" value as a small localized icon instead
 * of raw text - the user explicitly asked for this after noticing device-scanned
 * tracks with no ID3 artist tag show Android MediaStore's own literal placeholder
 * string, {@code "<unknown>"} (a real MediaStore convention, not a PrimeGram bug), as
 * plain visible text.
 */
public final class PrimeMusicArtistLabel {

    private PrimeMusicArtistLabel() {
    }

    public static boolean isUnknown(String artist) {
        if (artist == null) {
            return true;
        }
        String trimmed = artist.trim();
        return trimmed.isEmpty()
                || trimmed.equalsIgnoreCase("<unknown>")
                || trimmed.equals("Неизвестный исполнитель")
                || trimmed.equals("Unknown Artist");
    }

    /** Sets {@code view}'s text to {@code artist}, or - when unknown - clears the text
     *  and shows a small "unknown" icon in its place via a compound drawable, so no
     *  layout changes are needed at any call site. */
    public static void bind(TextView view, String artist) {
        if (isUnknown(artist)) {
            view.setText("");
            view.setContentDescription("Исполнитель не известен");
            Drawable icon = ContextCompat.getDrawable(view.getContext(), R.drawable.msg_help);
            if (icon != null) {
                icon = icon.mutate();
                icon.setColorFilter(new PorterDuffColorFilter(PrimeMusicTheme.TEXT_TERTIARY, PorterDuff.Mode.SRC_IN));
                int size = AndroidUtilities.dp(14);
                icon.setBounds(0, 0, size, size);
            }
            view.setCompoundDrawables(icon, null, null, null);
            view.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        } else {
            view.setCompoundDrawables(null, null, null, null);
            view.setContentDescription(null);
            view.setText(artist);
        }
    }
}
