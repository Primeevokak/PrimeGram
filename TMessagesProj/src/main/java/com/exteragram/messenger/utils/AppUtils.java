package com.exteragram.messenger.utils;

import android.graphics.Color;

import androidx.annotation.Keep;

import com.google.gson.Gson;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Calendar;

/**
 * PrimeGram: compatibility shim for exteraGram plugins that import
 * {@code com.exteragram.messenger.utils.AppUtils} directly rather than going through the
 * documented Python SDK. Real implementations, not stubs that throw - this class's contract
 * is small and every method here does what its exteraGram counterpart does.
 */
public final class AppUtils {

    private static Gson gson;

    private AppUtils() {
    }

    public static int compareVersionValues(String v1, String v2) {
        if (v1 == null) v1 = "";
        if (v2 == null) v2 = "";
        final String[] a = v1.split("\\.");
        final String[] b = v2.split("\\.");
        final int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? parsePart(a[i]) : 0;
            int y = i < b.length ? parsePart(b[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            final StringBuilder digits = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (Character.isDigit(c)) digits.append(c);
                else break;
            }
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean compareVersions(String operator, int a, int b) {
        return applyOperator(operator, Integer.compare(a, b));
    }

    public static boolean compareVersions(String operator, String v1, String v2) {
        return applyOperator(operator, compareVersionValues(v1, v2));
    }

    private static boolean applyOperator(String operator, int cmp) {
        if (operator == null) return false;
        switch (operator) {
            case "<": return cmp < 0;
            case ">": return cmp > 0;
            case "<=": return cmp <= 0;
            case ">=": return cmp >= 0;
            case "==": return cmp == 0;
            default: return false;
        }
    }

    public static void ensureRunningOnUi(Runnable r) {
        if (r == null) return;
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    public static Gson getGson() {
        if (gson == null) {
            gson = new Gson();
        }
        return gson;
    }

    public static int getNotificationColor() {
        return 0xff4a76a8;
    }

    @Keep
    public static Object getPrivateField(Object obj, String fieldName) {
        if (obj == null || fieldName == null) return null;
        try {
            Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Throwable t) {
            FileLog.e("AppUtils.getPrivateField", t);
            return null;
        }
    }

    @Keep
    public static Object getPrivateStaticField(Class<?> cls, String fieldName) {
        if (cls == null || fieldName == null) return null;
        try {
            Field field = findField(cls, fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable t) {
            FileLog.e("AppUtils.getPrivateStaticField", t);
            return null;
        }
    }

    @Keep
    public static void setPrivateField(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) return;
        try {
            Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Throwable t) {
            FileLog.e("AppUtils.setPrivateField", t);
        }
    }

    @Keep
    public static void setPrivateStaticField(Class<?> cls, String fieldName, Object value) {
        if (cls == null || fieldName == null) return;
        try {
            Field field = findField(cls, fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Throwable t) {
            FileLog.e("AppUtils.setPrivateStaticField", t);
        }
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> current = cls;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static int getSwipeVelocity() {
        return 4000;
    }

    public static String getVersionText() {
        return BuildVars.BUILD_VERSION_STRING;
    }

    public static boolean isAppModified() {
        return true;
    }

    public static boolean isWinter() {
        final int month = Calendar.getInstance().get(Calendar.MONTH);
        return month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY;
    }

    @Keep
    public static void log(String msg) {
        FileLog.d(String.valueOf(msg));
    }

    @Keep
    public static void log(String msg, Throwable t) {
        FileLog.e(String.valueOf(msg), t);
    }

    @Keep
    public static void log(Throwable t) {
        FileLog.e(t);
    }

    @Keep
    public static void printObjectDetails(Object obj) {
        if (obj == null) {
            FileLog.d("AppUtils.printObjectDetails: null");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(obj.getClass().getName()).append(" {\n");
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    sb.append("  ").append(field.getName()).append(" = ").append(field.get(obj)).append("\n");
                } catch (Throwable ignore) {
                }
            }
            current = current.getSuperclass();
        }
        sb.append("}");
        FileLog.d(sb.toString());
    }

    public static String stackTraceToString(Throwable t) {
        if (t == null) return "";
        final StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
