package cn.lgs.orbisops.domain.execution.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExecutionAdapterTemplate(
        long catalogId,
        String templateId,
        String templateName,
        ExecutionAdapterType adapterType,
        List<String> supportedActions,
        Map<String, Object> defaultConfig,
        ExecutionRiskLevel riskLevel,
        boolean readOnly,
        String description,
        ExecutionResourceStatus status,
        String createBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ExecutionAdapterTemplate {
        catalogId = Math.max(catalogId, 0L);
        templateId = required(templateId, "EXECUTION_TEMPLATE_ID_REQUIRED");
        templateName = text(templateName, templateId);
        if (adapterType == null) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_ADAPTER_TYPE_REQUIRED");
        }
        supportedActions = actions(supportedActions);
        defaultConfig = copy(defaultConfig);
        riskLevel = riskLevel == null ? ExecutionRiskLevel.HIGH : riskLevel;
        description = value(description);
        status = status == null ? ExecutionResourceStatus.ENABLED : status;
        createBy = required(createBy, "EXECUTION_TEMPLATE_ACTOR_REQUIRED");
    }

    public ExecutionAdapterTemplate update(
            String templateName,
            ExecutionAdapterType adapterType,
            List<String> supportedActions,
            Map<String, Object> defaultConfig,
            ExecutionRiskLevel riskLevel,
            boolean readOnly,
            String description,
            ExecutionResourceStatus status,
            LocalDateTime updatedAt) {
        return new ExecutionAdapterTemplate(
                catalogId,
                templateId,
                templateName,
                adapterType,
                supportedActions,
                defaultConfig,
                riskLevel,
                readOnly,
                description,
                status,
                createBy,
                createdAt,
                updatedAt);
    }

    public ExecutionAdapterTemplate withStatus(
            ExecutionResourceStatus nextStatus,
            LocalDateTime updatedAt) {
        return update(
                templateName,
                adapterType,
                supportedActions,
                defaultConfig,
                riskLevel,
                readOnly,
                description,
                nextStatus,
                updatedAt);
    }

    private static List<String> actions(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(ExecutionAdapterTemplate::value)
                .filter(action -> !action.isBlank())
                .distinct()
                .toList();
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private static String text(String input, String fallback) {
        String normalized = value(input);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
