package org.telegram.messenger;

import android.content.Context;
import android.provider.Settings;
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
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * PrimeGram: sealed on-disk PIN state - primary PIN, optional emergency PIN, and the
 * lockout counters, encrypted under an Android Keystore key ({@link PrimePinKeyStore}) so
 * "reset the PIN" without knowing it means deleting the key, not guessing the file format.
 *
 * <p>Deliberately separate from stock {@code SharedConfig} passcode: that one is a single
 * SHA-256 iteration in plain SharedPreferences, fine as a UI-only gate but not something
 * this feature should either weaken (by touching it) or inherit the weakness of (by
 * building on it). This is its own file, own key, own opt-in.
 */
public final class PrimePinVault {

    private static final int OUTER_MAGIC = 0x50475345; // "PGSE"
    private static final int INNER_MAGIC = 0x50475031; // "PGP1"
    private static final int STORAGE_VERSION = 1;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] AAD = "PrimeGram protected PIN state v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final int MAX_STATE_BYTES = 16 * 1024;
    private static final int MAX_KDF_ITERATIONS = 5_000_000;

    private static final Object PROCESS_LOCK = new Object();

    private final Context context;

    public PrimePinVault(Context context) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    public enum VaultState { NOT_ENROLLED, ENROLLED, CORRUPT, UNAVAILABLE }

    public enum EnrollmentResult { ENROLLED, INVALID_PIN, SOFTWARE_CONSENT_REQUIRED, ALREADY_ENROLLED, CORRUPT_STATE, UNAVAILABLE }

    public enum EmergencyStatus { SET, REMOVED, SAME_AS_PRIMARY, INVALID_PIN, NOT_ENROLLED, UNAVAILABLE }

    public enum VerificationStatus { ACCEPTED, EMERGENCY, REJECTED, LOCKED, INVALID_PIN, NOT_ENROLLED, CORRUPT_STATE, UNAVAILABLE }

    public static final class VaultInspection {
        public final VaultState state;
        public final long retryAfterMillis;
        public final PrimePinKeyStore.ProtectionLevel protectionLevel;

        VaultInspection(VaultState state, long retryAfterMillis, PrimePinKeyStore.ProtectionLevel protectionLevel) {
            this.state = state;
            this.retryAfterMillis = retryAfterMillis;
            this.protectionLevel = protectionLevel;
        }
    }

    public static final class VerificationResult {
        public final VerificationStatus status;
        public final long retryAfterMillis;
        public final int failedAttempts;
        public final PrimePinKeyStore.ProtectionLevel protectionLevel;

        VerificationResult(VerificationStatus status, long retryAfterMillis, int failedAttempts, PrimePinKeyStore.ProtectionLevel protectionLevel) {
            this.status = status;
            this.retryAfterMillis = retryAfterMillis;
            this.failedAttempts = failedAttempts;
            this.protectionLevel = protectionLevel;
        }
    }

    private static final class InnerState {
        int kdfIterations;
        PrimePinKeyStore.ProtectionLevel protectionLevel;
        int failedAttempts;
        long lockoutStart;
        long lockoutDeadline;
        int bootCount;
        byte[] salt;
        byte[] verifier;
        boolean hasEmergency;
        byte[] emergencySalt;
        byte[] emergencyVerifier;
    }

    private File dir() {
        return new File(context.getNoBackupFilesDir(), "primegram/security");
    }

    private File stateFile() {
        return new File(dir(), "pin_state.bin");
    }

    private File lockFile() {
        return new File(dir(), "pin_state.lock");
    }

    private static int readBootCount(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT);
        } catch (Settings.SettingNotFoundException | SecurityException e) {
            return PrimePinLockout.UNKNOWN_BOOT_COUNT;
        }
    }

    public boolean isEnrolled() {
        return stateFile().exists();
    }

    public VaultInspection inspect() {
        synchronized (PROCESS_LOCK) {
            try (FileLock ignored = acquireLock()) {
                if (!stateFile().exists()) {
                    return new VaultInspection(VaultState.NOT_ENROLLED, 0L, PrimePinKeyStore.ProtectionLevel.UNKNOWN);
                }
                InnerState state = readState();
                if (state == null) {
                    return new VaultInspection(VaultState.CORRUPT, 0L, PrimePinKeyStore.ProtectionLevel.UNKNOWN);
                }
                PrimePinLockout.State lockState = toLockoutState(state);
                PrimePinLockout.Evaluation eval = PrimePinLockout.evaluate(lockState, android.os.SystemClock.elapsedRealtime(), readBootCount(context));
                return new VaultInspection(VaultState.ENROLLED, eval.remainingMillis, state.protectionLevel);
            } catch (Exception e) {
                FileLog.e(e);
                return new VaultInspection(VaultState.UNAVAILABLE, 0L, PrimePinKeyStore.ProtectionLevel.UNKNOWN);
            }
        }
    }

    public EnrollmentResult enroll(char[] pin, boolean acceptSoftwareProtection) {
        if (!PrimePinContract.isValidPin(pin)) {
            return EnrollmentResult.INVALID_PIN;
        }
        synchronized (PROCESS_LOCK) {
            try (FileLock ignored = acquireLock()) {
                if (stateFile().exists()) {
                    return EnrollmentResult.ALREADY_ENROLLED;
                }
                PrimePinKeyStore.KeyMaterial keyMaterial = PrimePinKeyStore.getOrCreate();
                if (!keyMaterial.level.secure && !acceptSoftwareProtection) {
                    return EnrollmentResult.SOFTWARE_CONSENT_REQUIRED;
                }
                byte[] salt = PrimePinKdf.randomSalt();
                byte[] verifier = PrimePinKdf.createVerifier(pin, salt, PrimePinKdf.ITERATIONS);
                InnerState state = new InnerState();
                state.kdfIterations = PrimePinKdf.ITERATIONS;
                state.protectionLevel = keyMaterial.level;
                state.failedAttempts = 0;
                state.lockoutStart = 0L;
                state.lockoutDeadline = 0L;
                state.bootCount = readBootCount(context);
                state.salt = salt;
                state.verifier = verifier;
                state.hasEmergency = false;
                writeState(state, keyMaterial.key);
                return EnrollmentResult.ENROLLED;
            } catch (Exception e) {
                FileLog.e(e);
                return EnrollmentResult.UNAVAILABLE;
            }
        }
    }

    public EmergencyStatus enrollEmergency(char[] pin) {
        synchronized (PROCESS_LOCK) {
            try (FileLock ignored = acquireLock()) {
                if (!stateFile().exists()) {
                    return EmergencyStatus.NOT_ENROLLED;
                }
                InnerState state = readState();
                if (state == null) {
                    return EmergencyStatus.UNAVAILABLE;
                }
                if (pin == null || pin.length == 0) {
                    state.hasEmergency = false;
                    state.emergencySalt = null;
                    state.emergencyVerifier = null;
                    writeState(state, PrimePinKeyStore.load());
                    return EmergencyStatus.REMOVED;
                }
                if (!PrimePinContract.isValidPin(pin)) {
                    return EmergencyStatus.INVALID_PIN;
                }
                if (PrimePinKdf.verify(pin, state.salt, state.kdfIterations, state.verifier)) {
                    return EmergencyStatus.SAME_AS_PRIMARY;
                }
                byte[] salt = PrimePinKdf.randomSalt();
                state.emergencySalt = salt;
                state.emergencyVerifier = PrimePinKdf.createVerifier(pin, salt, state.kdfIterations);
                state.hasEmergency = true;
                writeState(state, PrimePinKeyStore.load());
                return EmergencyStatus.SET;
            } catch (Exception e) {
                FileLog.e(e);
                return EmergencyStatus.UNAVAILABLE;
            }
        }
    }

    /**
     * Emergency PIN is checked BEFORE the lockout gate, on purpose: under coercion an
     * attacker can burn attempts until the delay is long, and if the emergency PIN were
     * blocked by that same delay, the one scenario it exists for would be exactly the case
     * where it doesn't work.
     */
    public VerificationResult verify(char[] pin) {
        synchronized (PROCESS_LOCK) {
            try (FileLock ignored = acquireLock()) {
                if (!stateFile().exists()) {
                    return new VerificationResult(VerificationStatus.NOT_ENROLLED, 0L, 0, PrimePinKeyStore.ProtectionLevel.UNKNOWN);
                }
                InnerState state = readState();
                if (state == null) {
                    return new VerificationResult(VerificationStatus.CORRUPT_STATE, 0L, 0, PrimePinKeyStore.ProtectionLevel.UNKNOWN);
                }
                int bootCount = readBootCount(context);
                PrimePinLockout.Evaluation eval = PrimePinLockout.evaluate(toLockoutState(state), android.os.SystemClock.elapsedRealtime(), bootCount);
                if (eval.stateChanged) {
                    applyLockoutState(state, eval.state);
                }

                if (state.hasEmergency && PrimePinKdf.verify(pin, state.emergencySalt, state.kdfIterations, state.emergencyVerifier)) {
                    if (eval.stateChanged) {
                        writeState(state, PrimePinKeyStore.load());
                    }
                    return new VerificationResult(VerificationStatus.EMERGENCY, 0L, 0, state.protectionLevel);
                }

                if (eval.isLocked()) {
                    if (eval.stateChanged) {
                        writeState(state, PrimePinKeyStore.load());
                    }
                    return new VerificationResult(VerificationStatus.LOCKED, eval.remainingMillis, state.failedAttempts, state.protectionLevel);
                }

                if (!PrimePinContract.isValidPin(pin)) {
                    return new VerificationResult(VerificationStatus.INVALID_PIN, 0L, state.failedAttempts, state.protectionLevel);
                }

                if (PrimePinKdf.verify(pin, state.salt, state.kdfIterations, state.verifier)) {
                    PrimePinLockout.State cleared = PrimePinLockout.afterSuccess(bootCount);
                    applyLockoutState(state, cleared);
                    writeState(state, PrimePinKeyStore.load());
                    return new VerificationResult(VerificationStatus.ACCEPTED, 0L, 0, state.protectionLevel);
                }

                PrimePinLockout.State failed = PrimePinLockout.afterFailure(toLockoutState(state), android.os.SystemClock.elapsedRealtime(), bootCount);
                applyLockoutState(state, failed);
                writeState(state, PrimePinKeyStore.load());
                PrimePinLockout.Evaluation postFailure = PrimePinLockout.evaluate(failed, android.os.SystemClock.elapsedRealtime(), bootCount);
                return new VerificationResult(VerificationStatus.REJECTED, postFailure.remainingMillis, state.failedAttempts, state.protectionLevel);
            } catch (Exception e) {
                FileLog.e(e);
                return new VerificationResult(VerificationStatus.UNAVAILABLE, 0L, 0, PrimePinKeyStore.ProtectionLevel.UNKNOWN);
            }
        }
    }

    /** Verifies, and on success (primary OR emergency) deletes the vault file and Keystore
     *  key together - entering the emergency PIN here wipes rather than merely disables,
     *  which is intentional: this screen exists specifically as a "remove PIN" surface an
     *  attacker under coercion might be told to use. */
    public VerificationResult disable(char[] pin) {
        VerificationResult result = verify(pin);
        if (result.status == VerificationStatus.ACCEPTED || result.status == VerificationStatus.EMERGENCY) {
            synchronized (PROCESS_LOCK) {
                stateFile().delete();
                lockFile().delete();
                PrimePinKeyStore.deleteQuietly();
            }
        }
        return result;
    }

    private PrimePinLockout.State toLockoutState(InnerState s) {
        return new PrimePinLockout.State(s.failedAttempts, s.lockoutStart, s.lockoutDeadline, s.bootCount);
    }

    private void applyLockoutState(InnerState s, PrimePinLockout.State lock) {
        s.failedAttempts = lock.failedAttempts;
        s.lockoutStart = lock.lockoutStartedElapsedRealtime;
        s.lockoutDeadline = lock.lockoutDeadlineElapsedRealtime;
        s.bootCount = lock.bootCount;
    }

    private FileLock acquireLock() throws Exception {
        File dir = dir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        RandomAccessFile raf = new RandomAccessFile(lockFile(), "rw");
        FileChannel channel = raf.getChannel();
        return channel.lock();
    }

    private InnerState readState() {
        try {
            AtomicFile file = new AtomicFile(stateFile());
            byte[] envelope = file.readFully();
            if (envelope.length > MAX_STATE_BYTES + GCM_IV_LEN + 64) {
                return null;
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(envelope));
            int magic = in.readInt();
            if (magic != OUTER_MAGIC) {
                return null;
            }
            int version = in.readInt();
            if (version != STORAGE_VERSION) {
                return null;
            }
            byte[] iv = new byte[GCM_IV_LEN];
            in.readFully(iv);
            byte[] cipherText = new byte[in.available()];
            in.readFully(cipherText);

            SecretKey key = PrimePinKeyStore.load();
            if (key == null) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] plain = cipher.doFinal(cipherText);

            DataInputStream innerIn = new DataInputStream(new ByteArrayInputStream(plain));
            if (innerIn.readInt() != INNER_MAGIC) {
                return null;
            }
            InnerState state = new InnerState();
            innerIn.readInt(); // inner version, reserved
            state.kdfIterations = innerIn.readInt();
            if (state.kdfIterations < PrimePinKdf.ITERATIONS || state.kdfIterations > MAX_KDF_ITERATIONS) {
                return null;
            }
            state.protectionLevel = PrimePinKeyStore.ProtectionLevel.fromStorageId(innerIn.readInt());
            state.failedAttempts = innerIn.readInt();
            state.lockoutStart = innerIn.readLong();
            state.lockoutDeadline = innerIn.readLong();
            state.bootCount = innerIn.readInt();
            state.salt = readLenPrefixed(innerIn);
            state.verifier = readLenPrefixed(innerIn);
            state.hasEmergency = innerIn.readBoolean();
            if (state.hasEmergency) {
                state.emergencySalt = readLenPrefixed(innerIn);
                state.emergencyVerifier = readLenPrefixed(innerIn);
            }
            return state;
        } catch (EOFException eof) {
            return null; // truncated file - treat as corrupt, not "retry forever"
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void writeState(InnerState state, SecretKey key) throws Exception {
        ByteArrayOutputStream innerBytes = new ByteArrayOutputStream();
        DataOutputStream innerOut = new DataOutputStream(innerBytes);
        innerOut.writeInt(INNER_MAGIC);
        innerOut.writeInt(0);
        innerOut.writeInt(state.kdfIterations);
        innerOut.writeInt(state.protectionLevel.storageId);
        innerOut.writeInt(state.failedAttempts);
        innerOut.writeLong(state.lockoutStart);
        innerOut.writeLong(state.lockoutDeadline);
        innerOut.writeInt(state.bootCount);
        writeLenPrefixed(innerOut, state.salt);
        writeLenPrefixed(innerOut, state.verifier);
        innerOut.writeBoolean(state.hasEmergency);
        if (state.hasEmergency) {
            writeLenPrefixed(innerOut, state.emergencySalt);
            writeLenPrefixed(innerOut, state.emergencyVerifier);
        }
        byte[] plain = innerBytes.toByteArray();
        if (plain.length > MAX_STATE_BYTES) {
            throw new IllegalStateException("PIN state too large");
        }

        byte[] iv = new byte[GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(AAD);
        byte[] cipherText = cipher.doFinal(plain);

        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(envelope);
        out.writeInt(OUTER_MAGIC);
        out.writeInt(STORAGE_VERSION);
        out.write(iv);
        out.write(cipherText);

        File dir = dir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        AtomicFile file = new AtomicFile(stateFile());
        FileOutputStream fos = file.startWrite();
        try {
            fos.write(envelope.toByteArray());
            file.finishWrite(fos);
        } catch (Exception e) {
            file.failWrite(fos);
            throw e;
        }
    }

    private static byte[] readLenPrefixed(DataInputStream in) throws java.io.IOException {
        int len = in.readInt();
        if (len < 0 || len > 4096) {
            throw new java.io.IOException("bad length prefix");
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    private static void writeLenPrefixed(DataOutputStream out, byte[] data) throws java.io.IOException {
        out.writeInt(data.length);
        out.write(data);
    }
}
