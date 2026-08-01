package org.telegram.messenger.plugins;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

/**
 * PrimeGram: the app's side of every hook a plugin can be on.
 *
 * <p>The whole design of this class is the {@code static volatile boolean} at the top. A hook point
 * sits on a hot path - {@code sendMessage} runs for every message the user sends - and the cost of
 * having plugins at all must be one field read for users who have none. Only when a plugin has
 * actually subscribed does anything here touch Python.
 *
 * <p>Dispatch is synchronous, on the calling thread, deliberately: the caller is asking whether to
 * send this message, and there is no useful answer to that question later. Plugins therefore run on
 * whatever thread the app was on, which is the one place the engine's own queue does not apply.
 */
public final class PrimePluginHooks {

    /** Set from Python whenever a plugin subscribes to or leaves the send path. */
    private static volatile boolean sendMessageHooks;
    /** Same idea for the other two paths: no plugins claiming them, no work at all. */
    private static volatile boolean fileHooks;
    private static volatile boolean intentHooks;

    private PrimePluginHooks() {
    }

    public static void setSendMessageHooks(boolean value) {
        sendMessageHooks = value;
    }

    public static void setFileHooks(boolean value) {
        fileHooks = value;
    }

    public static void setIntentHooks(boolean value) {
        intentHooks = value;
    }

    /**
     * Offers a file the user tapped to the plugins. Returns true when one of them took it.
     *
     * <p>Called from the single place the app opens a document from, so a plugin claiming an
     * extension gets that file wherever it was tapped - a chat, shared media, downloads - without
     * every one of those screens having to know plugins exist.
     */
    public static boolean onFileOpen(java.io.File file, String fileName, Object message,
                                     Object activity, String place) {
        if (!fileHooks || file == null) {
            return false;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return false;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader == null) {
                return false;
            }
            final PyObject result = loader.callAttr("dispatch_file_open",
                    file.getAbsolutePath(), fileName, message, activity, place);
            return result != null && result.toBoolean();
        } catch (Throwable e) {
            // The file still opens the ordinary way; a broken plugin should not make a document
            // unopenable.
            FileLog.e(e);
            return false;
        }
    }

    /**
     * Offers an incoming intent to the plugins. Returns true when one of them handled it.
     *
     * @param after false before the app looks at the intent, true after it has
     */
    public static boolean onIntent(Object intent, boolean after) {
        if (!intentHooks || intent == null) {
            return false;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return false;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader == null) {
                return false;
            }
            final PyObject result = loader.callAttr("dispatch_intent", intent, after);
            return result != null && result.toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /**
     * Offers an outgoing message to the plugins. Returns true when one of them cancelled it.
     *
     * <p>A plugin that edits the params has edited the object that will be sent - there is no copy.
     * That is what {@code MODIFY} means in the SDK, and it is why the params reach Python at all.
     */
    public static boolean onSendMessage(int account, Object params) {
        if (!sendMessageHooks) {
            return false;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return false;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader == null) {
                return false;
            }
            final PyObject result = loader.callAttr("dispatch_send_message", account, params);
            return result != null && result.toBoolean();
        } catch (Throwable e) {
            // A plugin that throws must not stop the user's message. It has already been logged on
            // the Python side; here the only sane answer is to carry on sending.
            FileLog.e(e);
            return false;
        }
    }
}
