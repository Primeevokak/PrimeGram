package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * PrimeGram: local-only folders inside the Archive screen - a scoped-down version of Telegram's
 * own chat folders (`MessagesController.DialogFilter`/`TLRPC.TL_dialogFilter`), deliberately not
 * reusing that class since real folders are server-synced (`TL_messages_updateDialogFilter`) and
 * carry a whole rule-matching engine (contacts/bots/muted/read flags) this feature doesn't need -
 * a local archive folder is just a name and a set of chat ids. See the "Local-Only Folders Inside
 * Archive" plan.
 *
 * <p>Persisted as one JSON blob per account, mirroring
 * {@code org.telegram.messenger.blocks.PrimeBlockStore}'s simplicity rather than adding new
 * SQLite tables to {@link MessagesStorage} - the realistic scale here (a handful of folders, at
 * most a few hundred archived chat ids) doesn't need a database, and this keeps a local-only side
 * feature entirely out of that core file's schema/migration surface.
 */
public final class PrimeArchiveFolders {

    public static final class Folder {
        public int id;
        public String name;
        public int order;
        public final LinkedHashSet<Long> dialogIds = new LinkedHashSet<>();

        private Folder(int id, String name, int order) {
            this.id = id;
            this.name = name;
            this.order = order;
        }
    }

    private static final String PREFS_PREFIX = "primegram_archive_folders_";

    private static final PrimeArchiveFolders[] instances = new PrimeArchiveFolders[UserConfig.MAX_ACCOUNT_COUNT];

    private final int currentAccount;
    private final List<Folder> folders = new ArrayList<>();
    private int nextId = 1;
    private boolean loaded;

    private PrimeArchiveFolders(int account) {
        this.currentAccount = account;
    }

    public static synchronized PrimeArchiveFolders getInstance(int account) {
        PrimeArchiveFolders local = instances[account];
        if (local == null) {
            local = instances[account] = new PrimeArchiveFolders(account);
        }
        local.loadIfNeeded();
        return local;
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_PREFIX + currentAccount, Context.MODE_PRIVATE);
    }

    private synchronized void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;
        final String json = prefs().getString("folders", null);
        if (json == null) {
            return;
        }
        try {
            final JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject obj = array.getJSONObject(i);
                final Folder folder = new Folder(obj.getInt("id"), obj.getString("name"), obj.optInt("order", i));
                final JSONArray ids = obj.optJSONArray("dialogIds");
                if (ids != null) {
                    for (int j = 0; j < ids.length(); j++) {
                        folder.dialogIds.add(ids.getLong(j));
                    }
                }
                folders.add(folder);
                if (folder.id >= nextId) {
                    nextId = folder.id + 1;
                }
            }
        } catch (JSONException e) {
            FileLog.e(e);
        }
    }

    private void save() {
        try {
            final JSONArray array = new JSONArray();
            for (Folder folder : folders) {
                final JSONObject obj = new JSONObject();
                obj.put("id", folder.id);
                obj.put("name", folder.name);
                obj.put("order", folder.order);
                final JSONArray ids = new JSONArray();
                for (Long dialogId : folder.dialogIds) {
                    ids.put((long) dialogId);
                }
                obj.put("dialogIds", ids);
                array.put(obj);
            }
            prefs().edit().putString("folders", array.toString()).apply();
        } catch (JSONException e) {
            FileLog.e(e);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.primeArchiveFoldersChanged);
    }

    public synchronized List<Folder> getFolders() {
        return new ArrayList<>(folders);
    }

    public synchronized Folder getFolder(int id) {
        for (Folder folder : folders) {
            if (folder.id == id) {
                return folder;
            }
        }
        return null;
    }

    public synchronized Folder createFolder(String name) {
        final Folder folder = new Folder(nextId++, name, folders.size());
        folders.add(folder);
        save();
        return folder;
    }

    public synchronized void renameFolder(int id, String name) {
        final Folder folder = getFolder(id);
        if (folder == null) {
            return;
        }
        folder.name = name;
        save();
    }

    public synchronized void deleteFolder(int id) {
        final Folder folder = getFolder(id);
        if (folder == null) {
            return;
        }
        folders.remove(folder);
        save();
    }

    public synchronized void addDialog(int folderId, long dialogId) {
        final Folder folder = getFolder(folderId);
        if (folder == null) {
            return;
        }
        folder.dialogIds.add(dialogId);
        save();
    }

    public synchronized void removeDialog(int folderId, long dialogId) {
        final Folder folder = getFolder(folderId);
        if (folder == null) {
            return;
        }
        folder.dialogIds.remove(dialogId);
        save();
    }

    public synchronized boolean isInFolder(int folderId, long dialogId) {
        final Folder folder = getFolder(folderId);
        return folder != null && folder.dialogIds.contains(dialogId);
    }
}
