package org.telegram.messenger;

import android.content.Context;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PrimeGram: what actually happens when the emergency PIN is entered.
 *
 * <p>Order matters here and is deliberate at every step (mirrors NovaGram's own sequencing,
 * ported rather than reinvented):
 * <ol>
 *   <li>Best-effort server logout, while real account data and network gating are both still
 *   in their normal state - this is the last moment either is true.
 *   <li>Arm the decoy marker <b>before deleting anything</b> - if the process dies partway
 *   through the steps below, the device must fail toward "looks like a decoy" rather than toward
 *   "still has recoverable real data," and toward "not crash-looping on half-wiped state" rather
 *   than toward the login screen.
 *   <li>Clear {@link UserConfig} for every account slot, delete this feature's own Keystore
 *   alias, then recursively wipe app storage - the marker file wiping itself is expected and
 *   handled by step 4.
 *   <li>Re-arm the marker (same seed as step 2, not a new one, or the decoy would not match
 *   anything it had ever shown before this exact wipe) and seed the fake signed-in account.
 * </ol>
 *
 * <p>Crypto-erase, not overwrite: nothing here tries to overwrite file bytes before deleting them.
 * Flash storage wear-leveling means overwritten sectors are not reliably the same physical NAND
 * cells the original data lived on, so that would be a false guarantee, not a real one - deleting
 * the Keystore key that protected the PIN vault is the actual erasure event for that data, and for
 * everything else, standard filesystem deletion is what this scope commits to.
 */
public final class PrimeEmergencyWipe {

    private PrimeEmergencyWipe() {
    }

    /** Must be called off the UI thread - this does real (if bounded) network I/O and recursive
     *  filesystem work. The caller kills the process immediately after this returns. */
    public static void run(Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;

        bestEffortLogoutAllAccounts();

        // Captured before anything is touched, so re-arming after the wipe (step 4) shows the
        // exact same persona a partial failure between here and then would have already
        // committed to - never two different decoys for one wipe.
        final long seed = PrimeDecoyState.newSeed();
        PrimeDecoyState.arm(app, seed);

        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                try {
                    UserConfig.getInstance(a).clearConfig();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            PrimePinKeyStore.deleteQuietly();
        } catch (Throwable ignored) {
        }
        try {
            File pinDir = new File(app.getNoBackupFilesDir(), "primegram/security");
            deleteFile(new File(pinDir, "pin_state.bin"));
            deleteFile(new File(pinDir, "pin_state.lock"));
        } catch (Throwable ignored) {
        }

        wipeStorage(app);

        // The wipe above deleted the marker written in step 2 along with everything else -
        // this is the moment the device would otherwise show a blank/broken app or the normal
        // login screen instead of a decoy, so it is re-armed immediately, same seed.
        PrimeDecoyState.arm(app, seed);

        try {
            PrimeDecoyPersona persona = PrimeDecoyPersona.generate(seed);
            PrimeDecoyAccount.seed(persona);
        } catch (Throwable t) {
            FileLog.e(t);
        }

        try {
            PrimePinSession.setEnabled(false);
        } catch (Throwable ignored) {
        }
    }

    private static void bestEffortLogoutAllAccounts() {
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (!UserConfig.getInstance(a).isClientActivated()) {
                    continue;
                }
                final CountDownLatch latch = new CountDownLatch(1);
                try {
                    org.telegram.tgnet.ConnectionsManager.getInstance(a).sendRequest(
                            new org.telegram.tgnet.TLRPC.TL_auth_logOut(),
                            (response, error) -> latch.countDown());
                } catch (Throwable t) {
                    latch.countDown();
                }
                try {
                    // Bounded: under coercion this must finish in a predictable, short time
                    // regardless of network state, not hang the whole wipe on an unreachable
                    // server.
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void wipeStorage(Context app) {
        deleteContents(app.getFilesDir());
        deleteContents(app.getCacheDir());
        deleteContents(app.getNoBackupFilesDir());
        try {
            deleteContents(app.getExternalFilesDir(null));
        } catch (Throwable ignored) {
        }
        try {
            deleteContents(app.getExternalCacheDir());
        } catch (Throwable ignored) {
        }
        // UserConfig.clearConfig() (called above, per account) already clears its OWN prefs
        // through the SharedPreferences API - correct for that one file, but every OTHER prefs
        // file this app has ever written (proxy settings, notification state, cached search
        // history, feature toggles that happen to carry identifying data) lives here too, as its
        // own separate XML file, and none of those go through clearConfig() at all. This is the
        // backstop that actually gets all of them, not just the one this class happened to name.
        try {
            File dataDir = new File(app.getApplicationInfo().dataDir);
            deleteContents(new File(dataDir, "shared_prefs"));
            deleteContents(new File(dataDir, "databases"));
        } catch (Throwable ignored) {
        }
    }

    private static void deleteContents(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteFile(child);
        }
    }

    private static void deleteFile(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        try {
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteFile(child);
                    }
                }
            }
            f.delete();
        } catch (Throwable ignored) {
            // One locked/busy file must not abort the rest of the wipe.
        }
    }
}
