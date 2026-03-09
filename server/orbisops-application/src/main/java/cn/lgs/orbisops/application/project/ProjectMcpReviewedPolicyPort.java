package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.mcp.McpReviewedToolPolicySnapshot;

import java.util.List;

/** MCP Governance review facts consumed by Project Workspace activation. */
public interface ProjectMcpReviewedPolicyPort {

    List<McpReviewedToolPolicySnapshot> reviewedPolicies(String projectId, int limit);
}
