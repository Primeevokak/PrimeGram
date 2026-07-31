package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrimeWhatsNew;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram: the "what's new" panel.
 *
 * <p>A sheet rather than a full screen, and dismissable by swiping it away, because it arrives
 * uninvited on the launch after an update - when the user opened the app to read a message, not to
 * read about the app. Everything in it is one line of what they can now do, and the button says
 * the only thing there is to say.
 */
public class PrimeWhatsNewSheet {

    private PrimeWhatsNewSheet() {
    }

    public static void show(BaseFragment fragment) {
        final Context context = fragment == null ? null : fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        final TextView versionView = new TextView(context);
        versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        versionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        versionView.setText("Версия " + PrimeWhatsNew.currentVersion());
        versionView.setGravity(Gravity.CENTER);
        content.addView(versionView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 0, 21, 12));

        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (PrimeWhatsNew.Entry entry : PrimeWhatsNew.entries()) {
            list.addView(row(context, resourcesProvider, entry),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // Scrolls, because the list grows with every release and a sheet taller than the screen
        // would put the button out of reach.
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(list, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        content.addView(scroll, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 0, 1f, 0, 0, 0, 0));

        final TextView button = new TextView(context);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setText("Понятно");
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider)));
        content.addView(button, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 48, 16, 12, 16, 8));

        final BottomSheet sheet = new BottomSheet.Builder(context, false, resourcesProvider)
                .setTitle("Что нового", true)
                .setCustomView(content)
                .create();
        button.setOnClickListener(v -> sheet.dismiss());
        // Marked as seen when it is shown, not when the button is pressed: a user who swipes it
        // away has still seen it, and showing it again on the next launch would be a punishment
        // for dismissing it the way the sheet invites them to.
        PrimeWhatsNew.markSeen();
        fragment.showDialog(sheet);
    }

    private static View row(Context context, Theme.ResourcesProvider resourcesProvider,
                            PrimeWhatsNew.Entry entry) {
        final FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(9),
                AndroidUtilities.dp(21), AndroidUtilities.dp(9));

        final ImageView iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        final int iconRes = context.getResources().getIdentifier(
                entry.icon, "drawable", context.getPackageName());
        if (iconRes != 0) {
            iconView.setImageResource(iconRes);
        }
        iconView.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                PorterDuff.Mode.SRC_IN));
        container.addView(iconView, LayoutHelper.createFrame(28, 28,
                Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));

        final LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setText(entry.title);
        texts.addView(titleView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textView.setText(entry.text);
        texts.addView(textView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        container.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 40, 0, 0, 0));
        return container;
    }
}
