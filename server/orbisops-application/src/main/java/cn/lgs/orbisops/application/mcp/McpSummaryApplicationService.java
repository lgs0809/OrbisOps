package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.model.McpCatalogSummary;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpSummaryApplicationService implements McpSummaryPort {

    private final McpProjectDirectoryPort projects;
    private final McpProjectToolCatalogPort catalog;
    private final SelectRuntimeMcpToolsQuery runtimeTools;
    private final IMcpRuntimeCatalogRepository runtimeCatalog;
    private final McpJsonCodec jsonCodec;
    private final Clock clock;

    public McpSummaryApplicationService(McpProjectDirectoryPort projects,
                                        McpProjectToolCatalogPort catalog,
                                        SelectRuntimeMcpToolsQuery runtimeTools,
                                        IMcpRuntimeCatalogRepository runtimeCatalog,
                                        McpJsonCodec jsonCodec) {
        this(projects, catalog, runtimeTools, runtimeCatalog, jsonCodec, Clock.systemUTC());
    }

    McpSummaryApplicationService(McpProjectDirectoryPort projects,
                                 McpProjectToolCatalogPort catalog,
                                 SelectRuntimeMcpToolsQuery runtimeTools,
                                 IMcpRuntimeCatalogRepository runtimeCatalog,
                                 McpJsonCodec jsonCodec,
                                 Clock clock) {
        if (projects == null) throw new IllegalArgumentException("MCP_PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (catalog == null) throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        if (runtimeTools == null) throw new IllegalArgumentException("MCP_RUNTIME_QUERY_REQUIRED");
        if (runtimeCatalog == null) throw new IllegalArgumentException("MCP_RUNTIME_CATALOG_REPOSITORY_REQUIRED");
        if (jsonCodec == null) throw new IllegalArgumentException("MCP_JSON_CODEC_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("MCP_SUMMARY_CLOCK_REQUIRED");
        this.projects = projects;
        this.catalog = catalog;
        this.runtimeTools = runtimeTools;
        this.runtimeCatalog = runtimeCatalog;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> summary(String projectId) {
        String id = project(projectId);
        if (!runtimeCatalog.available()) {
            throw new IllegalStateException("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE");
        }
        List<Map<String, Object>> tools = catalog.list(id).stream()
                .filter(definition -> definition.status() == ProjectMcpStatus.ENABLED)
                .map(tool -> summaryTool(id, tool))
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", id);
        data.put("toolCount", tools.size());
        data.put("tools", tools);
        data.put("generatedAt", LocalDateTime.now(clock).toString());
        runtimeCatalog.saveCatalogSummary(new McpCatalogSummary(id, jsonCodec.encode(data), tools.size()));
        return data;
    }

    @Override
    public Map<String, Object> rebuildSummary(String projectId) {
        return summary(projectId);
    }

    private Map<String, Object> summaryTool(
            String projectId,
            McpProjectToolDescriptor tool) {
        String toolId = tool.mcpId();
        List<Map<String, Object>> executable = runtimeTools.runtimeExecutableTools(
                projectId, toolId, List.of(), List.of());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolId", toolId);
        data.put("toolName", tool.toolName());
        data.put("resourceType", tool.resourceType());
        data.put("allowedActions", tool.allowedActions());
        data.put("riskLevel", tool.riskLevel().name());
        data.put("readOnly", tool.readOnly());
        data.put("status", tool.status().name());
        data.put("description", "");
        data.put("runtimeExecutableToolCount", executable.size());
        data.put("runtimeExecutableTools", executable);
        return data;
    }

    private String project(String value) {
        String normalized = required(value, "MCP_PROJECT_ID_REQUIRED");
        if (!projects.exists(normalized)) throw new IllegalArgumentException("项目不存在：" + normalized);
        return normalized;
    }

    private List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> {
                String normalized = text(item, "");
                if (!normalized.isBlank()) result.add(normalized);
            });
        }
        return List.copyOf(result);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value, "");
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized) || "yes".equalsIgnoreCase(normalized);
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
