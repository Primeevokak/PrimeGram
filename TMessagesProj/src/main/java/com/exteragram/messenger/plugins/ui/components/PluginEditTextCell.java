package com.exteragram.messenger.plugins.ui.components;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import com.exteragram.messenger.plugins.Plugin;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.plugins.models.EditTextSetting;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.regex.Pattern;

/**
 * exteraGram API, PrimeGram implementation: a settings row with a text field bound to one plugin
 * setting. Plugins get the row as a ready {@link UItem} through {@code PluginEditTextCell.Factory.as(plugin,
 * setting)} and add it to their own list. The value lives under exteraGram's preference key
 * ({@code plugin_setting_<plugin>_<key>}), which PrimeGram maps onto its plugin settings store, so
 * {@code self.get_setting(key)} on the plugin side reads what was typed here.
 */
public class PluginEditTextCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private EditTextCell cell;
    private Plugin plugin;
    private EditTextSetting setting;
    private boolean binding;
    private boolean cellMultiline;
    private int cellMaxLength = Integer.MIN_VALUE;
    private String cellHint;

    public PluginEditTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    private static String prefKey(Plugin plugin, EditTextSetting setting) {
        return "plugin_setting_" + (plugin != null ? plugin.getId() : "") + "_" + setting.getKey();
    }

    private static SharedPreferences prefs() {
        return PluginsController.getInstance().getPreferences();
    }

    public void set(Plugin plugin, EditTextSetting setting) {
        this.plugin = plugin;
        this.setting = setting;
        if (setting == null) {
            return;
        }
        final String hint = setting.getHint() != null ? setting.getHint() : "";
        // EditTextCell takes its line mode and limit in the constructor: rebuild only when those differ.
        if (cell == null || cellMultiline != setting.getMultiline() || cellMaxLength != setting.getMaxLength() || !TextUtils.equals(cellHint, hint)) {
            removeAllViews();
            cellMultiline = setting.getMultiline();
            cellMaxLength = setting.getMaxLength();
            cellHint = hint;
            cell = new EditTextCell(getContext(), hint, cellMultiline, false, cellMaxLength > 0 ? cellMaxLength : -1, resourcesProvider) {
                @Override
                protected void onTextChanged(CharSequence newText) {
                    super.onTextChanged(newText);
                    PluginEditTextCell.this.onTextChanged(newText);
                }
            };
            cell.editText.setFilters(buildFilters(setting));
            addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        binding = true;
        try {
            String value = null;
            try {
                value = prefs().getString(prefKey(plugin, setting), null);
            } catch (Throwable ignore) {
            }
            cell.setText(value != null ? value : setting.getDefaultValue() != null ? setting.getDefaultValue() : "");
        } finally {
            binding = false;
        }
    }

    private static InputFilter[] buildFilters(EditTextSetting setting) {
        Pattern pattern = null;
        if (!TextUtils.isEmpty(setting.getMask())) {
            try {
                pattern = Pattern.compile(setting.getMask());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        final Pattern mask = pattern;
        final int max = setting.getMaxLength();
        final InputFilter maskFilter = (source, start, end, dest, dstart, dend) -> {
            if (mask == null) {
                return null;
            }
            final String result = dest.subSequence(0, dstart) + source.subSequence(start, end).toString() + dest.subSequence(dend, dest.length());
            return result.isEmpty() || mask.matcher(result).matches() ? null : "";
        };
        return max > 0 ? new InputFilter[]{new InputFilter.LengthFilter(max), maskFilter} : new InputFilter[]{maskFilter};
    }

    private void onTextChanged(CharSequence text) {
        if (binding || setting == null) {
            return;
        }
        final String value = text != null ? text.toString() : "";
        try {
            prefs().edit().putString(prefKey(plugin, setting), value).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (setting.getOnChangeCallback() != null) {
            try {
                setting.getOnChangeCallback().call(value);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public EditTextCell getCell() {
        return cell;
    }

    public static class Factory extends UItem.UItemFactory<PluginEditTextCell> {
        static { setup(new Factory()); }

        /** exteraGram's {@code Factory.as} - a Kotlin companion function; Python calls it through getattr. */
        public static UItem as(Plugin plugin, EditTextSetting setting) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = setting;
            item.object2 = plugin;
            return item;
        }

        @Override
        public PluginEditTextCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PluginEditTextCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((PluginEditTextCell) view).set(item.object2 instanceof Plugin ? (Plugin) item.object2 : null,
                    item.object instanceof EditTextSetting ? (EditTextSetting) item.object : null);
        }

        @Override
        public boolean isClickable() {
            return false;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.object == b.object;
        }
    }
}
