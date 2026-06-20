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

    private final List<MessageObject> feedItems = new ArrayList<>();
    private boolean isLoading = false;

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

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        adapter = new FeedAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < feedItems.size()) {
                MessageObject msg = feedItems.get(position);
                if (msg != null && getParentActivity() != null) {
                    Bundle args = new Bundle();
                    args.putLong("dialog_id", msg.getDialogId());
                    args.putInt("message_id", msg.getId());
                    presentFragment(new ChatActivity(args));
                }
            }
        });

        listView.setClipToPadding(false);
        listView.setPadding(0, org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));
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

        loadingView = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
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

    public void loadFeed() {
        if (isLoading) return;
        isLoading = true;
        feedItems.clear();
        if (adapter != null) adapter.notifyDataSetChanged();

        boolean excludeMuted = MessagesController.getGlobalMainSettings().getBoolean("primegram_feed_exclude_muted", false);
        boolean excludeArchived = MessagesController.getGlobalMainSettings().getBoolean("primegram_feed_exclude_archived", false);
        MessagesController mc = MessagesController.getInstance(currentAccount);

        ArrayList<TLRPC.Dialog> unreadDialogs = new ArrayList<>();
        if (mc.dialogs_dict != null) {
            for (int i = 0; i < mc.dialogs_dict.size(); i++) {
                TLRPC.Dialog d = mc.dialogs_dict.valueAt(i);
                if (d == null) continue;

                if (d.folder_id == 1 && excludeArchived) {
                    continue; // Skip archived
                }

                if (d.unread_count > 0 && DialogObject.isChatDialog(d.id)) {
                    TLRPC.Chat chat = mc.getChat(-d.id);
                    // Broadcast == true means it's a channel, megagroup == true means it's a supergroup
                    if (chat != null && chat.broadcast && !chat.megagroup) {
                        if (excludeMuted && mc.isDialogMuted(d.id, 0)) {
                            continue; // Skip muted
                        }
                        unreadDialogs.add(d);
                    }
                }
            }
        }

        org.telegram.messenger.MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                org.telegram.SQLite.SQLiteDatabase database = org.telegram.messenger.MessagesStorage.getInstance(currentAccount).getDatabase();
                ArrayList<MessageObject> msgs = new ArrayList<>();

                for (TLRPC.Dialog d : unreadDialogs) {
                    int limit = Math.min(d.unread_count, 50);
                    org.telegram.SQLite.SQLiteCursor cursor = database.queryFinalized(
                            String.format(java.util.Locale.US, "SELECT data, mid, date FROM messages_v2 WHERE uid = %d ORDER BY mid DESC LIMIT %d", d.id, limit));
                    
                    while (cursor.next()) {
                        org.telegram.tgnet.NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = d.id;
                            
                            MessageObject obj = new MessageObject(currentAccount, message, true, false);
                            msgs.add(obj);
                        }
                    }
                    cursor.dispose();
                }

                // Sort: Archive messages at the bottom (oldest), then sort by date descending
                Collections.sort(msgs, (a, b) -> {
                    TLRPC.Dialog dA = mc.dialogs_dict.get(a.getDialogId());
                    TLRPC.Dialog dB = mc.dialogs_dict.get(b.getDialogId());
                    boolean aArchived = (dA != null && dA.folder_id == 1);
                    boolean bArchived = (dB != null && dB.folder_id == 1);
                    if (aArchived != bArchived) {
                        return aArchived ? 1 : -1; // Archive goes to the bottom
                    }
                    return Integer.compare(b.messageOwner.date, a.messageOwner.date); // Newest first
                });

                AndroidUtilities.runOnUIThread(() -> {
                    feedItems.addAll(msgs);
                    isLoading = false;
                    if (adapter != null) adapter.notifyDataSetChanged();
                    updateEmptyView();
                    
                    AndroidUtilities.runOnUIThread(this::checkVisibleItems, 200);
                });

            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    isLoading = false;
                    updateEmptyView();
                });
            }
        });
    }

    private void checkVisibleItems() {
        if (listView == null || layoutManager == null || feedItems.isEmpty()) return;
        
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return;

        boolean updated = false;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        
        // Mid-point of the screen
        int listViewCenterY = listView.getMeasuredHeight() / 2;

        for (int i = first; i <= last; i++) {
            if (i >= 0 && i < feedItems.size()) {
                View view = layoutManager.findViewByPosition(i);
                if (view != null) {
                    int itemBottomY = view.getBottom();
                    // If the item's bottom is above the middle of the screen (or it's fully scrolled past)
                    if (itemBottomY < listViewCenterY) {
                        MessageObject msg = feedItems.get(i);
                        if (!msg.isOut() && msg.isUnread()) {
                            msg.messageOwner.unread = false;
                            updated = true;
                            
                            long dialogId = msg.getDialogId();
                            int maxId = msg.getId();
                            int maxDate = msg.messageOwner.date;
                            
                            // To correctly mark archive or main dialogs as read, use the normal markDialogAsRead
                            mc.markDialogAsRead(dialogId, maxId, 0, maxDate, false, 0, 0, true, 0);
                            mc.markMessageContentAsRead(msg); // Also mark content (like voice) as read
                        }
                    }
                }
            }
        }
    }

    private void updateEmptyView() {
        if (emptyView == null) return;
        emptyView.setVisibility(feedItems.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(feedItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private class FeedAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;
        private org.telegram.ui.Cells.ChatMessageCell.ChatMessageCellDelegate cellDelegate;

        FeedAdapter(Context ctx) {
            this.context = ctx;
            this.cellDelegate = new org.telegram.ui.Cells.ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public void didPressReplyMessage(org.telegram.ui.Cells.ChatMessageCell cell, int id, float x, float y, boolean longpress) {
                    openChat(cell.getMessageObject());
                }

                @Override
                public void didPressUrl(org.telegram.ui.Cells.ChatMessageCell cell, android.text.style.CharacterStyle url, boolean longPress) {
                    if (!longPress && url instanceof org.telegram.ui.Components.URLSpanNoUnderline) {
                         org.telegram.messenger.browser.Browser.openUrl(context, ((org.telegram.ui.Components.URLSpanNoUnderline)url).getURL());
                    } else if (!longPress && url instanceof android.text.style.URLSpan) {
                         org.telegram.messenger.browser.Browser.openUrl(context, ((android.text.style.URLSpan)url).getURL());
                    }
                }

                @Override
                public boolean needPlayMessage(org.telegram.ui.Cells.ChatMessageCell cell, org.telegram.messenger.MessageObject messageObject, boolean muted) {
                    return false;
                }

                @Override
                public void didPressUserAvatar(org.telegram.ui.Cells.ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                    openChat(cell.getMessageObject());
                }

                @Override
                public void didPressChannelAvatar(org.telegram.ui.Cells.ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY, boolean asForward) {
                    openChat(cell.getMessageObject());
                }
                
                @Override
                public void didPressBotButton(org.telegram.ui.Cells.ChatMessageCell cell, TLRPC.KeyboardButton button) {
                    openChat(cell.getMessageObject());
                }
                
                @Override
                public void didPressSideButton(org.telegram.ui.Cells.ChatMessageCell cell) {
                    openChat(cell.getMessageObject());
                }
            };
        }

        private void openChat(MessageObject msg) {
            if (msg == null || getParentActivity() == null) return;
            Bundle args = new Bundle();
            args.putLong("dialog_id", msg.getDialogId());
            args.putInt("message_id", msg.getId());
            presentFragment(new ChatActivity(args));
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            org.telegram.ui.Cells.ChatMessageCell cell = new org.telegram.ui.Cells.ChatMessageCell(context, currentAccount, false, null, null);
            cell.setDelegate(cellDelegate);
            // DO NOT setOnClickListener on cell directly, let RecyclerListView handle it or ChatMessageCell native touch event
            
            // Fix layout params for recycler view
            RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            layoutParams.bottomMargin = AndroidUtilities.dp(4);
            cell.setLayoutParams(layoutParams);
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            org.telegram.ui.Cells.ChatMessageCell cell = (org.telegram.ui.Cells.ChatMessageCell) holder.itemView;
            MessageObject msg = feedItems.get(position);
            
            // isChat = true forces the cell to show avatars and names (like in groups/channels)
            cell.isChat = true;
            cell.setFullyDraw(true);
            cell.setMessageObject(msg, null, false, false, false);
        }

        @Override
        public int getItemCount() {
            return feedItems.size();
        }
    }

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
