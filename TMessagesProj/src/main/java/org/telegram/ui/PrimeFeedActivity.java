package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
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
 * <p>Not yet carried over from the previous, simpler implementation: continuous scroll-based
 * pagination (a channel with more unread than the controller's per-load cap only shows the
 * newest of them until the tab is reopened) and precise scroll-position restore across tab
 * switches. Both are real gaps, not silently dropped - see {@link PrimeFeedController}.
 */
public class PrimeFeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private ChatActivityContainer chatContainer;
    private boolean embeddedChatCreated;

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
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (chatContainer != null) {
            chatContainer.onPause();
        }
        // A real ChatActivity's own read-tracking is single-dialog and does nothing useful with
        // dialog_id 0 - this is what actually clears what was shown from the unread set.
        PrimeFeedController.getInstance(currentAccount).markDeliveredAsRead();
    }

    @Override
    public void onFragmentDestroy() {
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
