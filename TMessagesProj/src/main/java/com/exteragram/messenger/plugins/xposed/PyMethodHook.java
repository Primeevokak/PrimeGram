package com.exteragram.messenger.plugins.xposed;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;

/**
 * PrimeGram: compatibility shim for
 * {@code com.exteragram.messenger.plugins.xposed.PyMethodHook} - a plugin that builds one of
 * these directly (rather than through PrimeGram's own {@code PrimePluginXposed}) still gets real
 * hooking, on the same Aliuhook/LSPlant-backed {@link XC_MethodHook} PrimeGram already depends
 * on for its own hook path.
 *
 * <p>{@code pythonCallback} is called by attribute name - {@code before_hooked_method} /
 * {@code after_hooked_method} - matching the method names on PrimeGram's own SDK
 * {@code base_plugin.MethodHook} ABC, since that is what a plugin importing this class directly
 * is realistically passing (an object implementing that shape). A missing attribute is not an
 * error: a hook that only wants "before" simply never had "after" to call.
 */
public class PyMethodHook extends XC_MethodHook implements AutoCloseable {

    private static final String BEFORE = "before_hooked_method";
    private static final String AFTER = "after_hooked_method";

    private final String pluginId;
    private final PyObject pythonCallback;
    private ArrayList<Object> beforeHookedFilters;
    private ArrayList<Object> afterHookedFilters;

    public PyMethodHook(String pluginId, PyObject pythonCallback) {
        this(pluginId, pythonCallback, PRIORITY_DEFAULT, true, true);
    }

    public PyMethodHook(String pluginId, PyObject pythonCallback, int priority) {
        this(pluginId, pythonCallback, priority, true, true);
    }

    public PyMethodHook(String pluginId, PyObject pythonCallback, boolean hasBeforeHook, boolean hasAfterHook) {
        this(pluginId, pythonCallback, PRIORITY_DEFAULT, hasBeforeHook, hasAfterHook);
    }

    public PyMethodHook(String pluginId, PyObject pythonCallback, int priority, boolean hasBeforeHook, boolean hasAfterHook) {
        super(priority);
        if (pythonCallback == null) {
            throw new IllegalArgumentException("pythonCallback must not be null");
        }
        this.pluginId = pluginId;
        this.pythonCallback = pythonCallback;
    }

    public ArrayList<Object> getBeforeHookedFilters() {
        return beforeHookedFilters;
    }

    public void setBeforeHookedFilters(ArrayList<Object> v) {
        beforeHookedFilters = v;
    }

    public ArrayList<Object> getAfterHookedFilters() {
        return afterHookedFilters;
    }

    public void setAfterHookedFilters(ArrayList<Object> v) {
        afterHookedFilters = v;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        call(BEFORE, param);
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        call(AFTER, param);
    }

    private void call(String attr, MethodHookParam param) {
        try {
            pythonCallback.callAttr(attr, param);
        } catch (Throwable t) {
            // Either the callback has no such attribute (this hook only wants the other half) or
            // the plugin's own code threw. Either way this runs inside somebody else's method
            // call, often the main thread - the exception must not propagate.
            FileLog.e("PyMethodHook(" + pluginId + ")." + attr + " failed", t);
        }
    }

    @Override
    public void close() {
    }
}
