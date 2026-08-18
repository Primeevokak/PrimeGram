package org.telegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PrimeMusicTrackRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** PrimeGram Music: title/artist substring search over the full local library - the
 *  simplified equivalent of namida's Search page (no separate remote-source tab, this
 *  fork has no YouTube-equivalent second source to search alongside Local). */
public class PrimeMusicSearchActivity extends BaseFragment {

    private LinearLayout results;
    private List<PrimeTrack> allTracks;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFF0B0B0F);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        EditText input = new EditText(context);
        input.setHint("Название или исполнитель…");
        input.setHintTextColor(0xFF6E6E77);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        column.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        column.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(results, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        allTracks = PrimeMusicLibrary.getAllTracks();

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

        input.requestFocus();
        AndroidUtilities.showKeyboard(input);

        fragmentView = root;
        return root;
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
            if (title.contains(needle) || artist.contains(needle)) {
                matches.add(track);
            }
        }
        if (matches.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Ничего не найдено");
            empty.setTextColor(0xFF6E6E77);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, AndroidUtilities.dp(24), 0, 0);
            results.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        for (PrimeTrack track : matches) {
            PrimeMusicTrackRow row = new PrimeMusicTrackRow(getContext(), getResourceProvider());
            row.bind(track);
            row.setOnClickListener(v -> PrimeMusicPlayback.playQueue(UserConfig.selectedAccount, matches, matches.indexOf(track)));
            row.getLikeIcon().setOnClickListener(v -> {
                PrimeMusicLibrary.setLiked(track.id, !track.liked);
                filter(query);
            });
            row.bindDefaultMenu(getParentActivity(), track, () -> filter(query));
            results.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }
}
