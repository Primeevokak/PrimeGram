package org.telegram.messenger.plugins;

import java.io.File;

/**
 * PrimeGram: one installed plugin, as the app sees it.
 *
 * <p>Deliberately free of Python: this object exists on screens that may run before the interpreter
 * has ever started, and it stays valid after a plugin has been switched off and its Python side
 * thrown away. Everything that can only be answered by running code - the settings a plugin
 * offers, its menu items, its hooks - lives on the engine side and is asked for by id.
 *
 * <p>{@link #error} and {@link #notResponding} are the two ways a plugin can be present but not
 * working, and they are kept apart on purpose: the first is "it refused to load and here is why",
 * the second is "it loaded and then went quiet", and only the second is worth offering to kill.
 */
public final class PrimePlugin {

    public final PluginManifest manifest;
    /** The {@code .plugin} file itself, inside our own plugins directory. */
    public final File file;

    private volatile boolean enabled;
    private volatile Throwable error;
    private volatile boolean notResponding;

    public PrimePlugin(PluginManifest manifest, File file) {
        this.manifest = manifest;
        this.file = file;
    }

    public String id() {
        return manifest.id;
    }

    public String name() {
        return manifest.name;
    }

    /** A plugin that failed is never enabled, whatever the settings file remembers. */
    public boolean isEnabled() {
        return enabled && error == null;
    }

    public void setEnabled(boolean value) {
        enabled = value && error == null;
    }

    public Throwable error() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }

    /**
     * Records why a plugin will not run. Switching it off here rather than leaving that to the
     * caller means a plugin cannot be both broken and enabled, in any order of events.
     */
    public void setError(Throwable value) {
        error = value;
        if (value != null) {
            enabled = false;
        }
    }

    public boolean isNotResponding() {
        return notResponding;
    }

    public void setNotResponding(boolean value) {
        notResponding = value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PrimePlugin && ((PrimePlugin) other).manifest.id.equals(manifest.id);
    }

    @Override
    public int hashCode() {
        return manifest.id.hashCode();
    }
}
