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

    private static final int ID_DELETE_PLAYLIST = 1;

    private final long playlistId;
    private LinearLayout container;

    public PrimeMusicPlaylistActivity(long playlistId) {
        super();
        this.playlistId = playlistId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(playlistTitle());
        actionBar.setAllowOverlayTitle(true);
        actionBar.createMenu().addItem(ID_DELETE_PLAYLIST, R.drawable.msg_delete);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_DELETE_PLAYLIST) {
                    confirmDeletePlaylist();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(org.telegram.messenger.music.PrimeMusicTheme.bg(org.telegram.messenger.music.PrimeMusicTheme.currentAccent));
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
            row.setOnClickListener(v -> {
                PrimeMusicPlayback.playQueue(UserConfig.selectedAccount, tracks, tracks.indexOf(track));
                reload();
            });
            row.getLikeIcon().setOnClickListener(v -> {
                PrimeMusicLibrary.setLiked(track.id, !track.liked);
                reload();
            });
            row.getMoreIcon().setOnClickListener(v -> {
                PrimeMusicLibrary.removeFromPlaylist(playlistId, track.id);
                reload();
            });
            row.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                org.telegram.ui.Components.PrimeMusicTrackMenu.show(getParentActivity(), row, track, getResourceProvider(), this::reload);
                return true;
            });
            container.addView(row, PrimeMusicTrackRow.createRowLayoutParams());
        }
    }

    private String playlistTitle() {
        for (org.telegram.messenger.music.PrimePlaylist playlist : PrimeMusicLibrary.getPlaylists()) {
            if (playlist.id == playlistId) {
                return playlist.name;
            }
        }
        return "Плейлист";
    }

    private void confirmDeletePlaylist() {
        if (getParentActivity() == null) {
            return;
        }
        new android.app.AlertDialog.Builder(getParentActivity())
                .setTitle("Удалить плейлист")
                .setMessage("Плейлист «" + playlistTitle() + "» будет удалён. Треки останутся в медиатеке.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    PrimeMusicLibrary.deletePlaylist(playlistId);
                    finishFragment();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
