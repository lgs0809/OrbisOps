package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;

public record SkillBehaviorToolExecutionRequest(
        String replayId,
        String projectId,
        String actor,
        String sessionId,
        String runId,
        SkillBehaviorReplayArm arm,
        SkillBehaviorReplayExecutionMode mode,
        int toolCallIndex,
        SkillBehaviorToolCall call
) {

    public SkillBehaviorToolExecutionRequest {
        replayId = required(replayId, "SKILL_REPLAY_ID_REQUIRED");
        projectId = required(projectId, "SKILL_REPLAY_PROJECT_ID_REQUIRED");
        actor = required(actor, "SKILL_REPLAY_ACTOR_REQUIRED");
        sessionId = required(sessionId, "SKILL_REPLAY_SESSION_ID_REQUIRED");
        runId = required(runId, "SKILL_REPLAY_RUN_ID_REQUIRED");
        if (arm == null || mode == null || call == null || toolCallIndex < 0) {
            throw new IllegalArgumentException("SKILL_REPLAY_TOOL_REQUEST_INVALID");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
