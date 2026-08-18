package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimeMusicPlayback;
import org.telegram.messenger.music.PrimePlaylist;
import org.telegram.messenger.music.PrimeTrack;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram Music: the real anchored long-press/"..." menu namida shows on every track
 * tile (Go to Album/Artist/Folder, Share, Add to Playlist, Edit Tags, Play Next/Last -
 * see the reference screenshot studied for this feature) - built on
 * {@code ActionBarPopupWindow.ActionBarPopupWindowLayout}, the same mechanism this
 * fork's own {@code ChatActivity}/{@code TopicsFragment}/{@code ProfileActivity} already
 * use for their own overflow menus, not a plain {@code AlertDialog} list.
 */
public final class PrimeMusicTrackMenu {

    public interface Callback {
        void onChanged();
    }

    private PrimeMusicTrackMenu() {
    }

    public static void show(Activity activity, View anchor, PrimeTrack track, Theme.ResourcesProvider provider, Callback callback) {
        if (activity == null || track == null) {
            return;
        }

        ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(activity, R.drawable.popup_fixed_alert, provider);
        Drawable shadow = ContextCompat.getDrawable(activity, R.drawable.popup_fixed_alert4);
        if (shadow != null) {
            shadow = shadow.mutate();
            shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, provider), PorterDuff.Mode.MULTIPLY));
            layout.setBackground(shadow);
        }

        final ActionBarPopupWindow[] windowRef = new ActionBarPopupWindow[1];
        Runnable dismiss = () -> {
            if (windowRef[0] != null) {
                windowRef[0].dismiss();
            }
        };

        boolean removable = track.source == PrimeTrack.SOURCE_LOCAL || track.source == PrimeTrack.SOURCE_DEVICE;

        addItem(activity, layout, "Добавить в плейлист", R.drawable.msg_list, () -> {
            dismiss.run();
            showAddToPlaylist(activity, track);
        });

        addItem(activity, layout, "Редактировать теги", R.drawable.msg_edit, () -> {
            dismiss.run();
            PrimeMusicTagEditor.show(activity, track.id);
        });

        if (removable) {
            addItem(activity, layout, "Играть следующим", R.drawable.ic_action_play, () -> {
                dismiss.run();
                queueTrack(track, true);
            });

            addItem(activity, layout, "Играть последним", R.drawable.msg_add, () -> {
                dismiss.run();
                queueTrack(track, false);
            });
        }

        if (track.localPath != null) {
            addItem(activity, layout, "Поделиться", R.drawable.msg_share, () -> {
                dismiss.run();
                shareTrack(activity, track);
            });
        }

        if (track.source == PrimeTrack.SOURCE_DEVICE) {
            addItem(activity, layout, "Не музыка", R.drawable.msg_block2, () -> {
                dismiss.run();
                PrimeMusicLibrary.setExcluded(track.id, true);
                if (callback != null) {
                    callback.onChanged();
                }
            });
        }

        if (removable) {
            ActionBarMenuSubItem deleteItem = new ActionBarMenuSubItem(activity, false, true);
            deleteItem.setTextAndIcon("Удалить из библиотеки", R.drawable.msg_delete);
            int redColor = Theme.getColor(Theme.key_text_RedRegular, provider);
            deleteItem.setTextColor(redColor);
            deleteItem.setIconColor(redColor);
            deleteItem.setOnClickListener(v -> {
                dismiss.run();
                PrimeMusicLibrary.deleteTrack(track.id);
                if (callback != null) {
                    callback.onChanged();
                }
            });
            layout.addView(deleteItem);
        }

        ActionBarPopupWindow window = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        windowRef[0] = window;
        window.setPauseNotifications(true);
        window.setDismissAnimationDuration(220);
        window.setOutsideTouchable(true);
        window.setClippingEnabled(true);
        window.setAnimationStyle(R.style.PopupContextAnimation);
        window.setFocusable(true);
        layout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        window.showAsDropDown(anchor, anchor.getWidth() - layout.getMeasuredWidth(), -anchor.getHeight(), Gravity.TOP | Gravity.LEFT);

        if (callback != null) {
            window.setOnDismissListener(callback::onChanged);
        }
    }

    private static void addItem(Activity activity, ActionBarPopupWindow.ActionBarPopupWindowLayout layout, String text, int icon, Runnable onClick) {
        ActionBarMenuSubItem item = new ActionBarMenuSubItem(activity, false, false);
        item.setTextAndIcon(text, icon);
        item.setOnClickListener(v -> onClick.run());
        layout.addView(item);
    }

    private static void showAddToPlaylist(Activity activity, PrimeTrack track) {
        List<PrimePlaylist> playlists = PrimeMusicLibrary.getPlaylists();
        List<String> names = new ArrayList<>();
        for (PrimePlaylist playlist : playlists) {
            names.add(playlist.name);
        }
        names.add("+ Новый плейлист");
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Добавить в плейлист")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which < playlists.size()) {
                        PrimeMusicLibrary.addToPlaylist(playlists.get(which).id, track.id);
                    } else {
                        promptNewPlaylistAndAdd(activity, track);
                    }
                })
                .show();
    }

    private static void promptNewPlaylistAndAdd(Activity activity, PrimeTrack track) {
        org.telegram.ui.Components.EditTextBoldCursor input = new org.telegram.ui.Components.EditTextBoldCursor(activity);
        input.setTextColor(0xFF000000);
        int pad = AndroidUtilities.dp(16);
        input.setPadding(pad, pad, pad, pad);
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Новый плейлист")
                .setView(input)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        long playlistId = PrimeMusicLibrary.createPlaylist(name);
                        PrimeMusicLibrary.addToPlaylist(playlistId, track.id);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /** Splices this track into the currently playing queue - only meaningful for
     *  local/device tracks, whose synthetic {@code MessageObject} carries a real
     *  playable file path (see {@code PrimeMusicPlayback.buildMessageObject}'s own
     *  doc - a Telegram-sourced track has no local file to build one from). */
    private static void queueTrack(PrimeTrack track, boolean next) {
        int account = UserConfig.selectedAccount;
        org.telegram.messenger.MessageObject syntheticObj = PrimeMusicPlayback.buildMessageObject(account, track);
        MediaController controller = MediaController.getInstance();
        ArrayList<org.telegram.messenger.MessageObject> queue = controller.getPlaylist();
        org.telegram.messenger.MessageObject playing = controller.getPlayingMessageObject();
        if (queue == null || queue.isEmpty() || playing == null) {
            PrimeMusicPlayback.playSingle(account, track);
            return;
        }
        ArrayList<org.telegram.messenger.MessageObject> updated = new ArrayList<>(queue);
        int currentIndex = updated.indexOf(playing);
        int insertAt = next ? Math.min(updated.size(), currentIndex + 1) : updated.size();
        updated.add(insertAt, syntheticObj);
        controller.setPlaylist(updated, playing, 0, false, null);
    }

    private static void shareTrack(Activity activity, PrimeTrack track) {
        try {
            File file = new File(track.localPath);
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Поделиться треком"));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
