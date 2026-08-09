package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.blocks.BlockType;
import org.telegram.messenger.blocks.BlockRegistry;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * PrimeGram Blocks: the searchable, categorized add-block picker - replaces the flat
 * {@code ItemOptions} popup the v1 editor used, which stopped being usable well before the target
 * "hundreds of blocks" scale (see the "Editor Redesign v2" plan §1).
 *
 * <p>Categories are collapsed to a handful of rows by default and expand on tap; typing in the
 * search field flattens everything into one filtered list. Both are the same "closed categorized
 * list, search once it's large" rule already applied to {@code PrimeGramSettingsActivity}'s own
 * sections this session, reused here rather than reinvented.
 *
 * <p>Built as a plain rebuilt {@code LinearLayout} inside a {@code ScrollView}, not a
 * {@code RecyclerView} - the realistic block count (dozens to a few hundred) rebuilds cheaply on
 * every keystroke, and this avoids a whole adapter/view-holder layer for a picker that is only
 * ever open briefly.
 */
public final class PrimeBlockPaletteSheet extends BottomSheet {

    private static final int COLLAPSED_COUNT = 4;

    private final BlockType.Category onlyCategory;
    private final Consumer<BlockType> callback;

    private final LinearLayout content;
    private final EditText search;
    private final java.util.Set<BlockType.Category> expanded = new java.util.HashSet<>();

    public static void show(BaseFragment fragment, BlockType.Category onlyCategory, Consumer<BlockType> callback) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        new PrimeBlockPaletteSheet(fragment.getParentActivity(), fragment.getResourceProvider(), onlyCategory, callback).show();
    }

    private PrimeBlockPaletteSheet(android.app.Activity activity, Theme.ResourcesProvider resourcesProvider,
                                    BlockType.Category onlyCategory, Consumer<BlockType> callback) {
        super(activity, false, resourcesProvider);
        this.onlyCategory = onlyCategory;
        this.callback = callback;
        setApplyTopPadding(false);

        final Context context = getContext();
        final FrameLayout frame = new FrameLayout(context);
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView title = new TextView(context);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(onlyCategory == BlockType.Category.TRIGGER ? "Выберите триггер" : "Добавить блок");
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 21, 16, 21, 8));

        search = new EditTextBoldCursor(context);
        search.setHint("Поиск блоков");
        search.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        search.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setSingleLine(true);
        search.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), PrimeSettingsUi.surfaceColor()));
        search.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        root.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 21, 4, 21, 12));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                rebuild();
            }
        });

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final ScrollView scrollView = new ScrollView(context);
        scrollView.addView(frame);
        setCustomView(scrollView);

        rebuild();
    }

    private void rebuild() {
        content.removeAllViews();
        final String query = search.getText() != null ? search.getText().toString().trim().toLowerCase(Locale.getDefault()) : "";

        final Map<BlockType.Category, List<BlockType>> byCategory = new LinkedHashMap<>();
        for (BlockType type : BlockRegistry.all()) {
            if (onlyCategory != null) {
                if (type.category != onlyCategory) {
                    continue;
                }
            } else if (type.category == BlockType.Category.TRIGGER) {
                continue;
            }
            if (!query.isEmpty() && !matches(type, query)) {
                continue;
            }
            byCategory.computeIfAbsent(type.category, c -> new ArrayList<>()).add(type);
        }

        if (byCategory.isEmpty()) {
            final TextView empty = new TextView(getContext());
            empty.setText("Ничего не найдено");
            empty.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 40, 0, 40));
            return;
        }

        final boolean forceExpanded = !query.isEmpty() || onlyCategory != null;
        for (Map.Entry<BlockType.Category, List<BlockType>> entry : byCategory.entrySet()) {
            final BlockType.Category category = entry.getKey();
            final List<BlockType> types = entry.getValue();
            if (byCategory.size() > 1) {
                content.addView(categoryHeader(category), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 12, 21, 4));
            }
            final boolean showAll = forceExpanded || expanded.contains(category) || types.size() <= COLLAPSED_COUNT;
            final int shown = showAll ? types.size() : COLLAPSED_COUNT;
            for (int i = 0; i < shown; i++) {
                content.addView(blockRow(types.get(i)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            if (!showAll) {
                final TextView more = new TextView(getContext());
                more.setText("Показать все (" + types.size() + ")");
                more.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
                more.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                more.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
                more.setOnClickListener(v -> {
                    expanded.add(category);
                    rebuild();
                });
                content.addView(more, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }
    }

    private static boolean matches(BlockType type, String query) {
        return (type.label != null && type.label.toLowerCase(Locale.getDefault()).contains(query))
                || (type.description != null && type.description.toLowerCase(Locale.getDefault()).contains(query));
    }

    private View categoryHeader(BlockType.Category category) {
        final TextView header = new TextView(getContext());
        header.setText(categoryLabel(category));
        header.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        header.setTypeface(AndroidUtilities.bold());
        return header;
    }

    private View blockRow(BlockType type) {
        final LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(9), AndroidUtilities.dp(21), AndroidUtilities.dp(9));
        row.setBackground(Theme.getSelectorDrawable(false));

        final View swatch = new View(getContext());
        final GradientDrawable swatchBg = new GradientDrawable();
        swatchBg.setShape(GradientDrawable.OVAL);
        swatchBg.setColor(categoryColor(type.category));
        swatch.setBackground(swatchBg);
        row.addView(swatch, LayoutHelper.createLinear(10, 10, 0, 0, 0, 14, 0));

        final LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView label = new TextView(getContext());
        label.setText(type.label);
        label.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        texts.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (type.description != null && !type.description.isEmpty()) {
            final TextView desc = new TextView(getContext());
            desc.setText(type.description);
            desc.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            texts.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> {
            dismiss();
            callback.accept(type);
        });
        return row;
    }

    private static String categoryLabel(BlockType.Category category) {
        switch (category) {
            case TRIGGER:
                return "ТРИГГЕРЫ";
            case CONDITION:
                return "УСЛОВИЯ";
            case WAIT:
                return "ОЖИДАНИЕ";
            case ACTION:
            default:
                return "ДЕЙСТВИЯ";
        }
    }

    /** Reuses this session's existing {@link IconBackgroundColors} palette, no new hex values. */
    public static int categoryColor(BlockType.Category category) {
        switch (category) {
            case TRIGGER:
                return IconBackgroundColors.ORANGE_DEEP.top;
            case CONDITION:
                return IconBackgroundColors.PURPLE.top;
            case WAIT:
                return IconBackgroundColors.CYAN.top;
            case ACTION:
            default:
                return IconBackgroundColors.BLUE_DEEP.top;
        }
    }
}
