package org.telegram.messenger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * PrimeGram: puts a chunked file back together on the receiving side.
 *
 * <p>Everything here is driven by the header in each chunk's file name, so nothing depends on
 * having seen the transfer start. A chat opened in the middle of an upload, or scrolled into from
 * the top a week later, produces the same picture: one file, its real name, and how much of it has
 * arrived.
 *
 * <p>Assembly appends each part to the destination and deletes the part immediately. Keeping all
 * the chunks and then joining them would need twice the file's size in free space, which for the
 * files this feature exists for is the difference between working and not.
 */
public final class PrimeBigFileReceiver implements NotificationCenter.NotificationCenterDelegate {

    private static volatile PrimeBigFileReceiver instance;
    private boolean observing;

    /** Everything known about one incoming transfer, gathered from the chunks seen so far. */
    public static final class Incoming {
        public final String alias;
        public String fileName;
        public long totalSize;
        public int total;
        /** Chunk index (1-based) to the message carrying it. */
        public final HashMap<Integer, MessageObject> chunks = new HashMap<>();

        Incoming(String alias) {
            this.alias = alias;
        }

        public int received() {
            return chunks.size();
        }

        public boolean isComplete() {
            if (total <= 0 || chunks.size() < total) {
                return false;
            }
            for (int i = 1; i <= total; i++) {
                if (!chunks.containsKey(i)) {
                    return false;
                }
            }
            return true;
        }

        /** How much of the file exists on the server so far, as a fraction. */
        public float progress() {
            return total <= 0 ? 0 : Math.min(1f, received() / (float) total);
        }

        /** How much of it is on this device, as a fraction. */
        public float downloadProgress(int account) {
            if (total <= 0) {
                return 0;
            }
            int done = 0;
            for (int i = 1; i <= total; i++) {
                final MessageObject chunk = chunks.get(i);
                if (chunk == null) {
                    continue;
                }
                final File file = FileLoader.getInstance(account).getPathToMessage(chunk.messageOwner);
                if (file != null && file.exists()) {
                    done++;
                }
            }
            return Math.min(1f, done / (float) total);
        }

        /** A percentage rather than a count of parts: the parts are our business, not the user's. */
        public String describe(int account) {
            return String.format(Locale.US, "%d%%", Math.round(downloadProgress(account) * 100));
        }
    }

    private final HashMap<String, Incoming> incoming = new HashMap<>();

    public static PrimeBigFileReceiver getInstance() {
        PrimeBigFileReceiver local = instance;
        if (local == null) {
            synchronized (PrimeBigFileReceiver.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimeBigFileReceiver();
                }
            }
        }
        return local;
    }

    // region recognising chunks

    /** The header on this message, or null when it is an ordinary file. */
    public static PrimeBigFile.Header headerOf(MessageObject messageObject) {
        if (messageObject == null || messageObject.getDocument() == null) {
            return null;
        }
        // The raw name: the ordinary accessor has already stripped the header, which is exactly
        // what every other caller wants and exactly what this one must not have.
        return PrimeBigFile.parse(FileLoader.getRawDocumentFileName(messageObject.getDocument()));
    }

    /**
     * Records a chunk that is on screen or in the loaded history.
     *
     * @return the transfer this chunk belongs to, or null when the message is not a chunk
     */
    public Incoming observe(MessageObject messageObject) {
        final PrimeBigFile.Header header = headerOf(messageObject);
        if (header == null) {
            return null;
        }
        synchronized (incoming) {
            Incoming transfer = incoming.get(header.alias);
            if (transfer == null) {
                transfer = new Incoming(header.alias);
                incoming.put(header.alias, transfer);
            }
            // Every chunk repeats the header, so these are written each time rather than only from
            // the first part - which is the whole point of repeating them.
            transfer.fileName = header.fileName;
            transfer.totalSize = header.totalSize;
            transfer.total = header.total;
            transfer.chunks.put(header.index, messageObject);
            startObserving();
            return transfer;
        }
    }

    /**
     * Keeps the download going once the user has started it.
     *
     * <p>The interface deliberately has nothing of its own: the stand-in message shows Telegram's
     * ordinary download button, and tapping it fetches the first part. When a part finishes, the
     * next one starts here, and when the last one lands the file is assembled. Stopping is
     * whatever the user already knows - cancel the download and the chain simply stops advancing,
     * because it only ever moves on completion.
     */
    private void startObserving() {
        if (observing) {
            return;
        }
        observing = true;
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.fileLoaded);
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.fileLoaded || args.length == 0 || !(args[0] instanceof String)) {
            return;
        }
        final String finished = (String) args[0];
        final List<Incoming> snapshot;
        synchronized (incoming) {
            snapshot = new ArrayList<>(incoming.values());
        }
        for (Incoming transfer : snapshot) {
            boolean mine = false;
            for (MessageObject chunk : transfer.chunks.values()) {
                if (chunk.getDocument() != null
                        && finished.equals(FileLoader.getAttachFileName(chunk.getDocument()))) {
                    mine = true;
                    break;
                }
            }
            if (mine) {
                continueDownload(account, transfer);
                return;
            }
        }
    }

    private void continueDownload(int account, Incoming transfer) {
        final List<MessageObject> pending = pendingDownloads(account, transfer.alias);
        if (!pending.isEmpty()) {
            final MessageObject next = pending.get(0);
            FileLoader.getInstance(account).loadFile(next.getDocument(), next,
                    FileLoader.PRIORITY_NORMAL, 0);
            return;
        }
        // Every part that exists is on the device. Assembling before the sender has finished
        // uploading would produce a truncated file, so the count has to be complete too.
        if (transfer.isComplete()) {
            assemble(account, transfer.alias, null);
        }
    }

    public Incoming byAlias(String alias) {
        synchronized (incoming) {
            return incoming.get(alias);
        }
    }

    /**
     * Whether this message should be drawn at all.
     *
     * <p>Only the first chunk of a set is shown, standing in for the whole file; the rest are
     * hidden. Choosing the first rather than the lowest-numbered <em>present</em> one keeps the
     * file in the place in the conversation where it was actually sent.
     */
    public boolean isHiddenChunk(MessageObject messageObject) {
        final PrimeBigFile.Header header = headerOf(messageObject);
        return header != null && header.index != 1;
    }

    // endregion

    // region assembly

    public interface AssemblyCallback {
        void onProgress(int done, int total);

        void onFinished(File result, String error);
    }

    /**
     * Joins the parts that have been downloaded into the destination file.
     *
     * <p>Runs off the main thread and can be called again as more parts arrive: it appends from
     * wherever it left off, so a user who starts the download early gets a file that grows rather
     * than one that begins again.
     */
    public void assemble(int account, String alias, AssemblyCallback callback) {
        final Incoming transfer = byAlias(alias);
        if (transfer == null) {
            if (callback != null) {
                callback.onFinished(null, "неизвестная передача");
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            File target = null;
            String error = null;
            try {
                final File dir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT);
                target = new File(dir, transfer.fileName);
                try (FileOutputStream out = new FileOutputStream(target, true)) {
                    for (int i = 1; i <= transfer.total; i++) {
                        final MessageObject chunk = transfer.chunks.get(i);
                        if (chunk == null) {
                            // A gap: everything after it has to wait, because appending out of
                            // order would silently corrupt the result.
                            break;
                        }
                        final File part = FileLoader.getInstance(account)
                                .getPathToMessage(chunk.messageOwner);
                        if (part == null || !part.exists()) {
                            break;
                        }
                        try (FileInputStream in = new FileInputStream(part)) {
                            final byte[] buffer = new byte[1024 * 1024];
                            int read;
                            while ((read = in.read(buffer)) > 0) {
                                out.write(buffer, 0, read);
                            }
                        }
                        //noinspection ResultOfMethodCallIgnored
                        part.delete();
                        final int done = i;
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onProgress(done, transfer.total));
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e("PrimeBigFileReceiver.assemble", e);
                error = e.getMessage() == null ? "ошибка сборки" : e.getMessage();
            }
            final File result = target;
            final String reason = error;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onFinished(reason == null ? result : null, reason));
            }
        });
    }

    /** The parts that have not been downloaded yet, so the UI can queue them. */
    public List<MessageObject> pendingDownloads(int account, String alias) {
        final Incoming transfer = byAlias(alias);
        final List<MessageObject> pending = new ArrayList<>();
        if (transfer == null) {
            return pending;
        }
        for (int i = 1; i <= transfer.total; i++) {
            final MessageObject chunk = transfer.chunks.get(i);
            if (chunk == null) {
                continue;
            }
            final File file = FileLoader.getInstance(account).getPathToMessage(chunk.messageOwner);
            if (file == null || !file.exists()) {
                pending.add(chunk);
            }
        }
        return pending;
    }

    // endregion
}
