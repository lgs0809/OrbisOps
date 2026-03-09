package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SkillHiddenEvaluationSuiteDraft(
        String projectId,
        String skillId,
        long baseVersion,
        String baseSkillHash,
        String suiteVersion,
        List<Map<String, Object>> hiddenCases,
        List<Map<String, Object>> mutationCases,
        String actor
) {

    public SkillHiddenEvaluationSuiteDraft {
        projectId = required(projectId, "SKILL_HIDDEN_SUITE_PROJECT_ID_REQUIRED");
        skillId = required(skillId, "SKILL_HIDDEN_SUITE_SKILL_ID_REQUIRED");
        if (baseVersion <= 0) throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_BASE_VERSION_INVALID");
        baseSkillHash = hash(baseSkillHash, "SKILL_HIDDEN_SUITE_BASE_HASH_INVALID");
        suiteVersion = required(suiteVersion, "SKILL_HIDDEN_SUITE_VERSION_REQUIRED");
        hiddenCases = maps(hiddenCases);
        mutationCases = maps(mutationCases);
        if (hiddenCases.isEmpty()) throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_CASES_REQUIRED");
        if (mutationCases.isEmpty()) throw new IllegalArgumentException("SKILL_MUTATION_SUITE_CASES_REQUIRED");
        actor = required(actor, "SKILL_HIDDEN_SUITE_ACTOR_REQUIRED");
    }

    public String hiddenEvalHash() {
        return CanonicalObjectHasher.sha256(hiddenCases);
    }

    public String mutationEvalHash() {
        return CanonicalObjectHasher.sha256(mutationCases);
    }

    public String suiteId() {
        return "skill-hidden-suite-" + CanonicalObjectHasher.sha256(Map.of(
                "projectId", projectId,
                "skillId", skillId,
                "baseVersion", baseVersion,
                "baseSkillHash", baseSkillHash,
                "suiteVersion", suiteVersion)).substring(0, 32);
    }

    private static List<Map<String, Object>> maps(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            result.add(item == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(item)));
        }
        return List.copyOf(result);
    }

    private static String hash(String value, String reasonCode) {
        String normalized = required(value, reasonCode).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
