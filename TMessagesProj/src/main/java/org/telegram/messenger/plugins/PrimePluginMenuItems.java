package org.telegram.messenger.plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mvel2.MVEL;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.ItemOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PrimeGram: the menu items plugins have asked to appear in the app's own menus.
 *
 * <p>A plugin adds one of these by calling {@code self.add_menu_item(...)} in Python, which is a
 * plain Python-side list until something publishes it here - {@code setItems} is that publish,
 * called from {@code base_plugin.py} every time the set changes. Whichever screen is about to show
 * a menu asks {@link #forType} for its slice of it, filtered to the items whose {@code condition}
 * (if any) evaluates true right now, in priority order - the same MVEL engine {@link
 * PrimePluginXposed#evalCondition} uses for hook filters, because this is the same idea: an
 * expression exteraGram plugins already ship, meaning the same thing here.
 *
 * <p>Every screen that shows one of these menus builds it fresh at open time - long-press, opening
 * a profile, tapping an overflow button - so there is no cache to invalidate here beyond the plain
 * list itself; the condition is what makes the same list look different from screen to screen.
 */
public final class PrimePluginMenuItems {

    public static final class Item {
        public final String id;
        public final String pluginId;
        public final String menuType;
        public final String text;
        public final String subtext;
        public final int iconResId;
        public final String condition;
        public final int priority;

        Item(String id, String pluginId, String menuType, String text, String subtext,
             int iconResId, String condition, int priority) {
            this.id = id;
            this.pluginId = pluginId;
            this.menuType = menuType;
            this.text = text;
            this.subtext = subtext;
            this.iconResId = iconResId;
            this.condition = condition;
            this.priority = priority;
        }
    }

    private static volatile List<Item> items = Collections.emptyList();
    private static final ConcurrentHashMap<String, Serializable> conditionCache = new ConcurrentHashMap<>();

    private PrimePluginMenuItems() {
    }

    /**
     * Replaces the whole set. {@code json} is an array of {@code {id, plugin_id, menu_type, text,
     * subtext, icon, condition, priority}} objects - built in {@code base_plugin.py}'s registry,
     * one call per change rather than a diff, because the whole list is small and a diff would be
     * more code for no real saving.
     */
    public static void setItems(String json) {
        final List<Item> parsed = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject row = array.optJSONObject(i);
                if (row == null) {
                    continue;
                }
                final String id = row.optString("id");
                final String menuType = row.optString("menu_type");
                final String text = row.optString("text");
                if (id.isEmpty() || menuType.isEmpty() || text.isEmpty()) {
                    continue;
                }
                parsed.add(new Item(id, row.optString("plugin_id"), menuType, text,
                        row.optString("subtext", null), resolveIcon(row.optString("icon", null)),
                        row.optString("condition", null), row.optInt("priority", 0)));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        items = parsed;
    }

    private static int resolveIcon(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        try {
            return ApplicationLoader.applicationContext.getResources()
                    .getIdentifier(name, "drawable", ApplicationLoader.applicationContext.getPackageName());
        } catch (Throwable e) {
            return 0;
        }
    }

    /** This type's items, highest priority first, with every conditioned one evaluated against
     *  {@code context} right now - nothing here is cached across calls with different context. */
    public static List<Item> forType(String menuType, Map<String, Object> context) {
        final List<Item> result = new ArrayList<>();
        for (Item item : items) {
            if (!item.menuType.equals(menuType)) {
                continue;
            }
            if (item.condition != null && !matches(item.condition, context)) {
                continue;
            }
            result.add(item);
        }
        result.sort(Comparator.comparingInt((Item i) -> i.priority).reversed());
        return result;
    }

    private static boolean matches(String expression, Map<String, Object> context) {
        try {
            final Serializable compiled = conditionCache.computeIfAbsent(expression, MVEL::compileExpression);
            final Boolean result = (Boolean) MVEL.executeExpression(compiled, context == null ? Collections.emptyMap() : context);
            return result != null && result;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Tells the owning plugin's {@code on_click} its item was picked, with the same context that
     *  decided whether it was shown. */
    public static void click(Item item, Map<String, Object> context) {
        PrimePluginHooks.onMenuItemClick(item.id, context);
    }

    /** The common case: append every matching item of {@code menuType} to an {@link ItemOptions}
     *  already being built, wiring the click straight through. Covers every menu here except the
     *  message context menu, which is not an {@code ItemOptions} - it predates that API. */
    public static void appendTo(ItemOptions itemOptions, String menuType, Map<String, Object> context) {
        for (Item item : forType(menuType, context)) {
            if (item.subtext != null && !item.subtext.isEmpty()) {
                itemOptions.add(item.text, item.subtext, () -> click(item, context));
            } else if (item.iconResId != 0) {
                itemOptions.add(item.iconResId, item.text, () -> click(item, context));
            } else {
                itemOptions.add(item.text, () -> click(item, context));
            }
        }
    }

    /**
     * The other case: a menu built out of {@code (id, icon, text)} triples handed to some older
     * API - {@code ActionBarMenuItem.addSubItem}, or the three parallel lists {@code
     * ChatActivity.fillMessageMenu} still uses - that has no room for a {@link Runnable} per item,
     * only an {@code int} it hands back on click. One of these, kept as a field on whichever screen
     * owns the menu, replaces the id-range-plus-index-lookup bookkeeping that used to be written out
     * by hand at every such call site (there were three, all doing the same thing slightly
     * differently). {@code load} at menu-build time, {@link #idFor} for each row added, {@link
     * #owns}/{@link #handle} in the click callback.
     */
    public static final class ClickRouter {
        private static final int BASE = 1_000_000;

        private List<Item> items = Collections.emptyList();
        private Map<String, Object> context = Collections.emptyMap();

        /** Call once per menu build, before adding any row. Returns the items to add, in order. */
        public List<Item> load(String menuType, Map<String, Object> context) {
            this.items = forType(menuType, context);
            this.context = context;
            return items;
        }

        /** The id to give row {@code index} of whatever {@link #load} just returned. */
        public int idFor(int index) {
            return BASE + index;
        }

        /** Whether {@code id} is one this router handed out - check before falling through to a
         *  screen's own {@code switch} on native ids, which share no range with this one. */
        public boolean owns(int id) {
            return id >= BASE;
        }

        /** Routes a click by the id {@link #idFor} produced. Returns false for an id from a stale
         *  build (the menu was rebuilt since), which a caller can treat as a no-op. */
        public boolean handle(int id) {
            final int index = id - BASE;
            if (index < 0 || index >= items.size()) {
                return false;
            }
            click(items.get(index), context);
            return true;
        }
    }
}
