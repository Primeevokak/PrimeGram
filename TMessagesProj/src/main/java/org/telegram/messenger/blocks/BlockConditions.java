package org.telegram.messenger.blocks;

import org.json.JSONObject;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.TLRPC;

import java.util.function.Consumer;

/**
 * PrimeGram Blocks: evaluates the real, closed set of {@code condition.*} blocks.
 *
 * <p>Callback-based rather than returning a plain {@code boolean}, because
 * {@code condition.language_is} genuinely has to wait on an async ML Kit call
 * ({@link LanguageDetector#detectLanguage}) - every other condition here just calls
 * {@code callback.accept(...)} immediately, so the common case has no real async overhead, but
 * {@link PrimeBlocksRuntime}'s interpreter has to be written expecting either.
 */
final class BlockConditions {

    private BlockConditions() {
    }

    static void evaluate(int account, String condType, JSONObject params, ScriptExecution exec, Consumer<Boolean> callback) {
        switch (condType) {
            case "condition.language_is":
                evaluateLanguageIs(params, exec, callback);
                break;
            case "condition.chat_type_is":
                callback.accept(evaluateChatTypeIs(account, params, exec));
                break;
            case "condition.sender_is_bot": {
                final TLRPC.User sender = MessagesController.getInstance(account).getUser(exec.senderId);
                callback.accept(sender != null && sender.bot);
                break;
            }
            case "condition.chat_is_muted":
                callback.accept(MessagesController.getInstance(account).isDialogMuted(exec.dialogId, 0));
                break;
            case "condition.message_contains_text": {
                final String needle = params.optString("text", "");
                final boolean negate = params.optBoolean("negate", false);
                final boolean contains = !needle.isEmpty() && exec.messageText != null && exec.messageText.contains(needle);
                callback.accept(contains != negate);
                break;
            }
            case "condition.not_replied_since_trigger":
                evaluateNotRepliedSinceTrigger(account, exec, callback);
                break;
            default:
                // Unknown condition id: as in BlockActions, compatibility is already checked
                // before a script runs at all - fail closed rather than guess.
                callback.accept(false);
                break;
        }
    }

    private static void evaluateLanguageIs(JSONObject params, ScriptExecution exec, Consumer<Boolean> callback) {
        final String wanted = params.optString("language", "");
        final boolean negate = params.optBoolean("negate", false);
        if (exec.messageText == null || exec.messageText.isEmpty() || wanted.isEmpty() || !LanguageDetector.hasSupport()) {
            callback.accept(false);
            return;
        }
        LanguageDetector.detectLanguage(exec.messageText,
                detected -> callback.accept(wanted.equalsIgnoreCase(detected) != negate),
                ex -> callback.accept(false));
    }

    private static boolean evaluateChatTypeIs(int account, JSONObject params, ScriptExecution exec) {
        final String wanted = params.optString("chat_type", "private");
        final String actual;
        if (exec.dialogId > 0) {
            actual = "private";
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-exec.dialogId);
            actual = chat != null && ChatObject.isChannelAndNotMegaGroup(chat) ? "channel" : "group";
        }
        return wanted.equals(actual);
    }

    private static void evaluateNotRepliedSinceTrigger(int account, ScriptExecution exec, Consumer<Boolean> callback) {
        MessagesStorage.getInstance(account).getLastOutgoingMessageDate(exec.dialogId, lastOutgoingDate -> {
            // No outgoing message at all, or the last one predates the trigger: we haven't replied yet.
            callback.accept(lastOutgoingDate == 0 || lastOutgoingDate <= exec.triggerDate);
        });
    }
}
