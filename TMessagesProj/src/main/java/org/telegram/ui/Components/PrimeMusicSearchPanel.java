package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.messenger.music.PrimeMusicTheme;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PrimeGram Music: a floating dropdown search panel anchored right below the Home
 * header's search icon, replacing the old full-screen {@code PrimeMusicSearchActivity}
 * - the user explicitly called the full-screen version overkill for what's really a
 * small live-filter over the local library ("можно было ограничиться плавающей плашкой
 * сверху низ которой уже появлялись бы уже треки"). Results update as the user types,
 * no submit step. Meant to be added as the LAST child of {@link PrimeMusicHomeView}'s
 * root {@code FrameLayout} so it draws on top of everything else in the tab.
 */
public class PrimeMusicSearchPanel extends FrameLayout {

    private static final int MAX_RESULTS_HEIGHT_DP = 360;

    private final EditText input;
    private final LinearLayout results;
    private final BaseFragment fragment;
    private List<PrimeTrack> allTracks = new ArrayList<>();

    public PrimeMusicSearchPanel(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        setVisibility(GONE);

        // Full-bleed scrim - tapping anywhere outside the card dismisses the panel,
        // the standard "floating dropdown" affordance.
        setOnClickListener(v -> hide());
        setBackgroundColor(0x99000000);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true); // swallow taps so they don't fall through to the scrim
        int accent = PrimeMusicTheme.currentAccent;
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(PrimeMusicTheme.card(accent));
        cardBg.setCornerRadius(dp(PrimeMusicTheme.CARD_RADIUS_DP));
        card.setBackground(cardBg);
        card.setElevation(dp(6));
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP, 12, 64, 12, 0));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        input = new EditText(context);
        input.setHint("Название или исполнитель…");
        input.setHintTextColor(PrimeMusicTheme.TEXT_TERTIARY);
        input.setTextColor(PrimeMusicTheme.TEXT_PRIMARY);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PrimeMusicTheme.SP_MEDIUM);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(dp(16), dp(12), dp(4), dp(12));
        inputRow.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.msg_close);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setColorFilter(new android.graphics.PorterDuffColorFilter(PrimeMusicTheme.TEXT_TERTIARY, android.graphics.PorterDuff.Mode.SRC_IN));
        close.setClickable(true);
        close.setBackground(Theme.createSelectorDrawable(0x20FFFFFF, Theme.RIPPLE_MASK_CIRCLE_20DP));
        close.setOnClickListener(v -> hide());
        inputRow.addView(close, LayoutHelper.createLinear(40, 40, 0, 4, 0, 12, 0));

        ScrollView resultsScroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(MAX_RESULTS_HEIGHT_DP), MeasureSpec.AT_MOST));
            }
        };
        card.addView(resultsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, 0, 0, dp(8));
        resultsScroll.addView(results, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void show() {
        allTracks = PrimeMusicLibrary.getAllTracks();
        setVisibility(VISIBLE);
        input.setText("");
        results.removeAllViews();
        input.requestFocus();
        AndroidUtilities.showKeyboard(input);
    }

    public void hide() {
        setVisibility(GONE);
        AndroidUtilities.hideKeyboard(input);
    }

    public boolean isShown_() {
        return getVisibility() == VISIBLE;
    }

    private void filter(String query) {
        results.removeAllViews();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return;
        }
        List<PrimeTrack> matches = new ArrayList<>();
        for (PrimeTrack track : allTracks) {
            String title = track.title != null ? track.title.toLowerCase(Locale.ROOT) : "";
            String artist = track.artist != null ? track.artist.toLowerCase(Locale.ROOT) : "";
            String album = track.album != null ? track.album.toLowerCase(Locale.ROOT) : "";
            if (title.contains(needle) || artist.contains(needle) || album.contains(needle)) {
                matches.add(track);
            }
        }
        if (matches.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Ничего не найдено");
            empty.setTextColor(PrimeMusicTheme.TEXT_TERTIARY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            results.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        for (PrimeTrack track : matches) {
            PrimeMusicTrackRow row = new PrimeMusicTrackRow(getContext(), fragment.getResourceProvider());
            row.bind(track);
            row.setOnClickListener(v -> {
                PrimeMusicPlayback.playQueue(UserConfig.selectedAccount, matches, matches.indexOf(track));
                hide();
            });
            row.getLikeIcon().setOnClickListener(v -> {
                PrimeMusicLibrary.setLiked(track.id, !track.liked);
                filter(query);
            });
            row.bindDefaultMenu(fragment.getParentActivity(), track, () -> filter(query));
            results.addView(row, PrimeMusicTrackRow.createRowLayoutParams());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Consumes every touch while visible so nothing underneath (the horizontal
        // carousels, the scroll view) reacts to a tap meant for the scrim/panel.
        return true;
    }
}
