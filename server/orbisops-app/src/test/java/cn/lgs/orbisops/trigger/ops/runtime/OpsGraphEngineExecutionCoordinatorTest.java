package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsGraphEngineExecutionCoordinatorTest {

    @Test
    void graphNodesPreserveExplicitDefinitionNodes() {
        Fixture fixture = fixture();
        List<OpsWorkflowNode> nodes = List.of(
                OpsWorkflowNode.builder().nodeId("start").type("START").build(),
                OpsWorkflowNode.builder().nodeId("report").type("REPORT").build());
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .nodes(nodes)
                .build();

        assertEquals(nodes, fixture.coordinator().graphNodes(definition));
    }

    @Test
    void graphNodesSynthesizeCanonicalChatNodeWhenDefinitionHasNoNodes() {
        Fixture fixture = fixture();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .name("Agent Name")
                .instruction("执行说明")
                .ragEnabled(true)
                .knowledgeBaseId("kb-1")
                .changePackageEnabled(true)
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .name("mcp-1")
                        .build()))
                .build();

        List<OpsWorkflowNode> nodes = fixture.coordinator().graphNodes(definition);

        assertEquals(1, nodes.size());
        OpsWorkflowNode node = nodes.get(0);
        assertEquals("chat", node.getNodeId());
        assertEquals("CHAT", node.getType());
        assertEquals("Agent Name", node.getAgent());
        assertEquals("执行说明", node.getInstruction());
        assertEquals(true, node.getRagEnabled());
        assertEquals("kb-1", node.getKnowledgeBaseId());
        assertEquals(true, node.getChangePackageEnabled());
        assertEquals(1, node.getMcpServers().size());
        assertEquals("mcp-1", node.getMcpServers().get(0).getName());
    }

    @Test
    void graphNodesPreserveStandaloneAgentScopeAndProjectCapabilityInheritance() {
        Fixture fixture = fixture();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .engine("AGENTSCOPE")
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder()
                        .agentId("main-assistant")
                        .name("平台主助手")
                        .role("MAIN_ASSISTANT")
                        .instruction("连续 ReAct 调查")
                        .maxDepth(1)
                        .maxIterations(12)
                        .outputKey("final_answer")
                        .inheritProjectCapabilities(true)
                        .allowedToolNames(List.of("project_mcp_*", "mcp_tool_catalog_*"))
                        .build()))
                .build();

        List<OpsWorkflowNode> nodes = fixture.coordinator().graphNodes(definition);

        assertEquals(1, nodes.size());
        OpsWorkflowNode node = nodes.get(0);
        assertEquals("main-assistant", node.getNodeId());
        assertEquals("AGENTSCOPE", node.getType());
        assertEquals("REACT", node.getMode());
        assertEquals("main-assistant", node.getAgent());
        assertEquals("连续 ReAct 调查", node.getInstruction());
        assertEquals("final_answer", node.getOutputKey());
        assertEquals("MAIN_ASSISTANT", node.getConfig().get("role"));
        assertEquals(true, node.getConfig().get("inheritProjectCapabilities"));
        assertEquals(List.of("project_mcp_*", "mcp_tool_catalog_*"),
                node.getConfig().get("allowedToolNames"));
    }

    @Test
    void compilerFailurePublishesFailedLifecycleRecordsRunFailureAndCleansState() {
        Fixture fixture = fixture();
        OpsAgentGraphCompilerAdapter compiler = mock(OpsAgentGraphCompilerAdapter.class);
        fixture.compilerRef().set(compiler);
        when(compiler.compile(any(), any()))
                .thenThrow(new IllegalStateException("invalid graph"));
        OpsAgentDefinition definition = definition();
        OpsAgentChatRequest request = request();
        List<OpsRuntimeEvent> events = new ArrayList<>();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.coordinator().execute(
                        definition, request, events, null, mockNodeHooks()));

        assertTrue(error.getMessage().contains("invalid graph"));
        verify(fixture.graphRuntimeStateManager()).initialize(request);
        verify(fixture.analysisStateManager()).publishRunStarted(definition, null);
        verify(fixture.analysisStateManager()).publishRunFinished(
                definition, null, "FAILED", "invalid graph");
        verify(fixture.graphRuntimeStateManager()).cleanup(request);
        verify(fixture.analysisStateManager()).remove("run-1");
        assertTrue(events.stream().anyMatch(event ->
                "RUN_FAILED".equals(event.getEventType())
                        && "FAILED".equals(event.getStatus())));
    }

    @Test
    void cancellationPublishesCanceledLifecycleWithoutSyntheticRunFailure() {
        Fixture fixture = fixture();
        OpsAgentGraphCompilerAdapter compiler = mock(OpsAgentGraphCompilerAdapter.class);
        fixture.compilerRef().set(compiler);
        when(compiler.compile(any(), any()))
                .thenThrow(new OpsRunCanceledException("cancel requested"));
        OpsAgentDefinition definition = definition();
        OpsAgentChatRequest request = request();
        List<OpsRuntimeEvent> events = new ArrayList<>();

        OpsRunCanceledException error = assertThrows(OpsRunCanceledException.class,
                () -> fixture.coordinator().execute(
                        definition, request, events, null, mockNodeHooks()));

        assertEquals("cancel requested", error.getMessage());
        verify(fixture.analysisStateManager()).publishRunFinished(
                definition, null, "CANCELED", "cancel requested");
        verify(fixture.analysisStateManager(), never()).publishRunFinished(
                definition, null, "FAILED", "cancel requested");
        verify(fixture.graphRuntimeStateManager()).cleanup(request);
        verify(fixture.analysisStateManager()).remove("run-1");
        assertTrue(events.stream().noneMatch(event ->
                "RUN_FAILED".equals(event.getEventType())));
    }

    @Test
    void compilerSupplierIsResolvedAtExecutionTime() {
        Fixture fixture = fixture();
        OpsAgentGraphCompilerAdapter compiler = mock(OpsAgentGraphCompilerAdapter.class);
        fixture.compilerRef().set(compiler);
        when(compiler.compile(any(), any()))
                .thenThrow(new IllegalStateException("late compiler used"));

        assertThrows(IllegalStateException.class,
                () -> fixture.coordinator().execute(
                        definition(), request(), new ArrayList<>(), null, mockNodeHooks()));

        verify(compiler).compile(any(), any());
    }

    @Test
    void runtimeCannotReclaimGraphEngineProtocolOrOrphanHelpers() {
        Set<String> forbiddenMethods = Set.of(
                "executeGraph",
                "graphNodes",
                "intentRouteConstraint",
                "analysisNodeSkills",
                "mergeStrings",
                "messageText");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
    }

    private Fixture fixture() {
        OpsGraphRuntimeStateManager graphRuntimeStateManager =
                mock(OpsGraphRuntimeStateManager.class);
        OpsAnalysisRuntimeStateManager analysisStateManager =
                mock(OpsAnalysisRuntimeStateManager.class);
        AtomicReference<OpsAgentGraphCompilerAdapter> compilerRef = new AtomicReference<>();
        OpsRuntimeEventJournal eventJournal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy =
                new OpsRuntimeNodeExecutionPolicy(routingPolicy);
        OpsGraphEngineExecutionCoordinator coordinator =
                new OpsGraphEngineExecutionCoordinator(
                        graphRuntimeStateManager,
                        analysisStateManager,
                        compilerRef::get,
                        mock(OpsGraphNodeExecutionCoordinator.class),
                        mock(OpsGraphTopologyAssembler.class),
                        mock(OpsRuntimeConversationContextCoordinator.class),
                        routingPolicy,
                        mock(OpsRuntimeSkillLearningCoordinator.class),
                        eventJournal,
                        nodeExecutionPolicy,
                        Runnable::run);
        return new Fixture(
                coordinator,
                graphRuntimeStateManager,
                analysisStateManager,
                compilerRef);
    }

    private OpsGraphNodeExecutionCoordinator.Hooks mockNodeHooks() {
        return mock(OpsGraphNodeExecutionCoordinator.Hooks.class);
    }

    private OpsAgentDefinition definition() {
        return OpsAgentDefinition.builder()
                .agentId("agent-1")
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("chat")
                        .type("CHAT")
                        .build()))
                .build();
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .query("问题")
                .metadata(new HashMap<>())
                .build();
    }

    private record Fixture(
            OpsGraphEngineExecutionCoordinator coordinator,
            OpsGraphRuntimeStateManager graphRuntimeStateManager,
            OpsAnalysisRuntimeStateManager analysisStateManager,
            AtomicReference<OpsAgentGraphCompilerAdapter> compilerRef) {
    }
}
