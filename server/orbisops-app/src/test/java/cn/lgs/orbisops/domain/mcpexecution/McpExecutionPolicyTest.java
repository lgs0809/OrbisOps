package cn.lgs.orbisops.domain.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionTarget;
import cn.lgs.orbisops.domain.mcpexecution.service.McpExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpExecutionPolicyTest {

    private final McpExecutionPolicy policy = new McpExecutionPolicy();

    @Test
    void blockedToolsMustOverrideAllowedAndNotificationTools() {
        McpExecutionConfig config = config(
                List.of("query_metrics", "notify"), List.of("notify"), List.of("notify"));

        assertFalse(policy.authorize(config, "notify").allowed());
        assertTrue(policy.authorize(config, "query_metrics").allowed());
        assertEquals("MCP_TOOL_NOT_AUTHORIZED",
                policy.authorize(config, "restart_service").reasonCode());
    }

    @Test
    void policyMustRejectUnknownEffectsAndClassifySafeReadTarget() {
        assertEquals("MCP_TOOL_EFFECT_UNKNOWN",
                policy.policyDecision(Map.of(
                        "policyStatus", "ACTIVE",
                        "reviewStatus", "HUMAN_REVIEWED",
                        "effectType", "UNKNOWN",
                        "effectScope", "READ_ONLY",
                        "mutability", "READ_ONLY"), "query_metrics").reasonCode());

        Map<String, Object> schema = Map.of(
                "policyStatus", "ACTIVE",
                "reviewStatus", "HUMAN_REVIEWED",
                "effectType", "READ_EXTERNAL_STATE",
                "effectScope", "READ_ONLY",
                "mutability", "READ_ONLY",
                "readOnly", true,
                "riskLevel", "LOW");
        McpExecutionTarget target = policy.target(config(List.of("query_metrics"), List.of(), List.of()),
                "query_metrics", schema);

        assertTrue(target.readOnly());
        assertFalse(target.requiresChangePackage());
        assertFalse(target.writesTargetResource());
    }

    @Test
    void systemVerifiedReadOnlyPolicyIsExecutableWithoutHumanReview() {
        Map<String, Object> schema = Map.of(
                "policyStatus", "ACTIVE",
                "reviewStatus", "SYSTEM_VERIFIED",
                "effectType", "READ_EXTERNAL_STATE",
                "effectScope", "TARGET_RESOURCE_READ",
                "mutability", "READ_ONLY",
                "readOnly", true,
                "riskLevel", "LOW");

        assertTrue(policy.policyDecision(schema, "query_metrics").allowed());
        McpExecutionTarget target = policy.target(
                config(List.of("query_metrics"), List.of(), List.of()),
                "query_metrics",
                schema);
        assertTrue(target.enabled());
        assertTrue(target.readOnly());
        assertFalse(target.requiresChangePackage());
    }

    @Test
    void trustedLandingScopeMustRequireBothTrustAndApproval() {
        McpExecutionRequest untrusted = new McpExecutionRequest(
                config(List.of("query_metrics"), List.of(), List.of()),
                "alice", "{}", Map.of("toolName", "query_metrics"), false);
        McpExecutionRequest trusted = new McpExecutionRequest(
                landingConfig(), "alice", "{}", Map.of("toolName", "query_metrics"), true);

        assertEquals("PRE_APPROVAL_WORKFLOW", policy.executionScope(untrusted));
        assertEquals("APPROVED_LANDING", policy.executionScope(trusted));
    }

    private McpExecutionConfig config(
            List<String> allowed,
            List<String> notification,
            List<String> blocked) {
        return new McpExecutionConfig(
                "Prometheus", "metrics", "project-1", "run-1", "agent-1", "node-1",
                "prometheus", "mcp-tool-1", "operation-1", "streamable-http", "",
                "http://localhost/mcp", 30, List.of(), Map.of(), Map.of(), Map.of(),
                allowed, notification, blocked, false, "", "", 0);
    }

    private McpExecutionConfig landingConfig() {
        return new McpExecutionConfig(
                "Prometheus", "metrics", "project-1", "run-1", "agent-1", "node-1",
                "prometheus", "mcp-tool-1", "operation-1", "streamable-http", "",
                "http://localhost/mcp", 30, List.of(), Map.of(), Map.of(), Map.of(),
                List.of("query_metrics"), List.of(), List.of(), true,
                "cp-1", "hash-1", 2);
    }
}
