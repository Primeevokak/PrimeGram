package org.telegram.messenger.blocks;

import org.telegram.messenger.MessageObject;

/**
 * PrimeGram Blocks: the per-run context a compiled script executes against.
 *
 * <p>The five primitive fields are deliberately what they are because they're exactly what
 * {@code WorkManager}'s {@code Data} can hold directly - when a {@code wait.duration} block
 * pauses a script, only these survive into the persisted job (see {@code PrimeBlockWaitWorker}).
 * {@link #triggerMessage} is the one non-primitive field: populated when a script runs
 * synchronously off a live {@code NotificationCenter} event (so actions like
 * {@code action.forward_to_saved} can use the real object the same way UI code does, without a
 * separate storage fetch), but it is intentionally left {@code null} after a process-death
 * resume - {@code BlockActions}/{@code BlockConditions} must not assume it is present.
 */
final class ScriptExecution {

    final int account;
    final long dialogId;
    final int triggerMessageId;
    final long senderId;
    final String messageText;
    final int triggerDate;

    /** Only set for a live, same-process run. Null after a {@code WorkManager} resume. */
    transient MessageObject triggerMessage;

    ScriptExecution(int account, long dialogId, int triggerMessageId, long senderId, String messageText, int triggerDate) {
        this.account = account;
        this.dialogId = dialogId;
        this.triggerMessageId = triggerMessageId;
        this.senderId = senderId;
        this.messageText = messageText;
        this.triggerDate = triggerDate;
    }
}
