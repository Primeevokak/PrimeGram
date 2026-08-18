package org.telegram.ui.Components;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.music.PrimeMusicLibrary;
import org.telegram.messenger.music.PrimePlaylist;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

/**
 * PrimeGram Music: the long-press action menu for a playlist card on Home - the user
 * explicitly asked for the white press-highlight on a playlist card to be replaced by
 * a real menu (rename/delete) instead of just an outline, matching the same request for
 * track rows ({@link PrimeMusicTrackMenu}).
 */
public final class PrimeMusicPlaylistMenu {

    public interface Callback {
        void onChanged();
    }

    private PrimeMusicPlaylistMenu() {
    }

    public static void show(Activity activity, View anchor, PrimePlaylist playlist, Theme.ResourcesProvider provider, Callback callback) {
        if (activity == null || playlist == null) {
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

        ActionBarMenuSubItem renameItem = new ActionBarMenuSubItem(activity, false, false);
        renameItem.setTextAndIcon("Переименовать", R.drawable.msg_edit);
        renameItem.setOnClickListener(v -> {
            dismiss.run();
            PrimeMusicPlaylistDialog.showRename(activity, playlist, () -> {
                if (callback != null) {
                    callback.onChanged();
                }
            });
        });
        layout.addView(renameItem);

        ActionBarMenuSubItem deleteItem = new ActionBarMenuSubItem(activity, false, true);
        deleteItem.setTextAndIcon("Удалить плейлист", R.drawable.msg_delete);
        int redColor = Theme.getColor(Theme.key_text_RedRegular, provider);
        deleteItem.setTextColor(redColor);
        deleteItem.setIconColor(redColor);
        deleteItem.setOnClickListener(v -> {
            dismiss.run();
            confirmDelete(activity, playlist, callback);
        });
        layout.addView(deleteItem);

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
    }

    private static void confirmDelete(Activity activity, PrimePlaylist playlist, Callback callback) {
        String name = playlist.name != null && !playlist.name.isEmpty() ? playlist.name : "плейлист";
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Удалить плейлист")
                .setMessage("Плейлист «" + name + "» будет удалён безвозвратно. Треки в нём останутся в библиотеке. Продолжить?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    PrimeMusicLibrary.deletePlaylist(playlist.id);
                    if (callback != null) {
                        callback.onChanged();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
