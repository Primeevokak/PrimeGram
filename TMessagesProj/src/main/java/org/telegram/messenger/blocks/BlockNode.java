package org.telegram.messenger.blocks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PrimeGram Blocks: the editor's own mutable working copy of a block tree (Phase C) - the same
 * shape {@link BlockInstance} describes, but with real, editable fields instead of a read-only
 * snapshot, since {@code BlockInstance} exists specifically to be a safe, immutable parse result
 * (see the "PrimeGram Blocks" plan §1). Converts both ways with the `.pr` JSON the rest of the
 * system (install, compatibility check, and eventually the runtime) actually reads.
 */
public final class BlockNode {

    public String id;
    public String type;
    public JSONObject params;
    /** Non-null only for {@code condition.if_else}. */
    public List<BlockNode> then;
    public List<BlockNode> otherwise;

    public BlockNode(String id, String type, JSONObject params) {
        this.id = id;
        this.type = type;
        this.params = params != null ? params : new JSONObject();
    }

    public static BlockNode newInstance(String type) {
        final BlockNode node = new BlockNode(UUID.randomUUID().toString().substring(0, 8), type, new JSONObject());
        final BlockType blockType = BlockRegistry.get(type);
        if (blockType != null) {
            for (ParamSpec spec : blockType.params) {
                try {
                    node.params.put(spec.key, spec.defaultValue);
                } catch (JSONException ignored) {
                }
            }
        }
        if ("condition.if_else".equals(type)) {
            node.then = new ArrayList<>();
            node.otherwise = new ArrayList<>();
        }
        return node;
    }

    public static BlockNode fromInstance(BlockInstance instance) {
        final BlockNode node = new BlockNode(instance.id, instance.type, cloneJson(instance.params));
        if (instance.then != null) {
            node.then = fromInstanceList(instance.then);
        }
        if (instance.otherwise != null) {
            node.otherwise = fromInstanceList(instance.otherwise);
        }
        return node;
    }

    public static List<BlockNode> fromInstanceList(List<BlockInstance> list) {
        final List<BlockNode> result = new ArrayList<>(list.size());
        for (BlockInstance instance : list) {
            result.add(fromInstance(instance));
        }
        return result;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type);
        json.put("params", params);
        if (then != null) {
            json.put("then", toJsonArray(then));
        }
        if (otherwise != null) {
            json.put("else", toJsonArray(otherwise));
        }
        return json;
    }

    public static JSONArray toJsonArray(List<BlockNode> list) throws JSONException {
        final JSONArray array = new JSONArray();
        for (BlockNode node : list) {
            array.put(node.toJson());
        }
        return array;
    }

    private static JSONObject cloneJson(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
