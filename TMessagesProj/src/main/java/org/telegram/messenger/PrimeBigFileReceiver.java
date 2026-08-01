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
        /** Which chat it belongs to - transfers are scoped per chat, not by alias alone. */
        public long dialogId;
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
     * The size to put on a document's card: the whole file when the document is part of one.
     *
     * <p>Reads it out of the header rather than out of any transfer we are tracking, so a part
     * scrolled past in a chat nobody has opened since still says how big the file is. A row
     * standing in for eight gigabytes has no business announcing two.
     */
    public static long displaySize(org.telegram.tgnet.TLRPC.Document document) {
        if (document == null) {
            return 0;
        }
        final PrimeBigFile.Header header = PrimeBigFile.parse(FileLoader.getRawDocumentFileName(document));
        return header != null ? header.totalSize : document.size;
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
        final String key = keyFor(messageObject.getDialogId(), header.alias);
        synchronized (incoming) {
            Incoming transfer = incoming.get(key);
            if (transfer == null) {
                transfer = new Incoming(header.alias);
                incoming.put(key, transfer);
            }
            // Every chunk repeats the header, so these are written each time rather than only from
            // the first part - which is the whole point of repeating them.
            transfer.dialogId = messageObject.getDialogId();
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
        final List<MessageObject> pending = pendingDownloads(account, transfer.dialogId, transfer.alias);
        if (!pending.isEmpty()) {
            final MessageObject next = pending.get(0);
            FileLoader.getInstance(account).loadFile(next.getDocument(), next,
                    FileLoader.PRIORITY_NORMAL, 0);
            return;
        }
        // Every part that exists is on the device. Assembling before the sender has finished
        // uploading would produce a truncated file, so the count has to be complete too.
        if (transfer.isComplete()) {
            assemble(account, transfer.dialogId, transfer.alias, null);
        }
    }

    /**
     * Transfers are kept per chat, not per alias alone.
     *
     * <p>The alias is eight random characters out of thirty-six, so two of them colliding by
     * accident is not a thing that happens. Scoping to the dialog anyway costs nothing and closes
     * the case that is not an accident: a chunk arriving in another chat carrying an alias it was
     * not given would otherwise be filed with somebody else's file and joined into it.
     */
    private static String keyFor(long dialogId, String alias) {
        return dialogId + ":" + alias;
    }

    public Incoming byAlias(long dialogId, String alias) {
        synchronized (incoming) {
            return incoming.get(keyFor(dialogId, alias));
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

    // region acting on the whole file

    /*
     * One row on screen stands for a file that is really seventeen messages, and the actions that
     * row offers have to mean what they look like. Forwarding it must carry all seventeen, or the
     * other side receives a first part and waits forever for the rest; deleting it must remove all
     * seventeen, or sixteen invisible messages are left behind holding several gigabytes on the
     * server that nothing in the interface can ever reach again.
     *
     * Both work from the chunks this client has actually seen. Parts are sent one after another,
     * so in practice they are loaded together and all of them are known - but a set that reaches
     * past the loaded window is expanded as far as it goes rather than not at all, which for
     * deletion is the difference between leaving sixteen orphans and leaving one or two.
     */

    /** Every message making up the same file as this one, in part order, or null if not a chunk. */
    public List<MessageObject> chunksOf(MessageObject messageObject) {
        final PrimeBigFile.Header header = headerOf(messageObject);
        if (header == null) {
            return null;
        }
        final Incoming transfer = byAlias(messageObject.getDialogId(), header.alias);
        if (transfer == null) {
            return null;
        }
        final List<MessageObject> all = new ArrayList<>();
        synchronized (incoming) {
            for (int i = 1; i <= transfer.total; i++) {
                final MessageObject chunk = transfer.chunks.get(i);
                if (chunk != null) {
                    all.add(chunk);
                }
            }
        }
        return all.isEmpty() ? null : all;
    }

    /**
     * The same list with the hidden parts of every chunked file put back, each right behind the
     * part that represents it. Returns the list unchanged when there is nothing to expand.
     */
    public ArrayList<MessageObject> withChunks(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        synchronized (incoming) {
            if (incoming.isEmpty()) {
                return messages;
            }
        }
        ArrayList<MessageObject> expanded = null;
        for (int a = 0; a < messages.size(); a++) {
            final MessageObject message = messages.get(a);
            final List<MessageObject> chunks = chunksOf(message);
            if (chunks == null || chunks.size() <= 1) {
                if (expanded != null) {
                    expanded.add(message);
                }
                continue;
            }
            if (expanded == null) {
                expanded = new ArrayList<>(messages.subList(0, a));
            }
            for (MessageObject chunk : chunks) {
                if (!expanded.contains(chunk)) {
                    expanded.add(chunk);
                }
            }
        }
        return expanded == null ? messages : expanded;
    }

    /** The same for message ids, which is the form deletion takes. */
    public ArrayList<Integer> withChunkIds(long dialogId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }
        ArrayList<Integer> expanded = null;
        synchronized (incoming) {
            if (incoming.isEmpty()) {
                return ids;
            }
            final java.util.Iterator<Incoming> iterator = incoming.values().iterator();
            while (iterator.hasNext()) {
                final Incoming transfer = iterator.next();
                if (transfer.dialogId != dialogId) {
                    continue;
                }
                boolean touched = false;
                for (MessageObject chunk : transfer.chunks.values()) {
                    if (ids.contains(chunk.getId())) {
                        touched = true;
                        break;
                    }
                }
                if (!touched) {
                    continue;
                }
                for (MessageObject chunk : transfer.chunks.values()) {
                    final Integer id = chunk.getId();
                    if (ids.contains(id)) {
                        continue;
                    }
                    if (expanded == null) {
                        expanded = new ArrayList<>(ids);
                    }
                    if (!expanded.contains(id)) {
                        expanded.add(id);
                    }
                }
                // Its messages are on their way out, so the transfer goes with them. Left behind
                // it would keep a set of deleted messages alive and answer questions about a file
                // that no longer exists.
                iterator.remove();
            }
        }
        return expanded == null ? ids : expanded;
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
    public void assemble(int account, long dialogId, String alias, AssemblyCallback callback) {
        final Incoming transfer = byAlias(dialogId, alias);
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
    public List<MessageObject> pendingDownloads(int account, long dialogId, String alias) {
        final Incoming transfer = byAlias(dialogId, alias);
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
