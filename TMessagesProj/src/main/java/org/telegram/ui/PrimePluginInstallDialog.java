package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.PluginManifest;
import org.telegram.messenger.plugins.PrimePlugin;
import org.telegram.messenger.plugins.PrimePluginsController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;

/**
 * PrimeGram: the confirmation shown when the user taps a {@code .plugin} file in a chat.
 *
 * <p>Everything on it is read from the file's own header, before a line of it has been executed -
 * that is the reason the manifest reader is written in Java. The user is agreeing to run a
 * stranger's code inside their messenger, and the least they are owed is to know what it calls
 * itself and who signed it, decided by someone other than the code being installed.
 *
 * <p>Laid out after exteraGram's own install sheet - a big icon, a version line that shows the
 * old version struck through next to the new one on an update, a full-width button that turns
 * into a loading spinner rather than just going dark. exteraGram ties the icon to a bundled
 * sticker pack per plugin and gates installs on a "trusted source" channel badge; neither exists
 * on our side, so both are left out rather than faked - the icon is a plain badge, and there is no
 * trust pill.
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
        final PrimePlugin existing = PrimePluginsController.getInstance().findById(manifest.id);

        new PrimePluginInstallSheet(activity, resourcesProvider, file, manifest, existing, compatible).show();
        return true;
    }

    private static void install(Activity activity, File file, PluginManifest manifest, Runnable onDone, java.util.function.Consumer<String> onError) {
        PrimePluginsController.getInstance().install(activity, file, new PrimePluginsController.InstallCallback() {
            @Override
            public void onInstalled(PrimePlugin plugin, boolean replacedExisting) {
                onDone.run();
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
                onError.accept(message);
            }
        });
    }

    /** The rich bottom sheet itself - icon, title, version diff, description, requirements,
     *  warning, and a full-width button that carries the whole install/update action. */
    private static final class PrimePluginInstallSheet extends BottomSheet {

        private final Activity activity;
        private final File file;
        private final PluginManifest manifest;
        private final boolean compatible;
        private final boolean isUpdate;
        private final ButtonWithCounterView button;
        private boolean installing;

        PrimePluginInstallSheet(Activity activity, Theme.ResourcesProvider resourcesProvider, File file,
                                 PluginManifest manifest, PrimePlugin existing, boolean compatible) {
            super(activity, false, resourcesProvider);
            this.activity = activity;
            this.file = file;
            this.manifest = manifest;
            this.compatible = compatible;
            this.isUpdate = existing != null;
            setApplyTopPadding(false);
            setDelegate(new BottomSheetDelegate() {
                @Override
                public boolean canDismiss() {
                    return !installing;
                }
            });

            final Context context = getContext();
            final FrameLayout frame = new FrameLayout(context);
            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            frame.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final FrameLayout iconFrame = new FrameLayout(context);
            root.addView(iconFrame, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

            final ImageView fallbackIcon = new ImageView(context);
            fallbackIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            fallbackIcon.setImageResource(R.drawable.msg_settings);
            fallbackIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
            fallbackIcon.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(78), getThemedColor(Theme.key_featuredStickers_addButton)));
            final int iconPad = AndroidUtilities.dp(20);
            fallbackIcon.setPadding(iconPad, iconPad, iconPad, iconPad);
            iconFrame.addView(fallbackIcon, LayoutHelper.createFrame(78, 78));

            // Same sticker a plugin's card shows once installed - resolved the same way, so the
            // icon does not appear to only exist after the fact.
            final org.telegram.ui.Components.PrimePluginCardCell.StickerIconView stickerIcon =
                    new org.telegram.ui.Components.PrimePluginCardCell.StickerIconView(context, 78);
            iconFrame.addView(stickerIcon, LayoutHelper.createFrame(78, 78));
            org.telegram.messenger.plugins.PrimePluginIcons.resolve(manifest, sticker -> {
                stickerIcon.setSticker(sticker);
                fallbackIcon.setVisibility(sticker != null ? View.GONE : View.VISIBLE);
            });

            final TextView title = new TextView(context);
            title.setGravity(Gravity.CENTER);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(manifest.name != null && !manifest.name.isEmpty() ? manifest.name : manifest.id);
            root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 40, 16, 40, 0));

            final TextView versionLine = new TextView(context);
            versionLine.setGravity(Gravity.CENTER);
            versionLine.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            versionLine.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            versionLine.setText(buildVersionLine(existing, manifest));
            root.addView(versionLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 4, 21, 0));

            if (manifest.description != null && !manifest.description.isEmpty()) {
                final TextView description = new TextView(context);
                description.setGravity(Gravity.LEFT);
                description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                description.setLineSpacing(AndroidUtilities.dp(2), 1f);
                description.setText(manifest.description);
                root.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 20, 21, 0));
            }

            if (manifest.requirements != null && !manifest.requirements.isEmpty()) {
                final TextView requirements = new TextView(context);
                requirements.setGravity(Gravity.LEFT);
                requirements.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                requirements.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                requirements.setText("Требует библиотеки: " + TextUtils.join(", ", manifest.requirements));
                root.addView(requirements, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 10, 21, 0));
            }

            final TextView warning = new TextView(context);
            warning.setGravity(Gravity.LEFT);
            warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            warning.setLineSpacing(AndroidUtilities.dp(2), 1f);
            if (compatible) {
                warning.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                warning.setText("Плагин выполняется внутри приложения и может читать и изменять всё, к чему у него есть доступ, включая переписку. Устанавливайте только то, чьему автору доверяете.");
            } else {
                warning.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                warning.setText("Плагину нужна другая версия приложения (" +
                        (manifest.minVersion != null ? manifest.minVersion : String.valueOf(manifest.appVersion)) + "). Установить его нельзя.");
            }
            root.addView(warning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 16, 21, 0));

            button = new ButtonWithCounterView(context, true, resourcesProvider);
            button.setRound();
            button.setText(compatible ? (isUpdate ? "Обновить" : "Установить") : "Понятно", false);
            applyPressBounce(button);
            button.setOnClickListener(v -> onButtonClick());
            root.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 28, 16, 16));

            final ScrollView scrollView = new ScrollView(context);
            scrollView.addView(frame);
            setCustomView(scrollView);
        }

        private void onButtonClick() {
            if (!compatible) {
                dismiss();
                return;
            }
            if (installing) {
                return;
            }
            installing = true;
            setCanDismissWithSwipe(false);
            setCanDismissWithTouchOutside(false);
            button.setLoading(true);
            install(activity, file, manifest, () -> {
                installing = false;
                button.setLoading(false);
                dismiss();
            }, errorMessage -> AndroidUtilities.runOnUIThread(() -> {
                installing = false;
                setCanDismissWithSwipe(true);
                setCanDismissWithTouchOutside(true);
                button.setLoading(false);
                if (getContext() != null) {
                    BulletinFactory.of(container, resourcesProvider).createErrorBulletin(errorMessage).show();
                }
            }));
        }

        private static CharSequence buildVersionLine(PrimePlugin existing, PluginManifest manifest) {
            if (existing == null) {
                final String version = manifest.version != null ? manifest.version : "?";
                return "Версия " + version;
            }
            final String from = existing.manifest.version != null ? existing.manifest.version : "?";
            final String to = manifest.version != null ? manifest.version : "?";
            if (from.equals(to)) {
                return "Переустановка версии " + to;
            }
            final SpannableStringBuilder text = new SpannableStringBuilder(from).append(" → ").append(to);
            text.setSpan(new StrikethroughSpan(), 0, from.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }

        /** Same tactile press-bounce exteraGram uses on its own plugin-related buttons. */
        private static void applyPressBounce(View view) {
            view.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).setInterpolator(null).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(350)
                                .setInterpolator(new OvershootInterpolator(1.5f)).start();
                        break;
                }
                return false;
            });
        }
    }
}
