package com.exteragram.messenger.plugins;

import org.telegram.messenger.BuildVars;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram: compatibility shim for {@code com.exteragram.messenger.plugins.Plugin} - the plugin
 * metadata/wrapper class some plugins reach for directly instead of going through the SDK's
 * {@code PluginMetadata}. Field shape matches the real class; nothing here is wired to
 * PrimeGram's own plugin registry ({@code PrimePluginStore}) since a plugin constructing one of
 * these itself (the only way it would end up with an instance) is describing its own metadata,
 * not asking PrimeGram what it already knows.
 */
public final class Plugin {

    private final String id;
    private final String name;
    private String appVersion = BuildVars.BUILD_VERSION_STRING;
    private String sdkVersion = PythonPluginsEngine.INSTANCE.getSDK_VERSION();
    private String version = "";
    private String description = "";
    private String author = "";
    private String engine = "python";
    private Throwable error;
    private boolean enabled = true;
    private boolean notResponding;
    private int index = -1;
    private String pack;
    private String icon;
    private List<String> requirements = new ArrayList<>();
    private PluginsController.PluginsEngine cachedEngine;

    public Plugin(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String v) {
        appVersion = v;
    }

    public String getSdkVersion() {
        return sdkVersion;
    }

    public void setSdkVersion(String v) {
        sdkVersion = v;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String v) {
        version = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String v) {
        author = v;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String v) {
        engine = v;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable e) {
        error = e;
        if (e != null) {
            enabled = false;
        }
    }

    public boolean hasError() {
        return error != null;
    }

    public boolean isEnabled() {
        return enabled && !hasError();
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public boolean getIsNotResponding() {
        return notResponding;
    }

    public void setNotResponding(boolean v) {
        notResponding = v;
    }

    public int getIndex() {
        return index;
    }

    public String getPack() {
        return pack;
    }

    public String getIcon() {
        return icon;
    }

    /** Format is {@code "pack/index"}, same as the real class. */
    public void setIcon(String link) {
        icon = link;
        if (link == null) {
            pack = null;
            index = -1;
            return;
        }
        final int slash = link.lastIndexOf('/');
        if (slash < 0) {
            pack = link;
            index = -1;
            return;
        }
        pack = link.substring(0, slash);
        try {
            index = Integer.parseInt(link.substring(slash + 1));
        } catch (NumberFormatException e) {
            index = -1;
        }
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<String> v) {
        requirements = v == null ? new ArrayList<>() : v;
    }

    public PluginsController.PluginsEngine getCachedEngine() {
        return cachedEngine;
    }

    public void setCachedEngine(PluginsController.PluginsEngine e) {
        cachedEngine = e;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Plugin)) return false;
        final Plugin that = (Plugin) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
