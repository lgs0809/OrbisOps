package cn.lgs.orbisops.application.worksession;

public final class ToolLoopCoordinator {

    public Budget prepare(int requestedRounds,
                          int requestedRecursionLimit,
                          int defaultRounds,
                          int maximumRounds,
                          int checkpointInterval) {
        int maxAllowed = Math.max(1, maximumRounds);
        int rounds = requestedRounds > 0 ? requestedRounds : Math.max(1, defaultRounds);
        rounds = Math.min(rounds, maxAllowed);
        int minimumRecursion = rounds * 2 + 4;
        int recursionLimit = requestedRecursionLimit > 0
                ? Math.max(requestedRecursionLimit, minimumRecursion)
                : minimumRecursion;
        int checkpointEvery = Math.max(1, Math.min(checkpointInterval, rounds));
        return new Budget(rounds, recursionLimit, checkpointEvery);
    }

    public RoundDecision beforeRound(int completedRounds, Budget budget, boolean canceled) {
        if (budget == null) throw new IllegalArgumentException("TOOL_LOOP_BUDGET_REQUIRED");
        if (completedRounds < 0) throw new IllegalArgumentException("TOOL_LOOP_ROUND_INVALID");
        if (canceled) return new RoundDecision(Action.CANCEL, "RUN_CANCELED", completedRounds);
        if (completedRounds >= budget.maxRounds()) {
            return new RoundDecision(Action.STOP, "TOOL_ROUND_BUDGET_EXHAUSTED", completedRounds);
        }
        if (completedRounds > 0 && completedRounds % budget.checkpointInterval() == 0) {
            return new RoundDecision(Action.CHECKPOINT, "TOOL_LOOP_CHECKPOINT_REQUIRED", completedRounds);
        }
        return new RoundDecision(Action.CONTINUE, "TOOL_LOOP_CONTINUE", completedRounds);
    }

    public enum Action {
        CONTINUE,
        CHECKPOINT,
        STOP,
        CANCEL
    }

    public record Budget(int maxRounds, int recursionLimit, int checkpointInterval) {
        public Budget {
            if (maxRounds <= 0 || maxRounds > 256) {
                throw new IllegalArgumentException("TOOL_LOOP_MAX_ROUNDS_INVALID");
            }
            if (recursionLimit < maxRounds + 2) {
                throw new IllegalArgumentException("TOOL_LOOP_RECURSION_LIMIT_INVALID");
            }
            if (checkpointInterval <= 0 || checkpointInterval > maxRounds) {
                throw new IllegalArgumentException("TOOL_LOOP_CHECKPOINT_INTERVAL_INVALID");
            }
        }
    }

    public record RoundDecision(Action action, String reasonCode, int completedRounds) {
        public RoundDecision {
            if (action == null) throw new IllegalArgumentException("TOOL_LOOP_ACTION_REQUIRED");
            reasonCode = reasonCode == null ? "" : reasonCode.trim();
            if (completedRounds < 0) throw new IllegalArgumentException("TOOL_LOOP_ROUND_INVALID");
        }
    }
}
