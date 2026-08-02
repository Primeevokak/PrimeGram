package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
    private static final int ID_SHARE = 3;
    private static final int ID_COPY_ERROR = 4;
    private static final int ID_ROW_BASE = 2000;

    private final PrimePlugin plugin;
    /** {@code ""} for the plugin's own screen; otherwise where this screen sits in the tree a
     *  {@code create_sub_fragment} row built - the same value Python used to remember these rows. */
    private final String path;
    /** Null for the plugin's own screen - the two only differ in this. */
    private final String parentPath;
    private final int parentIndex;
    private final CharSequence subTitle;
    private JSONArray rows = new JSONArray();
    private final java.util.HashMap<Integer, View> customViews = new java.util.HashMap<>();
    private final java.util.HashSet<Integer> customViewsRequested = new java.util.HashSet<>();

    public PrimePluginSettingsActivity(PrimePlugin plugin) {
        this.plugin = plugin;
        this.path = "";
        this.parentPath = null;
        this.parentIndex = -1;
        this.subTitle = null;
    }

    /** A screen a {@code create_sub_fragment} row opened, one level under {@code parentPath}. */
    public PrimePluginSettingsActivity(PrimePlugin plugin, String parentPath, int parentIndex, CharSequence title) {
        this.plugin = plugin;
        this.parentPath = parentPath;
        this.parentIndex = parentIndex;
        this.path = parentPath.isEmpty() ? String.valueOf(parentIndex) : parentPath + "/" + parentIndex;
        this.subTitle = title;
    }

    private boolean isRoot() {
        return parentPath == null;
    }

    @Override
    public boolean onFragmentCreate() {
        if (isRoot()) {
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
        } else {
            PrimePluginsController.getInstance().requestSubSettings(plugin.id(), parentPath, parentIndex, json -> {
                try {
                    rows = new JSONObject(json).optJSONArray("rows");
                } catch (Throwable e) {
                    rows = null;
                }
                if (rows == null) {
                    rows = new JSONArray();
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        }
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return isRoot() ? plugin.name() : subTitle;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (isRoot()) {
            items.add(UItem.asButtonCheck(ID_ENABLED, "Включён", enabledSubtitle())
                    .setChecked(plugin.isEnabled()));
            items.add(UItem.asShadow(plugin.manifest.description != null && !plugin.manifest.description.isEmpty()
                    ? plugin.manifest.description : null));
        }

        if (rows.length() == 0) {
            if (!isRoot()) {
                items.add(UItem.asCenterShadow("Здесь пусто."));
                return;
            }
            if (plugin.hasError()) {
                // The switch above already offers a retry - it clears the error and tries to load
                // again, same as everywhere else in the app a switch means "on". What used to sit
                // here instead was "включите его, чтобы увидеть настройки", on a plugin that could
                // not be switched on: an instruction pointing at a control that would not obey it.
                final Throwable error = plugin.error();
                final String message = error != null && error.getMessage() != null
                        ? error.getMessage() : "Плагин не удалось загрузить.";
                items.add(UItem.asCenterShadow("Ошибка: " + message));
                items.add(UItem.asButton(ID_COPY_ERROR, "Скопировать ошибку"));
            } else {
                items.add(UItem.asCenterShadow(plugin.isEnabled()
                        ? "У этого плагина нет настроек."
                        : "Плагин выключен. Включите его, чтобы увидеть настройки."));
            }
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
                case "custom":
                    addCustomRow(items, id, row, i);
                    break;
                default:
                    break;
            }
        }
        if (isRoot()) {
            addDeleteRow(items);
        }
    }

    /**
     * A row the plugin drew itself. The view arrives asynchronously - it lives in Python and has
     * to make the same trip across the interpreter boundary that no JSON string can make - so the
     * first pass through this row is a blank shadow, replaced once {@link
     * PrimePluginsController#requestCustomView} answers.
     */
    private void addCustomRow(ArrayList<UItem> items, int id, JSONObject row, int index) {
        final View view = customViews.get(id);
        if (view != null) {
            final ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
            }
            items.add(UItem.asCustom(id, view));
            if (row.optBoolean("clickable")) {
                view.setOnClickListener(v -> PrimePluginsController.getInstance()
                        .notifySettingClicked(plugin.id(), path, index));
            }
            return;
        }
        items.add(UItem.asShadow(null));
        if (customViewsRequested.add(id)) {
            PrimePluginsController.getInstance().requestCustomView(plugin.id(), path, index, fetched -> {
                if (fetched == null || getParentActivity() == null) {
                    return;
                }
                customViews.put(id, fetched);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        }
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
        items.add(UItem.asButton(ID_SHARE, "Поделиться файлом плагина"));
        items.add(UItem.asButton(ID_DELETE, "Удалить плагин").red());
        items.add(UItem.asShadow(null));
    }

    /**
     * Sends the {@code .plugin} file itself, the same way a shared document leaves any other
     * screen in the app - through the FileProvider, not a raw file:// URI, which Android has
     * refused to hand other apps since Nougat.
     */
    private void shareFile() {
        if (getParentActivity() == null || plugin.file == null || !plugin.file.exists()) {
            return;
        }
        try {
            final android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            final android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    getParentActivity(),
                    org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider",
                    plugin.file);
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.setFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivityForResult(
                    android.content.Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)), 500);
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(
                    "Не удалось поделиться файлом").show();
        }
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
                // The rows come from the plugin, so they only exist once it is running again -
                // and with fresh indices, so anything cached from before no longer applies.
                customViews.clear();
                customViewsRequested.clear();
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
        if (item.id == ID_SHARE) {
            shareFile();
            return;
        }
        if (item.id == ID_COPY_ERROR) {
            final Throwable error = plugin.error();
            AndroidUtilities.addToClipboard(error != null && error.getMessage() != null
                    ? error.getMessage() : "Плагин не удалось загрузить.");
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.copy, "Ошибка скопирована").show();
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
                        plugin.id(), path, index, value ? "true" : "false");
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
                if (row.optBoolean("has_sub_fragment")) {
                    openSubFragment(index, row);
                } else {
                    PrimePluginsController.getInstance().notifySettingClicked(plugin.id(), path, index);
                }
                break;
        }
    }

    /** Pushes the screen a {@code create_sub_fragment} row asked for, one level under this one. */
    private void openSubFragment(int index, JSONObject row) {
        if (getParentActivity() == null) {
            return;
        }
        final String title = row.optString("text");
        presentFragment(new PrimePluginSettingsActivity(
                plugin, path, index, title.isEmpty() ? plugin.name() : title));
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
                            plugin.id(), path, index, String.valueOf(which));
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
                            plugin.id(), path, index, org.json.JSONObject.quote(value));
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
