package com.exteragram.messenger.plugins.ui.components;

import android.view.View;

/** PrimeGram: exteraGram's callbacks for a plugin row's buttons - see {@link PluginCell}. */
public interface PluginCellDelegate {

    void sharePlugin();

    void openInExternalApp();

    void deletePlugin();

    void togglePlugin(View view);

    void openPluginSettings();

    void pinPlugin(View view);

    boolean canOpenInExternalApp();
}
