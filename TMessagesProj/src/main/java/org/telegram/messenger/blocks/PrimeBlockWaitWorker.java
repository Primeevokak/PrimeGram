package org.telegram.messenger.blocks;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import android.content.Context;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PrimeGram Blocks: what makes a {@code wait.duration} block survive the app being killed - see
 * the "Phase D: Execution Engine" plan §6. Persists only the primitives {@link ScriptExecution}
 * was designed to carry (account, dialogId, ids, text, date) plus the script id and the resume
 * program counter; {@code WorkManager}'s own storage (backed by a Room database, not process
 * memory) is what survives the kill.
 */
public final class PrimeBlockWaitWorker extends Worker {

    private static final String KEY_SCRIPT_ID = "script_id";
    private static final String KEY_PC = "pc";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_DIALOG_ID = "dialog_id";
    private static final String KEY_MESSAGE_ID = "message_id";
    private static final String KEY_SENDER_ID = "sender_id";
    private static final String KEY_MESSAGE_TEXT = "message_text";
    private static final String KEY_TRIGGER_DATE = "trigger_date";

    public PrimeBlockWaitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void schedule(String scriptId, int resumePc, ScriptExecution exec, int hours) {
        final Data data = new Data.Builder()
                .putString(KEY_SCRIPT_ID, scriptId)
                .putInt(KEY_PC, resumePc)
                .putInt(KEY_ACCOUNT, exec.account)
                .putLong(KEY_DIALOG_ID, exec.dialogId)
                .putInt(KEY_MESSAGE_ID, exec.triggerMessageId)
                .putLong(KEY_SENDER_ID, exec.senderId)
                .putString(KEY_MESSAGE_TEXT, exec.messageText)
                .putInt(KEY_TRIGGER_DATE, exec.triggerDate)
                .build();
        final OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PrimeBlockWaitWorker.class)
                .setInitialDelay(Math.max(0, hours), TimeUnit.HOURS)
                .setInputData(data)
                .build();
        WorkManager.getInstance(ApplicationLoader.applicationContext).enqueue(request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            final Data data = getInputData();
            final String scriptId = data.getString(KEY_SCRIPT_ID);
            final int pc = data.getInt(KEY_PC, -1);
            if (scriptId == null || pc < 0) {
                return Result.success();
            }

            // Reconciliation (plan §7): a script that's since been deleted or disabled must not
            // act on a stale, already-enqueued wait - no-op instead.
            final PrimeBlockScript script = PrimeBlocksController.getInstance().findById(scriptId);
            if (script == null || !script.isEnabled()) {
                return Result.success();
            }

            final ScriptExecution exec = new ScriptExecution(
                    data.getInt(KEY_ACCOUNT, 0),
                    data.getLong(KEY_DIALOG_ID, 0),
                    data.getInt(KEY_MESSAGE_ID, 0),
                    data.getLong(KEY_SENDER_ID, 0),
                    data.getString(KEY_MESSAGE_TEXT),
                    data.getInt(KEY_TRIGGER_DATE, 0));
            // exec.triggerMessage intentionally stays null - see ScriptExecution's own note.

            final List<Op> ops = BlockCompiler.compile(script.manifest.body);
            PrimeBlocksRuntime.step(scriptId, ops, exec, pc);
            return Result.success();
        } catch (Throwable t) {
            FileLog.e(t);
            return Result.success();
        }
    }
}
