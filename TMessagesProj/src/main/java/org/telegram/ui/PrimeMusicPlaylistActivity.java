package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PrimeMusicTrackRow;

import java.util.List;

/** PrimeGram Music: one playlist's track list, with remove-from-playlist support. */
public class PrimeMusicPlaylistActivity extends BaseFragment {

    private final long playlistId;
    private LinearLayout container;

    public PrimeMusicPlaylistActivity(long playlistId) {
        super();
        this.playlistId = playlistId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Плейлист");
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
        scrollView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.TOP));

        reload();
        fragmentView = scrollView;
        return scrollView;
    }

    private void reload() {
        container.removeAllViews();
        List<PrimeTrack> tracks = PrimeMusicLibrary.getPlaylistTracks(playlistId);
        for (PrimeTrack track : tracks) {
            PrimeMusicTrackRow row = new PrimeMusicTrackRow(getContext(), getResourceProvider());
            row.bind(track);
            row.setOnClickListener(v -> PrimeMusicPlayback.playQueue(UserConfig.selectedAccount, tracks, tracks.indexOf(track)));
            row.getLikeIcon().setOnClickListener(v -> {
                PrimeMusicLibrary.setLiked(track.id, !track.liked);
                reload();
            });
            row.getMoreIcon().setOnClickListener(v -> {
                PrimeMusicLibrary.removeFromPlaylist(playlistId, track.id);
                reload();
            });
            container.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }
}
