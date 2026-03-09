package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolRuntimeAccess;
import cn.lgs.orbisops.domain.mcp.service.McpToolPolicyGovernance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read model for the reviewed MCP tools that may participate in runtime selection. */
public final class McpRuntimeCatalogQueryService {

    private final McpProjectDirectoryPort projects;
    private final IMcpToolPolicyRepository policies;
    private final McpRuntimeViewMapper views;
    private final McpToolPolicyGovernance governance;

    public McpRuntimeCatalogQueryService(
            McpProjectDirectoryPort projects,
            IMcpToolPolicyRepository policies,
            McpRuntimeViewMapper views) {
        this(projects, policies, views, new McpToolPolicyGovernance());
    }

    McpRuntimeCatalogQueryService(
            McpProjectDirectoryPort projects,
            IMcpToolPolicyRepository policies,
            McpRuntimeViewMapper views,
            McpToolPolicyGovernance governance) {
        if (projects == null) throw new IllegalArgumentException("MCP_PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (policies == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REPOSITORY_REQUIRED");
        if (views == null) throw new IllegalArgumentException("MCP_RUNTIME_VIEW_MAPPER_REQUIRED");
        if (governance == null) throw new IllegalArgumentException("MCP_POLICY_GOVERNANCE_REQUIRED");
        this.projects = projects;
        this.policies = policies;
        this.views = views;
        this.governance = governance;
    }

    public List<Map<String, Object>> executableTools(
            String projectId,
            String toolIdOrMcpId,
            List<String> allowedTools,
            List<String> blockedTools) {
        return executableTools(projectId, toolIdOrMcpId, allowedTools, blockedTools, "PREPARE");
    }

    public List<Map<String, Object>> executableTools(
            String projectId,
            String toolIdOrMcpId,
            List<String> allowedTools,
            List<String> blockedTools,
            String executionStage) {
        String id = project(projectId);
        String toolId = required(toolIdOrMcpId, "MCP_TOOL_ID_REQUIRED");
        String stage = required(executionStage, "MCP_EXECUTION_STAGE_REQUIRED").toUpperCase(Locale.ROOT);
        if (!policies.available()) return List.of();
        List<String> allowed = safe(allowedTools);
        List<String> blocked = safe(blockedTools);
        Map<String, Map<String, Object>> byTool = new LinkedHashMap<>();
        for (McpToolPolicy policy : policies.findRuntimeExecutable(id, toolId)) {
            String toolName = policy.toolName();
            if (toolName.isBlank()
                    || matches(blocked, toolName)
                    || (!allowed.isEmpty() && !matches(allowed, "*") && !matches(allowed, toolName))
                    || !stageExecutable(policy, stage)) {
                continue;
            }
            byTool.putIfAbsent(toolName, views.runtimeToolSummary(policy));
        }
        return List.copyOf(byTool.values());
    }

    private boolean stageExecutable(McpToolPolicy policy, String stage) {
        if ("LANDING".equals(stage)) {
            return policy.landAllowed();
        }
        if ("PREPARE_CHANGE".equals(stage)) {
            return isPreApprovalExecutable(policy) || proposableProductionMutation(policy);
        }
        return isPreApprovalExecutable(policy);
    }

    private boolean proposableProductionMutation(McpToolPolicy policy) {
        return policy != null
                && !policy.readOnly()
                && policy.requiresApprovedPackage()
                && policy.landAllowed();
    }

    private boolean isPreApprovalExecutable(McpToolPolicy policy) {
        return governance.isPreApprovalExecutable(new McpToolRuntimeAccess(
                policy.effectType(),
                policy.effectScope(),
                policy.mutability(),
                policy.riskLevel(),
                policy.readOnly(),
                policy.investigateAllowed(),
                policy.prepareAllowed(),
                policy.requiresApprovedPackage()));
    }

    private String project(String value) {
        String normalized = required(value, "MCP_PROJECT_ID_REQUIRED");
        if (!projects.exists(normalized)) throw new IllegalArgumentException("项目不存在：" + normalized);
        return normalized;
    }

    private boolean matches(List<String> configured, String toolName) {
        String normalized = text(toolName).toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || configured == null || configured.isEmpty()) return false;
        return configured.stream().map(value -> text(value).toLowerCase(Locale.ROOT))
                .anyMatch(value -> "*".equals(value) || normalized.equals(value));
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
