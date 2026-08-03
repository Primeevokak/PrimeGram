package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.ui.Components.TranslateAlert2;

/**
 * PrimeGram: "translate before you send" - the mirror of the stock incoming-message translate,
 * for your own outgoing text. Ported from exteraGram's idea, but per-chat rather than global:
 * one contact who doesn't share your language shouldn't force every other chat through a
 * translator too, so both the on/off flag and the target language are keyed by dialogId, falling
 * back to the same "translate to" language the rest of the app already uses when a chat hasn't
 * picked one of its own.
 */
public class PrimeSendTranslate {

    /** Common enough to list directly; anything else the user still gets via the default. */
    public static final String[] LANGUAGE_CODES = {
            "ru", "en", "es", "de", "fr", "pt", "it", "tr", "ar", "zh", "ja", "ko", "uk", "pl", "kk"
    };
    public static final String[] LANGUAGE_NAMES = {
            "Русский", "English", "Español", "Deutsch", "Français", "Português", "Italiano",
            "Türkçe", "العربية", "中文", "日本語", "한국어", "Українська", "Polski", "Қазақша"
    };

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean isEnabled(long dialogId) {
        return prefs().getBoolean("primegram_send_translate_" + dialogId, false);
    }

    public static void setEnabled(long dialogId, boolean enabled) {
        prefs().edit().putBoolean("primegram_send_translate_" + dialogId, enabled).apply();
    }

    /** The chat's own choice if it made one, otherwise the same target language incoming
     *  translation already defaults to - so turning this on for the first time in a chat doesn't
     *  require picking a language too. */
    public static String getLanguage(long dialogId) {
        String lang = prefs().getString("primegram_send_translate_lang_" + dialogId, null);
        if (TextUtils.isEmpty(lang)) {
            lang = TranslateAlert2.getToLanguage();
        }
        return TextUtils.isEmpty(lang) ? "en" : lang;
    }

    public static void setLanguage(long dialogId, String langCode) {
        prefs().edit().putString("primegram_send_translate_lang_" + dialogId, langCode).apply();
    }

    public static String getLanguageName(String code) {
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(code)) {
                return LANGUAGE_NAMES[i];
            }
        }
        return code;
    }
}
