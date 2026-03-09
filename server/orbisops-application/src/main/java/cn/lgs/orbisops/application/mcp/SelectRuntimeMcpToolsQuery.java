package cn.lgs.orbisops.application.mcp;

import java.util.List;
import java.util.Map;

/** Current-run MCP tool selection only. Governance/history/snapshot queries use dedicated services. */
public final class SelectRuntimeMcpToolsQuery {

    private final McpRuntimeCatalogQueryService catalog;
    private final McpRuntimeViewMapper views;

    public SelectRuntimeMcpToolsQuery(
            McpRuntimeCatalogQueryService catalog,
            McpRuntimeViewMapper views) {
        if (catalog == null) throw new IllegalArgumentException("MCP_RUNTIME_CATALOG_QUERY_REQUIRED");
        if (views == null) throw new IllegalArgumentException("MCP_RUNTIME_VIEW_MAPPER_REQUIRED");
        this.catalog = catalog;
        this.views = views;
    }

    public List<Map<String, Object>> runtimeCatalog(
            String projectId,
            String toolIdOrMcpId,
            List<String> allowedTools,
            List<String> blockedTools) {
        return runtimeExecutableTools(projectId, toolIdOrMcpId, allowedTools, blockedTools).stream()
                .map(views::runtimeCatalogView)
                .toList();
    }

    public List<Map<String, Object>> runtimeCatalog(
            String projectId,
            String toolIdOrMcpId,
            List<String> allowedTools,
            List<String> blockedTools,
            String executionStage) {
        return runtimeExecutableTools(
                projectId, toolIdOrMcpId, allowedTools, blockedTools, executionStage).stream()
                .map(views::runtimeCatalogView)
                .toList();
    }

    public List<Map<String, Object>> runtimeExecutableTools(
            String projectId,
            String toolIdOrMcpId,
            List<String> allowedTools,
            List<String> blockedTools) {
        return catalog.executableTools(projectId, toolIdOrMcpId, allowedTools, blockedTools);
    }

    public List<Map<String, Object>> runtimeExecutableTools(
            String projectId,
            String toolIdOrMcpId,
            List<String> allowedTools,
            List<String> blockedTools,
            String executionStage) {
        return catalog.executableTools(
                projectId, toolIdOrMcpId, allowedTools, blockedTools, executionStage);
    }
}
