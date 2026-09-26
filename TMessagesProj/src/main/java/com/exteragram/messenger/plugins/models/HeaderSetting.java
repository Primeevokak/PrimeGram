package com.exteragram.messenger.plugins.models;

/**
 * PrimeGram: exteraGram's header setting model. Plugins that insert their own row into a
 * settings list mark it with one of these through {@code UItem.settingItem}, so the screen can
 * tell their row from its own; the text is drawn by the plugin's view, this only carries it.
 */
public class HeaderSetting {

    private String text;

    public HeaderSetting(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return "header";
    }
}
