package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpProjectToolCatalogPort;
import cn.lgs.orbisops.application.mcp.McpProjectToolDescriptor;
import cn.lgs.orbisops.application.mcp.McpRemoteToolDescriptor;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public final class OpsMcpProjectToolCatalogAdapter implements McpProjectToolCatalogPort {

    private final ProjectMcpCatalogApplicationService catalog;

    public OpsMcpProjectToolCatalogAdapter(ProjectMcpCatalogApplicationService catalog) {
        if (catalog == null) throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        this.catalog = catalog;
    }

    @Override
    public List<McpProjectToolDescriptor> list(String projectId) {
        return catalog.list(projectId).stream().map(this::descriptor).toList();
    }

    @Override
    public Optional<McpProjectToolDescriptor> find(String projectId, String toolIdOrMcpId) {
        return catalog.find(projectId, toolIdOrMcpId).map(this::descriptor);
    }

    private McpProjectToolDescriptor descriptor(ProjectMcpDefinition definition) {
        return new McpProjectToolDescriptor(
                definition.projectId(),
                definition.mcpId(),
                definition.mcpName(),
                definition.resourceType(),
                definition.transportType(),
                definition.allowedActions(),
                McpRiskLevel.failClosed(definition.riskLevel().name()),
                definition.readOnly(),
                definition.permissionPolicy(),
                definition.requestTimeout(),
                definition.status(),
                remoteTools(definition.transportConfig()));
    }

    private Map<String, McpRemoteToolDescriptor> remoteTools(Map<String, Object> transportConfig) {
        Map<String, Object> config = transportConfig == null ? Map.of() : transportConfig;
        boolean platformGenerated = Boolean.TRUE.equals(config.get("generated"))
                && !text(config.get("resourceId")).isBlank()
                && !text(config.get("serverTemplate")).isBlank();
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        collectRemoteTools(raw, config.get("remoteTools"));
        collectNamedMap(raw, config.get("toolSchemas"));
        collectNamedMap(raw, config.get("remoteToolMetadata"));
        Map<String, McpRemoteToolDescriptor> result = new LinkedHashMap<>();
        raw.forEach((name, metadata) -> {
            Map<String, Object> enriched = new LinkedHashMap<>(metadata);
            if (platformGenerated) {
                enriched.put("platformGenerated", true);
            }
            result.put(name, remoteDescriptor(name, Map.copyOf(enriched)));
        });
        return Map.copyOf(result);
    }

    private void collectRemoteTools(Map<String, Map<String, Object>> target, Object source) {
        if (!(source instanceof Iterable<?> iterable)) return;
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> metadata = map(raw);
            String name = firstText(metadata, "toolName", "remoteToolName", "name");
            if (!name.isBlank()) target.putIfAbsent(name, metadata);
        }
    }

    private void collectNamedMap(Map<String, Map<String, Object>> target, Object source) {
        if (!(source instanceof Map<?, ?> raw)) return;
        raw.forEach((key, value) -> {
            if (value instanceof Map<?, ?> metadata) {
                target.putIfAbsent(String.valueOf(key), map(metadata));
            }
        });
    }

    private McpRemoteToolDescriptor remoteDescriptor(String toolName,
                                                      Map<String, Object> metadata) {
        boolean riskDeclared = has(metadata, "riskLevel", "risk_level");
        boolean readOnlyDeclared = has(metadata, "readOnly", "read_only");
        boolean actionsDeclared = has(metadata, "allowedActions", "allowed_actions");
        return new McpRemoteToolDescriptor(
                toolName,
                firstText(metadata, "description", "desc", "summary"),
                strings(first(metadata, "allowedActions", "allowed_actions")),
                McpRiskLevel.failClosed(firstText(metadata, "riskLevel", "risk_level")),
                bool(first(metadata, "readOnly", "read_only")),
                riskDeclared && readOnlyDeclared && actionsDeclared,
                metadata);
    }

    private boolean has(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) return true;
        }
        return false;
    }

    private Object first(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) return source.get(key);
        }
        return null;
    }

    private String firstText(Map<String, Object> source, String... keys) {
        Object value = first(source, keys);
        return text(value);
    }

    private List<String> strings(Object source) {
        if (source == null) return List.of();
        List<String> result = new ArrayList<>();
        if (source instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(result, item));
        } else {
            for (String item : text(source).split("[,;，\\n]")) add(result, item);
        }
        return result.stream().distinct().toList();
    }

    private void add(List<String> target, Object value) {
        String normalized = text(value);
        if (!normalized.isBlank()) target.add(normalized);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private Map<String, Object> map(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return Map.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
