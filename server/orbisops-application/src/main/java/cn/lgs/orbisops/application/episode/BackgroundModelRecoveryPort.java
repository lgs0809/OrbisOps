package cn.lgs.orbisops.application.episode;

import java.util.List;

/** Maintenance of existing stopped jobs, not a second dispatcher or a success-state writer. */
public interface BackgroundModelRecoveryPort {
    enum Family { EPISODE_TURN, EPISODE_CONSOLIDATION, MEMORY, SKILL_REVIEW, SKILL_EVOLUTION }
    record Candidate(Family family, String taskId, String projectId, String sessionId, String ownerId,
                     String status, int attempts, String error, String inputHash, String variant) { }
    List<Candidate> candidates(Family family, String projectId, String taskId, String afterId, int limit);
    boolean restore(Candidate candidate, long nextAttemptMillis, String actor, Runnable authorize);
}
