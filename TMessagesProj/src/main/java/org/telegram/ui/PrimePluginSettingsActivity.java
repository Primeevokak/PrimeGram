package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.PrimePlugin;
import org.telegram.messenger.plugins.PrimePluginStore;
import org.telegram.messenger.plugins.PrimePluginsController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * PrimeGram: one plugin's settings, drawn by the app from a description the plugin supplied.
 *
 * <p>The plugin never touches a View. It returns a list of rows - a switch here, a choice there -
 * and this screen renders them with the same cells the rest of the app uses. That is what keeps a
 * plugin's settings from looking like a plugin's settings, and it is also why the values live in
 * Java: the screen has to work for a plugin that is currently switched off, when there is no Python
 * side left to ask.
 */
public class PrimePluginSettingsActivity extends UniversalFragment {

    private static final int ID_ENABLED = 1;
    private static final int ID_DELETE = 2;
    private static final int ID_ROW_BASE = 2000;

    private final PrimePlugin plugin;
    private JSONArray rows = new JSONArray();

    public PrimePluginSettingsActivity(PrimePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onFragmentCreate() {
        PrimePluginsController.getInstance().requestSettings(plugin.id(), json -> {
            try {
                rows = new JSONArray(json);
            } catch (Throwable e) {
                rows = new JSONArray();
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return plugin.name();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButtonCheck(ID_ENABLED, "Включён", enabledSubtitle())
                .setChecked(plugin.isEnabled()));
        items.add(UItem.asShadow(plugin.manifest.description != null && !plugin.manifest.description.isEmpty()
                ? plugin.manifest.description : null));

        if (rows.length() == 0) {
            items.add(UItem.asCenterShadow(plugin.isEnabled()
                    ? "У этого плагина нет настроек."
                    : "Плагин выключен. Включите его, чтобы увидеть настройки."));
            addDeleteRow(items);
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                continue;
            }
            final int id = ID_ROW_BASE + i;
            final String type = row.optString("type");
            final String text = row.optString("text");
            switch (type) {
                case "header":
                    items.add(UItem.asHeader(text));
                    break;
                case "divider":
                    items.add(UItem.asShadow(row.optString("text", null)));
                    break;
                case "switch":
                    items.add(UItem.asButtonCheck(id, text, row.optString("subtext"))
                            .setChecked(PrimePluginStore.getBoolean(
                                    plugin.id(), row.optString("key"), row.optBoolean("default"))));
                    break;
                case "selector":
                    items.add(UItem.asButton(id, text, selectorValue(row)));
                    break;
                case "input":
                case "edit_text":
                    items.add(UItem.asButton(id, text.isEmpty() ? row.optString("hint") : text,
                            PrimePluginStore.getString(plugin.id(), row.optString("key"),
                                    row.optString("default"))));
                    break;
                case "text":
                    items.add(UItem.asButton(id, text, row.optString("subtext")));
                    break;
                default:
                    // Custom rows need a View from the plugin, which needs the interpreter to build
                    // Android objects. Skipped rather than drawn empty, so the screen stays honest.
                    break;
            }
        }
        addDeleteRow(items);
    }

    /** Author and version, or nothing worth a second line. */
    private CharSequence enabledSubtitle() {
        final StringBuilder builder = new StringBuilder();
        if (plugin.manifest.author != null && !plugin.manifest.author.isEmpty()) {
            builder.append(plugin.manifest.author);
        }
        if (plugin.manifest.version != null && !plugin.manifest.version.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append("v").append(plugin.manifest.version);
        }
        return builder;
    }

    private void addDeleteRow(ArrayList<UItem> items) {
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_DELETE, "Удалить плагин").red());
        items.add(UItem.asShadow(null));
    }

    private CharSequence selectorValue(JSONObject row) {
        final JSONArray items = row.optJSONArray("items");
        if (items == null) {
            return "";
        }
        final int index = PrimePluginStore.getInt(plugin.id(), row.optString("key"), row.optInt("default"));
        return index >= 0 && index < items.length() ? items.optString(index) : "";
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ENABLED) {
            final boolean enable = !plugin.isEnabled();
            PrimePluginsController.getInstance().setEnabled(getContext(), plugin, enable);
            if (enable) {
                // The rows come from the plugin, so they only exist once it is running again.
                PrimePluginsController.getInstance().requestSettings(plugin.id(), json -> {
                    try {
                        rows = new JSONArray(json);
                    } catch (Throwable e) {
                        rows = new JSONArray();
                    }
                    listView.adapter.update(true);
                });
            }
            listView.adapter.update(true);
            return;
        }
        if (item.id == ID_DELETE) {
            confirmDelete();
            return;
        }
        final int index = item.id - ID_ROW_BASE;
        final JSONObject row = rows.optJSONObject(index);
        if (row == null) {
            return;
        }
        final String key = row.optString("key");
        switch (row.optString("type")) {
            case "switch": {
                final boolean value = !PrimePluginStore.getBoolean(plugin.id(), key, row.optBoolean("default"));
                PrimePluginStore.put(plugin.id(), key, value);
                PrimePluginsController.getInstance().notifySettingChanged(
                        plugin.id(), index, value ? "true" : "false");
                listView.adapter.update(true);
                break;
            }
            case "selector":
                showSelector(index, row, key);
                break;
            case "input":
            case "edit_text":
                showInput(index, row, key);
                break;
            default:
                PrimePluginsController.getInstance().notifySettingClicked(plugin.id(), index);
                break;
        }
    }

    private void confirmDelete() {
        final AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle("Удалить «" + plugin.name() + "»?")
                .setMessage("Файл плагина и все его настройки будут удалены.")
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, which) -> {
                    PrimePluginsController.getInstance().delete(plugin);
                    finishFragment();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        dialog.show();
        dialog.redPositive();
    }

    private void showSelector(int index, JSONObject row, String key) {
        final JSONArray options = row.optJSONArray("items");
        if (options == null || options.length() == 0) {
            return;
        }
        final CharSequence[] labels = new CharSequence[options.length()];
        for (int i = 0; i < options.length(); i++) {
            labels[i] = options.optString(i);
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(row.optString("text"))
                .setItems(labels, (dialog, which) -> {
                    PrimePluginStore.put(plugin.id(), key, which);
                    PrimePluginsController.getInstance().notifySettingChanged(
                            plugin.id(), index, String.valueOf(which));
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void showInput(int index, JSONObject row, String key) {
        final Context context = getContext();
        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, getResourceProvider()));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        editText.setBackgroundDrawable(null);
        editText.setHint(row.optString("hint"));
        editText.setText(PrimePluginStore.getString(plugin.id(), key, row.optString("default")));
        editText.setSelection(editText.getText().length());
        if (row.optBoolean("multiline")) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setSingleLine(false);
            editText.setMaxLines(4);
        } else {
            editText.setSingleLine(true);
        }

        final FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 24, 4, 24, 4));

        final AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(row.optString("text"))
                .setView(container)
                .setPositiveButton(LocaleController.getString(R.string.Save), (d, which) -> {
                    final String value = editText.getText().toString();
                    PrimePluginStore.put(plugin.id(), key, value);
                    PrimePluginsController.getInstance().notifySettingChanged(
                            plugin.id(), index, org.json.JSONObject.quote(value));
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        dialog.show();
        // The keyboard, without which every text row costs the user an extra tap.
        editText.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText), 80);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
