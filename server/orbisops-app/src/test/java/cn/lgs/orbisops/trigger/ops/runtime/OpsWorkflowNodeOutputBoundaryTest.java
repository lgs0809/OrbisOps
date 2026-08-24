package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpsWorkflowNodeOutputBoundaryTest {
    private final BoundWorkflowExecutionPlan plan = new BoundWorkflowExecutionPlan(
            1, 1, "definition-hash", "agent-1", "session-1", "run-1", "project-1",
            "bundle-1", "context-hash", "inspect", List.of(new BoundWorkflowNode(
            "inspect", "LLM", "CHAT", "llm", "config-hash", List.of())),
            List.of(), List.of(), "plan-hash", Instant.now(), List.of());

    @Test
    void declaredJsonStructureAndBusinessRulesAcceptSupportedConclusion() {
        var contract = OpsWorkflowNodeOutputContract.compile(node(jsonContract()));
        assertDoesNotThrow(() -> contract.validate(plan, "inspect", Map.of("output",
                "{\"result\":\"HEALTHY\",\"sampleCount\":100,\"p95ms\":1000,\"errorRate\":0.01}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plain success", "{} {}", "{\"result\":\"HEALTHY\"}",
            "{\"result\":\"HEALTHY\",\"sampleCount\":99,\"p95ms\":999,\"errorRate\":0.01}",
            "{\"result\":\"HEALTHY\",\"sampleCount\":100,\"p95ms\":1001,\"errorRate\":0.01}",
            "{\"result\":\"HEALTHY\",\"sampleCount\":100,\"p95ms\":999,\"errorRate\":0.011}",
            "{\"result\":\"OK\",\"sampleCount\":100,\"p95ms\":1,\"errorRate\":0}"
    })
    void malformedOrUnsupportedHealthyOutputCannotPass(String output) {
        assertThrows(RuntimeException.class, () -> OpsWorkflowNodeOutputContract.compile(node(jsonContract()))
                .validate(plan, "inspect", Map.of("output", output)));
    }

    @Test
    void declaredTextWorksButEmptyAndTransportFailuresDoNot() {
        var contract = OpsWorkflowNodeOutputContract.compile(node(Map.of("format", "TEXT")));
        assertDoesNotThrow(() -> contract.validate(plan, "inspect", Map.of("output", "只读采样完成")));
        for (String output : List.of("", "Error: connection reset", "Exception: 503 - {}")) {
            assertThrows(RuntimeException.class, () -> contract.validate(plan, "inspect", Map.of("output", output)));
        }
        assertThrows(RuntimeException.class, () -> contract.validate(plan, "inspect", Map.of()));
        assertThrows(RuntimeException.class, () -> contract.validate(plan, "inspect", Map.of("output", "x".repeat(1024 * 1024))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"runId", "sessionId", "projectId", "nodeId", "agentId", "agentVersion", "definitionHash", "planHash"})
    void outputCannotClaimAnotherIdentityOrVersion(String key) {
        var contract = OpsWorkflowNodeOutputContract.compile(node(Map.of("format", "JSON", "schema", Map.of("type", "object"))));
        assertThrows(RuntimeException.class, () -> contract.validate(plan, "inspect", Map.of("output", Map.of(key, "wrong"))));
    }

    @Test
    void schemaCannotLoadRemoteReferencesAndRuleCannotExecuteCode() {
        assertThrows(RuntimeException.class, () -> OpsWorkflowNodeOutputContract.compile(node(Map.of(
                "format", "JSON", "schema", Map.of("$ref", "https://invalid.example/schema.json")))));
        assertThrows(RuntimeException.class, () -> OpsWorkflowNodeOutputContract.compile(node(Map.of(
                "format", "TEXT", "rule", "T(java.lang.Runtime).getRuntime()"))));
        assertThrows(RuntimeException.class, () -> OpsWorkflowNodeOutputContract.compile(node(Map.of(
                "format", "JSON"))));
    }

    @Test
    void revokedResourceGrantBlocksNodeCompletionAndReplay() {
        var fixture = boundary();
        fixture.boundary.validate(request(), fixture.node, Map.of("output", "valid"));
        doThrow(new SecurityException("PROJECT_MCP_NOT_AUTHORIZED")).when(fixture.validator).compile(any());
        assertThrows(SecurityException.class, () -> fixture.boundary.validate(request(), fixture.node, Map.of("output", "valid")));
    }

    @Test
    void mutableNodeOrDefinitionCannotEscapeFrozenPlan() {
        var first = boundary();
        first.node.setInstruction("different task");
        assertThrows(SecurityException.class, () -> first.boundary.validate(request(), first.node, Map.of("output", "valid")));
        var second = boundary();
        second.definition.setVersion(2);
        assertThrows(SecurityException.class, () -> second.boundary.validate(request(), second.node, Map.of("output", "valid")));
        var third = boundary();
        var changedRequest = request(); changedRequest.setProjectId("project-2");
        assertThrows(SecurityException.class, () -> third.boundary.validate(changedRequest, third.node, Map.of("output", "valid")));
    }

    private Fixture boundary() {
        var validator = mock(OpsAgentDefinitionValidator.class);
        var compiled = mock(CompiledAgentDefinitionVersion.class);
        when(compiled.agentId()).thenReturn("agent-1"); when(compiled.definitionVersion()).thenReturn(1);
        when(compiled.definitionHash()).thenReturn("definition-hash"); when(validator.compile(any())).thenReturn(compiled);
        var node = node(Map.of("format", "TEXT"));
        var definition = OpsAgentDefinition.builder().agentId("agent-1").version(1).nodes(List.of(node)).build();
        return new Fixture(new OpsWorkflowNodeOutputBoundary(plan, definition, validator,
                mock(WorkflowApprovalApplicationService.class)), node, definition, validator);
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder().runId("run-1").sessionId("session-1").projectId("project-1")
                .metadata(new LinkedHashMap<>(Map.of("contextBundleId", "bundle-1", "contextBundleHash", "context-hash"))).build();
    }

    private OpsWorkflowNode node(Map<String, Object> contract) {
        return OpsWorkflowNode.builder().nodeId("inspect").type("CHAT")
                .config(new LinkedHashMap<>(Map.of("outputContract", contract))).build();
    }

    private Map<String, Object> jsonContract() {
        return Map.of("format", "JSON", "schema", Map.of(
                "type", "object", "required", List.of("result", "sampleCount", "p95ms", "errorRate"),
                "properties", Map.of("result", Map.of("enum", List.of("HEALTHY", "UNHEALTHY", "INSUFFICIENT_DATA")),
                        "sampleCount", Map.of("type", "integer", "minimum", 0), "p95ms", Map.of("type", "number", "minimum", 0),
                        "errorRate", Map.of("type", "number", "minimum", 0, "maximum", 1))),
                "rule", "nodeOutput.result != 'HEALTHY' || nodeOutput.sampleCount >= 100 && nodeOutput.p95ms <= 1000 && nodeOutput.errorRate <= 0.01");
    }

    private record Fixture(OpsWorkflowNodeOutputBoundary boundary, OpsWorkflowNode node,
                           OpsAgentDefinition definition, OpsAgentDefinitionValidator validator) {}
}
