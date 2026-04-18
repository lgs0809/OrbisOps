package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackageToolCollaboratorsTest {

    @Test
    void recoveryObjectsSurviveModelInputAndStringsStillCannotAuthorizeProductionWrites() {
        var operation = JSON.parseObject("""
                {"operationId":"restore", "mcpId":"service-control", "toolName":"apply",
                 "arguments":{"expectedVersion":"v1"}, "policyBound":true, "writesTargetResource":true,
                 "preconditions":{"version":"v1"},
                 "postCheck":{"toolsetId":"mcp.service-control", "toolName":"read", "arguments":{},
                              "expectedValues":{"resourceKey":"service://orders/prod","version":"v2"}},
                 "rollbackPlan":{"summary":"停止后续步骤，记录回执并重新审批恢复方案"},
                 "rollbackPrecondition":{"summary":"资源仍属于本操作且版本为 v2"},
                 "manualFallback":{"summary":"自动恢复条件不满足时停止并交接给服务负责人"}}
                """);
        var execution = mock(OpsToolExecutionService.class);
        when(execution.execute(any(), eq("u"))).thenReturn(Map.of("packageId", "prepared"));
        var gateway = new OpsChangePackageToolExecutionGateway(execution);
        var context = new OpsChangePackageToolExecutionGateway.Context("p", "u", "r", "s", Map.of(), true);
        for (var field : List.of("rollbackPrecondition", "manualFallback")) {
            var malformed = new java.util.LinkedHashMap<String, Object>(operation);
            malformed.put(field, "文字不能替代对象");
            assertThrows(IllegalArgumentException.class,
                    () -> gateway.execute(Map.of("mcpSteps", List.of(malformed)), context));
        }
        org.mockito.Mockito.verifyNoInteractions(execution);
        var input = JSON.parseObject(JSON.toJSONString(Map.of("actions", List.of(operation))), OpsChangePackageToolInput.class);
        var request = new OpsChangePackageToolRequestFactory().create(
                new OpsChangePackageToolRequestFactory.Context("p", "r", "b", "hash"), input,
                List.of(new OpsChangePackageRunEvidenceCollector.Evidence("e", "MCP", "r:read", "read", "2026-09-16", "sha256:hash", Map.of())));
        assertEquals(input.getActions(), request.get("mcpSteps"));
        assertEquals("prepared", gateway.execute(request, context).get("packageId"));
    }

    @Test
    void modelCannotSaveReviewedProductionActionWithMissingRecoverySafety() {
        var execution = mock(OpsToolExecutionService.class);
        var gateway = new OpsChangePackageToolExecutionGateway(execution);
        var operation = Map.<String, Object>of("policyBound", true, "writesTargetResource", true,
                "postCheck", Map.of("expectedValues", Map.of("version", "v2")));
        var context = new OpsChangePackageToolExecutionGateway.Context("p", "u", "r", "s", Map.of(), true);
        var failure = assertThrows(IllegalArgumentException.class,
                () -> gateway.execute(Map.of("mcpSteps", List.of(operation)), context));
        assertTrue(failure.getMessage().contains("ROLLBACK_PLAN_REQUIRED"));
        org.mockito.Mockito.verifyNoInteractions(execution);
    }

    @Test
    void evidenceCollectorFiltersEventsAndFreezesReferences() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC);
        OpsChangePackageRunEvidenceCollector collector =
                new OpsChangePackageRunEvidenceCollector(clock);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        events.add(toolEvent("prometheus.query", "mcp", "{\"up\":0}", ""));
        events.add(toolEvent("PrepareChangePackage", "tool", "self", "2026-07-26"));
        events.add(toolEvent("Skill", "tool", "skill output", "2026-07-26"));
        events.add(OpsRuntimeEvent.builder()
                .eventType("RAG_RETRIEVE")
                .status("SUCCEEDED")
                .summary("RAG 命中")
                .timestamp("2026-07-26T10:01:00")
                .payload(Map.of("documentId", "doc-1"))
                .build());
        events.add(OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("FAILED")
                .payload(Map.of("toolName", "failed", "output", "ignored"))
                .build());

        List<OpsChangePackageRunEvidenceCollector.Evidence> result =
                collector.collect("run-1", events);

        assertEquals(2, result.size());
        assertEquals("MCP", result.get(0).sourceType());
        assertEquals("2026-07-26T10:00", result.get(0).observedAt());
        assertTrue(result.get(0).contentHash().startsWith("sha256:"));
        assertEquals("RAG", result.get(1).sourceType());
        assertThrows(UnsupportedOperationException.class,
                () -> result.get(0).metadata().put("changed", true));
    }

    @Test
    void evidenceCollectorPromotesExecutedReadonlyRabbitMqMcpResultToAuthoritativeEvidence() {
        OpsRuntimeEvent rabbit = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .status("SUCCEEDED")
                .summary("RabbitMQ health completed")
                .payload(Map.of(
                        "toolName", "project_mcp_demo_rabbitmq_readonly_mcp",
                        "toolKind", "mcp",
                        "output", "{\"status\":\"ok\"}",
                        "remoteCallExecuted", true,
                        "allowed", true,
                        "resultId", "tool-result-rabbit",
                        "mcpId", "demo-rabbitmq-readonly-mcp",
                        "remoteToolName", "rabbitmq_health"))
                .build();

        List<OpsChangePackageRunEvidenceCollector.Evidence> result =
                new OpsChangePackageRunEvidenceCollector().collect("run-rabbit", List.of(rabbit));

        assertEquals(1, result.size());
        assertEquals("RABBITMQ", result.get(0).sourceType());
        assertEquals("tool-result-rabbit", result.get(0).sourceId());
        assertEquals(true, result.get(0).metadata().get("verified"));
        assertEquals("rabbitmq_health", result.get(0).metadata().get("remoteToolName"));
    }

    @Test
    void evidenceCollectorAcceptsOnlyVerifiedHashBoundDatasourceResults() {
        Map<String, Object> summary = Map.of("errorCount", 3, "uri", "/api/demo-project/join");
        String outputHash = CanonicalObjectHasher.sha256(summary);
        OpsRuntimeEvent valid = sourceQueryEvent("PROMETHEUS", true, "source-result-1", outputHash, summary);
        OpsRuntimeEvent unverified = sourceQueryEvent("PROMETHEUS", false, "source-result-2", outputHash, summary);
        OpsRuntimeEvent unknownSource = sourceQueryEvent("MODEL_TEXT", true, "source-result-3", outputHash, summary);
        OpsRuntimeEvent tampered = sourceQueryEvent("ELASTICSEARCH", true, "source-result-4", outputHash,
                Map.of("errorCount", 999));

        List<OpsChangePackageRunEvidenceCollector.Evidence> result =
                new OpsChangePackageRunEvidenceCollector().collect(
                        "run-verified", List.of(valid, unverified, unknownSource, tampered));

        assertEquals(1, result.size());
        assertEquals("PROMETHEUS", result.get(0).sourceType());
        assertEquals("source-result-1", result.get(0).sourceId());
        assertEquals("sha256:" + outputHash, result.get(0).contentHash());
        assertEquals(outputHash, result.get(0).metadata().get("outputHash"));
        assertEquals(true, result.get(0).metadata().get("verified"));
    }

    @Test
    void evidenceCollectorCapsSuccessfulEvidenceAtTwenty() {
        List<OpsRuntimeEvent> events = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            events.add(toolEvent("tool-" + index, "tool", "output-" + index, "2026-07-26"));
        }

        List<OpsChangePackageRunEvidenceCollector.Evidence> result =
                new OpsChangePackageRunEvidenceCollector().collect("run-2", events);

        assertEquals(20, result.size());
        assertEquals("run-evidence-20", result.get(19).evidenceId());
    }

    @Test
    void requestFactoryPreservesDefaultsAndEvidenceContract() {
        OpsChangePackageToolInput input = new OpsChangePackageToolInput();
        input.setTitle("处理异常实例");
        input.setSummary("实例不可用");
        input.setActions(null);
        input.setPreflightResult(null);
        input.setDryRunResult(null);
        input.setApprovalBoundary(null);
        input.setPreferredPlan(null);
        input.setAdjustmentPolicy(null);
        OpsChangePackageRunEvidenceCollector.Evidence evidence =
                new OpsChangePackageRunEvidenceCollector.Evidence(
                        "e-1", "MCP", "run-1:0:query", "summary",
                        "2026-07-26", "sha256:hash", Map.of());

        Map<String, Object> request = new OpsChangePackageToolRequestFactory().create(
                new OpsChangePackageToolRequestFactory.Context(
                        "project-1", "run-1", "bundle-1", "bundle-hash"),
                input,
                List.of(evidence));

        assertEquals("处理异常实例", request.get("objective"));
        assertFalse(request.containsKey("preparationGraphId"));
        assertEquals(List.of(), request.get("mcpSteps"));
        assertEquals(Map.of("steps", List.of()), request.get("preferredPlan"));
        assertEquals(Map.of("status", "PARTIAL"), request.get("preflightResult"));
        assertEquals(Map.of("status", "NOT_SUPPORTED", "executed", false), request.get("dryRunResult"));
        assertEquals(1, ((List<?>) request.get("evidence")).size());
    }

    @Test
    void requestFactoryRejectsMissingEvidenceBeforeExecution() {
        OpsChangePackageToolInput input = new OpsChangePackageToolInput();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new OpsChangePackageToolRequestFactory().create(
                        new OpsChangePackageToolRequestFactory.Context(
                                "project-1", "run-1", "bundle-1", "hash"),
                        input,
                        List.of()));

        assertTrue(error.getMessage().contains("证据"));
    }

    @Test
    void executionGatewayBuildsUnifiedRouterEnvelopeAndPreservesEmptyRuntimeFields() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        when(service.execute(any(), eq("alice"))).thenReturn(Map.of("packageId", "cp-1"));
        OpsChangePackageToolExecutionGateway gateway =
                new OpsChangePackageToolExecutionGateway(service);

        Map<String, Object> result = gateway.execute(
                Map.of("objective", "repair"),
                new OpsChangePackageToolExecutionGateway.Context(
                        "project-1",
                        "alice",
                        "run-1",
                        "",
                        Map.of(),
                        true));

        assertEquals("cp-1", result.get("packageId"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).execute(captor.capture(), eq("alice"));
        Map<String, Object> execution = captor.getValue();
        assertEquals("PRE_APPROVAL_WORKFLOW", execution.get("executionScope"));
        assertEquals("change_package", execution.get("toolsetId"));
        assertEquals("change_package_create", execution.get("toolName"));
        assertTrue(String.valueOf(execution.get("idempotencyKey")).startsWith("change-package:prepare:"));
        assertTrue(execution.containsKey("sessionId"));
        assertTrue(execution.containsKey("metadata"));
    }

    @Test
    void resultRendererKeepsToolProtocol() {
        String json = new OpsChangePackageToolResultRenderer().success(
                Map.of(
                        "packageId", "cp-1",
                        "status", "REVIEWING",
                        "version", 1,
                        "packageHash", "hash-1",
                        "riskLevel", "HIGH"),
                2,
                3);

        assertEquals("cp-1", JSON.parseObject(json).getString("packageId"));
        assertEquals(2, JSON.parseObject(json).getIntValue("evidenceCount"));
        assertEquals(3, JSON.parseObject(json).getIntValue("actionCount"));
        assertFalse(JSON.parseObject(json).getString("nextStep").isBlank());
    }

    private OpsRuntimeEvent sourceQueryEvent(String sourceType,
                                             boolean verified,
                                             String resultId,
                                             String outputHash,
                                             Map<String, Object> structuredSummary) {
        return OpsRuntimeEvent.builder()
                .eventType("SOURCE_QUERY_FINISHED")
                .nodeId("sub-agent-" + sourceType)
                .status("SUCCEEDED")
                .timestamp("2026-07-26T10:02:00")
                .payload(Map.of(
                        "sourceType", sourceType,
                        "source", "http://source.local",
                        "resultId", resultId,
                        "evidenceId", "evidence-" + resultId,
                        "outputHash", outputHash,
                        "fullOutputRef", "tool-result://" + resultId,
                        "verified", verified,
                        "observedAt", "2026-07-26T10:02:00",
                        "structuredSummary", structuredSummary))
                .build();
    }

    private OpsRuntimeEvent toolEvent(String toolName,
                                      String toolKind,
                                      String output,
                                      String timestamp) {
        return OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .nodeId("node-1")
                .status("SUCCEEDED")
                .summary("调用完成")
                .timestamp(timestamp)
                .payload(Map.of(
                        "toolName", toolName,
                        "toolKind", toolKind,
                        "owner", "node:node-1",
                        "output", output,
                        "durationMs", 10))
                .build();
    }
}
