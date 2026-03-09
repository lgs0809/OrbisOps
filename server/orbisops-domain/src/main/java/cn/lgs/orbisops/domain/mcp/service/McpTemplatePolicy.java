package cn.lgs.orbisops.domain.mcp.service;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class McpTemplatePolicy {

    private static final Set<String> RESOURCE_TYPES = Set.of(
            "mysql", "postgresql", "redis", "elasticsearch", "prometheus", "rabbitmq");
    private static final Set<String> TRANSPORT_TYPES = Set.of("stdio", "sse", "streamable-http");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public McpTemplateDefinition create(Map<String, Object> request) {
        return normalize(request, null, false);
    }

    public McpTemplateDefinition update(Map<String, Object> current, Map<String, Object> request) {
        if (current == null || current.isEmpty()) throw new IllegalArgumentException("MCP_TEMPLATE_CURRENT_REQUIRED");
        if (McpTemplateStatus.DELETED == McpTemplateStatus.require(text(current.get("status"), "ENABLED"))) {
            throw new IllegalStateException("MCP_TEMPLATE_DELETED");
        }
        return normalize(request, current, true);
    }

    public McpTemplateStatus transition(String currentStatus, String targetStatus) {
        McpTemplateStatus current = McpTemplateStatus.require(currentStatus);
        McpTemplateStatus target = McpTemplateStatus.require(targetStatus);
        if (current == McpTemplateStatus.DELETED) throw new IllegalStateException("MCP_TEMPLATE_DELETED");
        if (target == McpTemplateStatus.DELETED) throw new IllegalArgumentException("MCP_TEMPLATE_DELETE_REQUIRES_DEDICATED_COMMAND");
        return target;
    }

    public Map<String, Object> toMap(McpTemplateDefinition definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateId", definition.templateId());
        result.put("templateName", definition.templateName());
        result.put("resourceType", definition.resourceType());
        result.put("transportType", definition.transportType());
        result.put("defaultTransportConfig", definition.defaultTransportConfig());
        result.put("supportedActions", definition.supportedActions());
        result.put("riskLevel", definition.riskLevel());
        result.put("readOnly", definition.readOnly());
        result.put("description", definition.description());
        result.put("status", definition.status().name());
        result.put("createBy", definition.createBy());
        return result;
    }

    private McpTemplateDefinition normalize(Map<String, Object> request,
                                            Map<String, Object> current,
                                            boolean update) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (current != null) merged.putAll(current);
        if (request != null) merged.putAll(request);
        String existingId = current == null ? "" : text(current.get("templateId"), "");
        String requestedId = text(first(merged.get("templateId"), merged.get("mcpTemplateId")), existingId);
        if (update && !existingId.equals(requestedId)) {
            throw new IllegalArgumentException("MCP_TEMPLATE_ID_IMMUTABLE");
        }
        String resourceType = text(first(merged.get("resourceType"), merged.get("type")), "").toLowerCase(Locale.ROOT);
        if (!RESOURCE_TYPES.contains(resourceType)) {
            throw new IllegalArgumentException("MCP_TEMPLATE_RESOURCE_TYPE_UNSUPPORTED:" + resourceType);
        }
        String transport = text(merged.get("transportType"), "stdio").toLowerCase(Locale.ROOT);
        if (!TRANSPORT_TYPES.contains(transport)) {
            throw new IllegalArgumentException("MCP_TEMPLATE_TRANSPORT_UNSUPPORTED:" + transport);
        }
        String risk = text(merged.get("riskLevel"), "HIGH").toUpperCase(Locale.ROOT);
        if (!RISK_LEVELS.contains(risk)) {
            throw new IllegalArgumentException("MCP_TEMPLATE_RISK_UNKNOWN:" + risk);
        }
        McpTemplateStatus status = McpTemplateStatus.require(text(merged.get("status"), "ENABLED"));
        if (status == McpTemplateStatus.DELETED) {
            throw new IllegalArgumentException("MCP_TEMPLATE_CREATE_OR_UPDATE_DELETED_FORBIDDEN");
        }
        return new McpTemplateDefinition(
                requestedId,
                text(first(merged.get("templateName"), merged.get("name")), requestedId),
                resourceType,
                transport,
                map(merged.get("defaultTransportConfig")),
                strings(merged.get("supportedActions")),
                risk,
                bool(merged.get("readOnly")),
                text(merged.get("description"), ""),
                status,
                required(merged.get("createBy"), "MCP_TEMPLATE_CREATE_BY_REQUIRED"));
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            String text = text(item, "");
            if (!text.isBlank()) result.add(text);
        }
        return List.copyOf(result);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        return "true".equalsIgnoreCase(text(value, "false")) || "1".equals(text(value, ""));
    }

    private Object first(Object left, Object right) {
        return left == null || text(left, "").isBlank() ? right : left;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String required(Object value, String error) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
