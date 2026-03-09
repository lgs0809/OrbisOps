package cn.lgs.orbisops.domain.execution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record ExecutionTargetGenerationInput(
        String projectId,
        String templateId,
        String targetId,
        String targetName,
        String workerId,
        List<String> environments,
        Map<String, Object> configuration,
        Map<String, Object> resolvedConfig,
        List<String> allowedActions,
        Boolean approvalRequired,
        ExecutionResourceStatus status
) {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,120}");

    public ExecutionTargetGenerationInput {
        projectId = id(projectId, "EXECUTION_PROJECT_ID_INVALID");
        templateId = id(templateId, "EXECUTION_TEMPLATE_ID_INVALID");
        targetId = id(targetId, "EXECUTION_TARGET_ID_INVALID");
        targetName = text(targetName, targetId);
        workerId = id(workerId, "EXECUTION_WORKER_ID_INVALID");
        environments = environments(environments);
        configuration = copy(configuration);
        resolvedConfig = copy(resolvedConfig);
        allowedActions = actions(allowedActions);
        status = status == null ? ExecutionResourceStatus.ENABLED : status;
    }

    private static List<String> environments(List<String> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("EXECUTION_ENVIRONMENTS_REQUIRED");
        }
        List<String> result = source.stream()
                .map(item -> value(item).toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        if (result.isEmpty() || result.stream().anyMatch(item -> !ID.matcher(item).matches())) {
            throw new IllegalArgumentException("EXECUTION_ENVIRONMENTS_INVALID");
        }
        return result;
    }

    private static List<String> actions(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(ExecutionTargetGenerationInput::value)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String id(String input, String error) {
        String normalized = value(input);
        if (!ID.matcher(normalized).matches()) {
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
