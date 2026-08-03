package org.telegram.messenger;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SideMenultItemAnimator;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;
import org.telegram.ui.ProxyListActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.WebAppDisclaimerAlert;
import org.telegram.ui.bots.BotWebViewSheet;

/**
 * PrimeGram: root-navigation flavor switch - the classic hamburger side drawer instead of the
 * bottom {@code MainTabsActivity} tabs. Restored/adapted from Telegram-Android's pre-redesign
 * {@code LaunchActivity} drawer wiring (drag-reorder, long-press account preview, item clicks),
 * ported from inugram's {@code DrawerHelper.kt} equivalent. Off by default - {@code MainTabsActivity}
 * remains the default root fragment.
 */
public final class DrawerHelper {

    private static final String PREF_KEY = "primegram_navigation_drawer";

    private DrawerHelper() {
    }

    public static boolean isEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_KEY, false);
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_KEY, enabled).apply();
    }

    public static DialogsActivity createMainFragment() {
        return createMainFragment(null);
    }

    public static DialogsActivity createMainFragment(Bundle args) {
        return new DialogsActivity(args);
    }

    public static void addMainFragmentToStack(INavigationLayout actionBarLayout, String searchQuery) {
        DialogsActivity dialogsActivity = createMainFragment();
        if (searchQuery != null) {
            dialogsActivity.setInitialSearchString(searchQuery);
        }
        actionBarLayout.addFragmentToStack(dialogsActivity, INavigationLayout.FORCE_NOT_ATTACH_VIEW);
    }

    public static void ensureSetup(INavigationLayout parentLayout) {
        // Setup happens once in LaunchActivity.setupMainFragment; nothing lazy is needed here,
        // this hook exists so call sites outside LaunchActivity don't need a null check.
    }

    public static boolean toggleDrawer(INavigationLayout parentLayout) {
        if (parentLayout == null) return false;
        DrawerLayoutContainer container = parentLayout.getDrawerLayoutContainer();
        if (container == null || !container.isAllowOpenDrawer()) return false;
        if (container.isDrawerOpened()) {
            container.closeDrawer(false);
        } else {
            container.openDrawer(false);
        }
        return true;
    }

    public static void refreshMenuButton(org.telegram.ui.ActionBar.MenuDrawable menuDrawable, boolean animated) {
        // Rotation state (hamburger <-> back arrow) is driven by DialogsActivity's own
        // transitionAnimationStart/End hooks, mirroring backDrawable.setRotation there; nothing
        // extra to precompute here.
    }

    public static void notifyDataChanged() {
        if (currentAdapter != null) {
            currentAdapter.notifyDataSetChanged();
        }
    }

    private static DrawerLayoutAdapter currentAdapter;
    private static RecyclerListView currentSideMenu;

    public static RecyclerListView getSideMenu() {
        return currentSideMenu;
    }

    /** Builds the side menu (list + adapter + drag/long-press handling) and wires it into
     *  {@code drawerLayoutContainer}, then adds the drawer-mode root {@link DialogsActivity} to
     *  {@code actionBarLayout}. Mirrors stock pre-redesign {@code LaunchActivity#onCreate}. */
    public static void setupMainFragment(LaunchActivity activity, INavigationLayout actionBarLayout, DrawerLayoutContainer drawerLayoutContainer) {
        int currentAccount = UserConfig.selectedAccount;

        FrameLayout sideMenuContainer = new FrameLayout(activity);
        final SideMenultItemAnimator[] itemAnimatorRef = new SideMenultItemAnimator[1];
        RecyclerListView sideMenu = new RecyclerListView(activity) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                SideMenultItemAnimator itemAnimator = itemAnimatorRef[0];
                int restore = -1;
                if (itemAnimator != null && itemAnimator.isRunning() && itemAnimator.isAnimatingChild(child)) {
                    restore = canvas.save();
                    canvas.clipRect(0, itemAnimator.getAnimationClipTop(), getMeasuredWidth(), getMeasuredHeight());
                }
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (restore >= 0) {
                    canvas.restoreToCount(restore);
                    invalidate();
                    invalidateViews();
                }
                return result;
            }
        };
        SideMenultItemAnimator finalItemAnimator = new SideMenultItemAnimator(sideMenu);
        itemAnimatorRef[0] = finalItemAnimator;
        sideMenu.setItemAnimator(finalItemAnimator);
        sideMenu.setClipToPadding(false);
        // Trust the theme's own value when it has one - most do, built-in dark themes included.
        // Only a theme that never defined this legacy key at all falls back to a compiled-in
        // light-blue constant, which is the one case actually worth overriding.
        int menuBackground = Theme.hasThemeKey(Theme.key_chats_menuBackground) ? Theme.getColor(Theme.key_chats_menuBackground)
            : (Theme.isCurrentThemeDark() ? Theme.getColor(Theme.key_windowBackgroundWhite) : Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setBackgroundColor(menuBackground);
        sideMenuContainer.setBackgroundColor(menuBackground);
        sideMenu.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        sideMenu.setAllowItemsInteractionDuringAnimation(false);
        DrawerLayoutAdapter drawerLayoutAdapter = new DrawerLayoutAdapter(activity, finalItemAnimator, drawerLayoutContainer);
        drawerLayoutAdapter.onGhostSwitchToggled = checked -> {
            org.telegram.messenger.GreyZone.setGhostMode(checked);
            LaunchActivity.refreshGreyZoneUi();
        };
        sideMenu.setAdapter(drawerLayoutAdapter);
        sideMenuContainer.addView(sideMenu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        drawerLayoutContainer.setDrawerLayout(sideMenuContainer, sideMenu);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sideMenuContainer.getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = AndroidUtilities.isTablet() ? AndroidUtilities.dp(320) : Math.min(AndroidUtilities.dp(320), Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56));
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        sideMenuContainer.setLayoutParams(layoutParams);

        currentAdapter = drawerLayoutAdapter;
        currentSideMenu = sideMenu;

        sideMenu.setOnItemClickListener((view, position, x, y) -> {
            if (drawerLayoutAdapter.click(view, position)) {
                drawerLayoutContainer.closeDrawer(false);
                return;
            }
            if (position == 0) {
                drawerLayoutAdapter.setAccountsShown(!drawerLayoutAdapter.isAccountsShown(), true);
            } else if (view instanceof DrawerUserCell) {
                activity.switchToAccount(((DrawerUserCell) view).getAccountNumber(), true);
                drawerLayoutContainer.closeDrawer(false);
            } else if (view instanceof DrawerAddCell) {
                int freeAccounts = 0;
                Integer availableAccount = null;
                for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
                    if (!UserConfig.getInstance(a).isClientActivated()) {
                        freeAccounts++;
                        if (availableAccount == null) {
                            availableAccount = a;
                        }
                    }
                }
                if (!UserConfig.hasPremiumOnAccounts()) {
                    freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
                }
                if (freeAccounts > 0 && availableAccount != null) {
                    activity.presentFragment(new LoginActivity(availableAccount));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (!UserConfig.hasPremiumOnAccounts()) {
                    if (!actionBarLayout.getFragmentStack().isEmpty()) {
                        BaseFragment fragment = actionBarLayout.getFragmentStack().get(0);
                        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(fragment, activity, LimitReachedBottomSheet.TYPE_ACCOUNTS, currentAccount, null);
                        fragment.showDialog(limitReachedBottomSheet);
                        limitReachedBottomSheet.onShowPremiumScreenRunnable = () -> drawerLayoutContainer.closeDrawer(false);
                    }
                }
            } else {
                int id = drawerLayoutAdapter.getId(position);
                TLRPC.TL_attachMenuBot attachMenuBot = drawerLayoutAdapter.getAttachMenuBot(position);
                if (attachMenuBot != null) {
                    if (attachMenuBot.inactive || attachMenuBot.side_menu_disclaimer_needed) {
                        WebAppDisclaimerAlert.show(activity, (allowSendMessage) -> {
                            TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                            botRequest.bot = MessagesController.getInstance(currentAccount).getInputUser(attachMenuBot.bot_id);
                            botRequest.enabled = true;
                            botRequest.write_allowed = true;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(botRequest, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                attachMenuBot.inactive = attachMenuBot.side_menu_disclaimer_needed = false;
                                LaunchActivity.showAttachMenuBot(activity, currentAccount, attachMenuBot, null, true);
                                MediaDataController.getInstance(currentAccount).updateAttachMenuBotsInCache();
                            }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                        }, null, null);
                    } else {
                        LaunchActivity.showAttachMenuBot(activity, currentAccount, attachMenuBot, null, true);
                    }
                    return;
                }
                if (id == 6) {
                    Bundle args = new Bundle();
                    args.putBoolean("needFinishFragment", false);
                    activity.presentFragment(new ContactsActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == DrawerLayoutAdapter.ITEM_PROXY) {
                    activity.presentFragment(new ProxyListActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 8) {
                    activity.presentFragment(new SettingsActivity(new Bundle()));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == DrawerLayoutAdapter.ITEM_PLUGINS) {
                    activity.presentFragment(new org.telegram.ui.PrimePluginsActivity());
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == DrawerLayoutAdapter.ITEM_BROWSER) {
                    activity.presentFragment(new org.telegram.ui.PrimeBrowserActivity(""));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == DrawerLayoutAdapter.ITEM_WALLET) {
                    try {
                        org.telegram.messenger.browser.Browser.openUrl(activity, "https://t.me/wallet");
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == DrawerLayoutAdapter.ITEM_PARTNER) {
                    try {
                        org.telegram.messenger.browser.Browser.openUrl(activity, "https://t.me/govpn?start=2f6a4271-0e89-481c-b90f-ecfd0e1a888d");
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 11) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    activity.presentFragment(new ChatActivity(args));
                    drawerLayoutContainer.closeDrawer(false);
                } else if (id == 16) {
                    drawerLayoutContainer.closeDrawer(true);
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    args.putBoolean("my_profile", true);
                    activity.presentFragment(new org.telegram.ui.ProfileActivity(args, null));
                }
            }
        });

        final ItemTouchHelper sideMenuTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            private RecyclerView.ViewHolder selectedViewHolder;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (viewHolder.getItemViewType() != target.getItemViewType()) {
                    return false;
                }
                drawerLayoutAdapter.swapElements(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                clearSelectedViewHolder();
                if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                    selectedViewHolder = viewHolder;
                    final View view = viewHolder.itemView;
                    sideMenu.cancelClickRunnables(false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
                    ObjectAnimator.ofFloat(view, "elevation", AndroidUtilities.dp(1)).setDuration(150).start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                clearSelectedViewHolder();
            }

            private void clearSelectedViewHolder() {
                if (selectedViewHolder != null) {
                    final View view = selectedViewHolder.itemView;
                    selectedViewHolder = null;
                    view.setTranslationX(0f);
                    view.setTranslationY(0f);
                    final ObjectAnimator animator = ObjectAnimator.ofFloat(view, "elevation", 0f);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            view.setBackground(null);
                        }
                    });
                    animator.setDuration(150).start();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                final View view = viewHolder.itemView;
                if (drawerLayoutAdapter.isAccountsShown()) {
                    RecyclerView.ViewHolder topViewHolder = recyclerView.findViewHolderForAdapterPosition(drawerLayoutAdapter.getFirstAccountPosition() - 1);
                    RecyclerView.ViewHolder bottomViewHolder = recyclerView.findViewHolderForAdapterPosition(drawerLayoutAdapter.getLastAccountPosition() + 1);
                    if (topViewHolder != null && topViewHolder.itemView.getBottom() == view.getTop() && dY < 0f) {
                        dY = 0f;
                    } else if (bottomViewHolder != null && bottomViewHolder.itemView.getTop() == view.getBottom() && dY > 0f) {
                        dY = 0f;
                    }
                }
                view.setTranslationX(dX);
                view.setTranslationY(dY);
            }
        });
        sideMenuTouchHelper.attachToRecyclerView(sideMenu);

        sideMenu.setOnItemLongClickListener((view, position) -> {
            if (view instanceof DrawerUserCell) {
                final int accountNumber = ((DrawerUserCell) view).getAccountNumber();
                if (accountNumber == currentAccount || AndroidUtilities.isTablet()) {
                    sideMenuTouchHelper.startDrag(sideMenu.getChildViewHolder(view));
                } else {
                    final DialogsActivity fragment = new DialogsActivity(null) {
                        @Override
                        public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                            super.onTransitionAnimationEnd(isOpen, backward);
                            if (!isOpen && backward) {
                                drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(false);
                                actionBarLayout.getView().invalidate();
                            }
                        }

                        @Override
                        public void onPreviewOpenAnimationEnd() {
                            super.onPreviewOpenAnimationEnd();
                            drawerLayoutContainer.setAllowOpenDrawer(false, false);
                            drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(false);
                            activity.switchToAccount(accountNumber, true);
                            actionBarLayout.getView().invalidate();
                        }
                    };
                    fragment.setCurrentAccount(accountNumber);
                    actionBarLayout.presentFragmentAsPreview(fragment);
                    drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(true);
                    return true;
                }
            }
            if (view instanceof org.telegram.ui.Cells.DrawerActionCell) {
                TLRPC.TL_attachMenuBot attachMenuBot = drawerLayoutAdapter.getAttachMenuBot(position);
                if (attachMenuBot != null) {
                    BotWebViewSheet.deleteBot(currentAccount, attachMenuBot.bot_id, null);
                    return true;
                }
            }
            return false;
        });

        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);

        DialogsActivity dialogsActivity = createMainFragment();
        actionBarLayout.addFragmentToStack(dialogsActivity);
        drawerLayoutContainer.setAllowOpenDrawer(true, false);
    }
}
