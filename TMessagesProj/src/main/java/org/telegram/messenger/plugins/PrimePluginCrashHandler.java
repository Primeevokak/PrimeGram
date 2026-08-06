package org.telegram.messenger.plugins;

import org.telegram.messenger.AndroidUtilities;

import java.util.List;

/**
 * PrimeGram: what happens when a plugin's own code crashes mid-call.
 *
 * <p>Split out of {@link PrimePluginsController} - that class is the catalogue and lifecycle
 * (install/delete/enable), this is the one thing that reacts to a plugin misbehaving: walking the
 * import graph to find what else needs to go down with it, disabling that whole connected
 * component, and telling the user. Neither half needs the other's internals; this one only ever
 * reaches back into the controller through its public catalogue methods and the one package-
 * private unload it needs.
 */
final class PrimePluginCrashHandler {

    private static volatile PrimePluginCrashHandler instance;

    private PrimePluginCrashHandler() {
    }

    static PrimePluginCrashHandler getInstance() {
        PrimePluginCrashHandler local = instance;
        if (local == null) {
            synchronized (PrimePluginCrashHandler.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrimePluginCrashHandler();
                }
            }
        }
        return local;
    }

    /**
     * Disables exactly {@code pluginId} and whatever is joined to it by an import - not every
     * installed plugin, exteraGram's own Safe Mode's answer to the same problem. A plugin that
     * crashed mid-call is still linked into every other plugin's process, and a library plugin that
     * threw could just as easily be dragged down by, or drag down, whoever imported from it - so the
     * whole connected component gets disabled, on the reasoning that a link to something broken is
     * itself a reason not to trust the other side of it, not because the other side did anything
     * wrong itself.
     *
     * <p>Called from wherever a plugin's own code was caught misbehaving - most often from Python,
     * from inside the very call that failed - so this does nothing that could re-enter Python
     * synchronously: unloading happens on the engine queue, same as
     * {@link PrimePluginsController#setEnabled}.
     */
    void disableAfterCrash(String pluginId, String reason) {
        final PrimePluginsController controller = PrimePluginsController.getInstance();
        final PrimePlugin culprit = controller.findById(pluginId);
        if (culprit == null) {
            return;
        }
        final java.util.LinkedHashSet<String> chain = dependencyChain(pluginId);
        chain.add(pluginId);
        for (String id : chain) {
            final PrimePlugin plugin = controller.findById(id);
            if (plugin == null) {
                continue;
            }
            PrimePluginStore.setEnabled(id, false);
            plugin.setError(new PluginCrashException(id.equals(pluginId)
                    ? reason : "отключён вместе с «" + culprit.name() + "» - они связаны через импорт"));
            PrimePythonEngine.getInstance().queue().postRunnable(() -> controller.unloadFromPython(id));
        }
        controller.notifyChanged();
        showCrashDialog(culprit, chain.size() > 1);
    }

    /** Every plugin id reachable from {@code pluginId} by an import in either direction - not just
     *  what it imports, but who imports it too, since either side of that link can pull the other
     *  down. */
    private java.util.LinkedHashSet<String> dependencyChain(String pluginId) {
        final java.util.LinkedHashSet<String> visited = new java.util.LinkedHashSet<>();
        final java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(pluginId);
        visited.add(pluginId);
        while (!queue.isEmpty()) {
            final String current = queue.poll();
            for (String neighbor : neighbors(current)) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        visited.remove(pluginId);
        return visited;
    }

    private java.util.Set<String> neighbors(String pluginId) {
        final java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(PrimePluginStore.getDependencies(pluginId));
        final List<PrimePlugin> snapshot = PrimePluginsController.getInstance().getPlugins();
        for (int i = 0; i < snapshot.size(); i++) {
            final String otherId = snapshot.get(i).id();
            if (PrimePluginStore.getDependencies(otherId).contains(pluginId)) {
                result.add(otherId);
            }
        }
        return result;
    }

    private void showCrashDialog(PrimePlugin culprit, boolean tookOthersWithIt) {
        final org.telegram.ui.LaunchActivity activity = org.telegram.ui.LaunchActivity.instance;
        if (activity == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> new org.telegram.ui.ActionBar.AlertDialog.Builder(activity)
                .setTitle("Плагин отключён")
                .setMessage("«" + culprit.name() + "» вызвал сбой и был отключён."
                        + (tookOthersWithIt ? " Вместе с ним отключены связанные с ним плагины." : ""))
                .setPositiveButton("Понятно", null)
                .show());
    }

    /** Records that a plugin (as opposed to us) is why a call failed - shown on its settings row the
     *  same way any other load failure is. */
    public static final class PluginCrashException extends RuntimeException {
        public PluginCrashException(String message) {
            super(message);
        }
    }
}
