package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatActivityTopPanelLayout;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.MentionsContainerView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.SearchTabsAndFiltersLayout;

/**
 * PrimeGram: a single on/off switch for the "island" visual redesign - the rounded, glass,
 * floating action bars and tab strips upstream introduced - ported from inugram's
 * {@code NonIslandHelper.kt}/{@code InuConfig}, which exposes four separate flags (chat elements,
 * folders bar, shared media tabs, global search). Collapsed here into one, because the whole point
 * from this fork's side is "give me back the flat classic look", not four independent switches
 * nobody asked to reason about separately - if that granularity turns out to matter later, splitting
 * one preference key into four is a small change, not a rewrite.
 *
 * <p>Off by default: the modern island look ships as PrimeGram's own default, same as upstream: this
 * is an escape hatch for people who want the old one back, not a replacement default.
 */
public final class NonIslandHelper {

    private static final String PREF_KEY = "primegram_non_island_ui";

    private NonIslandHelper() {
    }

    public static boolean isEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_KEY, false);
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_KEY, enabled).apply();
    }

    public static boolean foldersBar() {
        return isEnabled();
    }

    public static boolean sharedMediaTabs() {
        return isEnabled();
    }

    public static boolean globalSearch() {
        return isEnabled();
    }

    public static boolean chatElements() {
        return isEnabled();
    }

    // inugram swaps to a dedicated flat send icon (R.drawable.ic_send) here; we don't carry that
    // asset, so the flat mode keeps the stock plane icon rather than importing a new drawable.
    public static int chatSendIcon() {
        return org.telegram.messenger.R.drawable.send_plane_24;
    }

    public static int chatInputRowHeight() {
        return chatElements() ? 48 : 44;
    }

    public static Drawable createInputButtonSelector(int color) {
        return chatElements()
                ? Theme.createSelectorDrawable(color)
                : Theme.createInsetRoundRectDrawable(color, AndroidUtilities.dp(19), AndroidUtilities.dp(1), AndroidUtilities.dp(3));
    }

    /** {@code frameWidthDp} is the enter view's own send-button frame width, in dp - the same value
     *  every caller already has on hand as a local when it lays the button out. */
    public static void applySendButtonRipple(View button, int frameWidthDp, int color) {
        if (!chatElements()) {
            return;
        }
        final int box = AndroidUtilities.dp(ChatActivityEnterView.DEFAULT_HEIGHT);
        final int inset = AndroidUtilities.dp(3);
        button.setBackground(Theme.createInsetRoundRectDrawable(color,
                (box - inset * 2) / 2f,
                AndroidUtilities.dp(frameWidthDp) - box + inset, inset, inset, inset));
    }

    /** {@code null} means "no opinion, let the caller's own default stand" - the island look does
     *  not need a nav-bar/status-bar color override where the flat one does. */
    public static Boolean needChatLightNavBar(float inputBubbleHeight, Theme.ResourcesProvider resourcesProvider) {
        if (!chatElements() || inputBubbleHeight <= 0f) {
            return null;
        }
        final int color = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        return AndroidUtilities.computePerceivedBrightness(color) <= 0.9f;
    }

    public static Boolean needChatLightStatusBar(Theme.ResourcesProvider resourcesProvider) {
        if (!chatElements()) {
            return null;
        }
        final int color = Theme.getColor(Theme.key_chat_topPanelBackground, resourcesProvider);
        return AndroidUtilities.computePerceivedBrightness(color) > 0.721f;
    }

    public static final float ATTACH_TAB_SHADOW_DP = 3f;

    public static void applyChatAttachTabBar(FrameLayout wrapper, RecyclerListView recyclerView) {
        if (!chatElements()) {
            return;
        }
        wrapper.setBackground(null);
        wrapper.setClipChildren(false);
        recyclerView.setClipToOutline(false);
        recyclerView.setOutlineProvider(null);
        final int innerPaddingTop = AndroidUtilities.dp(4);
        final int innerPadding = AndroidUtilities.dp(6);
        recyclerView.setPadding(innerPadding, innerPaddingTop, innerPadding, AndroidUtilities.navigationBarHeight);
        recyclerView.setClipToPadding(false);
        final ViewGroup.LayoutParams recyclerLpRaw = recyclerView.getLayoutParams();
        if (!(recyclerLpRaw instanceof FrameLayout.LayoutParams)) {
            return;
        }
        final FrameLayout.LayoutParams recyclerLp = (FrameLayout.LayoutParams) recyclerLpRaw;
        final int shadowH = AndroidUtilities.dp(ATTACH_TAB_SHADOW_DP);
        recyclerLp.topMargin = shadowH;
        final ViewGroup.LayoutParams lpRaw = wrapper.getLayoutParams();
        if (!(lpRaw instanceof FrameLayout.LayoutParams)) {
            return;
        }
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lpRaw;
        lp.height = shadowH + AndroidUtilities.dp(48) + innerPaddingTop + AndroidUtilities.navigationBarHeight;
        lp.bottomMargin = -AndroidUtilities.navigationBarHeight;
    }

    public static final int FOLDERS_BAR_HEIGHT_DP = 44;
    public static final int FOLDERS_BAR_OVERLAP_DP = 10;
    public static final int FOLDERS_BAR_VISIBLE_HEIGHT_DP = FOLDERS_BAR_HEIGHT_DP - FOLDERS_BAR_OVERLAP_DP;

    public static void applyMd3TabsStyle(GradientDrawable indicator, RecyclerListView listView, int selectorColor) {
        final float rad = AndroidUtilities.dpf2(3);
        indicator.setCornerRadii(new float[]{rad, rad, rad, rad, 0f, 0f, 0f, 0f});
        listView.setSelectorType(42);
        listView.setSelectorRadius(16);
        listView.setSelectorDrawableColor(selectorColor);
    }

    public static void adjustMd3TabSelectorRect(Rect rect) {
        final int cy = rect.centerY();
        rect.top = cy - AndroidUtilities.dp(16);
        rect.bottom = cy + AndroidUtilities.dp(16);
        rect.inset(AndroidUtilities.dp(2), 0);
    }

    public static void setMd3TabIndicatorBounds(Drawable indicator, float indicatorX, float indicatorWidth, float height, float hideProgress) {
        final float centerX = indicatorX + indicatorWidth / 2f;
        final float width = Math.max(AndroidUtilities.dp(24), indicatorWidth - AndroidUtilities.dp(2) * 2);
        final float hideOffset = hideProgress * AndroidUtilities.dp(3);
        indicator.setBounds(
                (int) (centerX - width / 2f),
                (int) (height - AndroidUtilities.dp(3) + hideOffset),
                (int) (centerX + width / 2f),
                (int) (height + hideOffset));
    }

    public static void applyFilterTabBar(FilterTabsView tabsView, SizeNotifierFrameLayout contentView) {
        if (!foldersBar()) {
            return;
        }
        tabsView.setBlurredBackground(null);
        tabsView.setBackground(null);
        tabsView.inu_blurHelper = new BlurBehindHelper(tabsView, contentView, Theme.key_windowBackgroundWhite, true, 0f, 0f, true);
        tabsView.setPadding(0, 0, 0, 0);
        final ViewGroup.LayoutParams lpRaw = tabsView.getLayoutParams();
        if (!(lpRaw instanceof FrameLayout.LayoutParams)) {
            return;
        }
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lpRaw;
        lp.height = AndroidUtilities.dp(FOLDERS_BAR_HEIGHT_DP);
        lp.leftMargin = 0;
        lp.rightMargin = 0;
    }

    public static void applyGlobalSearchBar(FragmentSearchField field, SizeNotifierFrameLayout contentView) {
        if (!globalSearch()) {
            return;
        }
        field.setupBlurredBackground(null);
        field.inu_blurHelper = new BlurBehindHelper(field, contentView, Theme.key_windowBackgroundWhite, true, 0f, 0f, false);
        final ViewGroup.LayoutParams lpRaw = field.getLayoutParams();
        if (lpRaw instanceof FrameLayout.LayoutParams) {
            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lpRaw;
            lp.leftMargin = 0;
            lp.rightMargin = 0;
        }
        updateGlobalSearchBarInsets(field);
    }

    public static void updateGlobalSearchBarInsets(FragmentSearchField field) {
        if (!globalSearch()) {
            return;
        }
        final int extraTopPadding = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(8);
        field.setPadding(0, extraTopPadding, 0, 0);
        final ViewGroup.LayoutParams lpRaw = field.getLayoutParams();
        if (!(lpRaw instanceof FrameLayout.LayoutParams)) {
            return;
        }
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lpRaw;
        final int height = AndroidUtilities.dp(DialogsActivity.SEARCH_FIELD_HEIGHT) + extraTopPadding;
        if (lp.height != height) {
            lp.height = height;
            field.requestLayout();
        }
    }

    public static void applyGlobalSearchTabs(SearchTabsAndFiltersLayout layout, SizeNotifierFrameLayout contentView) {
        if (!globalSearch()) {
            return;
        }
        layout.setBlurredBackground(null);
        layout.setBackground(null);
        layout.inu_blurHelper = new BlurBehindHelper(layout, contentView, Theme.key_windowBackgroundWhite, true, 0f, 0f, true);
        layout.setPadding(0, 0, 0, 0);
        layout.setTranslationY(-AndroidUtilities.dp(FOLDERS_BAR_OVERLAP_DP));
        final ViewGroup.LayoutParams lpRaw = layout.getLayoutParams();
        if (!(lpRaw instanceof FrameLayout.LayoutParams)) {
            return;
        }
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lpRaw;
        lp.height = AndroidUtilities.dp(FOLDERS_BAR_HEIGHT_DP);
        lp.leftMargin = 0;
        lp.rightMargin = 0;
    }

    public static void applyChatTopPanelButton(TextView view) {
        if (!chatElements()) {
            return;
        }
        view.setStateListAnimator(null);
        view.setBackground(Theme.createSelectorDrawable(
                Theme.multAlpha(view.getCurrentTextColor(), 0.10f), Theme.RIPPLE_MASK_ALL));
    }

    public static void drawChatHeaderShadow(INavigationLayout parentLayout, Canvas canvas,
                                             ChatActivityTopPanelLayout topPanelLayout,
                                             MentionsContainerView mentionContainer,
                                             float topicsTabsHeight, int actionBarBottom) {
        if (!chatElements()) {
            // upstream no longer draws an action-bar header shadow in island mode
            return;
        }
        final boolean hasPanel = topPanelLayout != null && actionBarBottom > 0;
        final int panelH = hasPanel ? (int) topPanelLayout.getAnimatedHeightWithPadding(0f) : 0;
        if (!(mentionContainer != null && mentionContainer.getVisibility() == View.VISIBLE)) {
            parentLayout.drawHeaderShadow(canvas, actionBarBottom + (int) topicsTabsHeight + panelH);
        }
    }
}
