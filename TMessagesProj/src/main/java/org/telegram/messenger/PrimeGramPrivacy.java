package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * PrimeGram: display-only privacy helpers.
 *
 * <p>Nothing here changes what is sent to the server or stored — it only affects what the
 * local UI renders, so screenshots can be shared without leaking the account's own phone
 * number. The real value stays intact everywhere it actually matters (calls, contacts).
 */
public class PrimeGramPrivacy {

    private static final String KEY_HIDE_PHONE = "primegram_hide_own_phone";
    private static final String KEY_FAKE_PHONE = "primegram_fake_phone";

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean isHidePhoneEnabled() {
        return prefs().getBoolean(KEY_HIDE_PHONE, false);
    }

    public static void setHidePhoneEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_HIDE_PHONE, enabled).apply();
    }

    /** Custom text shown instead of the real number. Empty means "mask the real one". */
    public static String getFakePhone() {
        return prefs().getString(KEY_FAKE_PHONE, "");
    }

    public static void setFakePhone(String value) {
        prefs().edit().putString(KEY_FAKE_PHONE, value == null ? "" : value.trim()).apply();
    }

    /**
     * @param formattedPhone the number as it would normally be displayed
     * @param isOwnProfile   only the account's own number is masked; other people's profiles
     *                       are left alone, since hiding those would just be confusing
     * @return what should actually be drawn
     */
    public static String maskPhoneForDisplay(String formattedPhone, boolean isOwnProfile) {
        if (!isOwnProfile || !isHidePhoneEnabled() || TextUtils.isEmpty(formattedPhone)) {
            return formattedPhone;
        }
        String custom = getFakePhone();
        if (!TextUtils.isEmpty(custom)) {
            return custom;
        }
        return maskDigits(formattedPhone);
    }

    /** Keeps the country code and the shape of the number, hides the identifying digits. */
    private static String maskDigits(String formatted) {
        StringBuilder sb = new StringBuilder(formatted.length());
        int digitsSeen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (Character.isDigit(c)) {
                digitsSeen++;
                // Leave the leading country-code digits readable, hide the rest.
                sb.append(digitsSeen <= 2 ? c : '•');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
