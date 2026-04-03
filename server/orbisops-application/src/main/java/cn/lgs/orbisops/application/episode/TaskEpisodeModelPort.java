package cn.lgs.orbisops.application.episode;

/** Dedicated, stateless sidecar model. Its output is never a conversation-memory message. */
public interface TaskEpisodeModelPort {
    int CALL_TIMEOUT_SECONDS = 60;
    String MODEL = "gpt-5.6-luna";
    String CLASSIFIER = "task-episode-luna-v1";
    String GENERATOR = "task-episode-progress-luna-v1";
    boolean available();
    Decision classify(String frozenInput);
    String consolidate(String frozenInput);

    final class RetryableFailure extends RuntimeException {
        private final long retryAfterMillis;
        public RetryableFailure(String code, long retryAfterMillis) {
            super(code); this.retryAfterMillis = Math.max(0, Math.min(86400000L, retryAfterMillis));
        }
        public long retryAfterMillis() { return retryAfterMillis; }
    }

    record Decision(String action, String episodeId, String goal, String reason) {
        public Decision {
            if (!"CREATE".equals(action) && !"CONTINUE".equals(action))
                throw new IllegalArgumentException("EPISODE_DECISION_INVALID");
            episodeId = episodeId == null ? "" : episodeId.trim();
            goal = goal == null ? "" : goal.trim();
            reason = reason == null ? "" : reason.trim();
            if (goal.length() > 2000 || reason.length() > 2000 || episodeId.length() > 80)
                throw new IllegalArgumentException("EPISODE_DECISION_TOO_LARGE");
            if ("CREATE".equals(action) && (!episodeId.isEmpty() || goal.isEmpty()))
                throw new IllegalArgumentException("EPISODE_CREATE_GOAL_REQUIRED");
            if ("CONTINUE".equals(action) && episodeId.isEmpty())
                throw new IllegalArgumentException("EPISODE_CONTINUE_TARGET_REQUIRED");
        }
    }
}
