package org.telegram.messenger.blocks;

import java.io.File;

/**
 * PrimeGram Blocks: one installed `.pr` script, as the app sees it - the Blocks equivalent of
 * {@code PrimePlugin}, deliberately free of any runtime/interpreter state so it stays valid on
 * screens that only need to list/enable/delete scripts.
 */
public final class PrimeBlockScript {

    public final PrimeBlockManifest manifest;
    /** The `.pr` file itself, inside {@link PrimeBlocksController#blocksDir()}. */
    public final File file;

    private volatile boolean enabled;
    /** Set by the §2.2-item-4 reconciliation check; a script failing it is force-disabled and stays that way until an update. */
    private volatile boolean unsupported;

    public PrimeBlockScript(PrimeBlockManifest manifest, File file) {
        this.manifest = manifest;
        this.file = file;
    }

    public String id() {
        return manifest.id;
    }

    public String name() {
        return manifest.name;
    }

    public boolean isEnabled() {
        return enabled && !unsupported;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isUnsupported() {
        return unsupported;
    }

    public void setUnsupported(boolean value) {
        unsupported = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PrimeBlockScript && ((PrimeBlockScript) other).manifest.id.equals(manifest.id);
    }

    @Override
    public int hashCode() {
        return manifest.id.hashCode();
    }
}
