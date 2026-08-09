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
import org.telegram.messenger.blocks.BlockRegistry;
import org.telegram.messenger.blocks.PrimeBlockManifest;
import org.telegram.messenger.blocks.PrimeBlockScript;
import org.telegram.messenger.blocks.PrimeBlocksController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.util.Set;

/**
 * PrimeGram Blocks: the confirmation shown when the user taps a {@code .pr} file in a chat.
 *
 * <p>Modeled directly on {@link PrimePluginInstallDialog}'s sheet (icon, version-diff line,
 * description, full-width button that becomes a spinner), but two things are genuinely
 * different, not just reworded: there is no requirements section (`.pr` has no library-dependency
 * concept), and the risk-disclosure line makes a materially different, true claim - a `.pr`
 * script cannot run arbitrary code, only the closed, enumerable set of built-in blocks it lists,
 * unlike a `.plugin`'s arbitrary Python. See the "PrimeGram Blocks" plan §5.2.
 */
public final class PrimeBlockInstallDialog {

    private PrimeBlockInstallDialog() {
    }

    public static boolean offer(Activity activity, File file, Theme.ResourcesProvider resourcesProvider) {
        if (!(activity instanceof LaunchActivity)) {
            return false;
        }
        final PrimeBlockManifest manifest;
        try {
            manifest = PrimeBlocksController.inspect(file);
        } catch (Throwable e) {
            new AlertDialog.Builder(activity, resourcesProvider)
                    .setTitle("Не похоже на .pr-скрипт")
                    .setMessage("В файле нет корректного описания блоков, поэтому установить его нельзя.")
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            return true;
        }

        final Set<String> used = manifest.usedBlockTypeIds();
        final BlockRegistry.CompatibilityResult compat =
                BlockRegistry.checkCompatibility(used, BuildVars.BUILD_VERSION_STRING);
        final PrimeBlockScript existing = PrimeBlocksController.getInstance().findById(manifest.id);

        new PrimeBlockInstallSheet(activity, resourcesProvider, file, manifest, existing, compat, used).show();
        return true;
    }

    private static void install(Activity activity, File file, Runnable onDone, java.util.function.Consumer<String> onError) {
        PrimeBlocksController.getInstance().install(activity, file, new PrimeBlocksController.InstallCallback() {
            @Override
            public void onInstalled(PrimeBlockScript script, boolean replacedExisting) {
                onDone.run();
                final org.telegram.ui.ActionBar.BaseFragment fragment = LaunchActivity.getLastFragment();
                if (fragment == null) {
                    return;
                }
                if (replacedExisting) {
                    BulletinFactory.of(fragment).createSuccessBulletin("Скрипт обновлён").show();
                } else {
                    BulletinFactory.of(fragment).createSimpleBulletin(
                            org.telegram.messenger.R.raw.info, "Скрипт установлен",
                            "Включите его в разделе «Блоки», чтобы он заработал").show();
                }
            }

            @Override
            public void onFailed(String reason) {
                final String message;
                switch (reason) {
                    case "version":
                        message = "Скрипт использует блоки, которых нет в этой версии приложения";
                        break;
                    case "malformed":
                        message = "Файл повреждён или устроен не так, как ожидалось";
                        break;
                    default:
                        message = "Не удалось скопировать файл скрипта";
                        break;
                }
                onError.accept(message);
            }
        });
    }

    private static final class PrimeBlockInstallSheet extends BottomSheet {

        private final Activity activity;
        private final File file;
        private final boolean compatible;
        private final boolean isUpdate;
        private final ButtonWithCounterView button;
        private boolean installing;

        PrimeBlockInstallSheet(Activity activity, Theme.ResourcesProvider resourcesProvider, File file,
                                PrimeBlockManifest manifest, PrimeBlockScript existing,
                                BlockRegistry.CompatibilityResult compat, Set<String> usedTypeIds) {
            super(activity, false, resourcesProvider);
            this.activity = activity;
            this.file = file;
            this.compatible = compat.isCompatible();
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

            // .pr scripts have no sticker-pack icon concept - a plain badge is honest, not a
            // placeholder standing in for something that will exist later.
            final ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageResource(R.drawable.msg_settings);
            icon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
            icon.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(78), getThemedColor(Theme.key_featuredStickers_addButton)));
            final int iconPad = AndroidUtilities.dp(20);
            icon.setPadding(iconPad, iconPad, iconPad, iconPad);
            root.addView(icon, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

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

            // Genuinely enumerable, unlike .plugin's Python source - full transparency about
            // exactly what this script can do, by name.
            if (compatible && !usedTypeIds.isEmpty()) {
                final TextView blocksLine = new TextView(context);
                blocksLine.setGravity(Gravity.LEFT);
                blocksLine.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                blocksLine.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                final java.util.List<String> labels = new java.util.ArrayList<>();
                for (String id : usedTypeIds) {
                    labels.add(BlockRegistry.labelFor(id));
                }
                blocksLine.setText("Использует блоки: " + TextUtils.join(", ", labels));
                root.addView(blocksLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 10, 21, 0));
            }

            final TextView warning = new TextView(context);
            warning.setGravity(Gravity.LEFT);
            warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            warning.setLineSpacing(AndroidUtilities.dp(2), 1f);
            if (compatible) {
                warning.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                warning.setText("Этот скрипт использует только встроенные блоки PrimeGram — " + usedTypeIds.size() +
                        (usedTypeIds.size() == 1 ? " блок" : " блоков") +
                        " всего. Он не может выполнять произвольный код и не может сделать ничего, кроме показанного выше.");
            } else {
                warning.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                final java.util.List<String> missing = new java.util.ArrayList<>();
                for (String id : compat.unknownTypeIds) {
                    missing.add(BlockRegistry.labelFor(id));
                }
                for (String id : compat.tooNewTypeIds) {
                    missing.add(BlockRegistry.labelFor(id));
                }
                final String word = missing.size() == 1 ? "блок" : "блоков";
                warning.setText("Этот скрипт использует " + missing.size() + " " + word +
                        ", которых нет в этой версии PrimeGram: " + TextUtils.join(", ", missing) +
                        ". Обновите приложение, чтобы установить его.");
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
            install(activity, file, () -> {
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

        private static CharSequence buildVersionLine(PrimeBlockScript existing, PrimeBlockManifest manifest) {
            if (existing == null) {
                final String version = manifest.version != null && !manifest.version.isEmpty() ? manifest.version : "?";
                return "Версия " + version;
            }
            final String from = existing.manifest.version != null && !existing.manifest.version.isEmpty() ? existing.manifest.version : "?";
            final String to = manifest.version != null && !manifest.version.isEmpty() ? manifest.version : "?";
            if (from.equals(to)) {
                return "Переустановка версии " + to;
            }
            final SpannableStringBuilder text = new SpannableStringBuilder(from).append(" → ").append(to);
            text.setSpan(new StrikethroughSpan(), 0, from.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }

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
