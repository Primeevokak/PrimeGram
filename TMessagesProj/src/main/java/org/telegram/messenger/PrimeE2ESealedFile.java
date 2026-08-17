package org.telegram.messenger;

import android.util.AtomicFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * PrimeGram E2E: a magic-tagged, AES-256-GCM-with-AAD, atomically-written sealed file -
 * the exact envelope shape {@link PrimePinVault} already uses in production, factored
 * out here so both {@link PrimeE2EIdentity} and {@link PrimeE2EVault} (the sessions
 * store) share one implementation instead of two copies of the same ~80 lines. Callers
 * own their own plaintext serialization; this class only handles "seal a byte blob to
 * disk" / "unseal it back," under a caller-supplied {@link SecretKey} and AAD.
 */
final class PrimeE2ESealedFile {

    private static final int OUTER_MAGIC = 0x50475345; // "PGSE" - same tag family as PrimePinVault
    private static final int STORAGE_VERSION = 1;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_STATE_BYTES = 256 * 1024;

    private final File stateFile;
    private final File lockFile;
    private final byte[] aad;

    PrimeE2ESealedFile(File stateFile, File lockFile, byte[] aad) {
        this.stateFile = stateFile;
        this.lockFile = lockFile;
        this.aad = aad;
    }

    boolean exists() {
        return stateFile.exists();
    }

    void delete() {
        stateFile.delete();
        lockFile.delete();
    }

    private FileLock acquireLock() throws Exception {
        File dir = stateFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
        FileChannel channel = raf.getChannel();
        return channel.lock();
    }

    /** @return the decrypted plaintext blob, or null if the file is missing, corrupt,
     *  truncated, or fails authentication - callers must treat any of those the same
     *  way ("no usable state"), never distinguish and never throw further up. */
    byte[] read(SecretKey key) {
        try (FileLock ignored = acquireLock()) {
            if (!stateFile.exists()) {
                return null;
            }
            AtomicFile file = new AtomicFile(stateFile);
            byte[] envelope = file.readFully();
            if (envelope.length > MAX_STATE_BYTES + GCM_IV_LEN + 64) {
                return null;
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(envelope));
            if (in.readInt() != OUTER_MAGIC) {
                return null;
            }
            if (in.readInt() != STORAGE_VERSION) {
                return null;
            }
            byte[] iv = new byte[GCM_IV_LEN];
            in.readFully(iv);
            byte[] cipherText = new byte[in.available()];
            in.readFully(cipherText);

            if (key == null) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(cipherText);
        } catch (EOFException eof) {
            return null; // truncated - corrupt, not "retry forever"
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    void write(byte[] plaintext, SecretKey key) throws Exception {
        if (plaintext.length > MAX_STATE_BYTES) {
            throw new IllegalStateException("PrimeE2E sealed state too large: " + plaintext.length);
        }
        try (FileLock ignored = acquireLock()) {
            // AndroidKeyStore keys are generated with setRandomizedEncryptionRequired(true)
            // (see PrimeE2EKeyStore) - under that setting the Keystore itself owns IV
            // generation for ENCRYPT_MODE and rejects a caller-supplied one, so the IV is
            // read back via cipher.getIV() AFTER init, not generated here (same gotcha
            // PrimePinVault already documents and works around).
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != GCM_IV_LEN) {
                throw new IllegalStateException("Keystore returned an unusable IV (len=" + (iv == null ? -1 : iv.length) + ")");
            }
            cipher.updateAAD(aad);
            byte[] cipherText = cipher.doFinal(plaintext);

            ByteArrayOutputStream envelope = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(envelope);
            out.writeInt(OUTER_MAGIC);
            out.writeInt(STORAGE_VERSION);
            out.write(iv);
            out.write(cipherText);

            File dir = stateFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            AtomicFile file = new AtomicFile(stateFile);
            FileOutputStream fos = file.startWrite();
            try {
                fos.write(envelope.toByteArray());
                file.finishWrite(fos);
            } catch (Exception e) {
                file.failWrite(fos);
                throw e;
            }
        }
    }
}
