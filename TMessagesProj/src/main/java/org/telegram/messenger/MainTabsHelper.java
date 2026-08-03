package org.telegram.messenger;

import android.content.SharedPreferences;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.DialogsActivity;

/**
 * PrimeGram: bottom main-tabs tweaks ported from inugram's bottom-tabs.patch - compact
 * (icon-only) tabs and hiding the tab bar entirely. Both are optional, off by default.
 *
 * <p>The patch's third option, hiding the "Contacts" tab, is not ported: that tab was already
 * excluded from the visible bar in this fork before this port, so there is nothing left to hide.
 */
public class MainTabsHelper {

    private static final String KEY_COMPACT = "primegram_bottom_tabs_compact";
    private static final String KEY_HIDE = "primegram_bottom_tabs_hide";

    private static final int MAIN_TABS_HEIGHT_COMPACT = 48;
    private static final int MAIN_TABS_MARGIN_COMPACT = 4;

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean isCompact() {
        return prefs().getBoolean(KEY_COMPACT, false);
    }

    public static void setCompact(boolean enabled) {
        prefs().edit().putBoolean(KEY_COMPACT, enabled).apply();
    }

    /**
     * True when the bottom tab bar should not exist at all - either because this toggle is on
     * directly, or because {@link DrawerHelper}'s classic side-menu navigation is on, which
     * already replaces the tab bar with its own root fragment. The two can't sensibly be active
     * at once; when both preferences happen to be on, DrawerHelper takes priority at every call
     * site that decides between it and a plain tabless fragment, so nothing breaks - this flag
     * only needs to answer "should MainTabsActivity be skipped", not which replacement wins.
     */
    public static boolean isHidden() {
        return prefs().getBoolean(KEY_HIDE, false) || DrawerHelper.isEnabled();
    }

    public static void setHidden(boolean enabled) {
        prefs().edit().putBoolean(KEY_HIDE, enabled).apply();
    }

    public static int getMainTabsHeight() {
        return isCompact() ? MAIN_TABS_HEIGHT_COMPACT : DialogsActivity.MAIN_TABS_HEIGHT;
    }

    public static int getMainTabsMargin() {
        return isCompact() ? MAIN_TABS_MARGIN_COMPACT : DialogsActivity.MAIN_TABS_MARGIN;
    }

    public static int getMainTabsHeightWithMargins() {
        return getMainTabsHeight() + getMainTabsMargin() * 2;
    }

    /**
     * Routes a bulletin shown from a tabless-root {@link DialogsActivity} (hasMainTabs but no
     * actual tab bar because it's hidden) past where the tab bar would have sat, instead of
     * leaving a gap under it. Fragments still inside a real {@code MainTabsActivity} are left
     * alone - that activity already has its own bottom-offset delegate.
     */
    public static FrameLayout resolveBulletinContainer(BaseFragment fragment) {
        if (fragment instanceof DialogsActivity && ((DialogsActivity) fragment).hasMainTabs && fragment.getParentActivity() != null) {
            return Bulletin.BulletinWindow.make(fragment.getParentActivity(), new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return isHidden() ? 0 : AndroidUtilities.dp(getMainTabsHeightWithMargins());
                }
            });
        }
        return null;
    }
}
