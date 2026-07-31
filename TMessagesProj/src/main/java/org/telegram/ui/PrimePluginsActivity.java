package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.plugins.PrimePlugin;
import org.telegram.messenger.plugins.PrimePluginsController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.List;

/**
 * PrimeGram: the list of installed plugins.
 *
 * <p>One row per plugin, and the row says the two things a user can act on: whether it is running,
 * and what went wrong if it is not. A plugin that failed keeps its row rather than disappearing -
 * a plugin that vanishes after an update looks like the app lost it, and the user still has a file
 * they may want to delete.
 */
public class PrimePluginsActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_LIBRARIES = 1;
    private static final int ID_PLUGIN_BASE = 1000;

    private List<PrimePlugin> shown;
    /** Name to version, filled in behind the screen; empty until the engine answers. */
    private org.json.JSONObject libraries = new org.json.JSONObject();

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginsDidUpdate);
        PrimePluginsController.getInstance().loadIfNeeded();
        refreshLibraries();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pluginsDidUpdate);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pluginsDidUpdate && listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return "Плагины";
    }

    @Override
    protected void fillItems(java.util.ArrayList<UItem> items, UniversalAdapter adapter) {
        shown = PrimePluginsController.getInstance().getPlugins();

        if (shown.isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asCenterShadow("Плагинов пока нет.\n\nОткройте файл .plugin в любом чате — приложение предложит его установить."));
            return;
        }

        items.add(UItem.asHeader("Установленные"));
        for (int i = 0; i < shown.size(); i++) {
            final PrimePlugin plugin = shown.get(i);
            // A row, not a switch: the switch lives on the plugin's own page, next to the settings
            // it governs. A list of switches would make the common tap - "what does this thing
            // even do" - the one gesture that is not available.
            items.add(UItem.asButtonCheck(ID_PLUGIN_BASE + i, plugin.name(), subtitle(plugin))
                    .setChecked(plugin.isEnabled()));
        }
        items.add(UItem.asShadow("Нажатие открывает настройки плагина, долгое — удаляет его вместе с ними.\n\nПлагины выполняются внутри приложения и могут читать и изменять всё, к чему у него есть доступ. Устанавливайте только те, чьему автору доверяете."));

        items.add(UItem.asButton(ID_LIBRARIES, "Скачанные библиотеки", librariesSummary()));
        items.add(UItem.asShadow("Плагин может попросить библиотеку, которой нет в приложении — она скачивается с PyPI при установке. Удалить их можно в любой момент: нужное скачается снова."));
    }

    /** What is worth saying under the name: the failure if there is one, otherwise who wrote it. */
    private static CharSequence subtitle(PrimePlugin plugin) {
        if (plugin.hasError()) {
            final Throwable error = plugin.error();
            final String message = error == null || error.getMessage() == null
                    ? "не удалось загрузить" : error.getMessage();
            return "Ошибка: " + message;
        }
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
        if (builder.length() == 0 && plugin.manifest.description != null) {
            builder.append(plugin.manifest.description);
        }
        return builder;
    }

    private CharSequence librariesSummary() {
        final int count = libraries.length();
        if (count == 0) {
            return "нет";
        }
        final StringBuilder builder = new StringBuilder();
        final java.util.Iterator<String> names = libraries.keys();
        while (names.hasNext() && builder.length() < 40) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(names.next());
        }
        return names.hasNext() ? builder + "…" : builder.toString();
    }

    private void refreshLibraries() {
        PrimePluginsController.getInstance().requestLibraries(json -> {
            try {
                libraries = new org.json.JSONObject(json);
            } catch (Throwable e) {
                libraries = new org.json.JSONObject();
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
    }

    private void confirmClearLibraries() {
        if (libraries.length() == 0) {
            BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.info, "Ничего не скачано").show();
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle("Удалить библиотеки?")
                .setMessage("Скачанные пакеты будут удалены. Те, что нужны включённым плагинам, скачаются заново при следующей загрузке.")
                .setPositiveButton(LocaleController.getString(org.telegram.messenger.R.string.Delete), (d, which) ->
                        PrimePluginsController.getInstance().clearLibraries(this::refreshLibraries))
                .setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null)
                .create();
        dialog.show();
        dialog.redPositive();
    }

    private PrimePlugin pluginAt(UItem item) {
        final int index = item.id - ID_PLUGIN_BASE;
        return shown != null && index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_LIBRARIES) {
            confirmClearLibraries();
            return;
        }
        final PrimePlugin plugin = pluginAt(item);
        if (plugin == null) {
            return;
        }
        if (plugin.hasError()) {
            // A plugin that refused to load has no settings to show and cannot be switched on.
            // The reason is the only useful thing left, so that is what the row does.
            showError(plugin);
            return;
        }
        presentFragment(new PrimePluginSettingsActivity(plugin));
    }

    private void showError(PrimePlugin plugin) {
        final Throwable error = plugin.error();
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(plugin.name())
                .setMessage(error != null && error.getMessage() != null
                        ? error.getMessage() : "Плагин не удалось загрузить.")
                .setPositiveButton(LocaleController.getString(org.telegram.messenger.R.string.OK), null)
                .show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        final PrimePlugin plugin = pluginAt(item);
        if (plugin == null) {
            return false;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle("Удалить «" + plugin.name() + "»?");
        builder.setMessage("Файл плагина и все его настройки будут удалены.");
        builder.setPositiveButton(LocaleController.getString(org.telegram.messenger.R.string.Delete), (dialog, which) -> {
            PrimePluginsController.getInstance().delete(plugin);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.ic_delete, "Плагин удалён").show();
        });
        builder.setNegativeButton(LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.redPositive();
        AndroidUtilities.vibrateCursor(view);
        return true;
    }
}
