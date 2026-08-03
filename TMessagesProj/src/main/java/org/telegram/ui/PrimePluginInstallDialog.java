package org.telegram.ui;

import android.app.Activity;
import android.text.SpannableStringBuilder;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.PluginManifest;
import org.telegram.messenger.plugins.PrimePluginsController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;

/**
 * PrimeGram: the confirmation shown when the user taps a {@code .plugin} file in a chat.
 *
 * <p>Everything on it is read from the file's own header, before a line of it has been executed -
 * that is the reason the manifest reader is written in Java. The user is agreeing to run a
 * stranger's code inside their messenger, and the least they are owed is to know what it calls
 * itself and who signed it, decided by someone other than the code being installed.
 */
public final class PrimePluginInstallDialog {

    private PrimePluginInstallDialog() {
    }

    /** Returns true when the file was ours to handle - even if the user then said no. */
    public static boolean offer(Activity activity, File file, Theme.ResourcesProvider resourcesProvider) {
        if (!(activity instanceof LaunchActivity)) {
            return false;
        }
        final PluginManifest manifest;
        try {
            manifest = PrimePluginsController.inspect(file);
        } catch (Throwable e) {
            // Not a plugin we can read. Saying so is better than the system chooser's shrug.
            new AlertDialog.Builder(activity, resourcesProvider)
                    .setTitle("Не похоже на плагин")
                    .setMessage("В файле нет заголовка плагина, поэтому установить его нельзя.")
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            return true;
        }

        final boolean compatible = manifest.isCompatibleWithApp(BuildVars.BUILD_VERSION_STRING);
        // Read before anything is written, so what we show is a true comparison rather than a
        // description of the state we are about to overwrite.
        final org.telegram.messenger.plugins.PrimePlugin existing =
                PrimePluginsController.getInstance().findById(manifest.id);

        final SpannableStringBuilder text = new SpannableStringBuilder();
        if (existing != null) {
            // An update, not a first install - said first, because it changes what every line
            // after it means: "версия" below is not what you are about to get, it is what you
            // are about to leave.
            final String from = existing.manifest.version;
            final String to = manifest.version;
            final int order = org.telegram.messenger.plugins.PluginVersions.compare(
                    from == null ? "" : from, to == null ? "" : to);
            if (from != null && to != null && !from.equals(to)) {
                text.append(order < 0 ? "Обновление: " : order > 0 ? "Более старая версия: " : "Переустановка: ")
                        .append(from).append(" → ").append(to).append("\n\n");
            } else {
                text.append("Обновление до той же версии (").append(to != null ? to : "?").append(")\n\n");
            }
            text.append("Настройки и включённость сохранятся.\n\n");
        }
        if (manifest.description != null && !manifest.description.isEmpty()) {
            text.append(manifest.description).append("\n\n");
        }
        if (manifest.author != null && !manifest.author.isEmpty()) {
            text.append("Автор: ").append(manifest.author).append("\n");
        }
        if (existing == null && manifest.version != null && !manifest.version.isEmpty()) {
            text.append("Версия: ").append(manifest.version).append("\n");
        }
        if (manifest.requirements != null && !manifest.requirements.isEmpty()) {
            text.append("Требует библиотеки: ")
                    .append(android.text.TextUtils.join(", ", manifest.requirements)).append("\n");
        }
        text.append("\n");
        if (compatible) {
            text.append("Плагин выполняется внутри приложения и может читать и изменять всё, к чему у него есть доступ, включая переписку. Устанавливайте только то, чьему автору доверяете.");
        } else {
            text.append("Плагину нужна другая версия приложения (").append(manifest.minVersion != null
                    ? manifest.minVersion : String.valueOf(manifest.appVersion)).append("). Установить его нельзя.");
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);
        builder.setTitle(manifest.name != null && !manifest.name.isEmpty() ? manifest.name : manifest.id);
        builder.setMessage(text);
        if (compatible) {
            builder.setPositiveButton(existing != null ? "Обновить" : "Установить",
                    (dialog, which) -> install(activity, file, manifest));
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        }
        builder.show();
        return true;
    }

    private static void install(Activity activity, File file, PluginManifest manifest) {
        PrimePluginsController.getInstance().install(activity, file, new PrimePluginsController.InstallCallback() {
            @Override
            public void onInstalled(org.telegram.messenger.plugins.PrimePlugin plugin, boolean replacedExisting) {
                final org.telegram.ui.ActionBar.BaseFragment fragment = LaunchActivity.getLastFragment();
                if (fragment == null) {
                    return;
                }
                if (plugin.hasError()) {
                    BulletinFactory.of(fragment).createErrorBulletin(
                            "Плагин установлен, но не запустился: " + plugin.error().getMessage()).show();
                } else if (replacedExisting) {
                    BulletinFactory.of(fragment).createSuccessBulletin("Плагин обновлён").show();
                } else {
                    // Says what happens next, because nothing has: the plugin is on the list and
                    // switched off, and a message that only said "установлен" would leave the user
                    // waiting for something that is not coming.
                    BulletinFactory.of(fragment).createSimpleBulletin(
                            org.telegram.messenger.R.raw.info, "Плагин установлен",
                            "Включите его в разделе «Плагины», чтобы он заработал").show();
                }
            }

            @Override
            public void onFailed(String reason) {
                final org.telegram.ui.ActionBar.BaseFragment fragment = LaunchActivity.getLastFragment();
                if (fragment == null) {
                    return;
                }
                final String message;
                switch (reason) {
                    case "version":
                        message = "Плагину нужна другая версия приложения";
                        break;
                    case "malformed":
                        message = "В файле нет заголовка плагина";
                        break;
                    default:
                        message = "Не удалось скопировать файл плагина";
                        break;
                }
                BulletinFactory.of(fragment).createErrorBulletin(message).show();
            }
        });
    }
}
