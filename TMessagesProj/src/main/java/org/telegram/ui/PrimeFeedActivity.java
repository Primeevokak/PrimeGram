package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PrimeGram "Стена" — лента непрочитанных постов из каналов.
 * Приоритет: 1) По порядку списка чатов, 2) Архив (опционально), 3) Свежие
 */
public class PrimeFeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private RecyclerListView listView;
    private FeedAdapter adapter;
    private LinearLayoutManager layoutManager;

    private TextView emptyView;
    private View loadingView;

    private final List<FeedItem> feedItems = new ArrayList<>();
    private boolean isLoading = false;

    // Preference key — can be extended from PrimeGramSettingsActivity
    public static final String PREF_INCLUDE_ARCHIVE = "primefeed_include_archive";

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(0);
        actionBar.setTitle("Лента");
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setAllowOverlayTitle(false);

        // Refresh button in action bar
        actionBar.createMenu().addItem(1, R.drawable.ic_ab_refresh);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == 1) {
                    loadFeed();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        adapter = new FeedAdapter(context);
        listView.setAdapter(adapter);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkVisibleItems();
            }
        });

        // Empty placeholder
        emptyView = new TextView(context);
        emptyView.setTextSize(16);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setText("Нет непрочитанных постов\nиз каналов");
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        // Loading indicator
        loadingView = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                // simple placeholder — the real loading overlay is just
                // a dimmed background; a progress bar would need ProgressBar widget
            }
        };
        loadingView.setVisibility(View.GONE);
        frameLayout.addView(loadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        loadFeed();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // ─────────────────────────────────────────────
    //  Feed loading logic
    // ─────────────────────────────────────────────

    public void loadFeed() {
        if (isLoading) return;
        isLoading = true;
        feedItems.clear();
        if (adapter != null) adapter.notifyDataSetChanged();

        boolean includeArchive = MessagesController.getGlobalMainSettings()
                .getBoolean(PREF_INCLUDE_ARCHIVE, false);

        AndroidUtilities.runOnUIThread(() -> {
            try {
                collectFeedItems(includeArchive);
            } catch (Exception e) {
                FileLog.e(e);
            }
            isLoading = false;
            if (adapter != null) adapter.notifyDataSetChanged();
            updateEmptyView();
            
            // Check visible items shortly after layout
            AndroidUtilities.runOnUIThread(this::checkVisibleItems, 200);
        }, 0);
    }

    private void collectFeedItems(boolean includeArchive) {
        MessagesController mc = MessagesController.getInstance(currentAccount);
        if (mc == null) return;

        // Use dialogsChannelsOnly — already filtered to broadcast channels in main list
        ArrayList<TLRPC.Dialog> mainChannels = mc.dialogsChannelsOnly;
        collectFrom(mc, mainChannels, false);

        // Optionally include archived channels (folder_id == 1)
        if (includeArchive) {
            ArrayList<TLRPC.Dialog> archiveDialogs = mc.dialogsByFolder.get(1);
            if (archiveDialogs != null) {
                for (int i = 0; i < archiveDialogs.size(); i++) {
                    TLRPC.Dialog d = archiveDialogs.get(i);
                    if (d == null) continue;
                    long dialogId = d.id;
                    if (!DialogObject.isChatDialog(dialogId)) continue;
                    TLRPC.Chat chat = mc.getChat(-dialogId);
                    if (chat == null || !chat.broadcast) continue;
                    if (d.unread_count <= 0) continue;

                    MessageObject topMsg = null;
                    if (d.top_message != 0) {
                        ArrayList<MessageObject> msgs = mc.dialogMessage.get(dialogId);
                        if (msgs != null && !msgs.isEmpty()) topMsg = msgs.get(0);
                    }

                    FeedItem item = new FeedItem();
                    item.dialog = d;
                    item.chat = chat;
                    item.unreadCount = d.unread_count;
                    item.topMessage = topMsg;
                    item.isFromArchive = true;
                    item.originalPosition = mainChannels.size() + i;
                    feedItems.add(item);
                }
            }
        }

        // Sort: archived items always after main-list items; within group keep order
        Collections.sort(feedItems, (a, b) -> {
            if (a.isFromArchive != b.isFromArchive) return a.isFromArchive ? 1 : -1;
            return Integer.compare(a.originalPosition, b.originalPosition);
        });
    }

    private void collectFrom(MessagesController mc, ArrayList<TLRPC.Dialog> dialogs, boolean fromArchive) {
        if (dialogs == null) return;
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog d = dialogs.get(i);
            if (d == null) continue;

            long dialogId = d.id;
            if (!DialogObject.isChatDialog(dialogId)) continue;

            TLRPC.Chat chat = mc.getChat(-dialogId);
            if (chat == null || !chat.broadcast) continue;

            int unread = d.unread_count;
            if (unread <= 0) continue;

            MessageObject topMsg = null;
            if (d.top_message != 0) {
                ArrayList<MessageObject> msgs = mc.dialogMessage.get(dialogId);
                if (msgs != null && !msgs.isEmpty()) topMsg = msgs.get(0);
            }

            FeedItem item = new FeedItem();
            item.dialog = d;
            item.chat = chat;
            item.unreadCount = unread;
            item.topMessage = topMsg;
            item.isFromArchive = fromArchive;
            item.originalPosition = i;
            feedItems.add(item);
        }
    }

    private void checkVisibleItems() {
        if (listView == null || layoutManager == null || feedItems.isEmpty()) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return;

        boolean updated = false;
        MessagesController mc = MessagesController.getInstance(currentAccount);

        for (int i = first; i <= last; i++) {
            if (i >= 0 && i < feedItems.size()) {
                FeedItem item = feedItems.get(i);
                if (item.unreadCount > 0 && item.topMessage != null) {
                    item.unreadCount = 0; // mark locally
                    updated = true;
                    
                    long dialogId = item.dialog.id;
                    int maxId = item.topMessage.getId();
                    int maxDate = item.topMessage.messageOwner != null ? item.topMessage.messageOwner.date : 0;
                    
                    // Mark as read on the server
                    mc.markDialogAsRead(dialogId, maxId, 0, maxDate, false, 0, 0, true, 0);
                }
            }
        }

        if (updated && adapter != null) {
            final int f = first;
            final int count = last - first + 1;
            AndroidUtilities.runOnUIThread(() -> {
                if (adapter != null) adapter.notifyItemRangeChanged(f, count);
            });
        }
    }

    private void updateEmptyView() {
        if (emptyView == null) return;
        emptyView.setVisibility(feedItems.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(feedItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ─────────────────────────────────────────────
    //  Data model
    // ─────────────────────────────────────────────

    private static class FeedItem {
        TLRPC.Dialog dialog;
        TLRPC.Chat chat;
        MessageObject topMessage;   // in-memory cached top message
        int unreadCount;
        boolean isFromArchive;
        int originalPosition;
    }

    // ─────────────────────────────────────────────
    //  Adapter
    // ─────────────────────────────────────────────

    private class FeedAdapter extends RecyclerListView.SelectionAdapter {

        private static final int TYPE_CHANNEL_POST = 0;

        private final Context context;

        FeedAdapter(Context ctx) {
            this.context = ctx;
        }

        @Override
        public int getItemViewType(int position) {
            return TYPE_CHANNEL_POST;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ChannelPostHolder(new ChannelPostCell(context));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((ChannelPostHolder) holder).bind(feedItems.get(position));
        }

        @Override
        public int getItemCount() {
            return feedItems.size();
        }
    }

    private static class ChannelPostHolder extends RecyclerView.ViewHolder {
        ChannelPostCell cell;

        ChannelPostHolder(ChannelPostCell cell) {
            super(cell);
            this.cell = cell;
        }

        void bind(FeedItem item) {
            cell.setItem(item);
        }
    }

    // ─────────────────────────────────────────────
    //  Channel post cell UI
    // ─────────────────────────────────────────────

    private class ChannelPostCell extends FrameLayout {

        private final BackupImageView avatarView;
        private final TextView channelNameView;
        private final TextView messagePreviewView;
        private final TextView unreadCountView;
        private final TextView archiveBadgeView;

        ChannelPostCell(Context ctx) {
            super(ctx);
            setBackground(Theme.createSelectorWithBackgroundDrawable(
                    Theme.getColor(Theme.key_windowBackgroundWhite),
                    Theme.getColor(Theme.key_listSelector)));

            int margin = AndroidUtilities.dp(8);
            setPadding(margin, AndroidUtilities.dp(12), margin, AndroidUtilities.dp(12));

            // Avatar
            avatarView = new BackupImageView(ctx);
            avatarView.setRoundRadius(AndroidUtilities.dp(22));
            addView(avatarView, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

            // Channel name
            channelNameView = new TextView(ctx);
            channelNameView.setTextSize(15);
            channelNameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            channelNameView.setTypeface(AndroidUtilities.bold());
            channelNameView.setSingleLine(true);
            channelNameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(channelNameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.LEFT | Gravity.TOP, 56, 2, 60, 0));

            // Message preview
            messagePreviewView = new TextView(ctx);
            messagePreviewView.setTextSize(13);
            messagePreviewView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            messagePreviewView.setMaxLines(2);
            messagePreviewView.setEllipsize(TextUtils.TruncateAt.END);
            addView(messagePreviewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.LEFT | Gravity.TOP, 56, 22, 8, 0));

            // Unread badge
            unreadCountView = new TextView(ctx);
            unreadCountView.setTextSize(11);
            unreadCountView.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText));
            unreadCountView.setGravity(Gravity.CENTER);
            unreadCountView.setTypeface(AndroidUtilities.bold());
            unreadCountView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                    Theme.getColor(Theme.key_chats_unreadCounter)));
            unreadCountView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(2), AndroidUtilities.dp(5), AndroidUtilities.dp(2));
            addView(unreadCountView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.RIGHT | Gravity.TOP, 0, 4, 0, 0));

            // Archive badge (shown for items from archive)
            archiveBadgeView = new TextView(ctx);
            archiveBadgeView.setTextSize(10);
            archiveBadgeView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
            archiveBadgeView.setGravity(Gravity.CENTER);
            archiveBadgeView.setText("архив");
            archiveBadgeView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8),
                    Theme.getColor(Theme.key_chats_menuBackground)));
            archiveBadgeView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(1), AndroidUtilities.dp(5), AndroidUtilities.dp(1));
            archiveBadgeView.setVisibility(View.GONE);
            addView(archiveBadgeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, 0));
        }

        void setItem(FeedItem item) {
            // Avatar
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(item.chat);
            avatarView.setForUserOrChat(item.chat, avatarDrawable);

            // Channel name
            channelNameView.setText(item.chat.title != null ? item.chat.title : "");

            // Message preview
            String preview = "";
            if (item.topMessage != null && item.topMessage.messageOwner != null) {
                TLRPC.Message msg = item.topMessage.messageOwner;
                if (!TextUtils.isEmpty(msg.message)) {
                    preview = msg.message;
                } else if (msg.media != null) {
                    if (msg.media instanceof TLRPC.TL_messageMediaPhoto) {
                        preview = "📷 Фото";
                    } else if (msg.media instanceof TLRPC.TL_messageMediaDocument) {
                        preview = "📎 Документ";
                    } else if (msg.media instanceof TLRPC.TL_messageMediaWebPage) {
                        preview = "🔗 Ссылка";
                    } else {
                        preview = "Медиа";
                    }
                }
            }
            messagePreviewView.setText(preview);

            // Unread count
            int unread = item.unreadCount;
            if (unread > 0) {
                unreadCountView.setVisibility(View.VISIBLE);
                unreadCountView.setText(unread > 999 ? "999+" : String.valueOf(unread));
            } else {
                unreadCountView.setVisibility(View.GONE);
            }

            // Archive badge
            archiveBadgeView.setVisibility(item.isFromArchive ? View.VISIBLE : View.GONE);

            // Click — open dialog
            setOnClickListener(v -> {
                if (getParentActivity() == null) return;
                Bundle args = new Bundle();
                args.putLong("dialog_id", item.dialog.id);
                presentFragment(new ChatActivity(args));
            });
        }
    }

    // ─────────────────────────────────────────────
    //  TabFragmentDelegate
    // ─────────────────────────────────────────────

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return true;
    }
}
