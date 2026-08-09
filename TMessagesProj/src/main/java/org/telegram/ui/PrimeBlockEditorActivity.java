package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.blocks.BlockNode;
import org.telegram.messenger.blocks.BlockRegistry;
import org.telegram.messenger.blocks.BlockType;
import org.telegram.messenger.blocks.ParamSpec;
import org.telegram.messenger.blocks.PrimeBlockManifest;
import org.telegram.messenger.blocks.PrimeBlockScript;
import org.telegram.messenger.blocks.PrimeBlocksController;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Cells.PrimeSliderCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PrimeBlockChipView;
import org.telegram.ui.Components.PrimeBlockPaletteSheet;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * PrimeGram Blocks: the editor, Phase C of the "PrimeGram Blocks" plan - vertical stack, one
 * trigger pinned at the top, a linear body below it, `condition.if_else` blocks holding a nested
 * indented `then`/`else` sub-stack each.
 *
 * <p>Scoped down from the original plan in one deliberate way: reordering is up/down buttons via
 * a long-press menu, not a drag gesture that can cross into/out of a branch. A branch-aware
 * {@code ItemTouchHelper} extension (see the plan §3.2) is real, fiddly touch-math work that
 * deserves its own pass once there is a working editor to test it against - this ships the
 * genuinely useful part (compose, edit, save, reopen a script) first.
 */
public class PrimeBlockEditorActivity extends UniversalFragment {

    private enum RowKind { TRIGGER, BLOCK, THEN_HEADER, ELSE_HEADER, ADD_TO_LIST }

    private static final class Row {
        RowKind kind;
        BlockNode node;
        List<BlockNode> ownerList;
        int depth;
    }

    private static final int ID_SAVE = 1;
    private static final int ID_ROW_BASE = 100;

    private final String existingScriptId;
    private BlockNode trigger;
    private final List<BlockNode> body = new ArrayList<>();

    private String manifestName = "Новый скрипт";
    private String manifestDescription = "";

    private final List<Row> flatRows = new ArrayList<>();
    private final HashMap<Integer, PrimeBlockChipView> chipCache = new HashMap<>();

    /** Opens the editor for a script already on disk. */
    public PrimeBlockEditorActivity(PrimeBlockScript script) {
        this.existingScriptId = script.id();
        this.manifestName = script.manifest.name;
        this.manifestDescription = script.manifest.description;
        this.trigger = BlockNode.fromInstance(script.manifest.trigger);
        this.body.addAll(BlockNode.fromInstanceList(script.manifest.body));
    }

    /** Starts a brand-new, blank script. */
    public PrimeBlockEditorActivity() {
        this.existingScriptId = null;
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        final ActionBarMenuItem save = actionBar.createMenu().addItem(ID_SAVE, R.drawable.ic_ab_done);
        save.setOnClickListener(v -> onSaveClicked());
        return view;
    }

    @Override
    protected CharSequence getTitle() {
        return manifestName;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        flatRows.clear();

        items.add(UItem.asHeader("Триггер"));
        final Row triggerRow = new Row();
        triggerRow.kind = RowKind.TRIGGER;
        triggerRow.node = trigger;
        flatRows.add(triggerRow);
        items.add(rowItem(flatRows.size() - 1, triggerRow));

        items.add(UItem.asHeader("Дальше"));
        flattenList(body, 0, items);
        addFlatRow(RowKind.ADD_TO_LIST, null, body, 0, items, "Добавить блок");

        items.add(UItem.asShadow("Если верно» и «Иначе» — вложенные списки блоков внутри условия «Если/иначе». Долгое нажатие на блок — переместить или удалить."));
    }

    private void flattenList(List<BlockNode> list, int depth, ArrayList<UItem> items) {
        for (BlockNode node : list) {
            final Row row = addFlatRow(RowKind.BLOCK, node, list, depth, items, null);
            if ("condition.if_else".equals(node.type)) {
                if (node.then == null) node.then = new ArrayList<>();
                if (node.otherwise == null) node.otherwise = new ArrayList<>();
                addFlatRow(RowKind.THEN_HEADER, node, node.then, depth + 1, items, "Если верно:");
                flattenList(node.then, depth + 1, items);
                addFlatRow(RowKind.ADD_TO_LIST, null, node.then, depth + 1, items, "Добавить блок");
                addFlatRow(RowKind.ELSE_HEADER, node, node.otherwise, depth + 1, items, "Иначе:");
                flattenList(node.otherwise, depth + 1, items);
                addFlatRow(RowKind.ADD_TO_LIST, null, node.otherwise, depth + 1, items, "Добавить блок");
            }
        }
    }

    private Row addFlatRow(RowKind kind, BlockNode node, List<BlockNode> ownerList, int depth,
                            ArrayList<UItem> items, String forcedLabel) {
        final Row row = new Row();
        row.kind = kind;
        row.node = node;
        row.ownerList = ownerList;
        row.depth = depth;
        flatRows.add(row);
        items.add(rowItem(flatRows.size() - 1, row, forcedLabel));
        return row;
    }

    private UItem rowItem(int rowIndex, Row row) {
        return rowItem(rowIndex, row, null);
    }

    private UItem rowItem(int rowIndex, Row row, String forcedLabel) {
        final String indent = repeat("    ", row.depth);
        switch (row.kind) {
            case TRIGGER: {
                final int id = ID_ROW_BASE + rowIndex;
                final PrimeBlockChipView chip = chipView(id);
                final BlockType type = row.node != null ? BlockRegistry.get(row.node.type) : null;
                final String label = row.node != null ? BlockRegistry.labelFor(row.node.type) : "Выбрать триггер";
                chip.bind(label, BlockType.Category.TRIGGER, 0,
                        type != null ? type.params : java.util.Collections.emptyList(),
                        row.node != null ? row.node.params : null, false);
                return UItem.asCustom(id, chip);
            }
            case BLOCK: {
                final int id = ID_ROW_BASE + rowIndex;
                final PrimeBlockChipView chip = chipView(id);
                final BlockType type = BlockRegistry.get(row.node.type);
                chip.bind(BlockRegistry.labelFor(row.node.type),
                        type != null ? type.category : BlockType.Category.ACTION, row.depth,
                        type != null ? type.params : java.util.Collections.emptyList(),
                        row.node.params, type == null);
                return UItem.asCustom(id, chip);
            }
            case THEN_HEADER:
            case ELSE_HEADER:
                return UItem.asHeader(indent + forcedLabel);
            case ADD_TO_LIST:
            default:
                return UItem.asButton(ID_ROW_BASE + rowIndex, indent + "+ " + forcedLabel, null);
        }
    }

    private PrimeBlockChipView chipView(int id) {
        PrimeBlockChipView chip = chipCache.get(id);
        if (chip == null) {
            chip = new PrimeBlockChipView(getContext(), getResourceProvider());
            chipCache.put(id, chip);
        }
        return chip;
    }

    private static String repeat(String s, int times) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(s);
        }
        return builder.toString();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        final int index = item.id - ID_ROW_BASE;
        if (index < 0 || index >= flatRows.size()) {
            return false;
        }
        final Row row = flatRows.get(index);
        if (row.kind == RowKind.BLOCK) {
            showBlockMenu(row, view);
            return true;
        }
        return false;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int index = item.id - ID_ROW_BASE;
        if (index < 0 || index >= flatRows.size()) {
            return;
        }
        final Row row = flatRows.get(index);
        switch (row.kind) {
            case TRIGGER:
                if (row.node != null && tryOpenParamEditor(row.node, item.id, x, y)) {
                    return;
                }
                PrimeBlockPaletteSheet.show(this, BlockType.Category.TRIGGER, chosen -> {
                    trigger = BlockNode.newInstance(chosen.id);
                    refresh();
                });
                break;
            case BLOCK:
                tryOpenParamEditor(row.node, item.id, x, y);
                break;
            case ADD_TO_LIST:
                PrimeBlockPaletteSheet.show(this, null, chosen -> {
                    row.ownerList.add(BlockNode.newInstance(chosen.id));
                    refresh();
                });
                break;
            default:
                break;
        }
    }

    /** Returns true if (x, y) landed on a parameter pill and an editor was opened for it. */
    private boolean tryOpenParamEditor(BlockNode node, int rowId, float x, float y) {
        final PrimeBlockChipView chip = chipCache.get(rowId);
        if (chip == null) {
            return false;
        }
        final ParamSpec spec = chip.hitTestParam(x, y);
        if (spec == null) {
            return false;
        }
        final BlockType type = BlockRegistry.get(node.type);
        if (type == null) {
            return false;
        }
        editParam(node, type, spec);
        return true;
    }

    private void showBlockMenu(Row row, View anchor) {
        final ItemOptions io = ItemOptions.makeOptions(this, anchor);
        final int idx = row.ownerList.indexOf(row.node);
        if (idx > 0) {
            io.add(R.drawable.msg_go_up, "Переместить вверх", () -> {
                java.util.Collections.swap(row.ownerList, idx, idx - 1);
                refresh();
            });
        }
        if (idx >= 0 && idx < row.ownerList.size() - 1) {
            io.add(R.drawable.msg_go_down, "Переместить вниз", () -> {
                java.util.Collections.swap(row.ownerList, idx, idx + 1);
                refresh();
            });
        }
        io.addGap();
        io.add(R.drawable.msg_delete, "Удалить", true, () -> {
            row.ownerList.remove(row.node);
            refresh();
        });
        io.show();
    }

    /**
     * Opens the small, focused editor for one pill, matching the "Editor Redesign v2" plan §2:
     * NUMBER gets a {@link PrimeSliderCell} sheet, ENUM a plain choice list (the "small" half of
     * "searchable-if-large / plain-list-if-small" - every {@code ParamSpec.enumValues} in this
     * registry is under ten entries), TEXT a one-field dialog, BOOLEAN toggles with no dialog.
     */
    private void editParam(BlockNode node, BlockType type, ParamSpec spec) {
        switch (spec.kind) {
            case BOOLEAN: {
                final boolean current = node.params.optBoolean(spec.key, spec.defaultValue == Boolean.TRUE);
                try {
                    node.params.put(spec.key, !current);
                } catch (JSONException ignored) {
                }
                refresh();
                break;
            }
            case NUMBER:
                editNumberParam(node, spec);
                break;
            case ENUM:
                editEnumParam(node, spec);
                break;
            case TEXT:
            case CHAT_REFERENCE:
            default:
                editTextParam(node, type, spec);
                break;
        }
    }

    private void editNumberParam(BlockNode node, ParamSpec spec) {
        final int min = (int) spec.min;
        final int max = (int) spec.max;
        final int current = node.params.optInt(spec.key, spec.defaultValue instanceof Number ? ((Number) spec.defaultValue).intValue() : min);
        final BottomSheet sheet = new BottomSheet(getContext(), false, getResourceProvider());
        final PrimeSliderCell slider = new PrimeSliderCell(getContext(), spec.label, min, max, current, getResourceProvider());
        slider.setListener((value, stop) -> {
            try {
                node.params.put(spec.key, value);
            } catch (JSONException ignored) {
            }
            if (stop) {
                refresh();
            } else {
                rebindChip(node);
            }
        });
        sheet.setCustomView(slider);
        sheet.show();
    }

    private void editEnumParam(BlockNode node, ParamSpec spec) {
        if (spec.enumValues == null || spec.enumValues.length == 0) {
            return;
        }
        final ItemOptions io = ItemOptions.makeOptions(this, listView);
        for (String value : spec.enumValues) {
            io.add(0, value, () -> {
                try {
                    node.params.put(spec.key, value);
                } catch (JSONException ignored) {
                }
                refresh();
            });
        }
        io.show();
    }

    private void editTextParam(BlockNode node, BlockType type, ParamSpec spec) {
        final EditTextBoldCursor field = new EditTextBoldCursor(getContext());
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setTextColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText));
        if (spec.kind == ParamSpec.Kind.NUMBER) {
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        final Object current = node.params.opt(spec.key);
        field.setText(current != null ? String.valueOf(current) : String.valueOf(spec.defaultValue));
        field.setSelection(field.getText().length());
        final int pad = AndroidUtilities.dp(21);
        field.setPadding(pad, AndroidUtilities.dp(6), pad, 0);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle(spec.label);
        builder.setView(field);
        builder.setPositiveButton("Готово", (dialog, which) -> {
            try {
                node.params.put(spec.key, field.getText().toString());
            } catch (JSONException ignored) {
            }
            refresh();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    /** Repaints one chip without a full adapter rebuild, for the slider's live-drag callback. */
    private void rebindChip(BlockNode node) {
        for (int i = 0; i < flatRows.size(); i++) {
            final Row row = flatRows.get(i);
            if (row.node == node) {
                final PrimeBlockChipView chip = chipCache.get(ID_ROW_BASE + i);
                if (chip != null) {
                    chip.invalidate();
                }
                return;
            }
        }
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void onSaveClicked() {
        if (trigger == null) {
            BulletinFactory.of(this).createErrorBulletin("Сначала выберите триггер").show();
            return;
        }
        try {
            final JSONObject root = new JSONObject();
            root.put("format_version", PrimeBlockManifest.CURRENT_FORMAT_VERSION);
            final JSONObject manifest = new JSONObject();
            final String id = existingScriptId != null ? existingScriptId : "script-" + UUID.randomUUID().toString().substring(0, 8);
            manifest.put("id", id);
            manifest.put("name", manifestName);
            manifest.put("description", manifestDescription);
            manifest.put("author", "");
            manifest.put("version", "1.0");
            manifest.put("min_app_version", "");
            root.put("manifest", manifest);
            final JSONObject program = new JSONObject();
            program.put("trigger", trigger.toJson());
            program.put("body", BlockNode.toJsonArray(body));
            root.put("program", program);

            final File tmp = File.createTempFile("draft", ".pr", ApplicationLoader.applicationContext.getCacheDir());
            try (FileWriter writer = new FileWriter(tmp)) {
                writer.write(root.toString(2));
            }

            PrimeBlocksController.getInstance().install(getParentActivity(), tmp, new PrimeBlocksController.InstallCallback() {
                @Override
                public void onInstalled(PrimeBlockScript script, boolean replacedExisting) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    BulletinFactory.of(PrimeBlockEditorActivity.this).createSuccessBulletin(
                            replacedExisting ? "Скрипт обновлён" : "Скрипт сохранён").show();
                    finishFragment();
                }

                @Override
                public void onFailed(String reason) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    BulletinFactory.of(PrimeBlockEditorActivity.this).createErrorBulletin("Не удалось сохранить: " + reason).show();
                }
            });
        } catch (JSONException e) {
            FileLog.e(e);
            BulletinFactory.of(this).createErrorBulletin("Не удалось собрать файл скрипта").show();
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createErrorBulletin("Не удалось записать файл").show();
        }
    }
}
