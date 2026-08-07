package org.telegram.messenger.plugins;

import com.chaquo.python.PyObject;

import org.telegram.messenger.FileLog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PrimeGram: the app's side of every hook a plugin can be on.
 *
 * <p>The whole design of this class is the {@code static volatile boolean} at the top. A hook point
 * sits on a hot path - {@code sendMessage} runs for every message the user sends - and the cost of
 * having plugins at all must be one field read for users who have none. Only when a plugin has
 * actually subscribed does anything here touch Python.
 *
 * <p>Dispatch is synchronous, on the calling thread, deliberately for {@link #onSendMessage},
 * {@link #onFileOpen} and {@link #onIntent}: the caller is asking a yes/no question about
 * something it is about to do right now, and there is no useful answer to that later. Those three
 * genuinely have no queue to hop onto.
 *
 * <p>{@link #onPreRequest} and {@link #onPostRequest} used to work the same way, and that was a
 * real bug, not just the same tradeoff applied consistently: {@link
 * org.telegram.tgnet.ConnectionsManager#sendRequest} - which calls them - is reachable from
 * arbitrary app threads, including the UI thread directly (the dialogs list's "⋮" menu calls
 * {@code ContactsController.loadGlobalPrivacySetting()} straight from an {@code OnClickListener}).
 * Running Python inline there means a plugin doing anything slow in {@code pre_request_hook} - or
 * simply losing a GIL race against whatever the plugin engine's own queue thread happened to be
 * running at that instant - froze the UI thread for however long that took, with no bound. Fixed
 * by always dispatching through {@link PrimePythonEngine#queue()} (the same serial queue every
 * other hook already used) and waiting on it with a short, hard timeout: fast enough that a
 * well-behaved plugin's answer arrives before the wait would ever matter, and short enough that a
 * slow or GIL-contended one no longer matters either - the request just goes out unmodified, the
 * same graceful-degradation this class already applied to a plugin that throws.
 */
public final class PrimePluginHooks {

    /** How long a caller of {@link #onPreRequest}/{@link #onPostRequest} will wait for a plugin's
     *  answer before giving up and using the request/response unchanged. Deliberately short - this
     *  can run on the UI thread, and the whole point is that a slow plugin must not be able to
     *  make every network request feel like it hung. */
    private static final long HOOK_TIMEOUT_MS = 250;

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

    public static void setPills(String json) {
        PrimePillStack.setPills(json);
    }

    /** Tells the plugin its pill was tapped. Fire-and-forget, same as a menu click. */
    public static void onPillClick(String pillId) {
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return;
        }
        engine.queue().postRunnable(() -> {
            try {
                final PyObject loader = engine.module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("dispatch_pill_click", pillId);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
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
    /** Sentinel distinguishing "the plugin queue answered null (cancel this request)" from "we
     *  gave up waiting and resultHolder still holds its unwritten initial value" - both look like
     *  "no result" otherwise, and they need opposite outcomes. */
    private static final Object CANCELLED = new Object();

    public static Object onPreRequest(int account, Object request) {
        if (!requestHooks || request == null) {
            return request;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return request;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final Object[] resultHolder = {request};
        engine.queue().postRunnable(() -> {
            try {
                final PyObject loader = engine.module("_prime_loader");
                if (loader != null) {
                    final PyObject result = loader.callAttr("dispatch_pre_request", account, request);
                    resultHolder[0] = (result == null || result.toJava(Object.class) == null) ? CANCELLED : result.toJava(Object.class);
                }
            } catch (Throwable e) {
                // The request goes out unchanged. A plugin must not be able to stop the client
                // from talking to the server by throwing.
                FileLog.e(e);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(HOOK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                FileLog.d("PrimePluginHooks: onPreRequest timed out, sending unmodified");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return resultHolder[0] == CANCELLED ? null : resultHolder[0];
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
        final CountDownLatch latch = new CountDownLatch(1);
        final Object[] resultHolder = {response};
        engine.queue().postRunnable(() -> {
            try {
                final PyObject loader = engine.module("_prime_loader");
                if (loader != null) {
                    final PyObject result = loader.callAttr("dispatch_post_request", account, request, response, error);
                    if (result != null) {
                        resultHolder[0] = result.toJava(Object.class);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(HOOK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                FileLog.d("PrimePluginHooks: onPostRequest timed out, delivering unmodified");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return resultHolder[0];
    }

    /** Offers a container of updates, and every update inside it, to the plugins. Fire-and-forget,
     *  same as {@link #onAppEvent} - nothing waits on a return value, so unlike the pre/post-request
     *  pair above this never needed to block its caller at all. It used to anyway: on the update
     *  path that caller is {@code Utilities.stageQueue} (see the comment at the two call sites in
     *  MessagesController), Telegram's own single serial queue for processing incoming updates -
     *  every update batch for every open chat and channel ran the plugin's Python inline on that
     *  queue, stalling it (and everything else waiting on it) for as long as the plugin's code and
     *  the GIL took. That is the periodic mid-scroll freezing this fixes: nothing about it needed
     *  interaction, because update batches arrive from the server on their own. */
    public static void onUpdates(int account, Object updates, boolean container) {
        if (!requestHooks || updates == null) {
            return;
        }
        final PrimePythonEngine engine = PrimePythonEngine.getInstance();
        if (!engine.isStarted()) {
            return;
        }
        engine.queue().postRunnable(() -> {
            try {
                final PyObject loader = engine.module("_prime_loader");
                if (loader != null) {
                    loader.callAttr("dispatch_updates", account, updates, container);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
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
