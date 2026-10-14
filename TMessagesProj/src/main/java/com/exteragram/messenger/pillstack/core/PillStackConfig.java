package com.exteragram.messenger.pillstack.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * exteraGram API, PrimeGram implementation: which registered pills are shown, in what order, and
 * which are hidden. Same preference file name and keys as exteraGram ("pillstackconfig",
 * "activePills", "hiddenPills", "lastActivePillId").
 *
 * <p>{@link #activePills}, {@link #hiddenPills} and {@link #lastActivePillId} are public fields as
 * well as having getters: exteraGram's are Kotlin properties, and plugins reach them both ways -
 * reSwaga reads {@code PillStackConfig.activePills} and calls {@code PillStackConfig.saveLastActivePillId}.
 *
 * <p>The weather/currency settings exist only so code written against exteraGram finds them; no
 * pill here reads them (PrimeGram has none of exteraGram's built-in pills).
 */
public final class PillStackConfig {

    private static final Object sync = new Object();
    private static SharedPreferences preferences;
    private static SharedPreferences.Editor editor;
    private static boolean configLoaded;
    private static final HashSet<Integer> pendingUpdates = new HashSet<>();

    public static List<Integer> activePills = new ArrayList<>();
    public static List<Integer> hiddenPills = new ArrayList<>();
    public static int lastActivePillId = -1;

    private PillStackConfig() {
    }

    public static SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("pillstackconfig", Context.MODE_PRIVATE);
        }
        return preferences;
    }

    public static SharedPreferences.Editor getEditor() {
        if (editor == null) {
            editor = getPreferences().edit();
        }
        return editor;
    }

    public static boolean getConfigLoaded() {
        return configLoaded;
    }

    public static void setConfigLoaded(boolean loaded) {
        configLoaded = loaded;
    }

    public static List<Integer> getActivePills() {
        loadConfig();
        return activePills;
    }

    public static void setActivePills(List<Integer> list) {
        activePills = list != null ? list : new ArrayList<>();
    }

    public static List<Integer> getHiddenPills() {
        loadConfig();
        return hiddenPills;
    }

    public static void setHiddenPills(List<Integer> list) {
        hiddenPills = list != null ? list : new ArrayList<>();
    }

    public static ArrayList<Integer> getDefaultActivePills() {
        return new ArrayList<>();
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }
            final SharedPreferences prefs = getPreferences();
            final String active = prefs.getString("activePills", null);
            final String hidden = prefs.getString("hiddenPills", null);
            if (active != null) {
                activePills = parse(active);
                hiddenPills = hidden != null ? parse(hidden) : new ArrayList<>();
            } else {
                activePills = getDefaultActivePills();
                hiddenPills = new ArrayList<>();
                for (PillRegistry.PillInfo info : PillRegistry.getRegisteredPills()) {
                    if (!activePills.contains(info.id())) {
                        hiddenPills.add(info.id());
                    }
                }
                savePillsLayout();
            }
            lastActivePillId = prefs.getInt("lastActivePillId", -1);
            // Flag first: sanitizePills() would otherwise call back into loadConfig().
            configLoaded = true;
            sanitizePills();
        }
    }

    public static void reloadConfig() {
        synchronized (sync) {
            configLoaded = false;
            loadConfig();
        }
    }

    /** Drops ids nothing has registered and files newly registered ones under hidden. */
    public static void sanitizePills() {
        loadConfig();
        boolean changed = activePills.removeIf(id -> !PillRegistry.isRegistered(id));
        changed |= hiddenPills.removeIf(id -> !PillRegistry.isRegistered(id));
        for (PillRegistry.PillInfo info : PillRegistry.getRegisteredPills()) {
            if (!activePills.contains(info.id()) && !hiddenPills.contains(info.id())) {
                hiddenPills.add(info.id());
                changed = true;
            }
        }
        if (changed) {
            savePillsLayout();
        }
    }

    public static void savePillsLayout() {
        getEditor().putString("activePills", serialize(activePills)).putString("hiddenPills", serialize(hiddenPills)).apply();
    }

    public static int getLastActivePillId() {
        return lastActivePillId;
    }

    public static void setLastActivePillId(int id) {
        lastActivePillId = id;
    }

    public static void saveLastActivePillId(int id) {
        lastActivePillId = id;
        getEditor().putInt("lastActivePillId", id).apply();
    }

    /** Tells the shown pills their settings changed: all of them with no ids, else just those. */
    public static void notifySettingsChanged(int... pillIds) {
        final Object[] args;
        if (pillIds == null || pillIds.length == 0) {
            for (PillRegistry.PillInfo info : PillRegistry.getRegisteredPills()) {
                pendingUpdates.add(info.id());
            }
            args = new Object[0];
        } else {
            args = new Object[pillIds.length];
            for (int i = 0; i < pillIds.length; i++) {
                pendingUpdates.add(pillIds[i]);
                args[i] = pillIds[i];
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationNameOnUIThread(NotificationCenter.pillStackSettingsChanged, args);
    }

    public static boolean checkAndClearPendingUpdate(int pillId) {
        return pendingUpdates.remove(pillId);
    }

    /** Whether a pillStackSettingsChanged with {@code args} concerns any of {@code pillIds}. */
    public static boolean shouldUpdatePill(Object[] args, int... pillIds) {
        if (args == null || args.length == 0 || pillIds == null || pillIds.length == 0) {
            return true;
        }
        for (Object arg : args) {
            if (arg instanceof Integer) {
                for (int id : pillIds) {
                    if (id == (Integer) arg) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ArrayList<Integer> parse(String data) {
        final ArrayList<Integer> list = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return list;
        }
        final String cleaned = data.startsWith("[") ? data.replaceAll("[\\[\\]\"]", "") : data;
        for (String part : cleaned.split(",")) {
            try {
                list.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignore) {
            }
        }
        return list;
    }

    private static String serialize(List<Integer> list) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    // ---- exteraGram's built-in pill settings: present for API compatibility only ----

    private static boolean getBool(String key, boolean def) {
        return getPreferences().getBoolean(key, def);
    }

    private static String getStr(String key, String def) {
        return getPreferences().getString(key, def);
    }

    public static boolean getUseCurrentLocation() {
        return getBool("useCurrentLocation", true);
    }

    public static void setUseCurrentLocation(boolean value) {
        getEditor().putBoolean("useCurrentLocation", value).apply();
    }

    public static String getCustomWeatherLocation() {
        return getStr("customWeatherLocation", null);
    }

    public static void setCustomWeatherLocation(String value) {
        getEditor().putString("customWeatherLocation", value).apply();
    }

    public static String getCustomWeatherAddress() {
        return getStr("customWeatherAddress", null);
    }

    public static void setCustomWeatherAddress(String value) {
        getEditor().putString("customWeatherAddress", value).apply();
    }

    public static boolean getInfiniteScrolling() {
        return getBool("infiniteScrolling", true);
    }

    public static void setInfiniteScrolling(boolean value) {
        getEditor().putBoolean("infiniteScrolling", value).apply();
    }

    public static String getGramTargetCurrency() {
        return getStr("gramTargetCurrency", "AUTO");
    }

    public static void setGramTargetCurrency(String value) {
        getEditor().putString("gramTargetCurrency", value).apply();
    }

    public static String getBtcTargetCurrency() {
        return getStr("btcTargetCurrency", "AUTO");
    }

    public static void setBtcTargetCurrency(String value) {
        getEditor().putString("btcTargetCurrency", value).apply();
    }

    public static String getUsdTargetCurrency() {
        return getStr("usdTargetCurrency", "AUTO");
    }

    public static void setUsdTargetCurrency(String value) {
        getEditor().putString("usdTargetCurrency", value).apply();
    }
}
