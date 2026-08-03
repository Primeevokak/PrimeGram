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

    public static void setMenuItems(String json) {
        PrimePluginMenuItems.setItems(json);
    }

    /** START/STOP/PAUSE/RESUME, for a plugin's {@code on_app_event}. Fire-and-forget, same as a
     *  menu click - nothing in the app is waiting on a plugin's reaction to its own lifecycle. */
    public static void onAppEvent(String eventName) {
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return;
        }
        engine.queue().postRunnable(() -> {
            try {
                final PyObject loader = engine.module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("dispatch_app_event", eventName);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    /** Tells the plugin its menu item was picked. Fire-and-forget, same as any other click - there
     *  is no answer for the app to wait on. */
    public static void onMenuItemClick(String itemId, java.util.Map<String, Object> context) {
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return;
        }
        engine.queue().postRunnable(() -> {
            try {
                final PyObject loader = engine.module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("dispatch_menu_click", itemId, context);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    /** Requests and updates share a flag: a plugin interested in one is usually interested in both. */
    private static volatile boolean requestHooks;

    public static void setRequestHooks(boolean value) {
        requestHooks = value;
    }

    public static boolean hasRequestHooks() {
        return requestHooks;
    }

    /** How many plugins are actually running right now - not how many are installed, which
     *  {@link PrimePluginsController#count()} answers without needing the interpreter at all, but
     *  how many made it through {@code load_plugin} and are still up. Pushed from Python, because
     *  only Python knows when a plugin has failed or been unloaded; read synchronously, because a
     *  settings row cannot wait on a round trip through the plugin queue to draw itself. */
    private static volatile int activeCount;

    public static void setActiveCount(int value) {
        activeCount = value;
    }

    public static int activeCount() {
        return activeCount;
    }

    /**
     * Offers an outgoing request to the plugins, before it is serialised.
     *
     * @return the request to send - the same object when nobody touched it, a different one when a
     *         plugin replaced it, or null when a plugin cancelled the whole thing
     */
    public static Object onPreRequest(int account, Object request) {
        if (!requestHooks || request == null) {
            return request;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return request;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader == null) {
                return request;
            }
            final PyObject result = loader.callAttr("dispatch_pre_request", account, request);
            if (result == null || result.toJava(Object.class) == null) {
                return null;
            }
            return result.toJava(Object.class);
        } catch (Throwable e) {
            // The request goes out unchanged. A plugin must not be able to stop the client from
            // talking to the server by throwing.
            FileLog.e(e);
            return request;
        }
    }

    /**
     * Offers a response to the plugins before the caller sees it.
     *
     * @return the response to deliver, which is usually the one that came in
     */
    public static Object onPostRequest(int account, Object request, Object response, Object error) {
        if (!requestHooks) {
            return response;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return response;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader == null) {
                return response;
            }
            final PyObject result = loader.callAttr("dispatch_post_request", account, request, response, error);
            return result == null ? response : result.toJava(Object.class);
        } catch (Throwable e) {
            FileLog.e(e);
            return response;
        }
    }

    /** Offers a container of updates, and every update inside it, to the plugins. */
    public static void onUpdates(int account, Object updates, boolean container) {
        if (!requestHooks || updates == null) {
            return;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return;
        }
        try {
            final PyObject loader = engine.module("_prime_loader");
            if (loader != null) {
                loader.callAttr("dispatch_updates", account, updates, container);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
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
