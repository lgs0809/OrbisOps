package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsProjectWorkspaceMaterializationMapper {

    private static final List<String> DEFAULT_ENVIRONMENTS = List.of("dev", "test", "prod");

    private final OpsProjectResourceCredentialPolicy credentialPolicy;

    public OpsProjectWorkspaceMaterializationMapper(
            OpsProjectResourceCredentialPolicy credentialPolicy) {
        if (credentialPolicy == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_CREDENTIAL_POLICY_REQUIRED");
        }
        this.credentialPolicy = credentialPolicy;
    }

    public ProjectDefinition project(Map<String, Object> source) {
        Map<String, Object> value = source == null ? Map.of() : source;
        String projectId = required(value.get("projectId"), "PROJECT_ID_REQUIRED");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = time(value.get("createdAt"), now);
        return new ProjectDefinition(
                projectId,
                text(value.get("name"), projectId),
                text(value.get("description"), ""),
                text(value.get("owner"), ""),
                strings(value.get("environments"), DEFAULT_ENVIRONMENTS),
                text(value.get("knowledgeBaseId"), ""),
                text(value.get("defaultAgentId"), ""),
                strings(value.get("skillIds"), List.of()),
                strings(value.get("sharedMcpIds"), List.of()),
                bool(value.get("enabled"), true),
                createdAt,
                time(value.get("updatedAt"), createdAt));
    }

    public ProjectResourceDefinition resource(Map<String, Object> source) {
        Map<String, Object> value = source == null ? Map.of() : source;
        ProjectResourceType type = ProjectResourceType.from(text(value.get("type"), "mysql"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = time(value.get("createdAt"), now);
        return new ProjectResourceDefinition(
                required(value.get("resourceId"), "PROJECT_RESOURCE_ID_REQUIRED"),
                required(value.get("projectId"), "PROJECT_ID_REQUIRED"),
                type,
                text(value.get("typeName"), type.value()),
                text(value.get("name"), type.value()),
                text(value.get("environment"), "prod"),
                text(value.get("endpoint"), type.defaultEndpoint()),
                credentialPolicy.normalizeStored(map(value.get("credential"))),
                text(value.get("status"), "PREVIEW"),
                map(value.get("schema")),
                OpsProjectCapabilityMetadataPolicy.enrichPermission(
                        type.value(), map(value.get("permission"))),
                createdAt,
                time(value.get("updatedAt"), createdAt));
    }

    public ProjectMcpDefinition mcp(Map<String, Object> source) {
        Map<String, Object> value = source == null ? Map.of() : source;
        String mcpId = text(value.get("mcpId"), text(value.get("toolId"), ""));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = time(value.get("createdAt"), now);
        return new ProjectMcpDefinition(
                required(mcpId, "PROJECT_MCP_ID_REQUIRED"),
                text(value.get("mcpName"), text(value.get("toolName"), mcpId)),
                required(value.get("projectId"), "PROJECT_ID_REQUIRED"),
                text(value.get("resourceId"), ""),
                text(value.get("resourceType"), "custom"),
                text(value.get("transportType"), "stdio"),
                text(value.get("templateId"), ""),
                map(value.get("transportConfig")),
                strings(value.get("allowedActions"), List.of()),
                ProjectMcpRiskLevel.failClosed(text(value.get("riskLevel"), "HIGH")),
                bool(value.get("readOnly"), false),
                map(value.get("permissionPolicy")),
                integer(value.get("requestTimeout"), 30),
                ProjectMcpStatus.from(text(value.get("status"), "ENABLED")),
                createdAt,
                time(value.get("updatedAt"), createdAt));
    }

    public Map<String, Object> projectView(
            ProjectDefinition definition,
            Map<String, Object> compatibilityPayload) {
        if (definition == null) {
            return Map.of();
        }
        Map<String, Object> view = copy(compatibilityPayload);
        view.put("projectId", definition.projectId());
        view.put("name", definition.name());
        view.put("description", definition.description());
        view.put("owner", definition.owner());
        view.put("environments", definition.environments());
        view.put("knowledgeBaseId", definition.knowledgeBaseId());
        view.put("defaultAgentId", definition.defaultAgentId());
        view.put("skillIds", definition.skillIds());
        view.put("sharedMcpIds", definition.sharedMcpIds());
        view.put("enabled", definition.enabled());
        view.put("createdAt", time(definition.createdAt()));
        view.put("updatedAt", time(definition.updatedAt()));
        return view;
    }

    public Map<String, Object> resourcePayload(
            ProjectResourceDefinition resource,
            Map<String, Object> compatibilityPayload) {
        if (resource == null) {
            return Map.of();
        }
        Map<String, Object> view = copy(compatibilityPayload);
        view.put("resourceId", resource.resourceId());
        view.put("projectId", resource.projectId());
        view.put("type", resource.type().value());
        view.put("typeName", resource.typeName());
        view.put("name", resource.name());
        view.put("environment", resource.environment());
        view.put("endpoint", resource.endpoint());
        view.put("credential", resource.credential());
        view.put("status", resource.status());
        view.put("schema", resource.schema());
        view.put("permission", resource.permission());
        view.put("createdAt", time(resource.createdAt()));
        view.put("updatedAt", time(resource.updatedAt()));
        return view;
    }

    public Map<String, Object> mcpPayload(
            ProjectMcpDefinition definition,
            Map<String, Object> compatibilityPayload) {
        if (definition == null) {
            return Map.of();
        }
        Map<String, Object> view = copy(compatibilityPayload);
        view.put("mcpId", definition.mcpId());
        view.put("toolId", definition.mcpId());
        view.put("mcpName", definition.mcpName());
        view.put("toolName", definition.mcpName());
        view.put("projectId", definition.projectId());
        view.put("resourceId", definition.resourceId());
        view.put("resourceType", definition.resourceType());
        view.put("transportType", definition.transportType());
        view.put("templateId", definition.templateId());
        view.put("transportConfig", definition.transportConfig());
        view.put("allowedActions", definition.allowedActions());
        view.put("riskLevel", definition.riskLevel());
        view.put("readOnly", definition.readOnly());
        view.put("permissionPolicy", definition.permissionPolicy());
        view.put("requestTimeout", definition.requestTimeout());
        view.put("status", definition.status().name());
        view.put("createdAt", time(definition.createdAt()));
        view.put("updatedAt", time(definition.updatedAt()));
        Map<String, Object> transportConfig = definition.transportConfig();
        if (transportConfig.get("remoteToolMetadata") instanceof Map<?, ?> metadata) {
            view.put("remoteToolMetadata", map(metadata));
        }
        if (transportConfig.get("remoteTools") instanceof Iterable<?> remoteTools) {
            List<Object> tools = new ArrayList<>();
            remoteTools.forEach(tools::add);
            view.put("remoteTools", List.copyOf(tools));
        }
        view.put("connectionStatus", text(transportConfig.get("connectionStatus"), ""));
        view.put("policyStatus", text(transportConfig.get("policyStatus"), ""));
        view.put("discoveryError", text(transportConfig.get("discoveryError"), ""));
        return view;
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
    }

    private Map<String, Object> map(Object source) {
        if (!(source instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<String> strings(Object source, List<String> fallback) {
        if (source == null) {
            return fallback == null ? List.of() : List.copyOf(fallback);
        }
        List<String> result = new ArrayList<>();
        if (source instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(result, item));
        } else {
            for (String item : String.valueOf(source).split("[,，\\n]")) {
                add(result, item);
            }
        }
        List<String> values = result.stream().distinct().toList();
        return values.isEmpty() && fallback != null ? List.copyOf(fallback) : values;
    }

    private void add(List<String> target, Object value) {
        String normalized = text(value, "");
        if (!normalized.isBlank() && !normalized.startsWith("{\"$ref\"")) {
            target.add(normalized);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = text(value, "");
        return normalized.isBlank() ? fallback
                : "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "enabled".equalsIgnoreCase(normalized);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private LocalDateTime time(Object value, LocalDateTime fallback) {
        String normalized = text(value, "");
        if (normalized.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(normalized.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String required(Object value, String error) {
        String normalized = text(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }
}
