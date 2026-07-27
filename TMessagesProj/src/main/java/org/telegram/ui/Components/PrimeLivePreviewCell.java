package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;

/**
 * PrimeGram: a real chat, rendered with the settings as they stand right now.
 *
 * <p>Wraps {@link ThemePreviewMessagesCell}, which draws genuine {@code ChatMessageCell}s over the
 * genuine wallpaper - the same code path that draws the chat itself. That matters: a hand-drawn
 * mock-up shows what somebody thought the setting does, while this shows what it does.
 *
 * <p>The design intent is one preview per section, sitting above the switches that affect it, so
 * the effects of several settings are visible together. A preview attached to a single switch can
 * only ever answer "what does this one do", and the interesting question is usually "what does all
 * of this look like".
 */
public class PrimeLivePreviewCell extends FrameLayout {

    private final Context context;
    private final INavigationLayout parentLayout;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int type;

    private ThemePreviewMessagesCell messagesCell;

    public PrimeLivePreviewCell(Context context, INavigationLayout parentLayout, int type,
                                Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.context = context;
        this.parentLayout = parentLayout;
        this.type = type;
        this.resourcesProvider = resourcesProvider;
        build();
    }

    private void build() {
        if (messagesCell != null) {
            removeView(messagesCell);
        }
        messagesCell = new ThemePreviewMessagesCell(context, parentLayout, type, 0, resourcesProvider);
        // Never focusable: this is a picture of a chat, and letting a screen reader or a keyboard
        // walk into fake messages would be worse than useless.
        messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    /**
     * Re-reads every setting the preview draws from.
     *
     * <p>Most of our tweaks are read inside {@code onDraw}, so an invalidate is enough for them.
     * The ones that change how much room a message needs - sticker size, bubble radius - are read
     * during measurement, so the layout pass has to be requested too. Doing both is cheaper than
     * working out which kind of setting just changed.
     */
    public void update() {
        if (messagesCell == null) {
            return;
        }
        invalidateTree(messagesCell);
        messagesCell.requestLayout();
        messagesCell.invalidate();
    }

    /** Rebuilds from scratch, for changes a redraw cannot express. */
    public void rebuild() {
        build();
    }

    private void invalidateTree(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateTree(group.getChildAt(i));
            }
        }
    }
}
