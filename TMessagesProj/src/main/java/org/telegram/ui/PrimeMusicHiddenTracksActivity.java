package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.music.PrimeAlbumArt;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicTheme;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

/**
 * PrimeGram Music: every track hidden via {@code PrimeMusicLibrary.setExcluded} (the
 * "Удалить из библиотеки" action for a device-scanned track - see
 * {@code PrimeMusicTrackMenu}'s own doc for why that's deliberately a reversible,
 * DB-only hide rather than a real file delete). Exists specifically because that hide
 * is one tap in a popup menu - easy to land on the wrong row by mistake, and with no
 * screen like this one there would be no way back short of deleting and re-adding the
 * file on the device itself.
 */
public class PrimeMusicHiddenTracksActivity extends BaseFragment {

    private LinearLayout container;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Скрытые треки");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(PrimeMusicTheme.bg(PrimeMusicTheme.currentAccent));
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        reload();
        fragmentView = scrollView;
        return scrollView;
    }

    private void reload() {
        container.removeAllViews();
        List<PrimeTrack> tracks = PrimeMusicLibrary.getExcludedTracks();
        if (tracks.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Скрытых треков нет");
            empty.setTextColor(PrimeMusicTheme.TEXT_TERTIARY);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, AndroidUtilities.dp(48), 0, 0);
            container.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        for (PrimeTrack track : tracks) {
            container.addView(buildRow(track), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));
        }
    }

    private View buildRow(PrimeTrack track) {
        Context context = getContext();
        FrameLayout row = new FrameLayout(context);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        row.setMinimumHeight(AndroidUtilities.dp(PrimeMusicTheme.TILE_CONTENT_HEIGHT_DP));

        FrameLayout coverFrame = new FrameLayout(context);
        row.addView(coverFrame, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.LEFT));
        ImageView coverIcon = new ImageView(context);
        coverIcon.setScaleType(ImageView.ScaleType.CENTER);
        coverFrame.addView(coverIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        String trackTitle = track.title != null && !track.title.isEmpty() ? track.title : "Без названия";
        String trackArtist = track.artist != null ? track.artist : "";
        PrimeAlbumArt.applyCover(coverFrame, coverIcon, track.coverPath, trackTitle + "_" + trackArtist, 10);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.LEFT, 64, 0, 100, 0));

        TextView title = new TextView(context);
        title.setText(trackTitle);
        title.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);

        TextView artist = new TextView(context);
        artist.setText(trackArtist);
        artist.setTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        artist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        TextView restore = new TextView(context);
        restore.setText("Восстановить");
        restore.setTextColor(PrimeMusicTheme.currentAccent | 0xFF000000);
        restore.setTypeface(AndroidUtilities.bold());
        restore.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_SMALL);
        restore.setGravity(Gravity.CENTER);
        restore.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6));
        restore.setBackground(Theme.createSelectorDrawable(0x18FFFFFF, Theme.RIPPLE_MASK_ALL));
        restore.setClickable(true);
        restore.setOnClickListener(v -> {
            PrimeMusicLibrary.setExcluded(track.id, false);
            reload();
        });
        row.addView(restore, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        return row;
    }
}
