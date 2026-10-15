package com.exteragram.messenger.plugins.models;

import com.chaquo.python.PyObject;

/**
 * exteraGram API: a free-text setting row - the model behind {@code PluginEditTextCell}. Same
 * constructor and accessors as exteraGram's Kotlin class, so a plugin building one by hand
 * (reSwaga: {@code EditTextSetting(key, hint, default, multiline, max_length, mask, on_change)})
 * gets the same object here.
 *
 * <p>{@code mask} is a regular expression the whole text must match; an edit that would break it is
 * rejected. {@code maxLength} of 0 or less means no limit.
 */
public final class EditTextSetting {

    private String key;
    private String hint;
    private String defaultValue;
    private boolean multiline;
    private int maxLength;
    private String mask;
    private PyObject onChangeCallback;

    public EditTextSetting(String key, String hint, String defaultValue, boolean multiline, int maxLength, String mask, PyObject onChangeCallback) {
        this.key = key;
        this.hint = hint;
        this.defaultValue = defaultValue;
        this.multiline = multiline;
        this.maxLength = maxLength;
        this.mask = mask;
        this.onChangeCallback = onChangeCallback;
    }

    public String getType() {
        return "edit_text";
    }

    public void cleanup() {
        if (onChangeCallback != null) {
            try {
                onChangeCallback.close();
            } catch (Throwable ignore) {
            }
        }
        onChangeCallback = null;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean getMultiline() {
        return multiline;
    }

    public void setMultiline(boolean multiline) {
        this.multiline = multiline;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public PyObject getOnChangeCallback() {
        return onChangeCallback;
    }

    public void setOnChangeCallback(PyObject onChangeCallback) {
        this.onChangeCallback = onChangeCallback;
    }
}
