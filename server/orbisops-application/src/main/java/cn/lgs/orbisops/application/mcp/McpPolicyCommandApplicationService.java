package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpToolPolicyRepository;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;
import cn.lgs.orbisops.domain.mcp.service.McpToolPolicyGovernance;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

public final class McpPolicyCommandApplicationService implements McpPolicyCommandPort {

    private final McpProjectDirectoryPort projects;
    private final IMcpToolPolicyRepository policies;
    private final McpToolSnapshotStorePort snapshots;
    private final McpAuditPort audit;
    private final McpPolicyCommandModelMapper mapper;
    private final McpToolPolicyGovernance governance = new McpToolPolicyGovernance();

    public McpPolicyCommandApplicationService(McpProjectDirectoryPort projects,
                                              IMcpToolPolicyRepository policies,
                                              McpToolSnapshotStorePort snapshots,
                                              McpAuditPort audit,
                                              McpJsonCodec jsonCodec) {
        this(projects, policies, snapshots, audit, jsonCodec, Clock.systemDefaultZone());
    }

    McpPolicyCommandApplicationService(McpProjectDirectoryPort projects,
                                       IMcpToolPolicyRepository policies,
                                       McpToolSnapshotStorePort snapshots,
                                       McpAuditPort audit,
                                       McpJsonCodec jsonCodec,
                                       Clock clock) {
        if (projects == null) throw new IllegalArgumentException("MCP_PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (policies == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REPOSITORY_REQUIRED");
        if (snapshots == null) throw new IllegalArgumentException("MCP_TOOL_SNAPSHOT_REPOSITORY_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("MCP_AUDIT_PORT_REQUIRED");
        this.projects = projects;
        this.policies = policies;
        this.snapshots = snapshots;
        this.audit = audit;
        this.mapper = new McpPolicyCommandModelMapper(jsonCodec, clock);
    }

    @Override
    public McpToolPolicy upsertToolPolicy(McpCommands.PolicyMutation command) {
        McpCommands.PolicyMutation required = required(command);
        String projectId = project(required.projectId());
        String operator = actor(required.actor());
        String resolvedPolicyId = text(required.policyId());
        if (resolvedPolicyId.isBlank()) resolvedPolicyId = "mcp-policy-" + UUID.randomUUID();
        McpToolPolicy policy = mapper.createManual(
                projectId, resolvedPolicyId, operator, required.patch());
        return saveAndAudit(policy, "upsert", null);
    }

    @Override
    public McpToolPolicy approveToolPolicy(McpCommands.PolicyMutation command) {
        McpCommands.PolicyMutation required = required(command);
        String projectId = project(required.projectId());
        String policyId = policyId(required.policyId());
        String operator = actor(required.actor());
        McpToolPolicy before = policyById(projectId, policyId);
        McpToolPolicy approved = mapper.approve(before, operator, required.patch());
        governance.validateForHumanPublish(mapper.review(approved));

        McpReviewableToolSnapshot latest = latestSnapshot(projectId,
                approved.mcpId(), approved.toolId(), approved.toolName());
        if (!latest.schemaHydrated()) {
            throw new SecurityException("MCP_TOOL_SCHEMA_NOT_HYDRATED：必须先从真实 MCP server 获取 input schema，才能发布策略");
        }
        if (!approved.schemaHash().equals(latest.schemaHash())) {
            throw new SecurityException("MCP_POLICY_STALE：工具 schema 已变化，请基于最新快照重新审核");
        }

        McpToolPolicy after = saveAndAudit(approved, "upsert", null);
        audit.record(projectId, "mcp-tool-policy", "approve", policyId,
                mapper.view(before), mapper.view(after));
        return after;
    }

    @Override
    public McpToolPolicy rejectToolPolicy(McpCommands.PolicyMutation command) {
        return updateStatus(required(command),
                McpToolPolicyStatus.REJECTED,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "reject");
    }

    @Override
    public McpToolPolicy disableToolPolicy(McpCommands.PolicyMutation command) {
        return updateStatus(required(command),
                McpToolPolicyStatus.DISABLED,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                "disable");
    }

    private McpToolPolicy saveAndAudit(McpToolPolicy policy,
                                       String action,
                                       Map<String, Object> before) {
        policyStore().save(policy);
        McpToolPolicy stored = policyStore()
                .findById(policy.projectId(), policy.policyId())
                .orElse(policy);
        audit.record(policy.projectId(), "mcp-tool-policy", action,
                policy.policyId(), before, mapper.view(stored));
        return stored;
    }

    private McpToolPolicy updateStatus(McpCommands.PolicyMutation command,
                                       McpToolPolicyStatus status,
                                       McpToolPolicyReviewStatus reviewStatus,
                                       String action) {
        String projectId = project(command.projectId());
        String policyId = policyId(command.policyId());
        String operator = actor(command.actor());
        McpToolPolicy before = policyById(projectId, policyId);
        Map<String, Object> metadata = mapper.metadataWithReviewReason(before, command.patch());
        boolean updated = policyStore().updateReviewStatus(
                projectId, policyId, status, reviewStatus,
                operator, mapper.encode(metadata));
        if (!updated) {
            throw new IllegalStateException(
                    "MCP_TOOL_POLICY_UPDATE_CONFLICT：策略状态更新失败，policyId=" + policyId);
        }
        McpToolPolicy afterPolicy = policyById(projectId, policyId);
        audit.record(projectId, "mcp-tool-policy", action, policyId,
                mapper.view(before), mapper.view(afterPolicy));
        return afterPolicy;
    }

    private McpReviewableToolSnapshot latestSnapshot(String projectId,
                                                     String mcpId,
                                                     String toolId,
                                                     String toolName) {
        McpReviewableToolSnapshot stored = snapshotStore()
                .latestReviewableSnapshot(projectId, mcpId, toolId, toolName)
                .orElseThrow(() -> new IllegalStateException(
                        "MCP_TOOL_SNAPSHOT_MISSING：找不到可审核的最新工具快照"));
        if (stored.schemaHash().isBlank()) {
            throw new IllegalStateException("MCP_TOOL_SNAPSHOT_MISSING：找不到可审核的最新工具快照");
        }
        return stored;
    }

    private McpToolPolicy policyById(String projectId, String policyId) {
        return policyStore().findById(projectId, policyId)
                .orElseThrow(() -> new IllegalArgumentException("MCP 工具策略不存在：" + policyId));
    }

    private IMcpToolPolicyRepository policyStore() {
        if (!policies.available()) throw new IllegalStateException("MCP_TOOL_POLICY_STORE_UNAVAILABLE");
        return policies;
    }

    private McpToolSnapshotStorePort snapshotStore() {
        if (!snapshots.available()) throw new IllegalStateException("MCP_TOOL_SNAPSHOT_STORE_UNAVAILABLE");
        return snapshots;
    }

    private McpCommands.PolicyMutation required(McpCommands.PolicyMutation command) {
        if (command == null) throw new IllegalArgumentException("MCP_POLICY_COMMAND_REQUIRED");
        return command;
    }

    private String project(String value) {
        String normalized = text(value);
        if (normalized.isBlank() || !projects.exists(normalized)) {
            throw new IllegalArgumentException("项目不存在：" + value);
        }
        return normalized;
    }

    private String actor(String value) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("MCP_ACTOR_REQUIRED");
        return normalized;
    }

    private String policyId(String value) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("MCP_POLICY_ID_REQUIRED");
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
