package com.exteragram.messenger.plugins.ui.components;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import com.exteragram.messenger.plugins.Plugin;

import org.telegram.messenger.plugins.PrimePlugin;
import org.telegram.messenger.plugins.PrimePluginsController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PrimePluginCardCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * PrimeGram: exteraGram's plugin row, for plugins that list plugins in screens of their own
 * (plugin managers and stores) through {@code PluginCell.Factory.asPlugin(plugin, delegate)} or
 * {@code UItem.asPlugin(...)}. It is PrimeGram's own plugin card; the buttons call back into the
 * plugin's {@link PluginCellDelegate} instead of acting themselves, which is exteraGram's contract.
 */
public class PluginCell extends PrimePluginCardCell {

    public PluginCell(Context context) {
        this(context, null);
    }

    public PluginCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    public void set(Plugin plugin, PluginCellDelegate delegate) {
        final PrimePlugin prime = plugin == null ? null : PrimePluginsController.getInstance().findById(plugin.getId());
        if (prime != null) {
            final StringBuilder subtitle = new StringBuilder();
            if (!TextUtils.isEmpty(prime.manifest.author)) {
                subtitle.append(prime.manifest.author);
            }
            if (!TextUtils.isEmpty(prime.manifest.version)) {
                if (subtitle.length() > 0) {
                    subtitle.append(" · ");
                }
                subtitle.append("v").append(prime.manifest.version);
            }
            set(prime, subtitle, prime.hasError() ? String.valueOf(prime.error()) : prime.manifest.description, prime.hasError());
        }
        setListener(delegate == null ? null : new Listener() {
            @Override
            public void onToggle() {
                delegate.togglePlugin(PluginCell.this);
            }

            @Override
            public void onOpenSettings() {
                delegate.openPluginSettings();
            }

            @Override
            public void onShare() {
                delegate.sharePlugin();
            }

            @Override
            public void onDelete() {
                delegate.deletePlugin();
            }

            @Override
            public void onCopyError() {
            }
        });
    }

    public static final class Factory extends UItem.UItemFactory<PluginCell> {
        static {
            setup(new Factory());
        }

        @Override
        public PluginCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PluginCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((PluginCell) view).set(item.object instanceof Plugin ? (Plugin) item.object : null,
                    item.object2 instanceof PluginCellDelegate ? (PluginCellDelegate) item.object2 : null);
        }

        public static UItem asPlugin(Plugin plugin, PluginCellDelegate delegate) {
            return asPlugin(0, plugin, delegate);
        }

        public static UItem asPlugin(int id, Plugin plugin, PluginCellDelegate delegate) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = plugin;
            item.object2 = delegate;
            return item;
        }
    }
}
