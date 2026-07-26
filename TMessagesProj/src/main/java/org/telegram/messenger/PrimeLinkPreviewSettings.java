package org.telegram.messenger;

/**
 * PrimeGram: whether holding a link renders the page in a preview window.
 *
 * <p>On by default, but switchable: the preview loads the page for real, which costs traffic
 * and tells the site it was visited. A user on a metered connection, or one who would rather
 * a held link stay unvisited, needs a way out.
 */
public class PrimeLinkPreviewSettings {

    private static final String KEY = "primegram_link_preview";

    public static boolean isEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(KEY, true);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY, enabled).apply();
    }
}
