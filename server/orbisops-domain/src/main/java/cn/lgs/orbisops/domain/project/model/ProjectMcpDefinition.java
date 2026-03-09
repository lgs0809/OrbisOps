package cn.lgs.orbisops.domain.project.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record ProjectMcpDefinition(
        String mcpId,
        String mcpName,
        String projectId,
        String resourceId,
        String resourceType,
        String transportType,
        String templateId,
        Map<String, Object> transportConfig,
        List<String> allowedActions,
        ProjectMcpRiskLevel riskLevel,
        boolean readOnly,
        Map<String, Object> permissionPolicy,
        int requestTimeout,
        ProjectMcpStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ProjectMcpDefinition {
        mcpId = required(mcpId, "PROJECT_MCP_ID_REQUIRED");
        mcpName = text(mcpName, mcpId);
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        resourceId = value(resourceId);
        resourceType = text(resourceType, "custom").toLowerCase(Locale.ROOT);
        transportType = text(transportType, "stdio").toLowerCase(Locale.ROOT);
        templateId = value(templateId);
        transportConfig = copy(transportConfig);
        allowedActions = list(allowedActions);
        riskLevel = riskLevel == null ? ProjectMcpRiskLevel.HIGH : riskLevel;
        permissionPolicy = copy(permissionPolicy);
        requestTimeout = requestTimeout <= 0 ? 30 : requestTimeout;
        status = status == null ? ProjectMcpStatus.ENABLED : status;
    }

    public ProjectMcpDefinition update(
            String mcpName,
            Map<String, Object> transportConfig,
            List<String> allowedActions,
            ProjectMcpRiskLevel riskLevel,
            boolean readOnly,
            Map<String, Object> permissionPolicy,
            int requestTimeout,
            ProjectMcpStatus status,
            LocalDateTime updatedAt) {
        return new ProjectMcpDefinition(
                mcpId,
                mcpName,
                projectId,
                resourceId,
                resourceType,
                transportType,
                templateId,
                transportConfig,
                allowedActions,
                riskLevel,
                readOnly,
                permissionPolicy,
                requestTimeout,
                status,
                createdAt,
                updatedAt);
    }

    public ProjectMcpDefinition withDiscoveryState(Map<String, Object> transportConfig,
                                                   ProjectMcpStatus status,
                                                   LocalDateTime updatedAt) {
        return update(mcpName, transportConfig, allowedActions, riskLevel, readOnly,
                permissionPolicy, requestTimeout, status, updatedAt);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static List<String> list(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream()
                .map(ProjectMcpDefinition::value)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
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
