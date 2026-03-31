package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionDecision;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRecordedResult;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpExecutionApplicationServiceTest {

    @Test
    void productionContractDisclosureDoesNotActivateOrInvokeAndExecutionStillRequiresApproval() {
        var schema = new java.util.LinkedHashMap<String, Object>(activeSchema());
        schema.put("effectType", "MUTATE_TARGET_RESOURCE");
        schema.put("effectScope", "PRODUCTION");
        schema.put("mutability", "PROD_MUTATING");
        schema.put("readOnly", false);
        RuntimeStub runtime = new RuntimeStub(schema, false, false);
        var remote = org.mockito.Mockito.mock(McpExecutionRemotePort.class);
        org.mockito.Mockito.when(remote.inspect(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of("inputSchema", Map.of("required", List.of("expectedVersion"))));
        var service = service(runtime, remote,
                (request, target) -> target.requiresChangePackage()
                        ? McpExecutionDecision.blocked("MCP_TOOL_REQUIRES_CHANGE_PACKAGE", "approval required")
                        : McpExecutionDecision.allowed(Map.of()),
                command -> recorded(command.durationMs()), event -> {});
        var request = request(List.of("query_metrics"), List.of());
        var disclosure = service.activate(request);
        assertEquals("SCHEMA_ONLY", disclosure.payload().get("status"));
        assertEquals(false, disclosure.payload().get("activated"));
        assertEquals(false, disclosure.payload().get("executionAllowed"));
        assertEquals(0, runtime.activationCount);
        assertThrows(McpExecutionDeniedException.class, () -> service.execute(request));
        org.mockito.Mockito.verify(remote, org.mockito.Mockito.never()).call(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void staleContractCannotBeDisclosedOrActivated() {
        var schema = new java.util.LinkedHashMap<String, Object>(activeSchema());
        schema.put("policyStatus", "STALE");
        RuntimeStub runtime = new RuntimeStub(schema, false, false);
        var service = service(runtime, remote(new AtomicBoolean()),
                (request, target) -> McpExecutionDecision.allowed(Map.of()),
                command -> recorded(command.durationMs()), event -> {});
        var result = service.activate(request(List.of("query_metrics"), List.of()));
        assertFalse(result.allowed());
        assertEquals("MCP_POLICY_STALE", result.decision());
        assertEquals(0, runtime.activationCount);
    }

    @Test
    void discoveryAndActivationMustShareRuntimeRemoteAndEvidenceBoundaries() {
        RuntimeStub runtime = new RuntimeStub(activeSchema(), false, true);
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        List<McpExecutionRecordPort.McpExecutionRecordCommand> records = new ArrayList<>();
        McpExecutionApplicationService service = service(
                runtime,
                remote(remoteCalled),
                (request, target) -> McpExecutionDecision.allowed(Map.of()),
                command -> {
                    records.add(command);
                    return recorded(command.durationMs());
                },
                event -> { });

        McpExecutionResponse discovery = service.discover(request(List.of("query_metrics"), List.of()));
        McpExecutionResponse activation = service.activate(request(List.of("query_metrics"), List.of()));

        assertTrue(discovery.allowed());
        assertEquals("tool_catalog", discovery.toolName());
        assertEquals("MCP_DISCOVERY", records.get(0).source());
        assertTrue(activation.allowed());
        assertEquals("query_metrics", activation.toolName());
        assertEquals("MCP_TOOL_DISCLOSURE", records.get(1).source());
        assertTrue(remoteCalled.get());
    }

    @Test
    void successfulRemoteExecutionMustRecordEvidenceAndAllowedAudit() {
        RuntimeStub runtime = new RuntimeStub(activeSchema(), false, true);
        List<McpExecutionRecordPort.McpExecutionRecordCommand> records = new ArrayList<>();
        List<McpExecutionAuditPort.McpExecutionAuditEvent> audits = new ArrayList<>();
        McpExecutionApplicationService service = service(
                runtime,
                new McpExecutionRemotePort() {
                    @Override
                    public Map<String, Object> inspect(McpExecutionConfig config, String toolName) {
                        return Map.of("schema", Map.of());
                    }

                    @Override
                    public String call(McpExecutionConfig config, String rawInput, String stage) {
                        assertEquals("PREPARE", stage);
                        return "{\"status\":\"ok\"}";
                    }
                },
                (request, target) -> McpExecutionDecision.allowed(Map.of("allowed", true)),
                command -> {
                    records.add(command);
                    return recorded(command.durationMs());
                },
                audits::add);

        McpExecutionResponse response = service.execute(request(List.of("query_metrics"), List.of()));

        assertTrue(response.allowed());
        assertEquals("MCP_REMOTE_TOOL", records.get(0).source());
        assertTrue(records.get(0).verified());
        assertEquals("allowed", audits.get(0).action());
        assertEquals("PRE_APPROVAL_WORKFLOW", response.executionScope());
    }

    @Test
    void unauthorizedToolMustRecordRuntimeBlockAndUnverifiedEvidence() {
        RuntimeStub runtime = new RuntimeStub(activeSchema(), false, true);
        AtomicBoolean remoteCalled = new AtomicBoolean(false);
        List<McpExecutionRecordPort.McpExecutionRecordCommand> records = new ArrayList<>();
        McpExecutionApplicationService service = service(
                runtime,
                remote(remoteCalled),
                (request, target) -> McpExecutionDecision.allowed(Map.of()),
                command -> {
                    records.add(command);
                    return recorded(command.durationMs());
                },
                event -> { });

        McpExecutionDeniedException denied = assertThrows(McpExecutionDeniedException.class, () ->
                service.execute(request(List.of("query_metrics"), List.of("restart_service"),
                        Map.of("toolName", "restart_service"))));

        assertEquals("MCP_TOOL_NOT_AUTHORIZED", denied.response().decision());
        assertFalse(remoteCalled.get());
        assertEquals(1, runtime.calls.size());
        assertEquals("BLOCKED", runtime.calls.get(0).status());
        assertEquals("TOOL_BLOCKED", records.get(0).source());
        assertFalse(records.get(0).verified());
    }

    @Test
    void activationRequirementAndRouterDenialMustFailClosed() {
        RuntimeStub activationRuntime = new RuntimeStub(activeSchema(), true, false);
        List<McpExecutionRecordPort.McpExecutionRecordCommand> activationRecords = new ArrayList<>();
        McpExecutionApplicationService activationService = service(
                activationRuntime, remote(new AtomicBoolean()),
                (request, target) -> McpExecutionDecision.allowed(Map.of()),
                command -> {
                    activationRecords.add(command);
                    return recorded(command.durationMs());
                }, event -> { });

        McpExecutionDeniedException notEnabled = assertThrows(McpExecutionDeniedException.class, () ->
                activationService.execute(request(List.of("query_metrics"), List.of())));
        assertEquals("MCP_TOOL_NOT_ENABLED", notEnabled.response().decision());
        assertFalse(activationRecords.get(0).verified());

        RuntimeStub routeRuntime = new RuntimeStub(activeSchema(), false, true);
        List<McpExecutionAuditPort.McpExecutionAuditEvent> audits = new ArrayList<>();
        McpExecutionApplicationService routeService = service(
                routeRuntime, remote(new AtomicBoolean()),
                (request, target) -> McpExecutionDecision.blocked(
                        "TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE", "package required"),
                command -> recorded(command.durationMs()), audits::add);

        McpExecutionDeniedException routeDenied = assertThrows(McpExecutionDeniedException.class, () ->
                routeService.execute(request(List.of("query_metrics"), List.of())));
        assertEquals("TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE",
                routeDenied.response().decision());
        assertEquals("blocked", audits.get(0).action());
        assertEquals(1, routeRuntime.calls.size());
    }

    @Test
    void independentVerificationChecksReviewedReadOnlyPolicyBeforeRemoteDispatch() {
        Map<String, Object> write = new java.util.LinkedHashMap<>(activeSchema());
        write.put("readOnly", false);
        write.put("effectType", "MUTATE_TEST_RESOURCE");
        write.put("prepareAllowed", true);
        write.put("mutability", "TEST_MUTATING");
        AtomicBoolean called = new AtomicBoolean();
        var service = service(new RuntimeStub(write, true, false), remote(called),
                (request, target) -> McpExecutionDecision.allowed(Map.of()),
                command -> recorded(command.durationMs()), event -> {});
        var denied = assertThrows(McpExecutionDeniedException.class, () -> service.execute(request(
                List.of("query_metrics"), List.of(), Map.of("toolName", "query_metrics", "requireReadOnly", true))));
        assertTrue(denied.getMessage().contains("READ_ONLY_REQUIRED"));
        assertFalse(called.get());
    }

    @Test
    void reviewedReadOnlyToolRemainsUsableForIndependentVerification() {
        AtomicBoolean called = new AtomicBoolean();
        var service = service(new RuntimeStub(activeSchema(), false, true), remote(called),
                (request, target) -> McpExecutionDecision.allowed(Map.of()),
                command -> recorded(command.durationMs()), event -> {});
        assertTrue(service.execute(request(List.of("query_metrics"), List.of(),
                Map.of("toolName", "query_metrics", "requireReadOnly", true))).allowed());
        assertTrue(called.get());
    }

    private McpExecutionApplicationService service(
            McpExecutionRuntimePort runtime,
            McpExecutionRemotePort remote,
            McpExecutionRouterPort router,
            McpExecutionRecordPort records,
            McpExecutionAuditPort audits) {
        AtomicLong nanos = new AtomicLong();
        return new McpExecutionApplicationService(
                runtime,
                remote,
                router,
                records,
                audits,
                () -> nanos.addAndGet(1_000_000L));
    }

    private McpExecutionRemotePort remote(AtomicBoolean called) {
        return new McpExecutionRemotePort() {
            @Override
            public Map<String, Object> inspect(McpExecutionConfig config, String toolName) {
                called.set(true);
                return Map.of("schema", Map.of());
            }

            @Override
            public String call(McpExecutionConfig config, String rawInput, String stage) {
                called.set(true);
                return "ok";
            }
        };
    }

    private McpExecutionRequest request(List<String> allowed, List<String> blocked) {
        return request(allowed, blocked, Map.of("toolName", "query_metrics"));
    }

    private McpExecutionRequest request(
            List<String> allowed,
            List<String> blocked,
            Map<String, Object> input) {
        McpExecutionConfig config = new McpExecutionConfig(
                "Prometheus", "metrics", "project-1", "run-1", "agent-1", "node-1",
                "prometheus", "mcp-tool-1", "operation-1", "streamable-http", "",
                "http://localhost/mcp", 30, List.of(), Map.of(), Map.of(), Map.of(),
                allowed, List.of(), blocked, false, "", "", 0);
        return new McpExecutionRequest(config, "alice", "{}", input, false);
    }

    private Map<String, Object> activeSchema() {
        return Map.of(
                "policyStatus", "ACTIVE",
                "reviewStatus", "HUMAN_REVIEWED",
                "effectType", "READ_EXTERNAL_STATE",
                "effectScope", "READ_ONLY",
                "mutability", "READ_ONLY",
                "readOnly", true,
                "riskLevel", "LOW");
    }

    private McpExecutionRecordedResult recorded(long durationMs) {
        return new McpExecutionRecordedResult(
                "result-1", "evidence-1", "preview", "a".repeat(64), false,
                "db:result-1", "b".repeat(64), durationMs);
    }

    private static final class RuntimeStub implements McpExecutionRuntimePort {
        private final Map<String, Object> schema;
        private final boolean requiresActivation;
        private final boolean activated;
        private int activationCount;
        private final List<McpRuntimeCallEvent> calls = new ArrayList<>();

        private RuntimeStub(
                Map<String, Object> schema,
                boolean requiresActivation,
                boolean activated) {
            this.schema = schema;
            this.requiresActivation = requiresActivation;
            this.activated = activated;
        }

        @Override
        public McpRuntimeCatalog catalog(McpExecutionConfig config) {
            return new McpRuntimeCatalog(List.of());
        }

        @Override
        public McpRuntimeActivation activate(
                McpExecutionRequest request,
                String toolName,
                String reason,
                Map<String, Object> remoteDefinition) {
            activationCount++;
            return new McpRuntimeActivation("SUCCEEDED", Map.of("status", "SUCCEEDED"));
        }

        @Override
        public McpRuntimeToolSchema schema(McpExecutionRequest request, String toolName) {
            return new McpRuntimeToolSchema(schema, requiresActivation);
        }

        @Override
        public McpRuntimeToolSchema hydrate(
                McpExecutionRequest request,
                String toolName,
                Map<String, Object> remoteDefinition) {
            return new McpRuntimeToolSchema(schema, requiresActivation);
        }

        @Override
        public boolean activated(McpExecutionConfig config, String toolName) {
            return activated;
        }

        @Override
        public void recordCall(McpRuntimeCallEvent event) {
            calls.add(event);
        }
    }
}
