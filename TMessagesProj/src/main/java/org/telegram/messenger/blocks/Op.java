package org.telegram.messenger.blocks;

import org.json.JSONObject;

import java.util.List;

/**
 * PrimeGram Blocks: one instruction in a script compiled by {@link BlockCompiler} - the runtime
 * equivalent of a line of bytecode. {@link PrimeBlocksRuntime} walks a {@code List<Op>} by
 * integer program counter rather than recursing the original block tree, so a paused
 * {@code wait.duration} can resume from a plain {@code (scriptId, pc)} pair - the two primitives
 * {@code WorkManager}'s {@code Data} can hold directly.
 */
abstract class Op {

    /** A standalone CONDITION block outside any {@code if_else}: false halts the rest of the script. */
    static final class GuardOp extends Op {
        final String condType;
        final JSONObject params;

        GuardOp(String condType, JSONObject params) {
            this.condType = condType;
            this.params = params;
        }
    }

    /** The test at the top of a {@code condition.if_else}: false jumps to {@code elseIndex}. */
    static final class TestOp extends Op {
        final List<CondCheck> conditions;
        final String operator;
        final int elseIndex;

        TestOp(List<CondCheck> conditions, String operator, int elseIndex) {
            this.conditions = conditions;
            this.operator = operator;
            this.elseIndex = elseIndex;
        }
    }

    /** One atomic condition inside a {@code TestOp}'s {@code params.conditions} array. */
    static final class CondCheck {
        final String type;
        final JSONObject params;

        CondCheck(String type, JSONObject params) {
            this.type = type;
            this.params = params;
        }
    }

    /** Unconditional jump - closes the `then` branch of an `if_else`, skipping its `else`. */
    static final class JumpOp extends Op {
        final int targetIndex;

        JumpOp(int targetIndex) {
            this.targetIndex = targetIndex;
        }
    }

    static final class ActionOp extends Op {
        final String actionType;
        final JSONObject params;

        ActionOp(String actionType, JSONObject params) {
            this.actionType = actionType;
            this.params = params;
        }
    }

    static final class WaitOp extends Op {
        final int hours;

        WaitOp(int hours) {
            this.hours = hours;
        }
    }
}
