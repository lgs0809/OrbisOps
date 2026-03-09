package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorFrozenSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SkillBehaviorReplayArmRequest(
        String replayId,
        String fixtureId,
        String projectId,
        String actor,
        String sessionId,
        String runId,
        String skillId,
        SkillBehaviorReplayArm arm,
        String activeSkillHash,
        SkillBehaviorReplayExecutionMode mode,
        SkillBehaviorFrozenSnapshot frozenSnapshot,
        Map<String, Object> fixture
) {

    public SkillBehaviorReplayArmRequest {
        replayId = required(replayId, "SKILL_REPLAY_ID_REQUIRED");
        fixtureId = required(fixtureId, "SKILL_REPLAY_FIXTURE_ID_REQUIRED");
        projectId = required(projectId, "SKILL_REPLAY_PROJECT_ID_REQUIRED");
        actor = required(actor, "SKILL_REPLAY_ACTOR_REQUIRED");
        sessionId = required(sessionId, "SKILL_REPLAY_SESSION_ID_REQUIRED");
        runId = required(runId, "SKILL_REPLAY_RUN_ID_REQUIRED");
        skillId = required(skillId, "SKILL_REPLAY_SKILL_ID_REQUIRED");
        if (arm == null || mode == null || frozenSnapshot == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_ARM_REQUEST_INVALID");
        }
        activeSkillHash = activeSkillHash == null ? "" : activeSkillHash.trim();
        fixture = fixture == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fixture));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
