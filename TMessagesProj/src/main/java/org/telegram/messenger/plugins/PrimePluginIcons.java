package org.telegram.messenger.plugins;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.HashSet;

/**
 * PrimeGram: a plugin's icon is a sticker.
 *
 * <p>{@code __icon__ = "VoiceToText7/6"} is not a reference to artwork bundled with exteraGram - it
 * is a sticker set's short name and an index into it, the same short name that appears in a
 * {@code t.me/addstickers/...} link. Any Telegram client can resolve that, ours included, because
 * a sticker set is an ordinary server object and fetching one by name is one request.
 *
 * <p>Results are cached in memory, keyed by set name: several plugins from the same author often
 * share a pack, and the list screen asks for every plugin's icon on every rebind.
 */
public final class PrimePluginIcons {

    public interface Callback {
        void onLoaded(TLRPC.Document sticker);
    }

    private static final HashMap<String, TLRPC.TL_messages_stickerSet> cache = new HashMap<>();
    private static final HashSet<String> loading = new HashSet<>();
    private static final HashMap<String, java.util.List<Callback>> waiting = new HashMap<>();

    private PrimePluginIcons() {
    }

    /**
     * Resolves {@code manifest.icon()} to the sticker it names. Calls back on the UI thread,
     * synchronously if the set is already known - a plugin re-entering this screen should not see
     * its icon disappear and come back.
     */
    public static void resolve(PluginManifest manifest, Callback callback) {
        if (manifest == null || manifest.iconPack == null || manifest.iconIndex < 0 || callback == null) {
            return;
        }
        final String setName = manifest.iconPack;
        final int index = manifest.iconIndex;

        final TLRPC.TL_messages_stickerSet cached = cache.get(setName);
        if (cached != null) {
            callback.onLoaded(stickerAt(cached, index));
            return;
        }

        synchronized (waiting) {
            java.util.List<Callback> queued = waiting.get(setName);
            if (queued == null) {
                queued = new java.util.ArrayList<>();
                waiting.put(setName, queued);
            }
            queued.add(callback);
        }
        if (!loading.add(setName)) {
            return;
        }

        final TLRPC.TL_inputStickerSetShortName input = new TLRPC.TL_inputStickerSetShortName();
        input.short_name = setName;
        final TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = input;

        final int account = UserConfig.selectedAccount;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    loading.remove(setName);
                    final TLRPC.TL_messages_stickerSet set =
                            response instanceof TLRPC.TL_messages_stickerSet
                                    ? (TLRPC.TL_messages_stickerSet) response : null;
                    if (set != null) {
                        cache.put(setName, set);
                    }
                    final java.util.List<Callback> queued;
                    synchronized (waiting) {
                        queued = waiting.remove(setName);
                    }
                    if (queued == null) {
                        return;
                    }
                    final TLRPC.Document sticker = set != null ? stickerAt(set, index) : null;
                    for (Callback pending : queued) {
                        pending.onLoaded(sticker);
                    }
                }));
    }

    private static TLRPC.Document stickerAt(TLRPC.TL_messages_stickerSet set, int index) {
        if (set == null || set.documents == null || index < 0 || index >= set.documents.size()) {
            return null;
        }
        return set.documents.get(index);
    }
}
