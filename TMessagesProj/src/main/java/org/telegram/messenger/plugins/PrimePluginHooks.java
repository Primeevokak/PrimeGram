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

    private PrimePluginHooks() {
    }

    public static void setSendMessageHooks(boolean value) {
        sendMessageHooks = value;
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
