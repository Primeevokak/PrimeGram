package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;

/**
 * PrimeGram: installs the native half of a hook before Aliuhook's own
 * {@code XposedBridge.hookMethod} gets to it, so a method is never live without its backup.
 *
 * <p>Aliuhook 1.1.4 does it in this order (checked against the bytecode): build a HookInfo,
 * {@code hook0} - from which point every call to the method is routed to HookInfo.callback -
 * then store the backup, then publish the record, and only after leaving the lock add the
 * plugin's callback. A call arriving from another thread between {@code hook0} and the backup
 * being stored finds {@code backup == null} and crashes the client with an NPE. Hooks go in while
 * the app is running, often on methods the UI and network threads call constantly, so that window
 * is hit in practice.
 *
 * <p>Here the record is created first with a {@link Hold} callback in it, published, and only
 * then hooked natively; a thread that arrives early waits in the Hold until the backup is set.
 * Aliuhook's hookMethod afterwards finds the ready record and just adds its callback. If the
 * internals ever change shape this switches itself off and hooking behaves exactly as before.
 */
final class PrimeHookPreinstall {

    /** How long an early caller may wait. Installation takes microseconds; this only bounds the
     *  damage if something unexpected happens between the two steps. */
    private static final long HOLD_TIMEOUT_MS = 1500;

    private static Boolean ready;
    private static Map<Member, Object> records;
    private static Constructor<?> newInfo;
    private static Field infoBackup;
    private static Field infoCallbacks;
    private static Method setAdd;
    private static Method setRemove;
    private static Method nativeHook;
    private static Method dispatchMethod;

    private PrimeHookPreinstall() {
    }

    @SuppressWarnings("unchecked")
    private static synchronized boolean bind() {
        if (ready != null) {
            return ready;
        }
        boolean ok = false;
        try {
            final Class<?> bridge = Class.forName("de.robv.android.xposed.XposedBridge");
            final Class<?> info = Class.forName("de.robv.android.xposed.XposedBridge$HookInfo");
            final Class<?> sortedSet = Class.forName("de.robv.android.xposed.XposedBridge$CopyOnWriteSortedSet");

            final Field recordsField = bridge.getDeclaredField("hookRecords");
            recordsField.setAccessible(true);
            records = (Map<Member, Object>) recordsField.get(null);

            final Field dispatch = bridge.getDeclaredField("callbackMethod");
            dispatch.setAccessible(true);
            dispatchMethod = (Method) dispatch.get(null);

            nativeHook = bridge.getDeclaredMethod("hook0", Object.class, Member.class, Method.class);
            nativeHook.setAccessible(true);

            newInfo = info.getDeclaredConstructor(Member.class);
            newInfo.setAccessible(true);
            infoBackup = info.getDeclaredField("backup");
            infoBackup.setAccessible(true);
            infoCallbacks = info.getDeclaredField("callbacks");
            infoCallbacks.setAccessible(true);

            setAdd = sortedSet.getDeclaredMethod("add", Object.class);
            setAdd.setAccessible(true);
            setRemove = sortedSet.getDeclaredMethod("remove", Object.class);
            setRemove.setAccessible(true);

            ok = records != null && dispatchMethod != null;
        } catch (Throwable t) {
            FileLog.e("PrimeHookPreinstall: Aliuhook layout not recognised, early-call guard off", new Exception(t));
        }
        ready = ok;
        return ok;
    }

    /** Makes {@code member} hooked natively with its backup in place. No-op if it already is. */
    static void prepare(Member member) {
        if (!(member instanceof Method) && !(member instanceof Constructor)) {
            return;
        }
        if (Modifier.isAbstract(member.getModifiers()) || !bind()) {
            return;
        }
        try {
            // Aliuhook takes this same lock around its own lookup, so the two can't interleave.
            synchronized (records) {
                if (records.get(member) != null) {
                    return;
                }
                final Object info = newInfo.newInstance(member);
                final Object callbacks = infoCallbacks.get(info);
                final Hold hold = new Hold();
                setAdd.invoke(callbacks, hold);
                records.put(member, info);
                Object backup = null;
                try {
                    backup = nativeHook.invoke(null, info, member, dispatchMethod);
                } finally {
                    if (backup != null) {
                        infoBackup.set(info, backup);
                    } else {
                        // Not hookable: leave no half-made record for hookMethod to trip over.
                        records.remove(member);
                    }
                    hold.release();
                    try {
                        setRemove.invoke(callbacks, hold);
                    } catch (Throwable ignore) {
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e("PrimeHookPreinstall.prepare " + member, new Exception(t));
        }
    }

    static void prepareMethods(Class<?> clazz, String name) {
        if (clazz == null || name == null) {
            return;
        }
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (name.equals(method.getName())) {
                    prepare(method);
                }
            }
        } catch (Throwable t) {
            FileLog.e("PrimeHookPreinstall.prepareMethods " + clazz + "." + name, new Exception(t));
        }
    }

    static void prepareConstructors(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        try {
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                prepare(constructor);
            }
        } catch (Throwable t) {
            FileLog.e("PrimeHookPreinstall.prepareConstructors " + clazz, new Exception(t));
        }
    }

    /** Parks a caller that reached the hook before its backup was stored. */
    private static final class Hold extends XC_MethodHook {
        private final CountDownLatch open = new CountDownLatch(1);

        Hold() {
            super(PRIORITY_HIGHEST);
        }

        void release() {
            open.countDown();
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                open.await(HOLD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
