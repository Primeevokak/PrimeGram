package org.telegram.messenger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * PrimeGram E2E: sealed-at-rest store for every peer's {@link PrimeE2ESession} this
 * account has. A handful of E2E-enabled chats at most is the expected usage, so all
 * sessions live in one sealed envelope (mirroring {@link PrimePinVault}'s single-file
 * model) rather than one file per peer - every mutation rewrites the whole map, which
 * stays small (tens of KB) and infrequent (once per E2E message, not once per
 * keystroke).
 */
final class PrimeE2EVault {

    private static final int INNER_MAGIC = 0x50474532; // "PGE2"
    private static final int INNER_VERSION = 1;
    private static final byte[] AAD = "PrimeGram E2E sessions v1".getBytes(StandardCharsets.US_ASCII);

    private static final Object LOCK = new Object();
    private static volatile Map<Long, PrimeE2ESession> cached;

    private PrimeE2EVault() {
    }

    private static File dir() {
        return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "primegram/security");
    }

    private static PrimeE2ESealedFile sealedFile() {
        return new PrimeE2ESealedFile(
                new File(dir(), "e2e_sessions.bin"),
                new File(dir(), "e2e_sessions.lock"),
                AAD);
    }

    static PrimeE2ESession getSession(long peerUserId) {
        synchronized (LOCK) {
            return loadAll().get(peerUserId);
        }
    }

    /** Upserts by {@code session.peerUserId} and persists immediately - callers mutate
     *  a {@link PrimeE2ESession} in place (the ratchet's own API works that way) and
     *  must call this right after any state-changing operation (send, receive, DH
     *  ratchet step) so a crash right after can't silently desync the ratchet from
     *  what was actually sent/received. */
    static void putSession(PrimeE2ESession session) {
        synchronized (LOCK) {
            Map<Long, PrimeE2ESession> all = loadAll();
            all.put(session.peerUserId, session);
            persist(all);
        }
    }

    static void deleteSession(long peerUserId) {
        synchronized (LOCK) {
            Map<Long, PrimeE2ESession> all = loadAll();
            if (all.remove(peerUserId) != null) {
                persist(all);
            }
        }
    }

    private static Map<Long, PrimeE2ESession> loadAll() {
        if (cached != null) {
            return cached;
        }
        Map<Long, PrimeE2ESession> map = new HashMap<>();
        try {
            byte[] plain = sealedFile().read(PrimeE2EKeyStore.getOrCreate().key);
            if (plain != null) {
                Map<Long, PrimeE2ESession> loaded = deserialize(plain);
                if (loaded != null) {
                    map = loaded;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        cached = map;
        return map;
    }

    private static void persist(Map<Long, PrimeE2ESession> all) {
        try {
            sealedFile().write(serialize(all), PrimeE2EKeyStore.getOrCreate().key);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static byte[] serialize(Map<Long, PrimeE2ESession> all) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(INNER_MAGIC);
        out.writeInt(INNER_VERSION);
        out.writeInt(all.size());
        for (PrimeE2ESession s : all.values()) {
            out.writeLong(s.selfUserId);
            out.writeLong(s.peerUserId);
            out.writeLong(s.dialogId);
            out.writeInt(s.mode.ordinal());
            PrimeE2EIo.writeBytes(out, s.peerIkSignPub);
            PrimeE2EIo.writeBytes(out, s.rootKey);
            PrimeE2EIo.writeBytes(out, s.sendChainKey);
            out.writeInt(s.sendMessageNumber);
            PrimeE2EIo.writeBytes(out, s.recvChainKey);
            out.writeInt(s.recvMessageNumber);
            out.writeInt(s.previousSendChainLength);
            PrimeE2EIo.writeBytes(out, s.myRatchetPriv);
            PrimeE2EIo.writeBytes(out, s.myRatchetPub);
            PrimeE2EIo.writeBytes(out, s.theirRatchetPub);
            PrimeE2EIo.writeBytes(out, s.pendingSpkPriv);
            PrimeE2EIo.writeBytes(out, s.pendingSpkPub);
            out.writeInt(s.skippedKeys.size());
            for (Map.Entry<String, byte[]> e : s.skippedKeys.entrySet()) {
                PrimeE2EIo.writeBytes(out, e.getKey().getBytes(StandardCharsets.US_ASCII));
                PrimeE2EIo.writeBytes(out, e.getValue());
            }
        }
        return bytes.toByteArray();
    }

    private static Map<Long, PrimeE2ESession> deserialize(byte[] plain) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain));
            if (in.readInt() != INNER_MAGIC || in.readInt() != INNER_VERSION) {
                return null;
            }
            int count = in.readInt();
            if (count < 0 || count > 10_000) {
                return null;
            }
            Map<Long, PrimeE2ESession> map = new HashMap<>();
            for (int i = 0; i < count; i++) {
                PrimeE2ESession s = new PrimeE2ESession();
                s.selfUserId = in.readLong();
                s.peerUserId = in.readLong();
                s.dialogId = in.readLong();
                s.mode = PrimeE2ESession.Mode.values()[in.readInt()];
                s.peerIkSignPub = emptyToNull(PrimeE2EIo.readBytes(in));
                s.rootKey = emptyToNull(PrimeE2EIo.readBytes(in));
                s.sendChainKey = emptyToNull(PrimeE2EIo.readBytes(in));
                s.sendMessageNumber = in.readInt();
                s.recvChainKey = emptyToNull(PrimeE2EIo.readBytes(in));
                s.recvMessageNumber = in.readInt();
                s.previousSendChainLength = in.readInt();
                s.myRatchetPriv = emptyToNull(PrimeE2EIo.readBytes(in));
                s.myRatchetPub = emptyToNull(PrimeE2EIo.readBytes(in));
                s.theirRatchetPub = emptyToNull(PrimeE2EIo.readBytes(in));
                s.pendingSpkPriv = emptyToNull(PrimeE2EIo.readBytes(in));
                s.pendingSpkPub = emptyToNull(PrimeE2EIo.readBytes(in));
                int skippedCount = in.readInt();
                if (skippedCount < 0 || skippedCount > PrimeE2ESession.MAX_SKIPPED_KEYS * 2) {
                    return null;
                }
                for (int j = 0; j < skippedCount; j++) {
                    String keyId = new String(PrimeE2EIo.readBytes(in), StandardCharsets.US_ASCII);
                    byte[] value = PrimeE2EIo.readBytes(in);
                    s.skippedKeys.put(keyId, value);
                }
                map.put(s.peerUserId, s);
            }
            return map;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static byte[] emptyToNull(byte[] b) {
        return (b == null || b.length == 0) ? null : b;
    }
}
