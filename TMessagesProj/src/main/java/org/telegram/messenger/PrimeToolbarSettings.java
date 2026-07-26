package org.telegram.messenger;

/**
 * PrimeGram: whether the formatting toolbar is shown above the message input.
 *
 * <p>Opt-in on purpose. The toolbar adds a row to {@code ChatActivityEnterView}, a view whose
 * height feeds a lot of surrounding layout; leaving it off by default means a user who never
 * asks for it runs exactly the layout upstream ships.
 */
public class PrimeToolbarSettings {

    private static final String KEY = "primegram_text_toolbar";

    public static boolean isEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(KEY, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(KEY, enabled).apply();
    }
}
