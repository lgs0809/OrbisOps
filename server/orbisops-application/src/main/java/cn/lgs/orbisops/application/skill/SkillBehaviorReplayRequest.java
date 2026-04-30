package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorFrozenSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayExecutionMode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public record SkillBehaviorReplayRequest(
        String replayId,
        String evaluationId,
        String fixtureId,
        String projectId,
        String actor,
        String sessionId,
        String runId,
        String skillId,
        SkillBehaviorReplayExecutionMode mode,
        SkillBehaviorFrozenSnapshot frozenSnapshot,
        Map<SkillBehaviorReplayArm, Map<String, Object>> armFixtures,
        Map<String, SkillBehaviorToolResult> frozenToolResults,
        double minimumSuccessDelta,
        long maximumCostMicros
) {

    public SkillBehaviorReplayRequest {
        replayId = required(replayId, "SKILL_REPLAY_ID_REQUIRED");
        evaluationId = required(evaluationId, "SKILL_REPLAY_EVALUATION_ID_REQUIRED");
        fixtureId = required(fixtureId, "SKILL_REPLAY_FIXTURE_ID_REQUIRED");
        projectId = required(projectId, "SKILL_REPLAY_PROJECT_ID_REQUIRED");
        actor = required(actor, "SKILL_REPLAY_ACTOR_REQUIRED");
        sessionId = required(sessionId, "SKILL_REPLAY_SESSION_ID_REQUIRED");
        runId = required(runId, "SKILL_REPLAY_RUN_ID_REQUIRED");
        skillId = required(skillId, "SKILL_REPLAY_SKILL_ID_REQUIRED");
        if (mode == null || frozenSnapshot == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_REQUEST_INVALID");
        }
        EnumMap<SkillBehaviorReplayArm, Map<String, Object>> fixtures =
                new EnumMap<>(SkillBehaviorReplayArm.class);
        if (armFixtures != null) {
            armFixtures.forEach((arm, fixture) -> fixtures.put(arm,
                    fixture == null ? Map.of()
                            : Collections.unmodifiableMap(new LinkedHashMap<>(fixture))));
        }
        for (SkillBehaviorReplayArm arm : SkillBehaviorReplayArm.values()) {
            if (!fixtures.containsKey(arm)) {
                throw new IllegalArgumentException("SKILL_REPLAY_ARM_FIXTURE_REQUIRED:" + arm);
            }
        }
        armFixtures = Collections.unmodifiableMap(fixtures);
        frozenToolResults = frozenToolResults == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(frozenToolResults));
        if (!Double.isFinite(minimumSuccessDelta)
                || minimumSuccessDelta < 0D || minimumSuccessDelta > 1D) {
            throw new IllegalArgumentException("SKILL_REPLAY_SUCCESS_DELTA_INVALID");
        }
        if (maximumCostMicros < 0) {
            throw new IllegalArgumentException("SKILL_REPLAY_COST_BUDGET_INVALID");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
