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
import android.util.TypedValue;
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
public class PrimeFeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate, org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private FeedAdapter adapter;
    private LinearLayoutManager layoutManager;

    private TextView emptyView;
    private View loadingView;

    private final List<MessageObject> feedItems = new ArrayList<>();
    private boolean isLoading = false;

    public static final String PREF_INCLUDE_ARCHIVE = "primefeed_include_archive";

    private java.util.HashSet<Long> requestedDialogs = new java.util.HashSet<>();

    /** PrimeGram: pagination - how many unread posts per channel we ask for. Bumped when the
     *  user scrolls near the bottom and some channel still has more unread than this cap. */
    private int primePerDialogLimit = 50;
    private static final int PRIME_PAGE_STEP = 50;
    private static final int PRIME_PAGE_MAX = 500;
    private boolean primeMoreAvailable = false;

    /** PrimeGram: scroll position to restore after the next successful load. Static so it
     *  survives the fragment being recreated when the tab host evicts an off-screen tab -
     *  mirrors exteraGram keeping SavedScrollPosition on the singleton FeedController rather
     *  than on the fragment itself. */
    private static long primeSavedScrollDialogId;
    private static int primeSavedScrollMessageId;
    private static int primeSavedScrollOffset;
    private static boolean primeHasSavedScroll;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).addObserver(this, org.telegram.messenger.NotificationCenter.messagesDidLoad);
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).addObserver(this, org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        primeSaveScrollPosition();
        super.onFragmentDestroy();
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).removeObserver(this, org.telegram.messenger.NotificationCenter.messagesDidLoad);
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).removeObserver(this, org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        // Both are deferred, so both can still be pending when the screen goes away.
        AndroidUtilities.cancelRunOnUIThread(primeReadCheck);
        AndroidUtilities.cancelRunOnUIThread(primeReloadFeed);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == org.telegram.messenger.NotificationCenter.messagesDidLoad || id == org.telegram.messenger.NotificationCenter.dialogsNeedReload) {
            // Coalesced. dialogsNeedReload arrives in bursts - a sync, a batch of new messages,
            // a read receipt - and each one rebuilt the entire feed, which means re-querying the
            // database and then notifyDataSetChanged over a list of the app's heaviest cells.
            // Rebuilding once after the burst settles looks identical and costs a fraction.
            AndroidUtilities.cancelRunOnUIThread(primeReloadFeed);
            AndroidUtilities.runOnUIThread(primeReloadFeed, 400);
        }
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
        // The rows here are ChatMessageCell - the most expensive view in the app - and this list
        // was left on the defaults: two offscreen views, and an item animator that has nothing to
        // animate because the feed only ever reloads wholesale. Both cost frames on every fling.
        listView.setItemAnimator(null);
        listView.setItemViewCacheSize(6);
        listView.setHasFixedSize(true);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < feedItems.size()) {
                MessageObject msg = feedItems.get(position);
                if (msg != null && getParentActivity() != null) {
                    Bundle args = new Bundle();
                    if (msg.getDialogId() < 0) {
                        args.putLong("chat_id", -msg.getDialogId());
                    } else {
                        args.putLong("user_id", msg.getDialogId());
                    }
                    args.putInt("message_id", msg.getId());
                    presentFragment(new ChatActivity(args));
                }
            }
        });

        listView.setClipToPadding(false);
        int topPadding = org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight();
        if (topPadding == 0) topPadding = AndroidUtilities.dp(56);
        listView.setPadding(0, topPadding + AndroidUtilities.dp(4), 0, AndroidUtilities.dp(80));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // Not on every scroll frame. Marking posts as read walks every visible position,
                // asks the layout manager for each view, and can end in a network request - none
                // of which belongs on a path that runs sixty times a second. Reading is judged by
                // where the list came to rest, and a fling that flies past a post was never a
                // person reading it anyway.
                primeScheduleReadCheck();
                if (primeMoreAvailable && !isLoading && layoutManager != null) {
                    int last = layoutManager.findLastVisibleItemPosition();
                    if (last != RecyclerView.NO_POSITION && last >= feedItems.size() - 5) {
                        primeLoadMore();
                    }
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkVisibleItems();
                }
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

        android.content.SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (!preferences.getBoolean("primegram_feed_onboarding_shown", false)) {
            FrameLayout onboardingView = new FrameLayout(context);
            onboardingView.setBackgroundColor(0xCC000000);
            
            LinearLayout onboardingContent = new LinearLayout(context);
            onboardingContent.setOrientation(LinearLayout.VERTICAL);
            onboardingContent.setGravity(Gravity.CENTER);
            
            ImageView onboardingIcon = new ImageView(context);
            onboardingIcon.setImageResource(R.drawable.msg_channel);
            onboardingIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            onboardingContent.addView(onboardingIcon, LayoutHelper.createLinear(80, 80, Gravity.CENTER, 0, 0, 0, 16));
            
            TextView onboardingTitle = new TextView(context);
            onboardingTitle.setText("PrimeFeed");
            onboardingTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            onboardingTitle.setTextColor(0xFFFFFFFF);
            onboardingTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            onboardingContent.addView(onboardingTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 16));
            
            TextView onboardingText = new TextView(context);
            onboardingText.setText("Единая лента непрочитанных постов из всех ваших каналов.\n\nНижняя панель скрыта для удобного чтения. Чтобы выйти, используйте жест назад или свайп.");
            onboardingText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            onboardingText.setTextColor(0xFFFFFFFF);
            onboardingText.setGravity(Gravity.CENTER);
            onboardingText.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
            onboardingContent.addView(onboardingText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 32));
            
            TextView onboardingBtn = new TextView(context);
            onboardingBtn.setText("Понятно");
            onboardingBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            onboardingBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            onboardingBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton)));
            onboardingBtn.setGravity(Gravity.CENTER);
            onboardingBtn.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(12), AndroidUtilities.dp(32), AndroidUtilities.dp(12));
            onboardingBtn.setOnClickListener(v -> {
                preferences.edit().putBoolean("primegram_feed_onboarding_shown", true).apply();
                onboardingView.animate().alpha(0.0f).setDuration(300).withEndAction(() -> {
                    frameLayout.removeView(onboardingView);
                }).start();
            });
            onboardingContent.addView(onboardingBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            
            onboardingView.addView(onboardingContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            frameLayout.addView(onboardingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            
            onboardingView.setAlpha(0.0f);
            onboardingView.animate().alpha(1.0f).setDuration(400).start();
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        primeSaveScrollPosition();
    }

    /** PrimeGram: remember where the user was reading so re-opening the feed tab doesn't dump
     *  them back at the top - ported from exteraGram's FeedController.SavedScrollPosition. */
    private void primeSaveScrollPosition() {
        if (layoutManager == null || feedItems.isEmpty()) {
            return;
        }
        int first = layoutManager.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || first < 0 || first >= feedItems.size()) {
            return;
        }
        View view = layoutManager.findViewByPosition(first);
        MessageObject msg = feedItems.get(first);
        primeSavedScrollDialogId = msg.getDialogId();
        primeSavedScrollMessageId = msg.getId();
        primeSavedScrollOffset = view != null ? view.getTop() : 0;
        primeHasSavedScroll = true;
    }

    private void primeRestoreScrollIfNeeded() {
        if (!primeHasSavedScroll || layoutManager == null || feedItems.isEmpty()) {
            return;
        }
        for (int i = 0; i < feedItems.size(); i++) {
            MessageObject msg = feedItems.get(i);
            if (msg.getDialogId() == primeSavedScrollDialogId && msg.getId() == primeSavedScrollMessageId) {
                layoutManager.scrollToPositionWithOffset(i, primeSavedScrollOffset);
                break;
            }
        }
    }

    public void loadFeed() {
        if (isLoading) return;
        isLoading = true;

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

                // PrimeGram: not just "still unread" - also anything shown recently enough that it
                // should stay in the feed a while longer even though it just got marked read. A
                // dialog whose last unread post was read a second ago must not silently drop out
                // here, or its posts vanish from the feed the moment reading them is what triggers
                // the reload.
                final boolean stillTracked = !org.telegram.messenger.PrimeFeedReadState.stillVisibleIds(d.id).isEmpty();
                if (d.unread_count <= 0 && !stillTracked) {
                    continue;
                }
                if (!DialogObject.isChatDialog(d.id)) {
                    continue;
                }

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

        org.telegram.messenger.MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                org.telegram.SQLite.SQLiteDatabase database = org.telegram.messenger.MessagesStorage.getInstance(currentAccount).getDatabase();
                ArrayList<MessageObject> msgs = new ArrayList<>();

                for (TLRPC.Dialog d : unreadDialogs) {
                    // PrimeGram: "still unread" OR "shown recently enough to stay a while longer" -
                    // the OR is what keeps a post from disappearing out of the query the instant
                    // scrolling past it marks it read. An empty tracked list needs a placeholder
                    // that matches nothing ("-1"), since "IN ()" is invalid SQL.
                    final List<Integer> tracked = org.telegram.messenger.PrimeFeedReadState.stillVisibleIds(d.id);
                    final StringBuilder trackedIds = new StringBuilder();
                    if (tracked.isEmpty()) {
                        trackedIds.append("-1");
                    } else {
                        for (int i = 0; i < tracked.size(); i++) {
                            if (i > 0) trackedIds.append(",");
                            trackedIds.append(tracked.get(i));
                        }
                    }
                    org.telegram.SQLite.SQLiteCursor cursor = database.queryFinalized(
                            String.format(java.util.Locale.US, "SELECT data, mid, date FROM messages_v2 WHERE uid = %d AND (mid > %d OR mid IN (%s)) ORDER BY mid DESC LIMIT %d", d.id, d.read_inbox_max_id, trackedIds, primePerDialogLimit));

                    while (cursor.next()) {
                        org.telegram.tgnet.NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = d.id;
                            message.unread = message.id > d.read_inbox_max_id;
                            message.out = false;
                            message.post = false;
                            message.from_id = new TLRPC.TL_peerChannel();
                            message.from_id.channel_id = -d.id;

                            MessageObject obj = new MessageObject(currentAccount, message, true, false);
                            obj.forceAvatar = true;
                            msgs.add(obj);
                            org.telegram.messenger.PrimeFeedReadState.markSeen(d.id, message.id);
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
                    feedItems.clear();
                    feedItems.addAll(msgs);
                    isLoading = false;
                    if (adapter != null) adapter.notifyDataSetChanged();
                    updateEmptyView();

                    boolean moreAvailable = false;
                    for (TLRPC.Dialog d : unreadDialogs) {
                        int found = 0;
                        for (MessageObject m : feedItems) {
                            if (m.getDialogId() == d.id) found++;
                        }
                        if (found < Math.min(d.unread_count, primePerDialogLimit)) {
                            moreAvailable = true;
                            if (!requestedDialogs.contains(d.id)) {
                                requestedDialogs.add(d.id);
                                MessagesController.getInstance(currentAccount).loadMessages(
                                    d.id, 0, false, Math.min(d.unread_count, primePerDialogLimit), 0, 0, false, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, true, 0, false
                                );
                            }
                        }
                        if (d.unread_count > primePerDialogLimit) {
                            moreAvailable = true;
                        }
                    }
                    primeMoreAvailable = moreAvailable;

                    AndroidUtilities.runOnUIThread(this::checkVisibleItems, 200);
                    primeRestoreScrollIfNeeded();
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

    /** PrimeGram: pagination - bump the per-channel cap and reload when scrolled near the
     *  bottom and some channel still has more unread posts than we've loaded. */
    private void primeLoadMore() {
        if (isLoading || primePerDialogLimit >= PRIME_PAGE_MAX) {
            return;
        }
        primePerDialogLimit = Math.min(PRIME_PAGE_MAX, primePerDialogLimit + PRIME_PAGE_STEP);
        requestedDialogs.clear();
        loadFeed();
    }

    /** Coalesces the read check to one run per half-second of scrolling. */
    private final Runnable primeReadCheck = this::checkVisibleItems;
    private final Runnable primeReloadFeed = this::loadFeed;

    private void primeScheduleReadCheck() {
        AndroidUtilities.cancelRunOnUIThread(primeReadCheck);
        AndroidUtilities.runOnUIThread(primeReadCheck, 500);
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

    /**
     * PrimeGram: real reaction toggle on tap, ported from ChatActivity.selectReaction's core
     * logic (MessageObject already tracks the local reaction state; only the popup/star/emoji
     * picker UI around it is chat-specific and skipped here).
     */
    private void primeToggleReaction(MessageObject msg, TLRPC.ReactionCount reactionCount) {
        if (msg == null || getParentActivity() == null) {
            return;
        }
        org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble.VisibleReaction visibleReaction =
                org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble.VisibleReaction.fromTL(reactionCount.reaction);
        boolean added = msg.selectReaction(visibleReaction, false, false);
        java.util.ArrayList<org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble.VisibleReaction> visibleReactions = new java.util.ArrayList<>(msg.getChoosenReactions());
        org.telegram.messenger.SendMessagesHelper.getInstance(currentAccount).sendReaction(msg, visibleReactions, added ? visibleReaction : null, false, true, this, () -> {
            int index = feedItems.indexOf(msg);
            if (index >= 0 && adapter != null) {
                adapter.notifyItemChanged(index);
            }
        });
        int index = feedItems.indexOf(msg);
        if (index >= 0 && adapter != null) {
            adapter.notifyItemChanged(index);
        }
    }

    /**
     * PrimeGram: open the actual discussion thread for a channel post instead of just the
     * source channel, mirroring what a real comment tap does in a normal chat - minus the
     * inline-preview-loading dance ChatActivity does, which only makes sense while already
     * scrolling that channel's own history.
     */
    private void primeOpenComments(MessageObject msg) {
        if (msg == null || getParentActivity() == null) {
            return;
        }
        TLRPC.MessageReplies replies = msg.messageOwner != null ? msg.messageOwner.replies : null;
        if (replies == null || replies.channel_id == 0) {
            openChatAt(msg.getDialogId(), msg.getId());
            return;
        }
        TLRPC.TL_messages_getDiscussionMessage req = new TLRPC.TL_messages_getDiscussionMessage();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(msg.getDialogId());
        req.msg_id = msg.getId();
        org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_discussionMessage) {
                TLRPC.TL_messages_discussionMessage discussion = (TLRPC.TL_messages_discussionMessage) response;
                MessagesController.getInstance(currentAccount).putUsers(discussion.users, false);
                MessagesController.getInstance(currentAccount).putChats(discussion.chats, false);
                if (!discussion.messages.isEmpty()) {
                    TLRPC.Message threadMessage = discussion.messages.get(0);
                    openChatAt(-threadMessage.peer_id.channel_id, threadMessage.id);
                    return;
                }
            }
            openChatAt(-replies.channel_id, 0);
        }));
    }

    private void openChatAt(long dialogId, int messageId) {
        if (getParentActivity() == null) {
            return;
        }
        Bundle args = new Bundle();
        if (dialogId < 0) {
            args.putLong("chat_id", -dialogId);
        } else {
            args.putLong("user_id", dialogId);
        }
        if (messageId != 0) {
            args.putInt("message_id", messageId);
        }
        presentFragment(new ChatActivity(args));
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
                public void didPressReaction(org.telegram.ui.Cells.ChatMessageCell cell, TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
                    if (longpress || reaction == null || reaction.reaction == null) {
                        openChat(cell.getMessageObject());
                        return;
                    }
                    primeToggleReaction(cell.getMessageObject(), reaction);
                }

                @Override
                public void didPressCommentButton(org.telegram.ui.Cells.ChatMessageCell cell) {
                    primeOpenComments(cell.getMessageObject());
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

                @Override
                public void didPressOther(org.telegram.ui.Cells.ChatMessageCell cell, float otherX, float otherY) {
                    openChat(cell.getMessageObject());
                }

                @Override
                public void didLongPress(org.telegram.ui.Cells.ChatMessageCell cell, float x, float y) {
                    openChat(cell.getMessageObject());
                }
            };
        }

        private void openChat(MessageObject msg) {
            if (msg == null || getParentActivity() == null) return;
            Bundle args = new Bundle();
            if (msg.getDialogId() < 0) {
                args.putLong("chat_id", -msg.getDialogId());
            } else {
                args.putLong("user_id", msg.getDialogId());
            }
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
            org.telegram.ui.Cells.ChatMessageCell cell = new org.telegram.ui.Cells.ChatMessageCell(context, currentAccount, true, null, null);
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
