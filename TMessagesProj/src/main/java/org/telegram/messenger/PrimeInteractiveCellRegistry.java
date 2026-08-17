package org.telegram.messenger;

import android.content.Context;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PrimeInteractiveActionCell;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: dynamic registry for custom, interactive chat-message rows -
 * {@link PrimeInteractiveActionCell} subclasses that render inline in the message
 * feed (like Telegram's own system/service messages) instead of an ordinary bubble or
 * being hidden outright. Built for the E2E handshake prompt, but deliberately generic:
 * any future feature registers its own {@link Factory} here and never has to touch
 * {@code ChatActivity.java} again.
 *
 * <p>Each registration gets a stable, unique view-type integer starting at
 * {@link #BASE_VIEW_TYPE} - RecyclerView's recycling contract requires every view of a
 * given view-type to be interchangeable (same concrete class), so with multiple
 * registered cell kinds each needs its own reserved type, not one shared value.
 * {@code BASE_VIEW_TYPE} sits well above every hard-coded view-type int
 * {@code ChatActivity}'s adapter already uses (0-8), so there's no collision risk.
 */
public final class PrimeInteractiveCellRegistry {

    public interface Factory {
        /** Called once per message row build - keep this cheap (a marker-prefix check,
         *  not a decrypt or DB read). */
        boolean handles(MessageObject obj);

        PrimeInteractiveActionCell create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    private static final int BASE_VIEW_TYPE = 1000;
    private static final List<Factory> factories = new ArrayList<>();

    private PrimeInteractiveCellRegistry() {
    }

    public static synchronized void register(Factory factory) {
        factories.add(factory);
    }

    /** @return the reserved view type for this message, or -1 if no registered factory
     *  handles it (the common case for every ordinary message). */
    public static synchronized int viewTypeFor(MessageObject obj) {
        for (int i = 0; i < factories.size(); i++) {
            if (factories.get(i).handles(obj)) {
                return BASE_VIEW_TYPE + i;
            }
        }
        return -1;
    }

    public static synchronized Factory factoryForViewType(int viewType) {
        int index = viewType - BASE_VIEW_TYPE;
        return (index >= 0 && index < factories.size()) ? factories.get(index) : null;
    }
}
