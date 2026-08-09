package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.blocks.PrimeBlockScript;
import org.telegram.messenger.blocks.PrimeBlocksController;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.PrimeCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.List;

/**
 * PrimeGram Blocks: the list of installed `.pr` scripts - mirrors {@link PrimePluginsActivity}'s
 * shape (list/enable-disable/delete) with plain switch rows rather than a bespoke card cell,
 * since a script has far less per-row state to show than a plugin (no running/crashed/
 * not-responding states yet - see the "PrimeGram Blocks" plan §5.4; the rich-card treatment can
 * follow later if it turns out to be worth it once Phase C/D exist to give a row more to say).
 *
 * <p>Tapping a script opens the block editor (Phase C) once it exists; for now the row's tap
 * target only toggles enabled, same as any other switch row in this app, and there's a separate
 * long-press for delete.
 */
public class PrimeBlocksActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_SCRIPT_BASE = 1000;

    private List<PrimeBlockScript> shown;

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        final ActionBarMenuItem add = actionBar.createMenu().addItem(0, R.drawable.msg_add);
        add.setOnClickListener(v -> presentFragment(new PrimeBlockEditorActivity()));
        return view;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.blocksDidUpdate);
        PrimeBlocksController.getInstance().loadIfNeeded();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.blocksDidUpdate);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.blocksDidUpdate && listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        return "Блоки";
    }

    @Override
    protected void fillItems(java.util.ArrayList<UItem> items, UniversalAdapter adapter) {
        shown = PrimeBlocksController.getInstance().getScripts();

        if (shown.isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asCenterShadow("Скриптов пока нет.\n\nОткройте файл .pr в любом чате — приложение предложит его установить."));
            return;
        }

        items.add(UItem.asHeader("Установленные"));
        for (int i = 0; i < shown.size(); i++) {
            items.add(scriptRow(ID_SCRIPT_BASE + i, shown.get(i)));
        }
        items.add(UItem.asShadow("Скрипт использует только встроенные блоки PrimeGram — он не может выполнять произвольный код. Долгое нажатие на строку — удалить."));
    }

    private UItem scriptRow(int id, PrimeBlockScript script) {
        final CharSequence subtitle;
        if (script.isUnsupported()) {
            subtitle = "Использует блоки, которых нет в этой версии приложения";
        } else {
            final StringBuilder builder = new StringBuilder();
            if (script.manifest.author != null && !script.manifest.author.isEmpty()) {
                builder.append(script.manifest.author);
            }
            if (script.manifest.version != null && !script.manifest.version.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append("v").append(script.manifest.version);
            }
            subtitle = builder.length() > 0 ? builder : script.manifest.description;
        }
        return PrimeCheckCell.Factory.check(id, 0,
                org.telegram.ui.Components.IconBackgroundColors.BLUE.top,
                org.telegram.ui.Components.IconBackgroundColors.BLUE.bottom,
                R.drawable.msg_settings, script.name(), subtitle,
                !script.isUnsupported() && script.isEnabled());
    }

    private PrimeBlockScript scriptForId(int itemId) {
        final int index = itemId - ID_SCRIPT_BASE;
        if (shown == null || index < 0 || index >= shown.size()) {
            return null;
        }
        return shown.get(index);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final PrimeBlockScript script = scriptForId(item.id);
        if (script == null) {
            return;
        }
        if (script.isUnsupported()) {
            BulletinFactory.of(this).createErrorBulletin("Обновите приложение, чтобы включить этот скрипт").show();
            return;
        }
        PrimeBlocksController.getInstance().setEnabled(script, !script.isEnabled());
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        final PrimeBlockScript script = scriptForId(item.id);
        if (script == null) {
            return false;
        }
        final ItemOptions io = ItemOptions.makeOptions(this, view);
        io.add(R.drawable.msg_edit, "Открыть в редакторе", () -> presentFragment(new PrimeBlockEditorActivity(script)));
        io.addGap();
        io.add(R.drawable.msg_delete, "Удалить", true, () -> confirmDelete(script));
        io.show();
        return true;
    }

    private void confirmDelete(PrimeBlockScript script) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
        builder.setTitle("Удалить «" + script.name() + "»?");
        builder.setMessage("Файл скрипта будет удалён.");
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            PrimeBlocksController.getInstance().delete(script);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, "Скрипт удалён").show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.redPositive();
    }
}
