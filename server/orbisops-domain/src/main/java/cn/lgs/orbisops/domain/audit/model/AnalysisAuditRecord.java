package cn.lgs.orbisops.domain.audit.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AnalysisAuditRecord(
        String analysisId,
        boolean success,
        String question,
        String intent,
        Integer rangeMinutes,
        String promWindow,
        String generatedAt,
        Long durationMs,
        List<String> selectedSources,
        List<String> executedSources,
        List<String> skippedSources,
        Map<String, String> resultStatuses,
        List<String> insightLevels,
        String conclusion,
        String errorMessage) {

    public AnalysisAuditRecord {
        analysisId = required(analysisId, "ANALYSIS_AUDIT_ID_REQUIRED");
        question = value(question);
        intent = value(intent);
        promWindow = value(promWindow);
        generatedAt = value(generatedAt);
        selectedSources = immutable(selectedSources);
        executedSources = immutable(executedSources);
        skippedSources = immutable(skippedSources);
        resultStatuses = immutableMap(resultStatuses);
        insightLevels = immutable(insightLevels);
        conclusion = value(conclusion);
        errorMessage = value(errorMessage);
        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException("ANALYSIS_AUDIT_DURATION_INVALID:" + durationMs);
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(AnalysisAuditRecord::value)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = AnalysisAuditRecord.value(key);
            if (!normalizedKey.isBlank()) {
                normalized.put(normalizedKey, AnalysisAuditRecord.value(value));
            }
        });
        return Map.copyOf(normalized);
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
