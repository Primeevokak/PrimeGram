package org.telegram.messenger.blocks;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PrimeGram Blocks: flattens a script's {@code BlockInstance} tree into a linear
 * {@code List<Op>} with explicit jump targets, the way any small compiler lowers nested
 * {@code if/else} into jumps. Done ahead of time (once per script, cached on
 * {@link PrimeBlockScript}) rather than walking the tree per trigger fire, because
 * {@link PrimeBlocksRuntime} needs to pause mid-script at a {@code wait.duration} block and
 * resume later from a plain integer program counter - trivial for a flat list, awkward for a
 * tree path.
 *
 * <p>A standalone {@code CONDITION}-category block placed directly in a body/branch list (which
 * the editor already allows outside any {@code condition.if_else}) compiles to a {@link Op.GuardOp}:
 * false halts the rest of that script run. {@code condition.if_else} itself is not yet a
 * selectable palette entry, but the format and this compiler support it for forward-compatible
 * and hand-authored {@code .pr} files.
 */
final class BlockCompiler {

    private BlockCompiler() {
    }

    static List<Op> compile(List<BlockInstance> body) {
        final List<Op> ops = new ArrayList<>();
        appendList(ops, body);
        return ops;
    }

    private static void appendList(List<Op> ops, List<BlockInstance> list) {
        if (list == null) {
            return;
        }
        for (BlockInstance node : list) {
            appendNode(ops, node);
        }
    }

    private static void appendNode(List<Op> ops, BlockInstance node) {
        if ("condition.if_else".equals(node.type)) {
            final int testIndex = ops.size();
            ops.add(null); // patched below once we know where the else branch starts
            appendList(ops, node.then);
            final int jumpIndex = ops.size();
            ops.add(null); // patched below once we know where the else branch ends
            final int elseStart = ops.size();
            appendList(ops, node.otherwise);
            final int afterElse = ops.size();
            ops.set(testIndex, new Op.TestOp(parseConditions(node.params), operatorOf(node.params), elseStart));
            ops.set(jumpIndex, new Op.JumpOp(afterElse));
            return;
        }

        final BlockType type = BlockRegistry.get(node.type);
        if (type == null) {
            // Compatibility is already checked before a script is ever run
            // (PrimeBlocksController#rescanLocked); an unknown id here means the format allows
            // something the registry doesn't - skip rather than guess.
            return;
        }
        switch (type.category) {
            case CONDITION:
                ops.add(new Op.GuardOp(node.type, node.params));
                break;
            case WAIT:
                ops.add(new Op.WaitOp(node.params.optInt("hours", 0)));
                break;
            case ACTION:
                ops.add(new Op.ActionOp(node.type, node.params));
                break;
            case TRIGGER:
            default:
                // A trigger can only ever be the script's root, never inside its body.
                break;
        }
    }

    private static List<Op.CondCheck> parseConditions(JSONObject ifElseParams) {
        final JSONArray array = ifElseParams != null ? ifElseParams.optJSONArray("conditions") : null;
        if (array == null) {
            return Collections.emptyList();
        }
        final List<Op.CondCheck> result = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            final JSONObject c = array.optJSONObject(i);
            if (c != null) {
                result.add(new Op.CondCheck(c.optString("type"), c.optJSONObject("params")));
            }
        }
        return result;
    }

    private static String operatorOf(JSONObject ifElseParams) {
        final String op = ifElseParams != null ? ifElseParams.optString("operator", "AND") : "AND";
        return "OR".equalsIgnoreCase(op) ? "OR" : "AND";
    }
}
