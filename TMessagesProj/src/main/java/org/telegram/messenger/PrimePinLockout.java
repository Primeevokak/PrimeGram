package org.telegram.messenger;

/**
 * PrimeGram: pure PIN lockout state machine - no Android/clock access, the caller supplies
 * {@code nowElapsedRealtime} and {@code currentBootCount} so this is trivially unit-testable
 * and so the delay math can't accidentally depend on wall-clock time (which the user
 * controls) instead of {@code elapsedRealtime} (which doesn't reset on a clock change,
 * only on reboot - hence also tracking boot count, so a reboot can't reset the delay either).
 *
 * <p>3 free attempts, then +5 minutes per additional failure, capped at 24 hours. A boot
 * count change or a clock rollback (now &lt; lockoutStarted) restarts the lockout at full
 * length from now, rather than trusting a state that could have been manipulated by
 * rebooting or winding the clock back.
 */
final class PrimePinLockout {

    static final int UNKNOWN_BOOT_COUNT = -1;
    private static final int FREE_ATTEMPTS = 3;
    private static final long STEP_MILLIS = 5 * 60_000L;
    private static final long MAX_DELAY_MILLIS = 24 * 60 * 60_000L;

    private PrimePinLockout() {
    }

    static final class State {
        final int failedAttempts;
        final long lockoutStartedElapsedRealtime;
        final long lockoutDeadlineElapsedRealtime;
        final int bootCount;

        State(int failedAttempts, long lockoutStartedElapsedRealtime, long lockoutDeadlineElapsedRealtime, int bootCount) {
            if (failedAttempts < 0) {
                throw new IllegalArgumentException("failedAttempts < 0");
            }
            this.failedAttempts = failedAttempts;
            this.lockoutStartedElapsedRealtime = lockoutStartedElapsedRealtime;
            this.lockoutDeadlineElapsedRealtime = lockoutDeadlineElapsedRealtime;
            this.bootCount = bootCount;
        }

        static State unlocked(int bootCount) {
            return new State(0, 0L, 0L, bootCount);
        }
    }

    static final class Evaluation {
        final State state;
        final long remainingMillis;
        final boolean stateChanged;

        Evaluation(State state, long remainingMillis, boolean stateChanged) {
            this.state = state;
            this.remainingMillis = remainingMillis;
            this.stateChanged = stateChanged;
        }

        boolean isLocked() {
            return remainingMillis > 0;
        }
    }

    static Evaluation evaluate(State state, long now, int bootCount) {
        boolean rebooted = bootCount != PrimePinLockout.UNKNOWN_BOOT_COUNT
                && state.bootCount != PrimePinLockout.UNKNOWN_BOOT_COUNT
                && bootCount != state.bootCount;
        boolean clockRolledBack = now < state.lockoutStartedElapsedRealtime;
        if ((rebooted || clockRolledBack) && state.failedAttempts >= FREE_ATTEMPTS) {
            long delay = delayFor(state.failedAttempts);
            State restarted = new State(state.failedAttempts, now, now + delay, bootCount);
            return new Evaluation(restarted, delay, true);
        }
        long remaining = Math.max(0L, state.lockoutDeadlineElapsedRealtime - now);
        State refreshed = bootCount != state.bootCount
                ? new State(state.failedAttempts, state.lockoutStartedElapsedRealtime, state.lockoutDeadlineElapsedRealtime, bootCount)
                : state;
        return new Evaluation(refreshed, remaining, refreshed != state);
    }

    static State afterFailure(State state, long now, int bootCount) {
        int attempts = state.failedAttempts == Integer.MAX_VALUE ? Integer.MAX_VALUE : state.failedAttempts + 1;
        long delay = delayFor(attempts);
        return new State(attempts, now, safeAdd(now, delay), bootCount);
    }

    static State afterSuccess(int bootCount) {
        return State.unlocked(bootCount);
    }

    private static long delayFor(int failedAttempts) {
        if (failedAttempts < FREE_ATTEMPTS) {
            return 0L;
        }
        long steps = (long) (failedAttempts - (FREE_ATTEMPTS - 1));
        long delay = steps * STEP_MILLIS;
        return Math.min(delay < 0 /* overflow */ ? MAX_DELAY_MILLIS : delay, MAX_DELAY_MILLIS);
    }

    private static long safeAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
