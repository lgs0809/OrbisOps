package cn.lgs.orbisops.application.project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProjectMcpTemplateGenerationPreparation(
        String mcpName,
        String resourceType,
        String templateId,
        String transportType,
        Map<String, Object> transportConfig,
        List<String> allowedActions,
        String riskLevel,
        boolean readOnly,
        Map<String, Object> permissionPolicy,
        int requestTimeout,
        String status
) {

    public ProjectMcpTemplateGenerationPreparation {
        mcpName = value(mcpName);
        resourceType = value(resourceType);
        templateId = value(templateId);
        transportType = value(transportType);
        transportConfig = copy(transportConfig);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        riskLevel = value(riskLevel);
        permissionPolicy = copy(permissionPolicy);
        status = value(status);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : new LinkedHashMap<>(source);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
