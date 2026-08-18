package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.music.PrimeMusicGroup;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PrimeMusicGroupRow;
import org.telegram.ui.Components.PrimeMusicSegmentedTabBar;
import org.telegram.ui.Components.PrimeMusicTrackRow;

import java.util.List;

/**
 * PrimeGram Music: the namida-style library browser - Треки/Альбомы/Артисты/Жанры/Папки
 * as distinct segments inside one screen (Playlists and Liked keep their own existing
 * dedicated screens/entry points on Home rather than being duplicated here, matching
 * namida's own split between drawer-level "Home" items and "library" items).
 */
public class PrimeMusicLibraryActivity extends BaseFragment {

    private static final int SEGMENT_TRACKS = 0;
    private static final int SEGMENT_ALBUMS = 1;
    private static final int SEGMENT_ARTISTS = 2;
    private static final int SEGMENT_GENRES = 3;
    private static final int SEGMENT_FOLDERS = 4;

    private LinearLayout listContainer;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Библиотека");
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

        PrimeMusicSegmentedTabBar tabBar = new PrimeMusicSegmentedTabBar(context,
                new String[]{"Треки", "Альбомы", "Артисты", "Жанры", "Папки"});
        column.addView(tabBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        column.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        tabBar.setListener(this::showSegment);
        showSegment(SEGMENT_TRACKS);

        fragmentView = root;
        return root;
    }

    private void showSegment(int segment) {
        listContainer.removeAllViews();
        if (segment == SEGMENT_TRACKS) {
            showTracks();
        } else {
            int groupType;
            switch (segment) {
                case SEGMENT_ALBUMS:
                    groupType = PrimeMusicGroup.TYPE_ALBUM;
                    showGroups(PrimeMusicLibrary.getAlbums(), groupType);
                    break;
                case SEGMENT_ARTISTS:
                    groupType = PrimeMusicGroup.TYPE_ARTIST;
                    showGroups(PrimeMusicLibrary.getArtists(), groupType);
                    break;
                case SEGMENT_GENRES:
                    groupType = PrimeMusicGroup.TYPE_GENRE;
                    showGroups(PrimeMusicLibrary.getGenres(), groupType);
                    break;
                case SEGMENT_FOLDERS:
                    groupType = PrimeMusicGroup.TYPE_FOLDER;
                    showGroups(PrimeMusicLibrary.getFolders(), groupType);
                    break;
            }
        }
        if (listContainer.getChildCount() == 0) {
            listContainer.addView(emptyLabel(), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24));
        }
    }

    private void showTracks() {
        List<PrimeTrack> tracks = PrimeMusicLibrary.getAllTracks();
        for (PrimeTrack track : tracks) {
            PrimeMusicTrackRow row = new PrimeMusicTrackRow(getContext(), getResourceProvider());
            row.bind(track);
            row.setOnClickListener(v -> PrimeMusicPlayback.playQueue(UserConfig.selectedAccount, tracks, tracks.indexOf(track)));
            row.getLikeIcon().setOnClickListener(v -> {
                PrimeMusicLibrary.setLiked(track.id, !track.liked);
                showSegment(SEGMENT_TRACKS);
            });
            row.bindDefaultMenu(getParentActivity(), track, () -> showSegment(SEGMENT_TRACKS));
            listContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private void showGroups(List<PrimeMusicGroup> groups, int groupType) {
        for (PrimeMusicGroup group : groups) {
            PrimeMusicGroupRow row = new PrimeMusicGroupRow(getContext(), getResourceProvider());
            row.bind(group);
            row.setOnClickListener(v -> presentFragment(new PrimeMusicGroupTracksActivity(groupType, group.key, group.title)));
            listContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private View emptyLabel() {
        TextView label = new TextView(getContext());
        label.setText("Здесь пока пусто");
        label.setTextColor(0xFF6E6E77);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        return label;
    }
}
