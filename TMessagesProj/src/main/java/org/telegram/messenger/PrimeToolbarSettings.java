package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * PrimeGram: the formatting toolbar - whether it is shown, and what is on it.
 *
 * <p>Opt-in on purpose. The toolbar adds a row to {@code ChatActivityEnterView}, a view whose
 * height feeds a lot of surrounding layout; leaving it off by default means a user who never asks
 * for it runs exactly the layout upstream ships.
 *
 * <p>The contents are a list of ids in the order they appear. Ten buttons is more than fits on a
 * narrow phone without scrolling, and which of them matter is a matter of what somebody writes:
 * a person who sends code wants monospace first and may never use spoiler at all. Storing the order
 * rather than a set of switches is what lets that be expressed in one gesture.
 */
public class PrimeToolbarSettings {

    private static final String KEY = "primegram_text_toolbar";
    private static final String KEY_ITEMS = "primegram_text_toolbar_items";

    public static final String BOLD = "bold";
    public static final String ITALIC = "italic";
    public static final String MONO = "mono";
    public static final String STRIKE = "strike";
    public static final String UNDERLINE = "underline";
    public static final String SPOILER = "spoiler";
    public static final String LINK = "link";
    public static final String QUOTE = "quote";
    public static final String CLEAR = "clear";
    public static final String COPY = "copy";

    /** Every button that exists, in the order the toolbar shipped with. */
    public static final String[] ALL = {
            BOLD, ITALIC, MONO, STRIKE, UNDERLINE, SPOILER, LINK, QUOTE, CLEAR, COPY
    };

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

    /**
     * The buttons to draw, in order.
     *
     * <p>Unknown ids are dropped and duplicates collapsed, so a settings file written by an older
     * or newer build cannot produce a toolbar with a button twice on it or a gap where one used to
     * be. An empty result falls back to the full set: a toolbar with nothing on it is a bug from
     * the user's side of the screen, whatever the stored value says.
     */
    public static List<String> items() {
        final String stored;
        try {
            stored = MessagesController.getGlobalMainSettings().getString(KEY_ITEMS, null);
        } catch (Throwable t) {
            return new ArrayList<>(Arrays.asList(ALL));
        }
        if (stored == null || stored.isEmpty()) {
            return new ArrayList<>(Arrays.asList(ALL));
        }
        final LinkedHashSet<String> known = new LinkedHashSet<>();
        for (String id : stored.split(",")) {
            final String trimmed = id.trim();
            for (String candidate : ALL) {
                if (candidate.equals(trimmed)) {
                    known.add(candidate);
                    break;
                }
            }
        }
        return known.isEmpty() ? new ArrayList<>(Arrays.asList(ALL)) : new ArrayList<>(known);
    }

    public static void setItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            reset();
            return;
        }
        MessagesController.getGlobalMainSettings().edit()
                .putString(KEY_ITEMS, android.text.TextUtils.join(",", items)).apply();
    }

    public static void reset() {
        MessagesController.getGlobalMainSettings().edit().remove(KEY_ITEMS).apply();
    }

    public static boolean isDefaultOrder() {
        return items().equals(Arrays.asList(ALL));
    }

    /** The buttons the user took off the bar, in their original order. */
    public static List<String> hiddenItems() {
        final List<String> shown = items();
        final List<String> hidden = new ArrayList<>();
        for (String id : ALL) {
            if (!shown.contains(id)) {
                hidden.add(id);
            }
        }
        return hidden;
    }

    /** The one-word name of a button, for the editor and for accessibility. */
    public static String title(String id) {
        switch (id) {
            case BOLD: return "Жирный";
            case ITALIC: return "Курсив";
            case MONO: return "Моноширинный";
            case STRIKE: return "Зачёркнутый";
            case UNDERLINE: return "Подчёркнутый";
            case SPOILER: return "Спойлер";
            case LINK: return "Ссылка";
            case QUOTE: return "Цитата";
            case CLEAR: return "Убрать формат";
            case COPY: return "Копировать";
            default: return id;
        }
    }

    /** The letter a button draws, or null when it draws an icon instead. */
    public static String letter(String id) {
        switch (id) {
            case BOLD: return "B";
            case ITALIC: return "I";
            case MONO: return "M";
            case STRIKE: return "S";
            case UNDERLINE: return "U";
            default: return null;
        }
    }

    /** The drawable a button uses, or 0 when it draws a letter instead. */
    public static int icon(String id) {
        switch (id) {
            case SPOILER: return R.drawable.msg_spoiler;
            case LINK: return R.drawable.menu_link_create;
            case QUOTE: return R.drawable.menu_select_quote;
            case CLEAR: return R.drawable.msg_clear;
            case COPY: return R.drawable.msg_copy;
            default: return 0;
        }
    }
}
