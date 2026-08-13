package org.telegram.messenger;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import org.telegram.ui.ActionBar.Theme;

/**
 * A small "BOT" pill meant to overlap the bottom-right corner of an avatar in the account
 * switcher UIs (this fork's own sidebar, and the classic drawer) - the only place PrimeGram lets
 * a bot session (logged in via {@link org.telegram.ui.BotLoginActivity}) sit side by side with a
 * real account, which without this reads exactly like picking a wrong/unfamiliar contact rather
 * than a deliberate, separate bot session.
 */
public final class PrimeBotBadge {

    private PrimeBotBadge() {
    }

    public static TextView createView(Context context) {
        TextView badge = new TextView(context);
        badge.setText("BOT");
        badge.setTextColor(0xFFFFFFFF);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 7);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        badge.setIncludeFontPadding(false);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(AndroidUtilities.dp(4));
        background.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        badge.setBackground(background);
        return badge;
    }
}
