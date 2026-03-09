package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.mcp.McpPolicyQueryService;
import cn.lgs.orbisops.application.mcp.McpReviewedToolPolicySnapshot;
import cn.lgs.orbisops.application.project.ProjectMcpReviewedPolicyPort;
import org.springframework.stereotype.Component;

import java.util.List;

/** ACL exposing MCP review facts to Project Workspace. */
@Component
public final class OpsProjectMcpReviewedPolicyAdapter implements ProjectMcpReviewedPolicyPort {

    private final McpPolicyQueryService policies;

    public OpsProjectMcpReviewedPolicyAdapter(McpPolicyQueryService policies) {
        if (policies == null) throw new IllegalArgumentException("MCP_POLICY_QUERY_REQUIRED");
        this.policies = policies;
    }

    @Override
    public List<McpReviewedToolPolicySnapshot> reviewedPolicies(String projectId, int limit) {
        return policies.reviewedPolicySnapshots(projectId, limit);
    }
}
