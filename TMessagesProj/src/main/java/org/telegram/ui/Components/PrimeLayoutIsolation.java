package org.telegram.ui.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.PrimePerfMonitor;

/**
 * PrimeGram: lets a child whose size never depends on its content (a chat or dialogs list,
 * MATCH_PARENT in a parent that sizes it) re-lay out by itself instead of the whole window.
 *
 * <p>{@link View#requestLayout()} climbs to the window root unconditionally, so every reason a list
 * has to re-lay out - its own padding moving with the input island, {@code notifyItemChanged} for
 * every reaction in a busy channel, a cell changing height - turned into a measure and layout pass
 * over every view in the window: drawer, action bar layout, every fragment container. The monitor
 * showed that 5-14 times a second in chats, each one a full traversal.
 *
 * <p>None of those can change the list's own bounds - its parent decides them - so the parent does
 * not need to hear about it. The list marks its request ({@link #request}); its parent, if it
 * cooperates ({@link #absorb}), swallows that one request and instead measures and lays out just
 * that child at its current bounds right before the next draw. A request the parent does not
 * absorb (not attached yet, not laid out yet, the window already mid-layout, the switch off) goes
 * up the normal way, so the worst case is exactly the old behaviour.
 *
 * <p>Main thread only, like {@code requestLayout} itself.
 */
public final class PrimeLayoutIsolation {

    private static final String PREFS = "primegram_layout_isolation";
    private static Boolean enabledCache;

    /** The child whose {@code requestLayout} is on the stack right now, if it opted in. */
    private static View requestingChild;

    private PrimeLayoutIsolation() {
    }

    public static boolean isEnabled() {
        if (enabledCache == null) {
            enabledCache = prefs().getBoolean("enabled", true);
        }
        return enabledCache;
    }

    public static void setEnabled(boolean enabled) {
        enabledCache = enabled;
        prefs().edit().putBoolean("enabled", enabled).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Child side: call from an overridden {@code requestLayout()} in place of {@code
     * super.requestLayout()}. The child still runs its own request logic (RecyclerView decides
     * whether to defer, View sets its own force-layout flag); only the parent learns where it came
     * from.
     */
    public static void request(View child, Runnable superRequestLayout) {
        if (!isEnabled()) {
            superRequestLayout.run();
            return;
        }
        final View previous = requestingChild;
        requestingChild = child;
        try {
            superRequestLayout.run();
        } finally {
            requestingChild = previous;
        }
    }

    /**
     * Parent side: call first thing in an overridden {@code requestLayout()}; return right away when
     * it answers true.
     */
    public static boolean absorb(ViewGroup parent) {
        final View child = requestingChild;
        if (child == null || child.getParent() != parent) {
            return false;
        }
        if (!child.isAttachedToWindow() || child.getWidth() <= 0 || child.getHeight() <= 0) {
            return false;
        }
        if (parent.isInLayout() || parent.isLayoutRequested()) {
            // The window is laying out (the request is being handled in this very pass), or the
            // parent will be re-laid out anyway - nothing to save.
            return false;
        }
        Pending pending = PENDING.get(parent);
        if (pending == null) {
            pending = new Pending(parent);
            PENDING.put(parent, pending);
        }
        pending.add(child);
        return true;
    }

    private static final java.util.WeakHashMap<ViewGroup, Pending> PENDING = new java.util.WeakHashMap<>();

    private static final class Pending implements ViewTreeObserver.OnPreDrawListener {
        private final ViewGroup parent;
        private final java.util.ArrayList<View> children = new java.util.ArrayList<>(2);
        private ViewTreeObserver observer;

        Pending(ViewGroup parent) {
            this.parent = parent;
        }

        void add(View child) {
            if (!children.contains(child)) {
                children.add(child);
            }
            if (observer == null) {
                observer = parent.getViewTreeObserver();
                observer.addOnPreDrawListener(this);
            }
            // The swallowed request scheduled no traversal; an invalidate does, and the pre-draw
            // pass of that traversal is where the child gets laid out.
            child.invalidate();
        }

        @Override
        public boolean onPreDraw() {
            if (observer != null) {
                // The observer captured at add() dies if the parent was re-attached in between;
                // the listener then lives on the parent's current one.
                (observer.isAlive() ? observer : parent.getViewTreeObserver()).removeOnPreDrawListener(this);
                observer = null;
            }
            final boolean timed = PrimePerfMonitor.trackingInvalidates;
            final long start = timed ? System.nanoTime() : 0;
            for (int i = 0; i < children.size(); i++) {
                final View child = children.get(i);
                // A full traversal in between may already have laid it out.
                if (child.getParent() != parent || !child.isLayoutRequested() || !child.isAttachedToWindow()) {
                    continue;
                }
                final int width = child.getWidth();
                final int height = child.getHeight();
                child.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                child.layout(child.getLeft(), child.getTop(), child.getLeft() + width, child.getTop() + height);
            }
            children.clear();
            if (timed) {
                PrimePerfMonitor.noteUiWork("список переложен отдельно", System.nanoTime() - start);
            }
            return true;
        }
    }
}
