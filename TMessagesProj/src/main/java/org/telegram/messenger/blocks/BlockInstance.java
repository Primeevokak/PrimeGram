package org.telegram.messenger.blocks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * PrimeGram Blocks: one node in a `.pr` script's block tree - a trigger, a condition, a wait, or
 * an action, plus (only for {@code condition.if_else}) its nested {@code then}/{@code else}
 * branches.
 *
 * <p>This tree, not the editor's flattened row list (Phase C), is the source of truth: it is
 * parsed directly from and serialized directly back to the `.pr` JSON shape, so nesting is never
 * reconstructed from indentation.
 */
public final class BlockInstance {

    public final String id;
    public final String type;
    public final JSONObject params;
    /** Non-null only for branching blocks (currently only {@code condition.if_else}). */
    public final List<BlockInstance> then;
    public final List<BlockInstance> otherwise;

    public BlockInstance(String id, String type, JSONObject params,
                          List<BlockInstance> then, List<BlockInstance> otherwise) {
        this.id = id;
        this.type = type;
        this.params = params != null ? params : new JSONObject();
        this.then = then;
        this.otherwise = otherwise;
    }

    public boolean hasBranches() {
        return then != null || otherwise != null;
    }

    static BlockInstance parse(JSONObject json) throws JSONException {
        final String id = json.getString("id");
        final String type = json.getString("type");
        final JSONObject params = json.optJSONObject("params");
        final List<BlockInstance> then = json.has("then") ? parseList(json.getJSONArray("then")) : null;
        final List<BlockInstance> otherwise = json.has("else") ? parseList(json.getJSONArray("else")) : null;
        return new BlockInstance(id, type, params, then, otherwise);
    }

    static List<BlockInstance> parseList(JSONArray array) throws JSONException {
        final List<BlockInstance> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            result.add(parse(array.getJSONObject(i)));
        }
        return result;
    }

    /** Walks this block and, for branching blocks, its nested conditions/branches, collecting every distinct type id. */
    void collectTypeIds(java.util.Set<String> out) {
        out.add(type);
        // condition.if_else carries its own nested "conditions" list inside params, not as
        // BlockInstance children (see the .pr format §1.1) - those are simple {type, params}
        // objects with no id/branches of their own, so their type ids are collected here too.
        final JSONArray conditions = params.optJSONArray("conditions");
        if (conditions != null) {
            for (int i = 0; i < conditions.length(); i++) {
                final JSONObject c = conditions.optJSONObject(i);
                if (c != null) {
                    out.add(c.optString("type"));
                }
            }
        }
        collectFromList(then, out);
        collectFromList(otherwise, out);
    }

    private static void collectFromList(List<BlockInstance> list, java.util.Set<String> out) {
        if (list == null) {
            return;
        }
        for (BlockInstance b : list) {
            b.collectTypeIds(out);
        }
    }
}
