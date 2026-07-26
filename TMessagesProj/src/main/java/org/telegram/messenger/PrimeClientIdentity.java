package org.telegram.messenger;

import android.text.TextUtils;

/**
 * PrimeGram: what this client calls itself in the "Active sessions" list.
 *
 * <p>Two fields make up a session row and only one of them is ours. The line under the title
 * is {@code app_name + app_version}, which Telegram fills from the registration behind the
 * api_id — for this build that reads "Telegram Web", and no value sent at connection time can
 * change it. The title itself is {@code device_model}, which the client does send.
 *
 * <p>Because the api_id is registered as a web client, the server treats {@code device_model}
 * as a browser name and stores "Unknown Browser" whenever it cannot recognise one — which is
 * what an Android build sending "Samsung SM-G991B" gets. Sending an actual browser name fixes
 * the row and also picks up the matching icon, since session lists select it by substring
 * ({@code chrome}, {@code safari}, {@code firefox}, {@code edge}, {@code opera}).
 *
 * <p>The stored value is used verbatim, so whatever the user types is exactly what appears.
 */
public class PrimeClientIdentity {

    private static final String KEY_CUSTOM = "primegram_session_name";
    /**
     * Recognised as a browser by both the server and the session list's icon matcher, while
     * still naming the client for anyone reviewing their own sessions.
     */
    private static final String DEFAULT_NAME = "Chrome · PrimeGram";
    /** Session titles render on one line; longer strings are simply cut off. */
    private static final int MAX_LENGTH = 64;

    /**
     * @param rawDeviceModel manufacturer + model, as Android reports it
     * @return the string to send as {@code device_model}
     */
    public static String decorateDeviceModel(String rawDeviceModel) {
        try {
            String name = getSessionName();
            if (TextUtils.isEmpty(name)) {
                return rawDeviceModel; // user cleared it: report the real device
            }
            return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
        } catch (Throwable t) {
            // Runs while ConnectionsManager is being constructed, before most of the app
            // exists. Anything unexpected here must fall back, never propagate.
            return rawDeviceModel;
        }
    }

    /** Empty means "send the real device model instead". */
    public static String getSessionName() {
        try {
            return ApplicationLoader.applicationContext
                    .getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE)
                    .getString(KEY_CUSTOM, DEFAULT_NAME);
        } catch (Throwable t) {
            return DEFAULT_NAME;
        }
    }

    public static void setSessionName(String name) {
        try {
            ApplicationLoader.applicationContext
                    .getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CUSTOM, name == null ? "" : name.trim())
                    .apply();
        } catch (Throwable ignore) {}
    }

    public static String getDefaultName() {
        return DEFAULT_NAME;
    }
}
