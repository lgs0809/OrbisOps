package cn.lgs.orbisops.application.project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProjectMcpGenerationPreparation(
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

    public ProjectMcpGenerationPreparation {
        mcpName = value(mcpName);
        resourceType = value(resourceType);
        templateId = value(templateId);
        transportType = value(transportType);
        transportConfig = transportConfig == null
                ? Map.of()
                : new LinkedHashMap<>(transportConfig);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        riskLevel = value(riskLevel);
        permissionPolicy = permissionPolicy == null
                ? Map.of()
                : new LinkedHashMap<>(permissionPolicy);
        status = value(status);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
