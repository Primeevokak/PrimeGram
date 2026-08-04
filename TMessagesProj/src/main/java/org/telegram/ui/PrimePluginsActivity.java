package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.PrimePlugin;
import org.telegram.messenger.plugins.PrimePluginsController;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.PrimePluginCardCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * PrimeGram: the list of installed plugins.
 *
 * <p>Each row is a card: an icon, whether it is running, and the four things a user can actually
 * do about a plugin without going anywhere else - open its settings, share the file, delete it,
 * and see why it failed if it did. Everything on the card is a real, independently working
 * control; the row itself carries no click of its own, so there is nothing left that looks
 * interactive and is not.
 */
public class PrimePluginsActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_LIBRARIES = 1;
    private static final int ID_PLUGIN_BASE = 1000;

    private List<PrimePlugin> shown;
    /** Name to version, filled in behind the screen; empty until the engine answers. */
    private org.json.JSONObject libraries = new org.json.JSONObject();

    /** Cells are kept and rebound, not recreated - a fresh view on every update would restart
     * the switch's own animation and drop mid-scroll ripples on the action buttons. */
    private final HashMap<Integer, PrimePluginCardCell> cells = new HashMap<>();

    private static final String PREF_WARNED = "primegram_plugins_warned";

    private boolean searching;
    private String query;
    private ActionBarMenuItem searchItem;

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        final ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        searching = true;
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                            listView.scrollToPosition(0);
                        }
                    }

                    @Override
                    public void onSearchCollapse() {
                        searching = false;
                        query = null;
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                            listView.scrollToPosition(0);
                        }
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        query = editText.getText().toString();
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    }
                });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
        searchItem.setVisibility(shown != null && !shown.isEmpty() ? View.VISIBLE : View.GONE);
        menu.addItem(1, R.drawable.msg_info).setOnClickListener(v -> showTrustInfo());
        return view;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!searching) {
            return super.onBackPressed(invoked);
        }
        if (!invoked) {
            return false;
        }
        actionBar.closeSearchField();
        return false;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginsDidUpdate);
        PrimePluginsController.getInstance().loadIfNeeded();
        refreshLibraries();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        primeShowWarningOnce();
    }

    /**
     * Said once, on the way in, before there is anything to install.
     *
     * <p>The shadow text under the list says the same thing, and shadow text is not read. This is
     * the one screen in the app where a user can hand a stranger's code the same access the app
     * itself has, and being told that at the moment of arriving is different from being told it
     * underneath a list they are already scrolling.
     */
    private void primeShowWarningOnce() {
        if (getParentActivity() == null
                || MessagesController.getGlobalMainSettings().getBoolean(PREF_WARNED, false)) {
            return;
        }
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_WARNED, true).apply();
        showTrustInfo();
    }

    /** Same text as the one-time warning, reachable again any time from the info button - a
     *  user who dismissed it once and forgot the details had no way back to them before this. */
    private void showTrustInfo() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle("Прежде чем ставить плагины")
                .setMessage("Плагин — это программа, которую написал не автор PrimeGram. Запущенный плагин работает внутри приложения и видит то же, что и оно: переписку, контакты, файлы.\n\nPrimeGram ничего не скачивает сам. Файл плагина приносите вы, и он остаётся выключенным, пока вы не включите его вручную — включение и есть согласие его запустить.\n\nСтавьте только то, чьему автору доверяете, и по возможности читайте исходный код: плагин — это обычный текстовый файл на Python.")
                .setPositiveButton("Понятно", null)
                .create();
        showDialog(dialog);
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
        final List<PrimePlugin> all = PrimePluginsController.getInstance().getPlugins();
        shown = all;
        if (searchItem != null) {
            searchItem.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
        }

        if (all.isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asCenterShadow("Плагинов пока нет.\n\nОткройте файл .plugin в любом чате — приложение предложит его установить."));
            return;
        }

        if (searching && !TextUtils.isEmpty(query)) {
            final List<PrimePlugin> filtered = new java.util.ArrayList<>();
            final String needle = query.toLowerCase(Locale.ROOT);
            for (PrimePlugin plugin : all) {
                if (plugin.name().toLowerCase(Locale.ROOT).contains(needle)) {
                    filtered.add(plugin);
                }
            }
            shown = filtered;
            if (filtered.isEmpty()) {
                items.add(UItem.asShadow(null));
                items.add(UItem.asCenterShadow("Ничего не нашлось."));
                return;
            }
        }

        if (!searching) {
            items.add(UItem.asHeader("Установленные"));
        }
        for (int i = 0; i < shown.size(); i++) {
            items.add(pluginRow(ID_PLUGIN_BASE + i, shown.get(i)));
        }
        if (searching) {
            return;
        }
        items.add(UItem.asShadow("Плагины выполняются внутри приложения и могут читать и изменять всё, к чему у него есть доступ. Устанавливайте только те, чьему автору доверяете."));

        items.add(UItem.asCenterShadow(engineStatusSummary()));

        items.add(UItem.asButton(ID_LIBRARIES, "Скачанные библиотеки", librariesSummary()));
        items.add(UItem.asShadow("Плагин может попросить библиотеку, которой нет в приложении — она скачивается с PyPI при установке. Удалить их можно в любой момент: нужное скачается снова."));
    }

    private UItem pluginRow(int id, PrimePlugin plugin) {
        PrimePluginCardCell cell = cells.get(id);
        if (cell == null) {
            final Context context = getContext();
            if (context == null) {
                return UItem.asShadow(null);
            }
            cell = new PrimePluginCardCell(context, getResourceProvider());
            cells.put(id, cell);
        }
        final PrimePluginCardCell bound = cell;
        cell.setListener(new PrimePluginCardCell.Listener() {
            @Override
            public void onToggle() {
                togglePlugin(plugin);
            }

            @Override
            public void onOpenSettings() {
                presentFragment(new PrimePluginSettingsActivity(plugin));
            }

            @Override
            public void onShare() {
                shareFile(plugin);
            }

            @Override
            public void onDelete() {
                confirmDelete(plugin);
            }

            @Override
            public void onCopyError() {
                copyFullError(plugin);
            }
        });
        cell.set(plugin, subtitle(plugin), description(plugin), plugin.hasError());
        return UItem.asCustom(id, bound);
    }

    /** The card only ever shows {@code error.getMessage()} - a line, not the traceback a bug
     *  report actually needs. This is the whole thing, on the clipboard, so there is something to
     *  paste when reporting a plugin crash instead of retyping a one-line summary. */
    private void copyFullError(PrimePlugin plugin) {
        final Throwable error = plugin.error();
        final String text;
        if (error == null) {
            text = "Плагин '" + plugin.id() + "' не удалось загрузить (подробностей нет).";
        } else if (error instanceof org.telegram.messenger.plugins.PrimePluginsController.PluginLoadException
                && !android.text.TextUtils.isEmpty(((org.telegram.messenger.plugins.PrimePluginsController.PluginLoadException) error).fullTraceback)) {
            // The real Python traceback - what actually happened inside the plugin, not just the
            // one-line summary the card shows. Falls through to the Java stack trace below only
            // when the failure never reached a Python frame at all (unreadable file, and so on).
            text = "Плагин: " + plugin.id() + "\n\n"
                    + ((org.telegram.messenger.plugins.PrimePluginsController.PluginLoadException) error).fullTraceback;
        } else {
            final java.io.StringWriter writer = new java.io.StringWriter();
            writer.write("Плагин: " + plugin.id() + "\n\n");
            error.printStackTrace(new java.io.PrintWriter(writer));
            text = writer.toString();
        }
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(this).createCopyBulletin("Ошибка скопирована целиком").show();
    }

    /** A toggle on a failed plugin means "try again" - there is nothing else it could mean. */
    private void togglePlugin(PrimePlugin plugin) {
        final boolean enable = plugin.hasError() || !plugin.isEnabled();
        PrimePluginsController.getInstance().setEnabled(getParentActivity(), plugin, enable);
        if (plugin.hasError() && enable) {
            BulletinFactory.of(this).createSimpleBulletin(
                    org.telegram.messenger.R.raw.info, "Пробуем снова").show();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    /** Author and version, or nothing worth a second line. */
    private static CharSequence subtitle(PrimePlugin plugin) {
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
        if (plugin.isNotResponding()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append("не отвечает");
        }
        return builder;
    }

    /** What goes where the description sits: the failure if there is one, otherwise the blurb. */
    private static CharSequence description(PrimePlugin plugin) {
        if (plugin.hasError()) {
            final Throwable error = plugin.error();
            return error != null && error.getMessage() != null
                    ? error.getMessage() : "Плагин не удалось загрузить.";
        }
        return plugin.manifest.description;
    }

    /** How many plugins are actually running, and whether any of them are on the request/update
     *  path - the two most common reasons "why is nothing happening" turns out to be "a plugin
     *  failed to load" or "a plugin is watching something, one way or another". */
    private CharSequence engineStatusSummary() {
        final int active = org.telegram.messenger.plugins.PrimePluginHooks.activeCount();
        final boolean requestHooks = org.telegram.messenger.plugins.PrimePluginHooks.hasRequestHooks();
        return "Активно: " + active + " из " + shown.size()
                + (requestHooks ? " · есть хуки запросов/обновлений" : "");
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

    private void shareFile(PrimePlugin plugin) {
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
                    android.content.Intent.createChooser(intent, LocaleController.getString(org.telegram.messenger.R.string.ShareFile)), 500);
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            BulletinFactory.of(this).createErrorBulletin("Не удалось поделиться файлом").show();
        }
    }

    private void confirmDelete(PrimePlugin plugin) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle("Удалить «" + plugin.name() + "»?");
        builder.setMessage("Файл плагина и все его настройки будут удалены.");
        builder.setPositiveButton(LocaleController.getString(org.telegram.messenger.R.string.Delete), (dialog, which) -> {
            // Cells are keyed by row index, not by plugin - one fewer plugin just means the
            // cached cell at the tail gets rebound to whatever now falls at that index on the
            // next fillItems, same as it would for any other change to the list.
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
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        // Plugin cards are VIEW_TYPE_CUSTOM and wire their own buttons directly - this is only
        // ever reached for the plain rows below the list, "Скачанные библиотеки" among them.
        if (item.id == ID_LIBRARIES) {
            confirmClearLibraries();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        // Every action a long press used to reach - delete, and now share and settings too - is a
        // button on the card. Nothing is left for this gesture to do.
        return false;
    }
}
