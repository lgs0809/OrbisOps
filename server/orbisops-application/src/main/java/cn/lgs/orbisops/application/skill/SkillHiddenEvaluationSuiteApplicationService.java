package cn.lgs.orbisops.application.skill;

import java.util.List;

/** Controlled publication and query boundary for versioned strict-tournament suites. */
public final class SkillHiddenEvaluationSuiteApplicationService {

    private final SkillHiddenEvaluationSuitePort port;

    public SkillHiddenEvaluationSuiteApplicationService(
            SkillHiddenEvaluationSuitePort port) {
        if (port == null) {
            throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_PORT_REQUIRED");
        }
        this.port = port;
    }

    public SkillHiddenEvaluationSuiteSnapshot publish(
            SkillHiddenEvaluationSuiteDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_DRAFT_REQUIRED");
        }
        return requireSnapshot(port.publish(draft));
    }

    public SkillHiddenEvaluationSuiteSnapshot require(
            String projectId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            String suiteVersion) {
        return port.find(
                        projectId,
                        skillId,
                        baseVersion,
                        baseSkillHash,
                        suiteVersion)
                .map(this::requireSnapshot)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SKILL_HIDDEN_SUITE_NOT_FOUND:"
                                + projectId + ":" + skillId + ":" + baseVersion + ":" + suiteVersion));
    }

    public List<SkillHiddenEvaluationSuiteSnapshot> list(
            String projectId,
            String skillId,
            int limit) {
        return List.copyOf(port.list(
                projectId,
                skillId,
                Math.max(1, Math.min(limit, 200))));
    }

    private SkillHiddenEvaluationSuiteSnapshot requireSnapshot(
            SkillHiddenEvaluationSuiteSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("SKILL_HIDDEN_SUITE_RESULT_REQUIRED");
        }
        return snapshot;
    }
}
