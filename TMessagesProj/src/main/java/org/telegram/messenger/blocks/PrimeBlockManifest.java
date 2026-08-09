package org.telegram.messenger.blocks;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PrimeGram Blocks: reads a `.pr` file's {@code manifest} section and walks its {@code program}
 * tree to collect every block-type id it uses - without interpreting or running anything.
 *
 * <p>Unlike {@code PluginManifest} (which must hand-parse Python-literal assignments out of a
 * truncated header because it cannot safely run a real Python parser on an untrusted file), `.pr`
 * is plain JSON: parsing the whole thing is already safe, since {@code org.json} parsing cannot
 * execute anything. There is no 64KB-header trick needed here.
 */
public final class PrimeBlockManifest {

    /** Bumped only if the JSON *shape* itself changes; a `.pr` claiming a newer one than this build knows is rejected outright. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    public final int formatVersion;
    public final String id;
    public final String name;
    public final String description;
    public final String author;
    public final String version;
    public final String minAppVersion;

    public final BlockInstance trigger;
    public final List<BlockInstance> body;

    private PrimeBlockManifest(int formatVersion, String id, String name, String description,
                                String author, String version, String minAppVersion,
                                BlockInstance trigger, List<BlockInstance> body) {
        this.formatVersion = formatVersion;
        this.id = id;
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
        this.minAppVersion = minAppVersion;
        this.trigger = trigger;
        this.body = body;
    }

    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static PrimeBlockManifest parse(File file) throws ParseException {
        final String text;
        try {
            text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ParseException("Не удалось прочитать файл", e);
        }
        final JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException e) {
            throw new ParseException("Файл повреждён или это не .pr-скрипт", e);
        }

        final int formatVersion = root.optInt("format_version", 1);
        if (formatVersion > CURRENT_FORMAT_VERSION) {
            throw new ParseException("Этот .pr-файл создан более новой версией PrimeGram и использует формат, которого эта версия ещё не понимает.");
        }

        final JSONObject manifest = root.optJSONObject("manifest");
        if (manifest == null) {
            throw new ParseException("В файле нет обязательного раздела manifest");
        }
        final String id = manifest.optString("id", null);
        final String name = manifest.optString("name", null);
        if (id == null || id.isEmpty() || name == null || name.isEmpty()) {
            throw new ParseException("В файле не заполнены id или name");
        }

        final JSONObject program = root.optJSONObject("program");
        if (program == null) {
            throw new ParseException("В файле нет обязательного раздела program");
        }
        try {
            final BlockInstance trigger = BlockInstance.parse(program.getJSONObject("trigger"));
            final List<BlockInstance> body = program.has("body")
                    ? BlockInstance.parseList(program.getJSONArray("body"))
                    : new java.util.ArrayList<>();
            return new PrimeBlockManifest(
                    formatVersion,
                    id,
                    name,
                    manifest.optString("description", ""),
                    manifest.optString("author", ""),
                    manifest.optString("version", ""),
                    manifest.optString("min_app_version", ""),
                    trigger,
                    body);
        } catch (JSONException e) {
            throw new ParseException("Не удалось разобрать структуру блоков в файле", e);
        }
    }

    /** Every distinct block-type id this script references - trigger, body, and every nested branch. */
    public Set<String> usedBlockTypeIds() {
        final Set<String> ids = new LinkedHashSet<>();
        trigger.collectTypeIds(ids);
        for (BlockInstance b : body) {
            b.collectTypeIds(ids);
        }
        return ids;
    }
}
