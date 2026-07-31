package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: sends one oversized file as a series of chunks.
 *
 * <p>Three things make this more than a loop over {@code prepareSendingDocument}.
 *
 * <p><b>One chunk on disk at a time.</b> An eight gigabyte file cut into four two-gigabyte
 * temporary copies needs sixteen gigabytes of free space, which a phone does not have. Each chunk
 * is extracted, sent, and deleted before the next one is cut, so the extra space needed is one
 * chunk rather than a second copy of the file.
 *
 * <p><b>It waits for the upload, not for the call.</b> {@code prepareSendingDocument} returns
 * immediately; the upload takes minutes. Sending the next chunk on return would put four
 * simultaneous multi-gigabyte uploads on one connection. So the sender listens for the upload
 * events and moves on when the previous part is actually on the server.
 *
 * <p><b>It survives being killed.</b> Sending eight gigabytes from a phone takes long enough that
 * the app will be backgrounded, lose the network and quite possibly be shut down by the system in
 * the middle. The plan and the progress are written to disk after every chunk, so the transfer
 * resumes at the next part instead of starting over.
 */
public final class PrimeBigFileSender implements NotificationCenter.NotificationCenterDelegate {

    private static final String STATE_FILE = "prime_bigfile_state.json";
    private static final String CHUNK_DIR = "prime_bigfile";

    private static volatile PrimeBigFileSender instance;

    /** One transfer in progress. */
    public static final class Transfer {
        public String alias;
        public String sourcePath;
        public String fileName;
        public long totalSize;
        public long dialogId;
        public int account;
        public int total;
        public int sent;
        /** Offset and length per chunk, in order. */
        public final List<long[]> parts = new ArrayList<>();

        /** Bytes already on the server, for the sender's progress bar. */
        public long uploadedBytes() {
            long done = 0;
            for (int i = 0; i < sent && i < parts.size(); i++) {
                done += parts.get(i)[1];
            }
            return done;
        }

        public float progress() {
            return totalSize <= 0 ? 0 : Math.min(1f, uploadedBytes() / (float) totalSize);
        }
    }

    private final List<Transfer> transfers = new ArrayList<>();
    private Transfer current;
    private String currentChunkPath;
    private boolean observing;

    private PrimeBigFileSender() {
        load();
    }

    public static PrimeBigFileSender getInstance() {
        PrimeBigFileSender local = instance;
        if (local == null) {
            synchronized (PrimeBigFileSender.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimeBigFileSender();
                }
            }
        }
        return local;
    }

    // region public API

    /**
     * Whether this file has to be split at all, and whether we are allowed to.
     *
     * @return null when the file can be sent normally, otherwise the reason it cannot be sent here
     */
    public static String checkSendable(int account, long size) {
        if (size <= PrimeBigFile.chunkLimitFor(account)) {
            return null;
        }
        if (!PrimeBigFile.isSendingEnabled()) {
            return "Файл больше лимита Telegram. Включите отправку больших файлов в настройках PrimeGram.";
        }
        if (size > PrimeBigFile.maxSendableSize()) {
            return "Файл больше " + AndroidUtilities.formatFileSize(PrimeBigFile.maxSendableSize()) + ".";
        }
        return null;
    }

    /** Starts a transfer. Returns the alias, or null when it could not be started. */
    public String send(int account, long dialogId, File source, String displayName) {
        if (source == null || !source.exists()) {
            return null;
        }
        final long size = source.length();
        final PrimeBigFile.Plan plan = PrimeBigFile.plan(
                displayName != null ? displayName : source.getName(), size,
                PrimeBigFile.chunkLimitFor(account));

        final Transfer transfer = new Transfer();
        transfer.alias = plan.alias;
        transfer.sourcePath = source.getAbsolutePath();
        transfer.fileName = plan.fileName;
        transfer.totalSize = size;
        transfer.dialogId = dialogId;
        transfer.account = account;
        transfer.total = plan.count();
        transfer.sent = 0;
        transfer.parts.addAll(plan.parts);

        synchronized (transfers) {
            transfers.add(transfer);
        }
        save();
        notifyChanged();
        pump();
        return transfer.alias;
    }

    public List<Transfer> getTransfers() {
        synchronized (transfers) {
            return new ArrayList<>(transfers);
        }
    }

    public Transfer findByAlias(String alias) {
        synchronized (transfers) {
            for (Transfer transfer : transfers) {
                if (transfer.alias.equals(alias)) {
                    return transfer;
                }
            }
        }
        return null;
    }

    public void cancel(String alias) {
        synchronized (transfers) {
            for (int i = 0; i < transfers.size(); i++) {
                if (transfers.get(i).alias.equals(alias)) {
                    transfers.remove(i);
                    break;
                }
            }
        }
        if (current != null && current.alias.equals(alias)) {
            current = null;
            deleteCurrentChunk();
        }
        save();
        notifyChanged();
        pump();
    }

    /** Picks up anything left unfinished by a previous run. Called once at start-up. */
    public void resumePending() {
        pump();
    }

    // endregion

    // region the pump

    private void pump() {
        if (current != null) {
            return;
        }
        final Transfer next;
        synchronized (transfers) {
            next = transfers.isEmpty() ? null : transfers.get(0);
        }
        if (next == null) {
            stopObserving();
            return;
        }
        if (next.sent >= next.total) {
            finish(next);
            return;
        }
        current = next;
        startObserving();
        sendChunk(next, next.sent);
    }

    private void sendChunk(Transfer transfer, int index) {
        Utilities.globalQueue.postRunnable(() -> {
            File chunk = null;
            try {
                chunk = extract(transfer, index);
            } catch (Throwable e) {
                FileLog.e("PrimeBigFileSender.extract", e);
            }
            if (chunk == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    // Out of space, or the source file has gone. Either way there is nothing to
                    // retry: the transfer is dropped rather than left spinning.
                    cancel(transfer.alias);
                });
                return;
            }
            currentChunkPath = chunk.getAbsolutePath();
            final File finalChunk = chunk;
            AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.plugins.PrimePluginSend.sendDocument(
                    transfer.account, transfer.dialogId, finalChunk.getAbsolutePath(), ""));
        });
    }

    /**
     * Cuts one chunk out of the source into the cache, named with its header.
     *
     * <p>The name is the payload: {@code prepareSendingDocument} takes the file's own name, so the
     * header has to <em>be</em> the temporary file's name for the other side to ever see it.
     */
    private File extract(Transfer transfer, int index) throws IOException {
        final long[] part = transfer.parts.get(index);
        final File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), CHUNK_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        final String name = PrimeBigFile.header(transfer.alias, index + 1, transfer.total,
                transfer.totalSize, transfer.fileName);
        final File target = new File(dir, name);
        if (target.exists() && target.length() == part[1]) {
            return target;
        }

        final File source = new File(transfer.sourcePath);
        if (!source.exists()) {
            return null;
        }
        try (RandomAccessFile in = new RandomAccessFile(source, "r");
             FileOutputStream out = new FileOutputStream(target)) {
            in.seek(part[0]);
            final byte[] buffer = new byte[1024 * 1024];
            long remaining = part[1];
            while (remaining > 0) {
                final int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read <= 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
        return target;
    }

    private void onChunkDone(boolean success) {
        final Transfer transfer = current;
        if (transfer == null) {
            return;
        }
        deleteCurrentChunk();
        if (!success) {
            // Left in place at the same index; the next pump retries this part rather than
            // skipping it, because a gap in the middle makes the whole file unusable.
            current = null;
            AndroidUtilities.runOnUIThread(this::pump, 5000);
            return;
        }
        transfer.sent++;
        save();
        notifyChanged();
        current = null;
        if (transfer.sent >= transfer.total) {
            finish(transfer);
            return;
        }
        // The pause between parts, so a set of chunks looks like somebody sending several files
        // rather than one process emitting them back to back.
        AndroidUtilities.runOnUIThread(this::pump, PrimeBigFile.nextDelayMs());
    }

    private void finish(Transfer transfer) {
        synchronized (transfers) {
            transfers.remove(transfer);
        }
        current = null;
        save();
        notifyChanged();
        pump();
    }

    private void deleteCurrentChunk() {
        final String path = currentChunkPath;
        currentChunkPath = null;
        if (path != null) {
            Utilities.globalQueue.postRunnable(() -> {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            });
        }
    }

    // endregion

    // region upload events

    private void startObserving() {
        if (observing) {
            return;
        }
        observing = true;
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(current != null ? current.account : UserConfig.selectedAccount)
                    .addObserver(this, NotificationCenter.fileUploaded);
            NotificationCenter.getInstance(current != null ? current.account : UserConfig.selectedAccount)
                    .addObserver(this, NotificationCenter.fileUploadFailed);
        });
    }

    private void stopObserving() {
        if (!observing) {
            return;
        }
        observing = false;
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.fileUploaded);
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.fileUploadFailed);
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (currentChunkPath == null || args.length == 0 || !(args[0] instanceof String)) {
            return;
        }
        // Matched by path: the upload events name the file being uploaded, which for us is the
        // temporary chunk we just wrote. Nothing else on the wire identifies it.
        if (!currentChunkPath.equals(args[0])) {
            return;
        }
        if (id == NotificationCenter.fileUploaded) {
            onChunkDone(true);
        } else if (id == NotificationCenter.fileUploadFailed) {
            onChunkDone(false);
        }
    }

    private void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.pluginsDidUpdate));
    }

    // endregion

    // region persistence

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    private void save() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final JSONArray array = new JSONArray();
                for (Transfer transfer : getTransfers()) {
                    final JSONObject object = new JSONObject();
                    object.put("alias", transfer.alias);
                    object.put("source", transfer.sourcePath);
                    object.put("name", transfer.fileName);
                    object.put("size", transfer.totalSize);
                    object.put("dialog", transfer.dialogId);
                    object.put("account", transfer.account);
                    object.put("total", transfer.total);
                    object.put("sent", transfer.sent);
                    final JSONArray parts = new JSONArray();
                    for (long[] part : transfer.parts) {
                        parts.put(new JSONArray().put(part[0]).put(part[1]));
                    }
                    object.put("parts", parts);
                    array.put(object);
                }
                final File file = new File(ApplicationLoader.getFilesDirFixed(), STATE_FILE);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(array.toString().getBytes());
                }
            } catch (Throwable e) {
                FileLog.e("PrimeBigFileSender.save", e);
            }
        });
    }

    private void load() {
        try {
            final File file = new File(ApplicationLoader.getFilesDirFixed(), STATE_FILE);
            if (!file.exists()) {
                return;
            }
            final byte[] bytes = new byte[(int) file.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(bytes);
            }
            final JSONArray array = new JSONArray(new String(bytes));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject object = array.getJSONObject(i);
                final Transfer transfer = new Transfer();
                transfer.alias = object.getString("alias");
                transfer.sourcePath = object.getString("source");
                transfer.fileName = object.getString("name");
                transfer.totalSize = object.getLong("size");
                transfer.dialogId = object.getLong("dialog");
                transfer.account = object.getInt("account");
                transfer.total = object.getInt("total");
                transfer.sent = object.getInt("sent");
                final JSONArray parts = object.getJSONArray("parts");
                for (int p = 0; p < parts.length(); p++) {
                    final JSONArray part = parts.getJSONArray(p);
                    transfer.parts.add(new long[]{part.getLong(0), part.getLong(1)});
                }
                // A transfer whose source has been deleted cannot be finished, and keeping it
                // would mean a progress bar that never moves.
                if (new File(transfer.sourcePath).exists()) {
                    transfers.add(transfer);
                }
            }
        } catch (Throwable e) {
            FileLog.e("PrimeBigFileSender.load", e);
        }
    }

    // endregion
}
