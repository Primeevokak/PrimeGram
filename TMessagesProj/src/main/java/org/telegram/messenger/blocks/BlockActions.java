package org.telegram.messenger.blocks;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * PrimeGram Blocks: performs the real, closed set of {@code action.*} blocks. Every call here
 * goes through the same public APIs the normal UI uses (nothing block-specific bypasses the
 * usual send/read/pin/mute/block paths) - see the "Phase D: Execution Engine" plan §4 for where
 * each signature was confirmed.
 */
final class BlockActions {

    private BlockActions() {
    }

    static void perform(int account, String actionType, JSONObject params, ScriptExecution exec) {
        switch (actionType) {
            case "action.send_reply": {
                final String text = params.optString("text", "");
                if (!text.isEmpty()) {
                    SendMessagesHelper.getInstance(account).sendMessage(
                            SendMessagesHelper.SendMessageParams.of(text, exec.dialogId));
                }
                break;
            }
            case "action.mark_as_read":
                MessagesController.getInstance(account).markDialogAsReadNow(exec.dialogId, 0);
                break;
            case "action.archive_chat":
                MessagesController.getInstance(account).addDialogToFolder(exec.dialogId, 1, -1, 0);
                break;
            case "action.pin_message":
                pinTriggerMessage(account, exec);
                break;
            case "action.mute_chat":
                NotificationsController.getInstance(account).muteUntil(exec.dialogId, 0, Integer.MAX_VALUE);
                break;
            case "action.forward_to_saved":
                forwardTriggerMessageToSaved(account, exec);
                break;
            case "action.block_user":
                if (exec.senderId != 0) {
                    MessagesController.getInstance(account).blockPeer(exec.senderId);
                }
                break;
            case "action.ui.set_sidebar_enabled":
                setSidebarEnabled(params.optBoolean("enabled", true));
                break;
            case "action.ui.set_channel_button_visible":
                PrimeBlocksUiOverrides.setVisible(params.optString("button", "search"), params.optBoolean("visible", true));
                break;
            default:
                // Unknown action id: compatibility is already checked before a script runs at
                // all, so this can only mean a newer .pr format outran this build's registry -
                // nothing safe to do but skip it.
                break;
        }
    }

    private static void setSidebarEnabled(boolean enabled) {
        // Same pref key + apply call the settings switch itself uses
        // (PrimeGramSettingsActivity.java's "Боковая панель" row) - reused verbatim, not
        // reimplemented, so a script and the settings screen can never disagree about what
        // "enabled" means here.
        MessagesController.getGlobalMainSettings().edit().putBoolean("primegram_sidebar_enabled", enabled).apply();
        if (org.telegram.ui.LaunchActivity.instance != null) {
            org.telegram.ui.LaunchActivity.instance.updateSidebarVisibility();
        }
    }

    private static void pinTriggerMessage(int account, ScriptExecution exec) {
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.Chat chat = exec.dialogId < 0 ? controller.getChat(-exec.dialogId) : null;
        final TLRPC.User user = exec.dialogId > 0 ? controller.getUser(exec.dialogId) : null;
        controller.pinMessage(chat, user, exec.triggerMessageId, false, true, false);
    }

    private static void forwardTriggerMessageToSaved(int account, ScriptExecution exec) {
        // Only wired for a live, same-process run (exec.triggerMessage set at trigger time from
        // the real NotificationCenter event, the same object the chat UI already has - see
        // ScriptExecution). After a WorkManager resume following process death there is no
        // MessageObject to forward; skip rather than attempt a fragile from-scratch storage
        // reconstruction for what should be a rare combination (wait.duration + forward, with an
        // app kill landing exactly in between).
        if (exec.triggerMessage == null) {
            FileLog.d("PrimeBlocks: forward_to_saved skipped - no live trigger message after resume");
            return;
        }
        final ArrayList<MessageObject> messages = new ArrayList<>();
        messages.add(exec.triggerMessage);
        SendMessagesHelper.getInstance(account).sendMessage(
                messages, UserConfig.getInstance(account).getClientUserId(), false, false, true, 0, 0);
    }
}
