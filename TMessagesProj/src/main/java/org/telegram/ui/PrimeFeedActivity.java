package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PrimeFeedController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * PrimeGram "Лента" - unread posts from subscribed broadcast channels.
 *
 * <p>Hosted in a real {@link ChatActivity} under {@link ChatActivity#MODE_FEED} rather than a
 * custom {@code RecyclerView} - a post here now gets exactly the same rendering, reactions, and
 * comment button as any other message cell in the app, because it genuinely is one. What stays
 * ours is only the data: {@link PrimeFeedController} supplies just the unread set, never a real
 * search, and this class is what turns "left the tab" into "mark what was shown as read".
 *
 * <p>Scroll position is remembered across tab switches by (dialogId, messageId) rather than a
 * pixel offset, in a static field so it survives the fragment itself being recreated when the
 * tab host evicts an off-screen tab - see {@link ChatActivity#primeGetFirstVisibleFeedPost()}
 * and {@link ChatActivity#primeScrollToFeedPost}.
 */
public class PrimeFeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate, NotificationCenter.NotificationCenterDelegate {

    private ChatActivityContainer chatContainer;
    private boolean embeddedChatCreated;

    private static long primeSavedScrollDialogId;
    private static int primeSavedScrollMessageId;
    private static boolean primeHasSavedScroll;

    /** Set once the initial load's scroll position has been settled (restored or defaulted to
     *  top), reset each time the fragment is (re)created. Without this, every later
     *  {@code messagesDidLoad} - including the ones {@link PrimeFeedController#loadMore} fires as
     *  the user scrolls up through older pages - re-ran the same restore, snapping the list back
     *  to wherever it was when the tab was last left and fighting the user's own scroll input. */
    private boolean scrollSettledThisSession;

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.messagesDidLoad);
        scrollSettledThisSession = false;
        return super.onFragmentCreate();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad && !scrollSettledThisSession && chatContainer != null && chatContainer.chatActivity != null
                && (Integer) args[10] == chatContainer.chatActivity.getClassGuid()) {
            scrollSettledThisSession = true;
            // Posted, not called directly: ChatActivity's own handler for this same notification
            // has to run first and actually populate its message list before there is anything
            // here to scroll to.
            AndroidUtilities.runOnUIThread(this::primeRestoreScrollIfNeeded);
        }
    }

    private void primeRestoreScrollIfNeeded() {
        if (chatContainer == null || chatContainer.chatActivity == null) {
            return;
        }
        // The feed only ever holds unread posts - a saved (dialogId, messageId) can easily no
        // longer be in the freshly loaded set (it, or everything ahead of it, got read elsewhere
        // meanwhile). Falling through to the top instead of leaving the default "opened at the
        // newest post" position covers both that case and the plain first-ever-open case below.
        if (!primeHasSavedScroll || !chatContainer.chatActivity.primeScrollToFeedPost(primeSavedScrollDialogId, primeSavedScrollMessageId)) {
            chatContainer.chatActivity.primeScrollToFeedTop();
        }
    }

    private void primeSaveScrollPosition() {
        if (chatContainer == null || chatContainer.chatActivity == null) {
            return;
        }
        final MessageObject first = chatContainer.chatActivity.primeGetFirstVisibleFeedPost();
        if (first == null) {
            return;
        }
        primeSavedScrollDialogId = first.getDialogId();
        primeSavedScrollMessageId = first.getId();
        primeHasSavedScroll = true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);

        final FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        final Bundle bundle = new Bundle();
        bundle.putInt("chatMode", ChatActivity.MODE_FEED);
        final ChatActivityContainer container = new ChatActivityContainer(context, getParentLayout(), bundle) {
            boolean activityCreated;

            @Override
            public void initChatActivity() {
                if (activityCreated) {
                    return;
                }
                activityCreated = true;
                embeddedChatCreated = true;
                super.initChatActivity();
                setupFeedActionBar();
            }
        };
        chatContainer = container;
        container.chatActivity.isInsideContainer = false;
        frameLayout.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void setupFeedActionBar() {
        final ChatActivity chatActivity = chatContainer != null ? chatContainer.chatActivity : null;
        if (chatActivity == null) {
            return;
        }
        final ActionBar innerActionBar = chatActivity.getActionBar();
        if (innerActionBar == null) {
            return;
        }
        final ActionBarMenu menu = innerActionBar.createMenu();
        if (menu.getItem(1) == null) {
            menu.addItem(1, R.drawable.msg_markread).setContentDescription("Отметить всё прочитанным");
        }
        innerActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == 1) {
                    showMarkAllReadDialog();
                }
            }
        });
    }

    private void showMarkAllReadDialog() {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("Отметить всё прочитанным")
                .setMessage("Все посты в ленте будут отмечены как прочитанные.")
                .setPositiveButton("Отметить", (dialog, which) -> PrimeFeedController.getInstance(currentAccount).markDeliveredAsRead())
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (chatContainer != null) {
            chatContainer.onResume();
            if (chatContainer.chatActivity != null) {
                // The embedded ChatActivity is constructed directly inside ChatActivityContainer,
                // never presentFragment()'d onto the real navigation stack - so nothing ever calls
                // its onBecomeFullyVisible(), and isFullyVisible stays false forever. That flag
                // gates several real actions inside ChatActivity, comment-opening among them:
                // didPressCommentButton -> openDiscussionMessageChat eventually checks it and
                // silently no-ops when false, which is why the comment button was clickable but
                // never actually opened anything. Calling it here, tied to the tab's own
                // onResume/onPause, keeps it true only while the feed is genuinely the visible tab
                // - the same thing a real fragment push/pop would leave it as.
                chatContainer.chatActivity.onBecomeFullyVisible();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        primeSaveScrollPosition();
        if (chatContainer != null) {
            if (chatContainer.chatActivity != null) {
                chatContainer.chatActivity.onBecomeFullyHidden();
            }
            chatContainer.onPause();
        }
        // A real ChatActivity's own read-tracking is single-dialog and does nothing useful with
        // dialog_id 0 - this is what actually clears what was shown from the unread set.
        PrimeFeedController.getInstance(currentAccount).markDeliveredAsRead();
    }

    @Override
    public void onFragmentDestroy() {
        primeSaveScrollPosition();
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoad);
        if (embeddedChatCreated && chatContainer != null) {
            chatContainer.chatActivity.onFragmentDestroy();
        }
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (chatContainer != null && chatContainer.chatActivity != null) {
            return chatContainer.chatActivity.onBackPressed(invoked);
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onParentScrollToTop() {
        if (chatContainer != null && chatContainer.chatActivity != null && chatContainer.chatActivity.getChatListView() != null) {
            chatContainer.chatActivity.getChatListView().smoothScrollToPosition(0);
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return true;
    }
}
