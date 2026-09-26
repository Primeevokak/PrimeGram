package com.exteragram.messenger.plugins.ui.components;

import android.app.Activity;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PrimePluginInstallDialog;

import java.io.File;

/**
 * PrimeGram: exteraGram's plugin install sheet, as far as plugins can see it. Every install that
 * goes through the exteraGram API ({@code PluginsController.showInstallDialog}) is constructed
 * here with the same three-argument constructor exteraGram has, so a plugin that hooks that
 * constructor to vet or redirect installs keeps working; {@link #show()} then opens PrimeGram's
 * own install sheet for the file.
 */
public final class InstallPluginBottomSheet {

    public static final class PluginInstallParams {
        private final String filePath;
        private boolean trusted;

        public PluginInstallParams(String filePath, boolean trusted) {
            this.filePath = filePath;
            this.trusted = trusted;
        }

        public static PluginInstallParams of(MessageObject messageObject) {
            if (messageObject == null || messageObject.messageOwner == null) {
                return new PluginInstallParams(null, false);
            }
            final File file = FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner);
            return new PluginInstallParams(file == null ? null : file.getAbsolutePath(), false);
        }

        public String getFilePath() {
            return filePath;
        }

        public boolean getTrusted() {
            return trusted;
        }

        public void setTrusted(boolean value) {
            trusted = value;
        }

        public File toFile() {
            return filePath == null ? null : new File(filePath);
        }
    }

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final PluginInstallParams params;

    public InstallPluginBottomSheet(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, PluginInstallParams params) {
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        this.params = params;
    }

    public PluginInstallParams getParams() {
        return params;
    }

    public void show() {
        final File file = params == null ? null : params.toFile();
        if (file == null) {
            return;
        }
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            final BaseFragment last = LaunchActivity.getLastFragment();
            activity = last == null ? null : last.getParentActivity();
        }
        if (activity == null || !PrimePluginInstallDialog.offer(activity, file, resourcesProvider)) {
            FileLog.d("InstallPluginBottomSheet: not a plugin or no activity: " + file);
        }
    }
}
