package org.telegram.messenger.blocks;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * PrimeGram Blocks: where a `.pr` script's {@code action.ui.*} visibility overrides live - the
 * mechanism behind "hide this button" (see the "Splash Screen + Blocks Trigger Expansion" plan's
 * Part 3). Mirrors {@link PrimeBlockStore}'s shape exactly, kept as its own class for the same
 * reason that one is separate from {@code PrimePluginStore}: a distinct, narrow concept, not
 * worth blurring into a shared prefs blob.
 *
 * <p>An override can only ever hide something a surface's own logic already wanted to show - it
 * is read as an extra {@code &&} on top of that logic, never a replacement for it, so it can never
 * force a button on that real chat/app state says shouldn't exist. See
 * {@code ChatActivityChannelButtonsLayout.showButton}'s call site.
 */
public final class PrimeBlocksUiOverrides {

    private static final String PREFS = "primegram_blocks_ui";
    private static final String VISIBLE_PREFIX = "visible_";

    private PrimeBlocksUiOverrides() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Visible unless a script has explicitly hidden it. */
    public static boolean isVisible(String elementId) {
        return prefs().getBoolean(VISIBLE_PREFIX + elementId, true);
    }

    public static void setVisible(String elementId, boolean visible) {
        prefs().edit().putBoolean(VISIBLE_PREFIX + elementId, visible).apply();
    }
}
