package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.mcpexecution.*;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowBoundToolInvocation;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.mcpexecution.model.*;
import cn.lgs.orbisops.domain.runtime.workflow.model.*;
import cn.lgs.orbisops.domain.toolexecution.model.*;
import cn.lgs.orbisops.domain.toolset.model.*;
import cn.lgs.orbisops.trigger.application.mcpexecution.OpsMcpExecutionMapper;
import cn.lgs.orbisops.trigger.application.toolexecution.OpsToolExecutionMapper;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import cn.lgs.orbisops.trigger.application.toolexecution.dispatch.OpsMcpToolExecutionDispatchHandler;
import cn.lgs.orbisops.trigger.ops.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real Workflow mapping, MCP dispatch, and current MCP policy together. */
class WorkflowBoundMcpReadOnlyActivationTest {
    @Test
    void boundReviewedExtensionUsesCurrentRunActivationThenActualDispatch() {
        Fixture f = new Fixture(schema("ACTIVE", true), List.of("observe"));
        Object result = f.dispatch(true, false);
        assertTrue(f.activated.get());
        assertEquals(1, f.disclosures.size());
        assertEquals(1, f.businessCalls.size());
        assertEquals("run-1", f.disclosures.get(0).config().runId());
        assertFalse(f.disclosures.get(0).trustedLandingRuntime());
        assertTrue(result instanceof Map<?, ?>);
        assertEquals("MCP", ((Map<?, ?>) result).get("providerType"));
        assertEquals("native-result", ((Map<?, ?>) result).get("providerResultId"));
        assertEquals(Map.of("service", "test-only"), f.arguments);
    }

    @Test
    void boundReadonlyIntentCannotInvokeAnAuthoritativelyMutatingTool() {
        Fixture f = new Fixture(schema("ACTIVE", false), List.of("observe"));
        SecurityException denied = assertThrows(SecurityException.class, () -> f.dispatch(true, false));
        assertTrue(denied.getMessage().contains("READ_ONLY_REQUIRED"));
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
        assertEquals("BLOCKED", f.records.get(0).status());
        assertEquals(0, f.inspections, "Current readonly restriction must reject writes before remote disclosure");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "STALE", "DISABLED"})
    void currentPolicyCannotBeBorrowedFromAnOlderReviewedWorkflow(String status) {
        Fixture f = new Fixture(schema(status, true), List.of("observe"));
        assertThrows(SecurityException.class, () -> f.dispatch(true, false));
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
        assertTrue(f.records.stream().allMatch(r -> r.status().equals("BLOCKED")));
    }

    @Test
    void projectAllowListRefusalDoesNotEvenInspectTheRemoteContract() {
        Fixture f = new Fixture(schema("ACTIVE", true), List.of("another-tool"));
        SecurityException denied = assertThrows(SecurityException.class, () -> f.dispatch(true, false));
        assertTrue(denied.getMessage().contains("MCP_TOOL_NOT_AUTHORIZED"));
        assertEquals(0, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    @Test
    void proposalDoesNotAcquireActivationOrLandingAuthority() {
        Fixture f = new Fixture(schema("ACTIVE", false), List.of("observe"));
        assertThrows(SecurityException.class, () -> f.dispatch(false, true));
        assertFalse(f.activated.get());
        assertEquals(0, f.inspections);
        assertTrue(f.businessCalls.isEmpty());
        assertFalse(f.mapped.trustedLandingRuntime());
        assertEquals(Map.of(), f.request.landingContext());
    }

    @Test
    void freshDisclosureCannotActivateUsingAnEarlierActiveSchema() {
        Fixture f = new Fixture(schema("ACTIVE", true), List.of("observe"));
        f.disclosedAttributes = schema("PENDING_REVIEW", true);
        SecurityException denied = assertThrows(SecurityException.class, () -> f.dispatch(true, false));
        assertTrue(denied.getMessage().contains("MCP_POLICY_PENDING_REVIEW"));
        assertEquals(1, f.inspections);
        assertFalse(f.activated.get());
        assertTrue(f.businessCalls.isEmpty());
    }

    static Map<String, Object> schema(String status, boolean readOnly) {
        return Map.of("policyStatus", status, "reviewStatus", "HUMAN_REVIEWED",
                "effectType", readOnly ? "READ_EXTERNAL_STATE" : "MUTATE_TARGET_RESOURCE",
                "effectScope", readOnly ? "READ_ONLY" : "PRODUCTION",
                "mutability", readOnly ? "READ_ONLY" : "PROD_MUTATING",
                "readOnly", readOnly, "riskLevel", readOnly ? "LOW" : "HIGH");
    }

    static final class Fixture {
        final AtomicBoolean activated = new AtomicBoolean();
        final List<McpExecutionRequest> disclosures = new ArrayList<>();
        final List<McpExecutionConfig> businessCalls = new ArrayList<>();
        final List<McpExecutionRecordPort.McpExecutionRecordCommand> records = new ArrayList<>();
        final McpExecutionApplicationService mcp;
        final OpsMcpServerConfig config;
        Map<String, Object> currentAttributes;
        Map<String, Object> disclosedAttributes;
        final OpsProjectMcpRuntimeConfigService configs = mock(OpsProjectMcpRuntimeConfigService.class);
        int inspections;
        boolean requiresActivation = true;
        boolean activationFails;
        Map<String, Object> arguments;
        ToolExecutionRequest request;
        McpExecutionRequest mapped;
        McpExecutionRequest beforeActivation;
        Map<String, Object> afterActivationAttributes;

        Fixture(Map<String, Object> attributes, List<String> allowed) {
            currentAttributes = attributes;
            disclosedAttributes = attributes;
            McpExecutionRuntimePort runtime = mock(McpExecutionRuntimePort.class);
            when(runtime.schema(any(), eq("observe"))).thenAnswer(i -> {
                mapped = i.getArgument(0);
                if (beforeActivation == null) beforeActivation = mapped;
                return new McpRuntimeToolSchema(currentAttributes, requiresActivation);
            });
            when(runtime.hydrate(any(), eq("observe"), anyMap())).thenAnswer(i -> {
                currentAttributes = disclosedAttributes;
                return new McpRuntimeToolSchema(currentAttributes, requiresActivation);
            });
            when(runtime.activated(any(), eq("observe"))).thenAnswer(i -> activated.get());
            when(runtime.activate(any(), eq("observe"), anyString(), anyMap())).thenAnswer(i -> {
                McpExecutionRequest r = i.getArgument(0);
                assertEquals("project-1", r.config().projectId());
                assertEquals("run-1", r.config().runId());
                assertEquals("fixture", r.config().mcpId());
                assertFalse(r.trustedLandingRuntime());
                if (!activationFails) {
                    activated.set(true);
                    if (afterActivationAttributes != null) currentAttributes = afterActivationAttributes;
                }
                disclosures.add(r);
                String status = activationFails ? "FAILED" : "SUCCEEDED";
                return new McpRuntimeActivation(status, Map.of("status", status));
            });
            McpExecutionRemotePort remote = new McpExecutionRemotePort() {
                public Map<String, Object> inspect(McpExecutionConfig c, String tool) {
                    inspections++;
                    assertEquals("observe", tool);
                    return Map.of("inputSchema", Map.of("type", "object"));
                }
                public String call(McpExecutionConfig c, String input, String stage) {
                    if (requiresActivation) assertTrue(activated.get());
                    assertEquals("PREPARE", stage);
                    assertFalse(c.landingApproved());
                    assertFalse(c.headers().containsKey("X-Ops-Execution-Key"));
                    businessCalls.add(c);
                    return "{\"status\":\"AVAILABLE\"}";
                }
            };
            mcp = new McpExecutionApplicationService(runtime, remote,
                    (r, t) -> t.requiresChangePackage()
                            ? McpExecutionDecision.blocked("MCP_TOOL_REQUIRES_CHANGE_PACKAGE", "approval required")
                            : McpExecutionDecision.allowed(Map.of()),
                    c -> {
                        records.add(c);
                        return new McpExecutionRecordedResult("native-result", "native-evidence", "preview",
                                "a".repeat(64), false, "db:native-result", "b".repeat(64), c.durationMs());
                    }, e -> {}, System::nanoTime);
            config = OpsMcpServerConfig.builder().projectId("project-1")
                    .mcpId("fixture").toolId("fixture-tool").runId("run-1").allowedTools(allowed)
                    .transport("streamable-http").url("http://127.0.0.1/never-network").build();
            when(configs.resolve("project-1", "fixture")).thenReturn(Optional.of(config));
        }

        Object dispatch(boolean readOnly, boolean proposal) {
            return dispatch(readOnly, proposal, false);
        }

        Object dispatchCompatibility(boolean readOnly) {
            return dispatch(readOnly, false, true);
        }

        private Object dispatch(boolean readOnly, boolean proposal, boolean compatibility) {
            // Capture the real adapter boundary without changing production application collaborators.
            ToolExecutionApplicationService unified = mock(ToolExecutionApplicationService.class);
            ToolExecutionResponse response = mock(ToolExecutionResponse.class);
            when(response.recorded()).thenReturn(new ToolExecutionRecordedResult("mapped", "evidence", "preview",
                    "a".repeat(64), false, "db:mapped", "b".repeat(64), 0));
            when(response.payload()).thenReturn(Map.of());
            when(response.allowed()).thenReturn(true);
            when(response.decision()).thenReturn("ALLOWED");
            when(response.target()).thenReturn(target());
            when(response.scope()).thenReturn(ToolExecutionScope.PRE_APPROVAL_WORKFLOW);
            when(unified.execute(any(ToolExecutionRequest.class))).thenReturn(response);
            BoundWorkflowResourceSnapshot resource = new BoundWorkflowResourceSnapshot(
                    BoundWorkflowResourceKind.TOOL, "fixture", 22, "reviewed-definition",
                    "TOOLSET_SNAPSHOT", true, true);
            if (compatibility) {
                new OpsToolExecutionService(unified, new OpsToolExecutionMapper(), mcp, new OpsMcpExecutionMapper())
                        .executeMcp(config, "{\"toolName\":\"observe\",\"arguments\":{\"service\":\"test-only\"}}",
                                "ops-agent", readOnly);
            } else {
                new OpsWorkflowBoundToolExecutionAdapter(unified).execute(new WorkflowBoundToolInvocation(
                        "project-1", "actor-1", "session-1", "run-1", "observe-node", 1, 0,
                        "run-1:observe-node:1:0", "mcp.fixture", "observe", resource,
                        Map.of("service", "test-only"), readOnly, proposal));
            }
            var capture = ArgumentCaptor.forClass(ToolExecutionRequest.class);
            verify(unified).execute(capture.capture());
            request = capture.getValue();
            assertEquals(ToolExecutionScope.PRE_APPROVAL_WORKFLOW, request.scope());
            assertEquals(Map.of(), request.landingContext());
            arguments = request.arguments();
            // MCP dispatcher and application/domain policy are the actual production implementations.
            OpsMcpExecutionMapper mapper = new OpsMcpExecutionMapper();
            var handler = new OpsMcpToolExecutionDispatchHandler(mcp, mapper, configs);
            return handler.dispatch(target(), request);
        }

        private ToolExecutionTarget target() {
            var provider = new ToolProviderDescriptor(ToolProviderType.MCP, "fixture", "MCP", "",
                    "fixture", "observe", "{}", "{}");
            return new ToolExecutionTarget("mcp.fixture", "observe", "MCP", "LOW",
                    true, false, false, false, false, provider,
                    ToolSemantics.readOnlyTool(), ToolSchema.inputOnly("{\"type\":\"object\"}"));
        }
    }
}
