package cn.lgs.orbisops.application.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SkillHiddenEvaluationSet(
        String suiteId,
        List<Map<String, Object>> hiddenCases,
        List<Map<String, Object>> mutationCases,
        String hiddenEvalHash,
        String mutationEvalHash
) {

    public SkillHiddenEvaluationSet {
        suiteId = required(suiteId, "SKILL_HIDDEN_EVAL_SUITE_ID_REQUIRED");
        hiddenCases = maps(hiddenCases);
        mutationCases = maps(mutationCases);
        hiddenEvalHash = required(hiddenEvalHash, "SKILL_HIDDEN_EVAL_HASH_REQUIRED");
        mutationEvalHash = required(mutationEvalHash, "SKILL_MUTATION_EVAL_HASH_REQUIRED");
        if (hiddenCases.isEmpty()) {
            throw new IllegalArgumentException("SKILL_HIDDEN_EVAL_CASES_REQUIRED");
        }
        if (mutationCases.isEmpty()) {
            throw new IllegalArgumentException("SKILL_MUTATION_EVAL_CASES_REQUIRED");
        }
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

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
