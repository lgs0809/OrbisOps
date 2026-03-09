package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;

import java.util.List;
import java.util.Map;

/** Query boundary for MCP policy models and projections. */
public final class McpPolicyQueryService {

    private final McpProjectDirectoryPort projects;
    private final IMcpToolPolicyRepository policies;
    private final McpRuntimeViewMapper views;

    public McpPolicyQueryService(
            McpProjectDirectoryPort projects,
            IMcpToolPolicyRepository policies,
            McpRuntimeViewMapper views) {
        if (projects == null) throw new IllegalArgumentException("MCP_PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (policies == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REPOSITORY_REQUIRED");
        if (views == null) throw new IllegalArgumentException("MCP_RUNTIME_VIEW_MAPPER_REQUIRED");
        this.projects = projects;
        this.policies = policies;
        this.views = views;
    }

    public List<Map<String, Object>> policies(String projectId, int limit) {
        String id = project(projectId);
        int max = limit(limit);
        if (!policies.available()) return List.of();
        var result = policies.findAllForAdmin(id, max);
        return result == null ? List.of() : result.stream().map(views::policyAdminView).toList();
    }

    public List<McpToolPolicy> policyModels(String projectId, int limit) {
        String id = project(projectId);
        int max = limit(limit);
        if (!policies.available()) return List.of();
        List<McpToolPolicy> result = policies.findAll(id, max);
        return result == null ? List.of() : List.copyOf(result);
    }

    public List<McpReviewedToolPolicySnapshot> reviewedPolicySnapshots(
            String projectId,
            int limit) {
        return policyModels(projectId, limit).stream()
                .map(McpReviewedToolPolicySnapshot::from)
                .toList();
    }

    public Map<String, Object> view(McpToolPolicy policy) {
        return views.policyView(policy);
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
