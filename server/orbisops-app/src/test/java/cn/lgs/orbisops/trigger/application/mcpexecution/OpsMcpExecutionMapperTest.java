package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMcpExecutionMapperTest {

    private final OpsMcpExecutionMapper mapper = new OpsMcpExecutionMapper();

    @Test
    void taskDeadlineMustSurviveTypedAndRuntimeConfigBoundaries() {
        var config = config();
        var deadline = java.time.Instant.now().plusSeconds(3);
        config.setAuthorityDeadline(deadline);
        var request = mapper.request(config, "{\"toolName\":\"query_metrics\"}", "alice", false);
        assertEquals(deadline, request.config().authorityDeadline());
        assertEquals(deadline, mapper.legacy(request.config(), "PREPARE").getAuthorityDeadline());
    }

    @Test
    void ordinaryEntryMustIgnoreForgedLandingFlags() {
        OpsMcpServerConfig config = config();
        config.setLandingApproved(true);
        config.setInternalCaller(OpsToolsetRouter.LANDING_INTERNAL_CALLER);
        config.setLandingRuntimeToken(OpsToolsetRouter.LANDING_RUNTIME_TOKEN);

        McpExecutionRequest request = mapper.request(
                config, "{\"toolName\":\"query_metrics\"}", "alice", false);

        assertFalse(request.trustedLandingRuntime());
        assertFalse(request.config().landingApproved());
        OpsMcpServerConfig legacy = mapper.legacy(request.config(), "PREPARE");
        assertEquals("", legacy.getInternalCaller());
        assertEquals("", legacy.getLandingRuntimeToken());
    }

    @Test
    void trustedLandingEntryMustInjectServerOwnedCredentialsOnlyInTrigger() {
        OpsMcpServerConfig config = config();
        config.setLandingApproved(true);
        config.setChangePackageId("cp-1");
        config.setApprovedPackageHash("hash-1");
        config.setApprovedPackageVersion(2);

        McpExecutionRequest request = mapper.request(
                config, "{\"toolName\":\"restart_service\"}", "landing-worker", true);
        OpsMcpServerConfig legacy = mapper.legacy(request.config(), "LANDING");

        assertTrue(request.trustedLandingRuntime());
        assertTrue(request.config().landingApproved());
        assertEquals(OpsToolsetRouter.LANDING_INTERNAL_CALLER, legacy.getInternalCaller());
        assertEquals(OpsToolsetRouter.LANDING_RUNTIME_TOKEN, legacy.getLandingRuntimeToken());
        assertEquals("cp-1", legacy.getChangePackageId());
    }

    @Test
    void shouldProjectTypedResponseToLegacyEnvelope() {
        McpExecutionResponse response = new McpExecutionResponse(
                true, "ALLOWED", "PRE_APPROVAL_WORKFLOW", "mcp.prometheus", "query_metrics",
                new McpExecutionRecordedResult(
                        "result-1", "evidence-1", "preview", "a".repeat(64), false,
                        "db:result-1", "b".repeat(64), 5L),
                Map.of("status", "SUCCEEDED"));

        Map<String, Object> view = mapper.view(response);

        assertEquals("result-1", view.get("resultId"));
        assertEquals("evidence-1", view.get("evidenceId"));
        assertEquals("mcp.prometheus", view.get("toolsetId"));
        assertEquals("PRE_APPROVAL_WORKFLOW", view.get("executionScope"));
    }

    @Test
    void shouldExposeStructuredProviderResultFromJsonRemoteOutput() {
        McpExecutionResponse response = new McpExecutionResponse(
                true, "ALLOWED", "APPROVED_LANDING", "mcp.service", "restart_service",
                new McpExecutionRecordedResult(
                        "result-2", "evidence-2", "preview", "c".repeat(64), false,
                        "db:result-2", "d".repeat(64), 7L),
                Map.of("rawPreview", "{\"status\":\"SUCCEEDED\",\"receiptId\":\"receipt-1\"}"));

        Map<String, Object> view = mapper.view(response);

        assertEquals("SUCCEEDED", ((Map<?, ?>) view.get("providerResult")).get("status"));
        assertEquals("receipt-1", ((Map<?, ?>) view.get("providerResult")).get("receiptId"));
    }

    @Test
    void shouldUnwrapStandardMcpTextContentIntoStructuredProviderResult() {
        McpExecutionResponse response = new McpExecutionResponse(
                true, "ALLOWED", "PRE_APPROVAL_WORKFLOW", "mcp.service", "restart_service_dry_run",
                new McpExecutionRecordedResult(
                        "result-3", "evidence-3", "preview", "e".repeat(64), false,
                        "db:result-3", "f".repeat(64), 4L),
                Map.of("rawPreview", "[{\"text\":\"{\\\"status\\\":\\\"PASSED\\\",\\\"service\\\":\\\"order-service\\\",\\\"expectedVersion\\\":1}\"}]"));

        Map<String, Object> view = mapper.view(response);
        Map<?, ?> provider = (Map<?, ?>) view.get("providerResult");

        assertEquals("PASSED", provider.get("status"));
        assertEquals("order-service", provider.get("service"));
        assertEquals(1, provider.get("expectedVersion"));
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder()
                .name("Prometheus")
                .projectId("project-1")
                .runId("run-1")
                .agentId("agent-1")
                .nodeId("node-1")
                .mcpId("prometheus")
                .toolId("mcp-tool-1")
                .operationId("operation-1")
                .transport("streamable-http")
                .url("http://localhost/mcp")
                .allowedTools(List.of("query_metrics", "restart_service"))
                .build();
    }
}
