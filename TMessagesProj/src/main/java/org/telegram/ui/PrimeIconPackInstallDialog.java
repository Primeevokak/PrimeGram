package org.telegram.ui;

import android.app.Activity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PrimeIconPacks;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;

/**
 * PrimeGram: the confirmation shown when the user taps a {@code .icons} file - the same seam
 * {@link PrimePluginInstallDialog} uses for {@code .plugin}, in {@link AndroidUtilities#openForView}.
 *
 * <p>Much lighter than the plugin sheet on purpose: an icon pack is a folder of images, never
 * code, so there is nothing here to warn about beyond "this replaces some of the app's own icons" -
 * a plain confirm/cancel, not an install flow with a loading state and a crash story.
 */
public final class PrimeIconPackInstallDialog {

    private PrimeIconPackInstallDialog() {
    }

    /** Returns true when the file was ours to handle - even if the user then said no. */
    public static boolean offer(Activity activity, File file, Theme.ResourcesProvider resourcesProvider) {
        if (!(activity instanceof LaunchActivity)) {
            return false;
        }
        final PrimeIconPacks.SourceMetadata meta;
        try {
            meta = PrimeIconPacks.inspect(file);
        } catch (Throwable e) {
            new AlertDialog.Builder(activity, resourcesProvider)
                    .setTitle("Не похоже на набор иконок")
                    .setMessage("Не удалось прочитать файл как набор иконок.")
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            return true;
        }

        final String name = meta != null && meta.packName != null ? meta.packName : file.getName();
        final StringBuilder message = new StringBuilder("Заменит часть иконок приложения.");
        if (meta != null) {
            message.append("\n\n").append(meta.iconCount).append(" иконок");
            if (meta.author != null && !meta.author.isEmpty()) {
                message.append("\nАвтор: ").append(meta.author);
            }
            if (meta.version != null && !meta.version.isEmpty()) {
                message.append("\nВерсия: ").append(meta.version);
            }
        }

        new AlertDialog.Builder(activity, resourcesProvider)
                .setTitle("Установить «" + name + "»?")
                .setMessage(message.toString())
                .setPositiveButton("Установить", (dialog, which) -> install(activity, file, name, resourcesProvider))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
        return true;
    }

    private static void install(Activity activity, File file, String displayName, Theme.ResourcesProvider resourcesProvider) {
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            PrimeIconPacks.Pack installed = null;
            String error = null;
            try {
                installed = PrimeIconPacks.installFromZip(file, displayName);
            } catch (Throwable t) {
                error = t.getMessage() != null ? t.getMessage() : "не удалось установить набор";
            }
            final PrimeIconPacks.Pack finalInstalled = installed;
            final String finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (finalError != null) {
                    new AlertDialog.Builder(activity, resourcesProvider)
                            .setTitle("Не удалось установить")
                            .setMessage(finalError)
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(activity, resourcesProvider)
                        .setTitle("Готово")
                        .setMessage("Набор «" + finalInstalled.name + "» установлен. Включить его сейчас?")
                        .setPositiveButton("Включить", (dialog, which) -> PrimeIconPacks.setActivePackId(finalInstalled.id))
                        .setNegativeButton("Позже", null)
                        .show();
            });
        });
    }
}
