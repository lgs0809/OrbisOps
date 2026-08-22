package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsAnalysisChangePackageCoordinatorTest {

    @Test
    void disabledEvaluationDoesNotAssembleResources() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(
                resourceAssembler, mock(OpsRuntimeLlmInvoker.class));
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .changePackageEnabled(false)
                .build();

        String decision = coordinator.evaluate(
                definition,
                OpsWorkflowNode.builder().nodeId("report").type("REPORT").build(),
                request(),
                response(true),
                new ArrayList<>(),
                null,
                "请修复问题",
                "REPORT");

        assertEquals("", decision);
        verifyNoInteractions(resourceAssembler);
    }

    @Test
    void resourceAssemblyFailureRemainsFailClosed() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        when(resourceAssembler.assembleNode(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("resource unavailable"));
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(
                resourceAssembler, mock(OpsRuntimeLlmInvoker.class));

        assertThrows(IllegalStateException.class, () -> coordinator.evaluate(
                enabledDefinition(),
                enabledNode(),
                request(),
                response(true),
                new ArrayList<>(),
                null,
                "请修复问题",
                "REPORT"));
    }

    @Test
    void unavailableChangePackageToolShortCircuitsBeforeModelCall() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        when(resourceAssembler.assembleNode(any(), any(), any(), any(), any()))
                .thenReturn(OpsRuntimeResourceBundle.builder()
                        .metadata(Map.of("changePackageToolEnabled", false))
                        .build());
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        OpsAnalysisResponseDTO response = response(true);
        List<OpsRuntimeEvent> events = new ArrayList<>();

        String decision = coordinator.evaluate(
                enabledDefinition(), enabledNode(), request(), response,
                events, null, "请修复问题", "REPORT");

        assertEquals("", decision);
        assertTrue(events.stream().anyMatch(event ->
                "CHANGE_PACKAGE_SKIPPED".equals(event.getEventType())
                        && "CHANGE_PACKAGE_TOOL_UNAVAILABLE".equals(event.getPayload().get("reason"))));
        assertTrue(response.getExecutionNotes().stream().anyMatch(note -> note.contains("PREPARE 工具当前不可用")));
        verify(llmInvoker, never()).call(any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void modelFailureFailsClosedWithoutOverwritingReport() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        when(resourceAssembler.assembleNode(any(), any(), any(), any(), any()))
                .thenReturn(enabledBundle());
        when(llmInvoker.call(any(), any(), any(), any(), any(), anyLong()))
                .thenThrow(new IllegalStateException("model unavailable"));
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        OpsAnalysisResponseDTO response = response(true);
        List<OpsRuntimeEvent> events = new ArrayList<>();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> coordinator.evaluate(
                enabledDefinition(), enabledNode(), request(), response,
                events, null, "请修复问题", "REPORT"));

        assertEquals("CHANGE_PACKAGE_GOVERNANCE_FAILED", error.getMessage());
        assertEquals("诊断报告", response.getMarkdownReport());
        assertTrue(response.getExecutionNotes().stream()
                .anyMatch(note -> note.contains("fail-closed")));
        assertTrue(events.stream().anyMatch(event ->
                "CHANGE_PACKAGE_EVALUATION_FAILED".equals(event.getEventType())));
    }

    @Test
    void transientTransportFailureWithoutToolExecutionRetriesOnce() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        when(resourceAssembler.assembleNode(any(), any(), any(), any(), any()))
                .thenReturn(enabledBundle());
        when(llmInvoker.call(any(), any(), any(), any(), any(), anyLong()))
                .thenThrow(new IllegalStateException("transport", new IOException("handshake")))
                .thenReturn("NO_CHANGE_PACKAGE: evidence insufficient");
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        OpsAnalysisResponseDTO response = response(true);
        List<OpsRuntimeEvent> events = new ArrayList<>();

        String decision = coordinator.evaluate(
                enabledDefinition(), enabledNode(), request(), response,
                events, null, "请修复问题", "REPORT");

        assertEquals("NO_CHANGE_PACKAGE: evidence insufficient", decision);
        assertTrue(events.stream().anyMatch(event ->
                "CHANGE_PACKAGE_MODEL_RETRY".equals(event.getEventType())));
        verify(llmInvoker, org.mockito.Mockito.times(2))
                .call(any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void successfulEvaluationUsesToolEventAsCreationAuthority() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        when(resourceAssembler.assembleNode(any(), any(), any(), any(), any()))
                .thenReturn(enabledBundle());
        when(llmInvoker.call(any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<OpsRuntimeEvent> runtimeEvents = invocation.getArgument(3, List.class);
                    runtimeEvents.add(OpsRuntimeEvent.builder()
                            .eventType("TOOL_CALL_FINISHED")
                            .nodeId("report")
                            .status("SUCCEEDED")
                            .payload(Map.of("toolName", "PrepareChangePackage"))
                            .build());
                    return "CHANGE_PACKAGE_CREATED: package-1";
                });
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        OpsAnalysisResponseDTO response = response(true);
        List<OpsRuntimeEvent> events = new ArrayList<>();

        String decision = coordinator.evaluate(
                enabledDefinition(), enabledNode(), request(), response,
                events, null, "请修复问题", "REPORT");

        assertEquals("CHANGE_PACKAGE_CREATED: package-1", decision);
        assertTrue(response.getExecutionNotes().stream()
                .anyMatch(note -> note.contains("已基于本轮证据创建 ChangePackage")));
        OpsRuntimeEvent evaluated = events.stream()
                .filter(event -> "CHANGE_PACKAGE_EVALUATED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertEquals(true, evaluated.getPayload().get("created"));
        OpsRuntimeEvent workflowOutcome = events.stream()
                .filter(event -> "WORKFLOW_OUTCOME".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertEquals(true, workflowOutcome.getPayload().get("requiresAction"));
        assertEquals("NOT_APPLICABLE", workflowOutcome.getPayload().get("verificationStatus"));
    }

    @Test
    void successfulReactOutcomePreventsDuplicateWorkflowOutcome() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        when(resourceAssembler.assembleNode(any(), any(), any(), any(), any()))
                .thenReturn(enabledBundle());
        when(llmInvoker.call(any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<OpsRuntimeEvent> runtimeEvents = invocation.getArgument(3, List.class);
                    runtimeEvents.add(OpsRuntimeEvent.builder()
                            .eventType("TOOL_CALL_FINISHED")
                            .nodeId("report")
                            .status("SUCCEEDED")
                            .payload(Map.of("toolName", "PrepareChangePackage"))
                            .build());
                    return "CHANGE_PACKAGE_CREATED: package-1";
                });
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        events.add(OpsRuntimeEvent.builder()
                .eventType("REACT_OUTCOME")
                .status("SUCCEEDED")
                .payload(Map.of(
                        "requiresAction", true,
                        "verificationStatus", "NOT_APPLICABLE",
                        "abstained", false,
                        "evidenceCompleteness", "PARTIAL"))
                .build());

        coordinator.evaluate(
                enabledDefinition(), enabledNode(), request(), response(true),
                events, null, "请修复问题", "REPORT");

        assertTrue(events.stream().noneMatch(event ->
                "WORKFLOW_OUTCOME".equals(event.getEventType())));
    }

    @Test
    void explicitAnalysisRequestCanEvaluateWithoutLegacyInvestigationPlan() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        when(resourceAssembler.assembleAgent(any(), any(), any(), any()))
                .thenReturn(enabledBundle());
        when(llmInvoker.call(any(), any(), any(), any(), any(), anyLong()))
                .thenReturn("NO_CHANGE_PACKAGE: evidence insufficient");
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setChangeRequested(true);
        OpsAgentChatRequest runtimeRequest = request();
        runtimeRequest.getMetadata().put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .markdownReport("ReAct 诊断报告")
                .build();

        String decision = coordinator.evaluate(
                enabledDefinition(),
                OpsWorkflowNode.builder().nodeId("end").type("END").build(),
                runtimeRequest,
                response,
                new ArrayList<>(),
                null,
                "请生成 ChangePackage",
                "END");

        assertEquals("NO_CHANGE_PACKAGE: evidence insufficient", decision);
        verify(llmInvoker).call(any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void endNodeUsesChangePackageCapableAgentNodeForFallbackEvaluation() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        OpsRuntimeResourceBundle resourceBundle = enabledBundleWithTools();
        Map<String, Object> authoritativeSummary = Map.of(
                "status", "SUCCEEDED",
                "preview", "order-service up=0 on 8091 and 8092");
        String authoritativeHash = cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(authoritativeSummary);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        events.add(OpsRuntimeEvent.builder()
                .eventType("SOURCE_QUERY_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of(
                        "sourceType", "PROMETHEUS",
                        "source", "LOCAL_PROMETHEUS",
                        "resultId", "result-1",
                        "evidenceId", "evidence-1",
                        "outputHash", authoritativeHash,
                        "fullOutputRef", "db:result-1",
                        "verified", true,
                        "structuredSummary", authoritativeSummary))
                .build());
        when(resourceAssembler.assembleNode(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OpsWorkflowNode resourceNode = invocation.getArgument(1, OpsWorkflowNode.class);
                    assertEquals("investigate", resourceNode.getNodeId());
                    return resourceBundle;
                });
        when(llmInvoker.call(any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    String userPrompt = invocation.getArgument(1, String.class);
                    OpsRuntimeResourceBundle prepareBundle = invocation.getArgument(2, OpsRuntimeResourceBundle.class);
                    assertTrue(userPrompt.contains("order-service up=0"));
                    assertTrue(userPrompt.contains("evidence-1"));
                    assertTrue(userPrompt.contains("\"operations\":[]"));
                    assertEquals(List.of("PrepareChangePackage"),
                            prepareBundle.getTools().stream()
                                    .map(tool -> tool.getToolDefinition().name())
                                    .toList());
                    assertTrue(prepareBundle.getSkillNames().isEmpty());
                    assertFalse(Boolean.TRUE.equals(prepareBundle.getRagEnabled()));
                    return "NO_CHANGE_PACKAGE: evidence insufficient";
                });
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        OpsWorkflowNode investigate = OpsWorkflowNode.builder()
                .nodeId("investigate")
                .type("AGENT")
                .changePackageEnabled(true)
                .build();
        OpsWorkflowNode end = OpsWorkflowNode.builder()
                .nodeId("end")
                .type("END")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .nodes(List.of(investigate, end))
                .build();
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setChangeRequested(true);
        OpsAgentChatRequest runtimeRequest = request();
        runtimeRequest.getMetadata().put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);

        String decision = coordinator.evaluate(
                definition,
                end,
                runtimeRequest,
                OpsAnalysisResponseDTO.builder().markdownReport("ReAct diagnosis").build(),
                events,
                null,
                "prepare a reviewable change",
                "END");

        assertEquals("NO_CHANGE_PACKAGE: evidence insufficient", decision);
        verify(resourceAssembler, org.mockito.Mockito.times(2))
                .assembleNode(any(), any(), any(), any(), any());
        verify(llmInvoker).call(any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void topLevelReactRequestCanEvaluateWithoutLegacyAnalysisState() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        OpsRuntimeResourceBundle resourceBundle = enabledBundleWithTools();
        when(resourceAssembler.assembleAgent(any(), any(), any(), any()))
                .thenReturn(resourceBundle);
        when(llmInvoker.call(any(), any(), any(), any(), any(), anyLong()))
                .thenReturn("NO_CHANGE_PACKAGE: evidence insufficient");
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        OpsAgentChatRequest runtimeRequest = request();
        runtimeRequest.setChangeRequested(true);
        Map<String, Object> authoritativeSummary = Map.of(
                "status", "SUCCEEDED",
                "preview", "order-service up=0");
        String authoritativeHash = cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(authoritativeSummary);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        events.add(OpsRuntimeEvent.builder()
                .eventType("SOURCE_QUERY_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of(
                        "sourceType", "PROMETHEUS",
                        "source", "LOCAL_PROMETHEUS",
                        "resultId", "result-top-level",
                        "evidenceId", "evidence-top-level",
                        "outputHash", authoritativeHash,
                        "fullOutputRef", "db:result-top-level",
                        "verified", true,
                        "structuredSummary", authoritativeSummary))
                .build());

        String decision = coordinator.evaluate(
                enabledDefinition(),
                OpsWorkflowNode.builder().nodeId("end").type("END").build(),
                runtimeRequest,
                null,
                events,
                null,
                "请生成 ChangePackage",
                "END");

        assertEquals("NO_CHANGE_PACKAGE: evidence insufficient", decision);
        verify(resourceAssembler, org.mockito.Mockito.times(2))
                .assembleAgent(any(), any(), any(), any());
        verify(llmInvoker).call(any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void existingSuccessfulProposalSkipsFallbackEvaluationAcrossNodes() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(resourceAssembler, llmInvoker);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        events.add(OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_FINISHED")
                .nodeId("investigate")
                .status("SUCCEEDED")
                .payload(Map.of("toolName", "PrepareChangePackage"))
                .build());

        String decision = coordinator.evaluate(
                enabledDefinition(), enabledNode(), request(), response(true),
                events, null, "请修复问题", "REPORT");

        assertEquals("", decision);
        verifyNoInteractions(resourceAssembler, llmInvoker);
    }

    @Test
    void proposalCreationRequiresSuccessfulPrepareToolAtMatchingNode() {
        OpsAnalysisChangePackageCoordinator coordinator = coordinator(null, null);
        List<OpsRuntimeEvent> events = List.of(
                OpsRuntimeEvent.builder()
                        .eventType("TOOL_CALL_FINISHED")
                        .nodeId("other")
                        .status("SUCCEEDED")
                        .payload(Map.of("toolName", "PrepareChangePackage"))
                        .build(),
                OpsRuntimeEvent.builder()
                        .eventType("TOOL_CALL_FINISHED")
                        .nodeId("report")
                        .status("FAILED")
                        .payload(Map.of("toolName", "PrepareChangePackage"))
                        .build(),
                OpsRuntimeEvent.builder()
                        .eventType("TOOL_CALL_FINISHED")
                        .nodeId("report")
                        .status("SUCCEEDED")
                        .payload(Map.of("toolName", "OtherTool"))
                        .build());

        assertFalse(coordinator.proposalCreated(events, "report"));
        assertTrue(coordinator.proposalCreated(List.of(
                OpsRuntimeEvent.builder()
                        .eventType("TOOL_CALL_FINISHED")
                        .nodeId("report")
                        .status("SUCCEEDED")
                        .payload(Map.of("toolName", "PrepareChangePackage"))
                        .build()), "report"));
    }

    @Test
    void runtimeCannotReclaimChangePackageEvaluationProtocol() {
        Set<String> forbiddenMethods = Set.of(
                "evaluateAnalysisChangePackage",
                "proposalCreated",
                "appendAnalysisExecutionNotes",
                "validateAnalysisSkills");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
    }

    private OpsAnalysisChangePackageCoordinator coordinator(
            OpsRuntimeResourceAssembler resourceAssembler,
            OpsRuntimeLlmInvoker llmInvoker) {
        OpsAnalysisRuntimeStateManager stateManager =
                OpsAnalysisRuntimeStateManagerTestFactory.create();
        OpsRuntimeEventJournal eventJournal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        return new OpsAnalysisChangePackageCoordinator(
                resourceAssembler,
                new OpsRuntimePromptAssembler(),
                llmInvoker,
                stateManager,
                eventJournal);
    }

    private OpsAgentDefinition enabledDefinition() {
        return OpsAgentDefinition.builder()
                .agentId("agent-1")
                .changePackageEnabled(true)
                .build();
    }

    private OpsWorkflowNode enabledNode() {
        return OpsWorkflowNode.builder()
                .nodeId("report")
                .type("REPORT")
                .changePackageEnabled(true)
                .build();
    }

    private OpsRuntimeResourceBundle enabledBundle() {
        return OpsRuntimeResourceBundle.builder()
                .metadata(Map.of("changePackageToolEnabled", true))
                .build();
    }

    private OpsRuntimeResourceBundle enabledBundleWithTools() {
        return OpsRuntimeResourceBundle.builder()
                .ragEnabled(true)
                .skillContext("demo-ops context")
                .skillNames(List.of("demo-ops"))
                .tools(List.of(
                        tool("Skill"),
                        tool("PrepareChangePackage"),
                        tool("project_mcp_demo_openapi_prod_readonly_mcp"),
                        tool("project_mcp_demo_service_control_mcp"),
                        tool("prometheus_query")))
                .metadata(Map.of("changePackageToolEnabled", true))
                .build();
    }

    private ToolCallback tool(String name) {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        if (name != null && name.toLowerCase().contains("openapi")) {
            when(callback.call(any(String.class))).thenReturn("{\"operations\":[]}");
        }
        return callback;
    }

    private OpsAnalysisResponseDTO response(boolean changeRequested) {
        return OpsAnalysisResponseDTO.builder()
                .markdownReport("诊断报告")
                .investigationPlan(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .changeRequested(changeRequested)
                        .build())
                .build();
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .metadata(new HashMap<>())
                .build();
    }
}
