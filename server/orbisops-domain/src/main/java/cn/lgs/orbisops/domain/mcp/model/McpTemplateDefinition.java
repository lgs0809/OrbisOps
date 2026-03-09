package cn.lgs.orbisops.domain.mcp.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpTemplateDefinition(String templateId,
                                    String templateName,
                                    String resourceType,
                                    String transportType,
                                    Map<String, Object> defaultTransportConfig,
                                    List<String> supportedActions,
                                    String riskLevel,
                                    boolean readOnly,
                                    String description,
                                    McpTemplateStatus status,
                                    String createBy) {

    public McpTemplateDefinition {
        templateId = required(templateId, "MCP_TEMPLATE_ID_REQUIRED");
        templateName = required(templateName, "MCP_TEMPLATE_NAME_REQUIRED");
        resourceType = required(resourceType, "MCP_TEMPLATE_RESOURCE_TYPE_REQUIRED");
        transportType = required(transportType, "MCP_TEMPLATE_TRANSPORT_REQUIRED");
        defaultTransportConfig = defaultTransportConfig == null || defaultTransportConfig.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(defaultTransportConfig));
        supportedActions = supportedActions == null || supportedActions.isEmpty()
                ? List.of()
                : supportedActions.stream()
                        .map(McpTemplateDefinition::value)
                        .filter(action -> !action.isBlank())
                        .distinct()
                        .toList();
        riskLevel = required(riskLevel, "MCP_TEMPLATE_RISK_REQUIRED");
        description = value(description);
        if (status == null) throw new IllegalArgumentException("MCP_TEMPLATE_STATUS_REQUIRED");
        createBy = value(createBy);
    }

    private static String required(String input, String error) {
        String value = value(input);
        if (value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
