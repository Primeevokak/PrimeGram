package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * PrimeGram: method hooking for plugins.
 *
 * <p>Plugins written for exteraGram call {@code hook_method} and expect the Xposed API - an
 * interface designed for a rooted phone with a framework injected into every process. None of that
 * is true here. What makes it work anyway is LSPlant, which rewrites entry points of methods
 * <em>inside this process only</em>: the app hooks itself, and nothing outside it is touched.
 *
 * <p>This class exists because the Python side cannot subclass {@link XC_MethodHook} - Chaquopy
 * builds dynamic proxies for interfaces, and that is an abstract class. So the plugin implements
 * {@link Callback}, which is an interface, and the forwarding happens here.
 *
 * <p>Everything is guarded. A device where LSPlant cannot start is a device where hooking is
 * unavailable and the rest of the client works exactly as before; a plugin whose callback throws
 * gets its exception logged rather than passed into the middle of somebody else's method call.
 */
public final class PrimePluginXposed {

    /** What the Python side implements. Both halves are optional - a null does nothing. */
    public interface Callback {
        void before(XC_MethodHook.MethodHookParam param);

        void after(XC_MethodHook.MethodHookParam param);
    }

    private static volatile Boolean available;

    private PrimePluginXposed() {
    }

    /**
     * Whether hooking works on this device.
     *
     * <p>Answered by loading the class, which runs its static initialiser, which is where the
     * native library is loaded. Failure here is a fact about the device - an ART version LSPlant
     * does not know - so it is remembered rather than retried on every call.
     */
    public static boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        synchronized (PrimePluginXposed.class) {
            if (available == null) {
                boolean ok;
                try {
                    Class.forName("de.robv.android.xposed.XposedBridge", true,
                            PrimePluginXposed.class.getClassLoader());
                    ok = true;
                } catch (Throwable t) {
                    FileLog.e("PrimePluginXposed: hooking unavailable", new Exception(t));
                    ok = false;
                }
                available = ok;
            }
            return available;
        }
    }

    public static XC_MethodHook.Unhook hookMethod(Member member, int priority, Callback callback) {
        if (!isAvailable() || member == null || callback == null) {
            return null;
        }
        try {
            return XposedBridge.hookMethod(member, new Forwarder(priority, callback));
        } catch (Throwable t) {
            FileLog.e("PrimePluginXposed.hookMethod " + member, new Exception(t));
            return null;
        }
    }

    public static List<XC_MethodHook.Unhook> hookAllMethods(Class<?> clazz, String methodName,
                                                            int priority, Callback callback) {
        if (!isAvailable() || clazz == null || methodName == null || callback == null) {
            return new ArrayList<>();
        }
        try {
            final Set<XC_MethodHook.Unhook> hooks =
                    XposedBridge.hookAllMethods(clazz, methodName, new Forwarder(priority, callback));
            return new ArrayList<>(hooks);
        } catch (Throwable t) {
            FileLog.e("PrimePluginXposed.hookAllMethods " + clazz + "." + methodName, new Exception(t));
            return new ArrayList<>();
        }
    }

    public static List<XC_MethodHook.Unhook> hookAllConstructors(Class<?> clazz, int priority,
                                                                 Callback callback) {
        if (!isAvailable() || clazz == null || callback == null) {
            return new ArrayList<>();
        }
        try {
            final Set<XC_MethodHook.Unhook> hooks =
                    XposedBridge.hookAllConstructors(clazz, new Forwarder(priority, callback));
            return new ArrayList<>(hooks);
        } catch (Throwable t) {
            FileLog.e("PrimePluginXposed.hookAllConstructors " + clazz, new Exception(t));
            return new ArrayList<>();
        }
    }

    public static void unhook(Object unhook) {
        if (!(unhook instanceof XC_MethodHook.Unhook)) {
            return;
        }
        try {
            ((XC_MethodHook.Unhook) unhook).unhook();
        } catch (Throwable t) {
            FileLog.e("PrimePluginXposed.unhook", new Exception(t));
        }
    }

    /** Calls the original method with the arguments as they now stand. */
    public static Object invokeOriginal(Member member, Object thisObject, Object[] args) throws Throwable {
        return XposedBridge.invokeOriginalMethod(member, thisObject, args);
    }

    private static final class Forwarder extends XC_MethodHook {

        private final Callback callback;

        Forwarder(int priority, Callback callback) {
            super(priority);
            this.callback = callback;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                callback.before(param);
            } catch (Throwable t) {
                // Deliberately swallowed. This runs inside somebody else's method, often on the
                // main thread; letting a plugin's mistake out of here would crash the app with a
                // stack trace pointing at Telegram's code rather than at the plugin.
                FileLog.e("PrimePluginXposed: before hook failed", new Exception(t));
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                callback.after(param);
            } catch (Throwable t) {
                FileLog.e("PrimePluginXposed: after hook failed", new Exception(t));
            }
        }
    }
}
