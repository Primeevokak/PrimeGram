package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.music.PrimeAlbumArt;
import org.telegram.messenger.music.PrimeMusicGroup;
import org.telegram.ui.ActionBar.Theme;

/** PrimeGram Music: one row in a grouped library browse list (album/artist/genre/
 *  folder) - same visual shape as {@link PrimeMusicTrackRow} but with a chevron
 *  instead of like/more, since tapping always drills into the group's track list. */
public class PrimeMusicGroupRow extends FrameLayout {

    private final FrameLayout coverFrame;
    private final ImageView coverIcon;
    private final TextView title;
    private final TextView subtitle;

    public PrimeMusicGroupRow(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setPadding(dp(16), dp(8), dp(16), dp(8));

        coverFrame = new FrameLayout(context);
        addView(coverFrame, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        coverIcon = new ImageView(context);
        coverIcon.setScaleType(ImageView.ScaleType.CENTER);
        coverIcon.setImageResource(R.drawable.msg_filled_data_music);
        coverIcon.setColorFilter(new PorterDuffColorFilter(0xEEFFFFFF, PorterDuff.Mode.SRC_IN));
        coverFrame.addView(coverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.LEFT, 64, 0, 40, 0));

        title = new TextView(context);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);

        subtitle = new TextView(context);
        subtitle.setTextColor(0xFF8E8E93);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.arrow_more);
        chevron.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        addView(chevron, LayoutHelper.createFrame(18, 18, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        setClickable(true);
        setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ALL));
        setMinimumHeight(dp(64));
    }

    public void bind(PrimeMusicGroup group) {
        title.setText(group.title != null ? group.title : "");
        subtitle.setText(group.subtitle != null ? group.subtitle : "");
        PrimeAlbumArt.applyCover(coverFrame, coverIcon, group.coverPath, group.title, 10);
    }
}
