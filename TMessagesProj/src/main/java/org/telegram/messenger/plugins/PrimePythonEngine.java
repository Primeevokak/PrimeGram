package org.telegram.messenger.plugins;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

/**
 * PrimeGram: the one place the Python interpreter is started, and the only door into it.
 *
 * <p>Everything a plugin does happens on {@link #queue()}, a thread of its own. Not for speed - a
 * plugin is mostly idle - but because there is exactly one interpreter and a global lock around it:
 * two threads calling into Python take turns whether we plan for it or not, and a plugin that
 * decides to sleep for a second would take the UI thread with it if it were allowed to run there.
 * One queue makes that impossible rather than unlikely.
 *
 * <p>Starting is lazy. A user with no plugins installed should never pay for the interpreter, and
 * on this fork that is most users - so nothing here runs until something asks for a module.
 */
public final class PrimePythonEngine {

    private static volatile PrimePythonEngine instance;

    private final DispatchQueue queue = new DispatchQueue("primePlugins");
    private volatile boolean started;
    private volatile Throwable startError;

    private PrimePythonEngine() {
    }

    public static PrimePythonEngine getInstance() {
        PrimePythonEngine local = instance;
        if (local == null) {
            synchronized (PrimePythonEngine.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimePythonEngine();
                }
            }
        }
        return local;
    }

    /** The thread every call into Python must happen on. */
    public DispatchQueue queue() {
        return queue;
    }

    public boolean isStarted() {
        return started;
    }

    /** Why the interpreter refused to start, or null. Worth showing: it is never the user's doing. */
    public Throwable startError() {
        return startError;
    }

    /**
     * Starts the interpreter if it is not already running, and returns whether it is usable.
     *
     * <p>Must be called from {@link #queue()}. A failure is remembered rather than retried: if the
     * runtime is missing from this build, it will still be missing in a second, and retrying once
     * per plugin would turn one problem into a list of identical ones.
     */
    public boolean ensureStarted(Context context) {
        if (started) {
            return true;
        }
        if (startError != null) {
            return false;
        }
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(context.getApplicationContext()));
            }
            started = true;
            return true;
        } catch (Throwable e) {
            startError = e;
            FileLog.e(e);
            return false;
        }
    }

    /**
     * A module from our SDK or from a plugin. Null when the interpreter is not up - callers are
     * expected to check, because "no plugins" is a normal state of this app, not an error.
     */
    public PyObject module(String name) {
        if (!started) {
            return null;
        }
        try {
            return Python.getInstance().getModule(name);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Runs work on the plugin thread, starting the interpreter first if it is needed. */
    public void post(Context context, Runnable action) {
        queue.postRunnable(() -> {
            if (!ensureStarted(context)) {
                return;
            }
            try {
                action.run();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }
}
