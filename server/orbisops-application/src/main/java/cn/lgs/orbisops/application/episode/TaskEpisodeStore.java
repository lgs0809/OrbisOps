package cn.lgs.orbisops.application.episode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TaskEpisodeStore {
    void captureContext(String projectId, String sessionId, String runId, Map<String, Object> snapshot);
    int discover(int limit);
    List<String> pendingSessions(long now, int limit);
    Optional<Claim> claimTurn(String session, String owner, long now, boolean modelAvailable);
    boolean assign(Claim claim, TaskEpisodeModelPort.Decision decision, long now);
    void fail(Claim claim, String reason, boolean modelUnavailable, long now);
    default void fail(Claim claim, String reason, boolean modelUnavailable, long now, long retryAfterMillis) {
        fail(claim, reason, modelUnavailable, now);
    }
    int sweep(String projectId, String reason, long now, int limit);
    List<Long> pendingConsolidations(long now, int limit);
    Optional<Claim> claimConsolidation(long id, String owner, long now, boolean modelAvailable);
    boolean complete(Claim claim, String content, long now);
    Map<String, Object> view(String projectId, String sessionId, String userId, boolean admin, int limit);

    record Claim(long id, String kind, String sessionId, String episodeId, long revision,
                 String owner, long epoch, String inputJson, String inputHash, int attempts) { }
}
