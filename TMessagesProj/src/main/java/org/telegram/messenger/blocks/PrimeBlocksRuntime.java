package org.telegram.messenger.blocks;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * PrimeGram Blocks: the interpreter - the piece that was missing entirely before this phase (see
 * the "Phase D: Execution Engine" plan's Context section). One instance per account, since
 * {@code NotificationCenter} itself is per-account, but {@link PrimeBlocksController}'s script
 * catalogue is global - any account's matching event can fire any installed, enabled script.
 *
 * <p>Execution is continuation-passing (see {@link #step}), not a plain loop, because
 * {@code condition.language_is} depends on an async ML Kit call ({@link BlockConditions}) and a
 * paused {@code wait.duration} resumes via {@link PrimeBlockWaitWorker} on its own callback.
 */
public final class PrimeBlocksRuntime implements NotificationCenter.NotificationCenterDelegate {

    private static final PrimeBlocksRuntime[] instances = new PrimeBlocksRuntime[8];

    public static synchronized PrimeBlocksRuntime getInstance(int account) {
        PrimeBlocksRuntime local = instances[account];
        if (local == null) {
            local = instances[account] = new PrimeBlocksRuntime(account);
        }
        return local;
    }

    /** Shared across accounts - a compiled program doesn't depend on which account is running it. */
    private static final Map<String, List<Op>> compiledCache = new HashMap<>();

    /** Guards the call-start/end observers, which live on the account-independent global center - see {@link #attach()}. */
    private static boolean globalAttached;

    private final int currentAccount;
    private boolean attached;

    private PrimeBlocksRuntime(int account) {
        this.currentAccount = account;
    }

    public void attach() {
        if (attached) {
            return;
        }
        attached = true;
        final NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.didReceiveNewMessages);
        nc.addObserver(this, NotificationCenter.messagesDeleted);
        nc.addObserver(this, NotificationCenter.didUpdateReactions);
        nc.addObserver(this, NotificationCenter.primeMessageEdited);
        nc.addObserver(this, NotificationCenter.primeUserJoinedChat);
        nc.addObserver(this, NotificationCenter.messagesRead);
        nc.addObserver(this, NotificationCenter.didUpdatePollResults);
        nc.addObserver(this, NotificationCenter.historyCleared);
        nc.addObserver(this, NotificationCenter.channelRightsUpdated);

        // didStartedCall/didEndCall are posted on NotificationCenter.getGlobalInstance(), not a
        // per-account center (see VoIPService.java) - registering them here for every account
        // would run a matching script once per logged-in account for the same single call, so
        // only the first account to attach ever registers them.
        if (!globalAttached) {
            globalAttached = true;
            final NotificationCenter global = NotificationCenter.getGlobalInstance();
            global.addObserver(this, NotificationCenter.didStartedCall);
            global.addObserver(this, NotificationCenter.didEndCall);
        }

        // Synthetic trigger (not backed by a real TL update) for scripts that configure
        // PrimeGram's own UI rather than react to a chat event - see BuiltinActions' action.ui.*
        // entries and the "Splash Screen + Blocks Trigger Expansion" plan's Part 3. Queued behind
        // Utilities.globalQueue rather than run synchronously here: PrimeBlocksController.loadIfNeeded()
        // (called right before attach() in ApplicationLoader) reads scripts from disk on that same
        // serial queue, so this only sees the real script list once that read has actually finished.
        final int accountForAppStarted = currentAccount;
        Utilities.globalQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() ->
                matchAndRun("trigger.app_started", new ScriptExecution(accountForAppStarted, 0, 0, 0, null, 0))));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (PrimeBlocksController.getInstance().count() == 0) {
            return;
        }
        if (id == NotificationCenter.didReceiveNewMessages) {
            onNewMessages(args);
        } else if (id == NotificationCenter.messagesDeleted) {
            onMessagesDeleted(args);
        } else if (id == NotificationCenter.didUpdateReactions) {
            onReactionAdded(args);
        } else if (id == NotificationCenter.primeMessageEdited) {
            onMessageEdited(args);
        } else if (id == NotificationCenter.primeUserJoinedChat) {
            onUserJoinedChat(args);
        } else if (id == NotificationCenter.messagesRead) {
            onMessagesRead(args);
        } else if (id == NotificationCenter.didUpdatePollResults) {
            matchAndRun("trigger.poll_answered", new ScriptExecution(currentAccount, 0, 0, 0, null, 0));
        } else if (id == NotificationCenter.historyCleared) {
            onHistoryCleared(args);
        } else if (id == NotificationCenter.channelRightsUpdated) {
            matchAndRun("trigger.channel_rights_updated", new ScriptExecution(currentAccount, 0, 0, 0, null, 0));
        } else if (id == NotificationCenter.didStartedCall) {
            matchAndRun("trigger.call_started", new ScriptExecution(currentAccount, 0, 0, 0, null, 0));
        } else if (id == NotificationCenter.didEndCall) {
            matchAndRun("trigger.call_ended", new ScriptExecution(currentAccount, 0, 0, 0, null, 0));
        }
    }

    private void onMessagesRead(Object[] args) {
        if (args.length < 1 || !(args[0] instanceof org.telegram.messenger.support.LongSparseIntArray)) {
            return;
        }
        final org.telegram.messenger.support.LongSparseIntArray inbox =
                (org.telegram.messenger.support.LongSparseIntArray) args[0];
        for (int i = 0; i < inbox.size(); i++) {
            final long dialogId = inbox.keyAt(i);
            final int maxId = inbox.valueAt(i);
            matchAndRun("trigger.message_read", new ScriptExecution(currentAccount, dialogId, maxId, 0, null, 0));
        }
    }

    private void onHistoryCleared(Object[] args) {
        if (args.length < 1 || !(args[0] instanceof Long)) {
            return;
        }
        final long dialogId = (Long) args[0];
        matchAndRun("trigger.chat_history_cleared", new ScriptExecution(currentAccount, dialogId, 0, 0, null, 0));
    }

    @SuppressWarnings("unchecked")
    private void onNewMessages(Object[] args) {
        if (args.length < 2 || !(args[1] instanceof ArrayList)) {
            return;
        }
        final ArrayList<MessageObject> messages = (ArrayList<MessageObject>) args[1];
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null) {
                continue;
            }
            final long dialogId = message.getDialogId();
            final long senderId = message.getSenderId();
            final ScriptExecution exec = new ScriptExecution(currentAccount, dialogId, message.getId(),
                    senderId, message.messageOwner.message, message.messageOwner.date);
            exec.triggerMessage = message;
            matchAndRun("trigger.message_received", exec);
            if (senderId > 0 && !ContactsController.getInstance(currentAccount).isContact(senderId)) {
                matchAndRun("trigger.message_from_non_contact", exec);
            }
        }
    }

    private void onMessagesDeleted(Object[] args) {
        // Call-site arg shapes vary (5 different overloads post this notification with different
        // extra trailing args) - only the two that matter here (ids, channel/dialog id) are read,
        // defensively, and this simply doesn't fire rather than guess on a shape it doesn't
        // recognize.
        if (args.length < 2 || !(args[0] instanceof ArrayList) || !(args[1] instanceof Long)) {
            return;
        }
        final long raw = (Long) args[1];
        final long dialogId = raw > 0 ? -raw : raw;
        if (dialogId == 0) {
            return;
        }
        final ScriptExecution exec = new ScriptExecution(currentAccount, dialogId, 0, 0, null, 0);
        matchAndRun("trigger.message_deleted", exec);
    }

    private void onReactionAdded(Object[] args) {
        if (args.length < 2 || !(args[0] instanceof Long)) {
            return;
        }
        final long dialogId = (Long) args[0];
        final int messageId = args[1] instanceof Integer ? (Integer) args[1] : 0;
        final ScriptExecution exec = new ScriptExecution(currentAccount, dialogId, messageId, 0, null, 0);
        matchAndRun("trigger.reaction_added", exec);
    }

    private void onMessageEdited(Object[] args) {
        if (args.length < 2 || !(args[0] instanceof Long)) {
            return;
        }
        final long dialogId = (Long) args[0];
        final int messageId = args[1] instanceof Integer ? (Integer) args[1] : 0;
        final ScriptExecution exec = new ScriptExecution(currentAccount, dialogId, messageId, 0, null, 0);
        matchAndRun("trigger.message_edited", exec);
    }

    private void onUserJoinedChat(Object[] args) {
        // Only fires for legacy basic groups (TL_updateChatParticipantAdd) - megagroup/channel
        // joins have no equivalent hook wired yet (see the plan's §3 note on MessagesController).
        if (args.length < 2 || !(args[0] instanceof Long) || !(args[1] instanceof Long)) {
            return;
        }
        final long dialogId = (Long) args[0];
        final long userId = (Long) args[1];
        final ScriptExecution exec = new ScriptExecution(currentAccount, dialogId, 0, userId, null, 0);
        matchAndRun("trigger.user_joined_chat", exec);
    }

    private void matchAndRun(String triggerType, ScriptExecution exec) {
        for (PrimeBlockScript script : PrimeBlocksController.getInstance().getScripts()) {
            if (!script.isEnabled()) {
                continue;
            }
            if (script.manifest.trigger == null || !triggerType.equals(script.manifest.trigger.type)) {
                continue;
            }
            try {
                step(script.id(), compiledFor(script), exec, 0);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private static List<Op> compiledFor(PrimeBlockScript script) {
        List<Op> ops = compiledCache.get(script.id());
        if (ops == null) {
            ops = BlockCompiler.compile(script.manifest.body);
            compiledCache.put(script.id(), ops);
        }
        return ops;
    }

    /** Package-visible so {@link PrimeBlockWaitWorker} can resume a paused script. */
    static void step(String scriptId, List<Op> ops, ScriptExecution exec, int pc) {
        if (pc < 0 || pc >= ops.size()) {
            return;
        }
        final Op op = ops.get(pc);
        if (op instanceof Op.ActionOp) {
            final Op.ActionOp action = (Op.ActionOp) op;
            BlockActions.perform(exec.account, action.actionType, action.params, exec);
            step(scriptId, ops, exec, pc + 1);
        } else if (op instanceof Op.WaitOp) {
            PrimeBlockWaitWorker.schedule(scriptId, pc + 1, exec, ((Op.WaitOp) op).hours);
        } else if (op instanceof Op.GuardOp) {
            final Op.GuardOp guard = (Op.GuardOp) op;
            BlockConditions.evaluate(exec.account, guard.condType, guard.params, exec, ok -> {
                if (ok) {
                    step(scriptId, ops, exec, pc + 1);
                }
            });
        } else if (op instanceof Op.TestOp) {
            final Op.TestOp test = (Op.TestOp) op;
            evaluateAll(exec.account, test.conditions, test.operator, exec, 0, ok ->
                    step(scriptId, ops, exec, ok ? pc + 1 : test.elseIndex));
        } else if (op instanceof Op.JumpOp) {
            step(scriptId, ops, exec, ((Op.JumpOp) op).targetIndex);
        }
    }

    private static void evaluateAll(int account, List<Op.CondCheck> conditions, String operator,
                                     ScriptExecution exec, int index, Consumer<Boolean> callback) {
        if (index >= conditions.size()) {
            // AND with nothing left to fail is vacuously true; OR with nothing left to satisfy is false.
            callback.accept(!"OR".equals(operator));
            return;
        }
        final Op.CondCheck check = conditions.get(index);
        final JSONObject params = check.params != null ? check.params : new JSONObject();
        BlockConditions.evaluate(account, check.type, params, exec, result -> {
            if ("OR".equals(operator) == result) {
                // OR short-circuits true, AND short-circuits false - both are "operator == result".
                callback.accept(result);
                return;
            }
            evaluateAll(account, conditions, operator, exec, index + 1, callback);
        });
    }
}
