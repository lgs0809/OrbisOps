package cn.lgs.orbisops.application.mcp;

import java.util.List;
import java.util.Map;

public final class McpGovernanceApplicationService {

    private final McpSummaryPort summaries;
    private final McpPolicyCommandPort policyCommands;
    private final McpRuntimeHistoryQueryService historyQueries;
    private final McpToolSnapshotQueryService snapshotQueries;
    private final McpPolicyQueryService policyQueries;

    public McpGovernanceApplicationService(
            McpSummaryPort summaries,
            McpPolicyCommandPort policyCommands,
            McpRuntimeHistoryQueryService historyQueries,
            McpToolSnapshotQueryService snapshotQueries,
            McpPolicyQueryService policyQueries) {
        if (summaries == null) throw new IllegalArgumentException("MCP_SUMMARY_PORT_REQUIRED");
        if (policyCommands == null) throw new IllegalArgumentException("MCP_POLICY_COMMAND_PORT_REQUIRED");
        if (historyQueries == null) throw new IllegalArgumentException("MCP_RUNTIME_HISTORY_QUERY_REQUIRED");
        if (snapshotQueries == null) throw new IllegalArgumentException("MCP_TOOL_SNAPSHOT_QUERY_REQUIRED");
        if (policyQueries == null) throw new IllegalArgumentException("MCP_POLICY_QUERY_REQUIRED");
        this.summaries = summaries;
        this.policyCommands = policyCommands;
        this.historyQueries = historyQueries;
        this.snapshotQueries = snapshotQueries;
        this.policyQueries = policyQueries;
    }

    public Map<String, Object> summary(String projectId) {
        return summaries.summary(projectId(projectId));
    }

    public Map<String, Object> rebuildSummary(String projectId) {
        return summaries.rebuildSummary(projectId(projectId));
    }

    public List<Map<String, Object>> runtimeActivations(String projectId, int limit) {
        return historyQueries.runtimeActivations(projectId(projectId), limit(limit));
    }

    public List<Map<String, Object>> toolSnapshots(String projectId, int limit) {
        return snapshotQueries.snapshots(projectId(projectId), limit(limit));
    }

    public List<Map<String, Object>> toolPolicies(String projectId, int limit) {
        return policyQueries.policies(projectId(projectId), limit(limit));
    }

    public Map<String, Object> upsertToolPolicy(McpCommands.PolicyMutation command) {
        return policyQueries.view(policyCommands.upsertToolPolicy(command));
    }

    public Map<String, Object> approveToolPolicy(McpCommands.PolicyMutation command) {
        return policyQueries.view(policyCommands.approveToolPolicy(command));
    }

    public Map<String, Object> rejectToolPolicy(McpCommands.PolicyMutation command) {
        return policyQueries.view(policyCommands.rejectToolPolicy(command));
    }

    public Map<String, Object> disableToolPolicy(McpCommands.PolicyMutation command) {
        return policyQueries.view(policyCommands.disableToolPolicy(command));
    }

    public List<Map<String, Object>> decisions(String projectId, int limit) {
        return historyQueries.decisions(projectId(projectId), limit(limit));
    }

    public List<Map<String, Object>> mcpCalls(String projectId, int limit) {
        return historyQueries.calls(projectId(projectId), limit(limit));
    }

    public Map<String, Object> mcpCall(String projectId, String callId) {
        return historyQueries.call(projectId(projectId), required(callId, "MCP_CALL_ID_REQUIRED"));
    }

    private String projectId(String value) {
        return required(value, "MCP_PROJECT_ID_REQUIRED");
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
