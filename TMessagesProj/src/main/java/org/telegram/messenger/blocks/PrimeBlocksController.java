package org.telegram.messenger.blocks;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * PrimeGram Blocks: the catalogue of installed `.pr` scripts, mirroring
 * {@code PrimePluginsController}'s shape but with no Python engine underneath - there is nothing
 * to load into an interpreter here, only a JSON tree {@link BlockRuntime} (Phase D) reads
 * directly. Install/delete/enable work runs on {@link Utilities#globalQueue} instead of a
 * Chaquopy queue, since there is no interpreter thread to serialize against.
 */
public final class PrimeBlocksController {

    /** What a Blocks script file is called, and the only extension we will install. Distinct from `.plugin`. */
    public static final String EXTENSION = ".pr";

    /** Anything larger than this is not a hand-composed block script; refuse early. */
    private static final long MAX_SIZE = 2 * 1024 * 1024;

    private static volatile PrimeBlocksController instance;

    private final List<PrimeBlockScript> scripts = new ArrayList<>();
    private volatile boolean cataloguedOnce;

    private PrimeBlocksController() {
    }

    public static PrimeBlocksController getInstance() {
        PrimeBlocksController local = instance;
        if (local == null) {
            synchronized (PrimeBlocksController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimeBlocksController();
                }
            }
        }
        return local;
    }

    /** Where installed `.pr` scripts live - our own files dir, so uninstalling the app takes them too. */
    public static File blocksDir() {
        final File dir = new File(ApplicationLoader.getFilesDirFixed(), "blocks");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public List<PrimeBlockScript> getScripts() {
        synchronized (scripts) {
            return new ArrayList<>(scripts);
        }
    }

    public PrimeBlockScript findById(String id) {
        if (id == null) {
            return null;
        }
        synchronized (scripts) {
            for (PrimeBlockScript s : scripts) {
                if (id.equals(s.id())) {
                    return s;
                }
            }
        }
        return null;
    }

    public int count() {
        synchronized (scripts) {
            return scripts.size();
        }
    }

    /** Safe to call repeatedly - only the first call actually rescans. */
    public void loadIfNeeded() {
        if (cataloguedOnce) {
            return;
        }
        cataloguedOnce = true;
        Utilities.globalQueue.postRunnable(this::rescanLocked);
    }

    private void rescanLocked() {
        final List<PrimeBlockScript> found = new ArrayList<>();
        final File[] files = blocksDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(EXTENSION)) {
                    continue;
                }
                try {
                    final PrimeBlockManifest manifest = PrimeBlockManifest.parse(file);
                    final PrimeBlockScript script = new PrimeBlockScript(manifest, file);
                    script.setEnabled(PrimeBlockStore.isEnabled(manifest.id));
                    // Phase A/E's per-block-type-id check, re-run at load time - see the plan's
                    // §2.2 item 4: a script installed on a newer build than this one is running
                    // must come up force-disabled, not silently half-run.
                    final Set<String> used = manifest.usedBlockTypeIds();
                    final BlockRegistry.CompatibilityResult compat =
                            BlockRegistry.checkCompatibility(used, BuildVars.BUILD_VERSION_STRING);
                    script.setUnsupported(!compat.isCompatible());
                    found.add(script);
                } catch (Throwable e) {
                    // A file we cannot parse at all is not shown - there is no id to list it
                    // under and nothing truthful to say about it beyond its filename.
                    FileLog.e(e);
                }
            }
        }
        Collections.sort(found, (a, b) -> a.name().compareToIgnoreCase(b.name()));
        synchronized (scripts) {
            scripts.clear();
            scripts.addAll(found);
        }
        notifyChanged();
    }

    public interface InstallCallback {
        void onInstalled(PrimeBlockScript script, boolean replacedExisting);

        void onFailed(String reason);
    }

    /** Reads a candidate file's manifest without installing anything, for the confirmation sheet. */
    public static PrimeBlockManifest inspect(File file) throws PrimeBlockManifest.ParseException, IOException {
        if (file == null || !file.exists()) {
            throw new IOException("no file");
        }
        if (file.length() > MAX_SIZE) {
            throw new IOException("too large");
        }
        return PrimeBlockManifest.parse(file);
    }

    /**
     * Copies a `.pr` file into our directory and leaves it disabled. Overwriting an existing
     * script of the same id is the update path, and - like `.plugin` - keeps whatever on/off
     * state the user had chosen, since they are replacing a version of something they already
     * configured, not installing a stranger that happens to share a name.
     */
    public void install(Context context, File source, InstallCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            PrimeBlockManifest manifest = null;
            String failure = null;
            boolean replaced = false;
            File target = null;
            try {
                manifest = inspect(source);
                final Set<String> used = manifest.usedBlockTypeIds();
                final BlockRegistry.CompatibilityResult compat =
                        BlockRegistry.checkCompatibility(used, BuildVars.BUILD_VERSION_STRING);
                if (!compat.isCompatible()) {
                    failure = "version";
                } else {
                    target = new File(blocksDir(), manifest.id + EXTENSION);
                    replaced = target.exists();
                    copy(source, target);
                }
            } catch (PrimeBlockManifest.ParseException e) {
                failure = "malformed";
            } catch (Throwable e) {
                FileLog.e(e);
                failure = "io";
            }

            final String reason = failure;
            if (reason != null) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onFailed(reason));
                }
                return;
            }

            final PrimeBlockScript script = new PrimeBlockScript(manifest, target);
            // Installed, not enabled - same rule as .plugin. An update keeps whatever the user
            // had chosen for the previous version.
            final boolean keepEnabled = replaced && PrimeBlockStore.isEnabled(manifest.id);
            PrimeBlockStore.setEnabled(manifest.id, keepEnabled);
            script.setEnabled(keepEnabled);
            synchronized (scripts) {
                scripts.remove(script);
                scripts.add(script);
                Collections.sort(scripts, (a, b) -> a.name().compareToIgnoreCase(b.name()));
            }
            notifyChanged();

            final boolean wasReplaced = replaced;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onInstalled(script, wasReplaced));
            }
        });
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            final byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    public void delete(PrimeBlockScript script) {
        if (script == null) {
            return;
        }
        synchronized (scripts) {
            scripts.remove(script);
        }
        notifyChanged();
        Utilities.globalQueue.postRunnable(() -> {
            //noinspection ResultOfMethodCallIgnored
            script.file.delete();
            PrimeBlockStore.forget(script.id());
        });
    }

    public void setEnabled(PrimeBlockScript script, boolean enabled) {
        if (script == null) {
            return;
        }
        PrimeBlockStore.setEnabled(script.id(), enabled);
        script.setEnabled(enabled);
        notifyChanged();
    }

    void notifyChanged() {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.blocksDidUpdate));
    }
}
