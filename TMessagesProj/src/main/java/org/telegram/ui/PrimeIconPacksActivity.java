package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.PrimeIconPacks;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: which icon pack draws the app's icons, if any.
 *
 * <p>A pack is a zip of images named after the drawable they replace - {@code msg_delete.png},
 * {@code msg_delete.svg} - unpacked once on install and read from disk from then on through {@link
 * PrimeIconPacks}. There is no catalogue here, no download - installing one means having the zip
 * already, the same way installing a plugin means having the {@code .plugin} file already.
 */
public class PrimeIconPacksActivity extends UniversalFragment {

    private static final int ID_INSTALL = 1;
    private static final int ID_NONE = 2;
    private static final int ID_PACK_BASE = 100;
    private static final int REQUEST_ZIP = 77;

    private List<PrimeIconPacks.Pack> packs = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        reload();
        return super.onFragmentCreate();
    }

    private void reload() {
        packs = PrimeIconPacks.listPacks();
    }

    @Override
    protected CharSequence getTitle() {
        return "Наборы иконок";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final String active = PrimeIconPacks.getActivePackId();

        items.add(UItem.asButton(ID_INSTALL, "Установить набор", "из .zip"));
        items.add(UItem.asShadow("Архив с картинками, названными как заменяемая иконка: "
                + "msg_delete.png, msg_delete.svg. Набор не обязан покрывать все иконки — "
                + "недостающие останутся родными."));

        if (!packs.isEmpty()) {
            items.add(UItem.asHeader("Наборы"));
            items.add(UItem.asCheck(ID_NONE, "Родные иконки").setChecked(active == null));
            for (int i = 0; i < packs.size(); i++) {
                final PrimeIconPacks.Pack pack = packs.get(i);
                items.add(UItem.asCheck(ID_PACK_BASE + i,
                                pack.name + " · " + pack.iconCount + " иконок")
                        .setChecked(pack.id.equals(active)));
            }
            items.add(UItem.asShadow("Долгий тап по набору — удалить."));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_INSTALL) {
            pickZip();
        } else if (item.id == ID_NONE) {
            PrimeIconPacks.setActivePackId(null);
            listView.adapter.update(true);
        } else if (item.id >= ID_PACK_BASE && item.id < ID_PACK_BASE + packs.size()) {
            PrimeIconPacks.setActivePackId(packs.get(item.id - ID_PACK_BASE).id);
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_PACK_BASE && item.id < ID_PACK_BASE + packs.size()) {
            confirmDelete(packs.get(item.id - ID_PACK_BASE));
            return true;
        }
        return false;
    }

    private void confirmDelete(PrimeIconPacks.Pack pack) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("Удалить «" + pack.name + "»?")
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                    PrimeIconPacks.deletePack(pack.id);
                    reload();
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void pickZip() {
        try {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            startActivityForResult(intent, REQUEST_ZIP);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_ZIP || data == null || data.getData() == null) {
            return;
        }
        final Context context = getContext();
        if (context == null) {
            return;
        }
        String path = AndroidUtilities.getPath(data.getData());
        if (path == null || path.startsWith("content://")) {
            path = MediaController.copyFileToCache(data.getData(), "zip");
        }
        if (path == null) {
            BulletinFactory.of(this).createErrorBulletin("Не удалось прочитать файл").show();
            return;
        }
        final File zip = new File(path);
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            PrimeIconPacks.Pack installed = null;
            String error = null;
            try {
                installed = PrimeIconPacks.installFromZip(zip, zip.getName().replace(".zip", ""));
            } catch (Throwable t) {
                error = t.getMessage() != null ? t.getMessage() : "не удалось установить набор";
            }
            final PrimeIconPacks.Pack finalInstalled = installed;
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalError != null) {
                    BulletinFactory.of(this).createErrorBulletin(finalError).show();
                    return;
                }
                reload();
                if (finalInstalled != null) {
                    PrimeIconPacks.setActivePackId(finalInstalled.id);
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }
}
