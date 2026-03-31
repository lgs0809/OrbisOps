package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;

import java.util.List;
import java.util.Map;

/** Query boundary for MCP activations, routing decisions and call history. */
public final class McpRuntimeHistoryQueryService {

    private final McpProjectDirectoryPort projects;
    private final IMcpRuntimeCatalogRepository runtimeCatalog;
    private final McpRuntimeViewMapper views;

    public McpRuntimeHistoryQueryService(
            McpProjectDirectoryPort projects,
            IMcpRuntimeCatalogRepository runtimeCatalog,
            McpRuntimeViewMapper views) {
        if (projects == null) throw new IllegalArgumentException("MCP_PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (runtimeCatalog == null) throw new IllegalArgumentException("MCP_RUNTIME_CATALOG_REPOSITORY_REQUIRED");
        if (views == null) throw new IllegalArgumentException("MCP_RUNTIME_VIEW_MAPPER_REQUIRED");
        this.projects = projects;
        this.runtimeCatalog = runtimeCatalog;
        this.views = views;
    }

    public List<Map<String, Object>> runtimeActivations(String projectId, int limit) {
        String id = project(projectId);
        return runtime().findActivations(id, limit(limit)).stream()
                .map(views::activationView)
                .toList();
    }

    public List<Map<String, Object>> decisions(String projectId, int limit) {
        String id = project(projectId);
        return runtime().findRoutingDecisions(id, limit(limit)).stream()
                .map(views::decisionView)
                .toList();
    }

    public List<Map<String, Object>> calls(String projectId, int limit) {
        String id = project(projectId);
        return runtime().findToolCalls(id, limit(limit)).stream()
                .map(views::toolCallView)
                .toList();
    }

    public Map<String, Object> call(String projectId, String callId) {
        String id = project(projectId);
        String normalizedCallId = required(callId, "MCP_CALL_ID_REQUIRED");
        return runtime().findToolCall(id, normalizedCallId)
                .map(views::toolCallView)
                .orElseThrow(() -> new IllegalArgumentException("MCP 调用记录不存在：" + normalizedCallId));
    }

    private IMcpRuntimeCatalogRepository runtime() {
        if (!runtimeCatalog.available()) {
            throw new IllegalStateException("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE");
        }
        return runtimeCatalog;
    }

    private String project(String value) {
        String normalized = required(value, "MCP_PROJECT_ID_REQUIRED");
        if (!projects.exists(normalized)) throw new IllegalArgumentException("项目不存在：" + normalized);
        return normalized;
    }

    private int limit(int value) {
        if (value <= 0 || value > 1000) throw new IllegalArgumentException("MCP_QUERY_LIMIT_INVALID");
        return value;
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
