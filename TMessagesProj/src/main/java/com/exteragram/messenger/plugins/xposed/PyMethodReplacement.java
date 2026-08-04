package com.exteragram.messenger.plugins.xposed;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;

/**
 * PrimeGram: compatibility shim for
 * {@code com.exteragram.messenger.plugins.xposed.PyMethodReplacement} - see
 * {@link PyMethodHook} for the reasoning; this is the same idea for a hook that replaces the
 * original method entirely instead of running alongside it.
 */
public class PyMethodReplacement extends XC_MethodReplacement implements AutoCloseable {

    private static final String REPLACE = "replace_hooked_method";

    private final String pluginId;
    private final PyObject pythonCallback;

    public PyMethodReplacement(String pluginId, PyObject pythonCallback) {
        this(pluginId, pythonCallback, PRIORITY_DEFAULT);
    }

    public PyMethodReplacement(String pluginId, PyObject pythonCallback, int priority) {
        super(priority);
        if (pythonCallback == null) {
            throw new IllegalArgumentException("pythonCallback must not be null");
        }
        this.pluginId = pluginId;
        this.pythonCallback = pythonCallback;
    }

    @Override
    protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
        try {
            return pythonCallback.callAttr(REPLACE, param);
        } catch (Throwable t) {
            FileLog.e("PyMethodReplacement(" + pluginId + ") failed", t);
            throw t;
        }
    }

    @Override
    public void close() {
    }
}
