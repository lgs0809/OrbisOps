package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSubWorkflowNodeExecutorTest {

    @Test
    void latestPublishedExecutesAsPinnedDurableChildAndPreservesTrustedPrincipal() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsChatApplicationService chat = mock(OpsChatApplicationService.class);
        ObjectProvider<OpsChatApplicationService> provider = provider(chat);
        OpsSubWorkflowNodeExecutor executor = new OpsSubWorkflowNodeExecutor(definitions, provider);
        OpsAgentDefinition child = childDefinition(7, "hash-v7");
        when(definitions.resolveForProject("child-flow", null, false, "project-1"))
                .thenReturn(child);
        missingChildRun(chat);
        when(chat.chat(any(OpsAgentChatRequest.class), eq("user-1")))
                .thenReturn(OpsAgentChatResponse.builder().content("child-result").build());
        Object trustedPrincipal = new Object();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(OpsTrustedRequestMetadata.AUTH_PRINCIPAL, trustedPrincipal);

        Map<String, Object> result = executor.execute(
                parentDefinition(),
                subWorkflowNode(Map.of()),
                parentRequest(metadata),
                Map.of("preparedInput", "state-input"));

        ArgumentCaptor<OpsAgentChatRequest> childRequest =
                ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(chat).chat(childRequest.capture(), eq("user-1"));
        OpsAgentChatRequest request = childRequest.getValue();
        assertEquals("WORKFLOW", request.getMode());
        assertEquals("project-1", request.getProjectId());
        assertEquals("child-flow", request.getAgentDefinitionId());
        assertEquals(7, request.getAgentVersion());
        assertEquals("PINNED_VERSION", request.getMetadata().get("agentBindingMode"));
        assertEquals(trustedPrincipal, request.getMetadata().get(OpsTrustedRequestMetadata.AUTH_PRINCIPAL));
        assertEquals("parent-flow", request.getMetadata().get("parentWorkflowId"));
        assertEquals("child-flow", request.getMetadata().get("subWorkflowId"));
        assertEquals(1, request.getMetadata().get("subWorkflowDepth"));
        assertTrue(request.getRunId().startsWith("parent-run-sub-"));
        assertTrue(request.getSessionId().startsWith("parent-session-sub-"));
        assertEquals("child-result", result.get("output"));
        assertEquals(7, result.get("workflowVersion"));
        assertEquals("hash-v7", result.get("workflowDefinitionHash"));
        assertEquals(false, result.get("reused"));
    }

    @Test
    void pinnedVersionResolvesRequestedPublishedVersion() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsChatApplicationService chat = mock(OpsChatApplicationService.class);
        OpsSubWorkflowNodeExecutor executor = new OpsSubWorkflowNodeExecutor(definitions, provider(chat));
        when(definitions.resolveForProject("child-flow", 3, false, "project-1"))
                .thenReturn(childDefinition(3, "hash-v3"));
        missingChildRun(chat);
        when(chat.chat(any(OpsAgentChatRequest.class), eq("user-1")))
                .thenReturn(OpsAgentChatResponse.builder().content("ok").build());

        Map<String, Object> result = executor.execute(
                parentDefinition(),
                subWorkflowNode(Map.of("versionPolicy", "PINNED_VERSION", "version", 3)),
                parentRequest(new HashMap<>()),
                Map.of());

        assertEquals(3, result.get("workflowVersion"));
        verify(definitions).resolveForProject("child-flow", 3, false, "project-1");
    }

    @Test
    void succeededChildIsReusedBeforeLatestVersionIsResolved() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsChatApplicationService chat = mock(OpsChatApplicationService.class);
        OpsSubWorkflowNodeExecutor executor = new OpsSubWorkflowNodeExecutor(definitions, provider(chat));
        when(chat.run(anyString(), eq("project-1"))).thenReturn(Map.of(
                "status", "SUCCEEDED",
                "agent_id", "child-flow",
                "agent_version", 2,
                "agent_definition_hash", "hash-v2",
                "response", Map.of("content", "persisted-child-output")));

        Map<String, Object> result = executor.execute(
                parentDefinition(),
                subWorkflowNode(Map.of("versionPolicy", "LATEST_PUBLISHED")),
                parentRequest(new HashMap<>()),
                Map.of());

        assertEquals("persisted-child-output", result.get("output"));
        assertEquals(2, result.get("workflowVersion"));
        assertEquals("hash-v2", result.get("workflowDefinitionHash"));
        assertEquals(true, result.get("reused"));
        verify(definitions, never()).resolveForProject(anyString(), any(), eq(false), anyString());
        verify(chat, never()).chat(any(), anyString());
    }

    @Test
    void existingNonTerminalChildFailsClosedInsteadOfDuplicatingExecution() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsChatApplicationService chat = mock(OpsChatApplicationService.class);
        OpsSubWorkflowNodeExecutor executor = new OpsSubWorkflowNodeExecutor(definitions, provider(chat));
        when(chat.run(anyString(), eq("project-1"))).thenReturn(Map.of(
                "status", "RUNNING",
                "agent_id", "child-flow",
                "agent_version", 4,
                "agent_definition_hash", "hash-v4"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(
                        parentDefinition(),
                        subWorkflowNode(Map.of()),
                        parentRequest(new HashMap<>()),
                        Map.of()));

        assertEquals("SUB_WORKFLOW_CHILD_RUN_NOT_REUSABLE:RUNNING", error.getMessage());
        verify(definitions, never()).resolveForProject(anyString(), any(), eq(false), anyString());
        verify(chat, never()).chat(any(), anyString());
    }

    @Test
    void directSelfReferenceAndDepthOverflowFailClosed() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsChatApplicationService chat = mock(OpsChatApplicationService.class);
        OpsSubWorkflowNodeExecutor executor = new OpsSubWorkflowNodeExecutor(definitions, provider(chat));
        OpsWorkflowNode self = OpsWorkflowNode.builder()
                .nodeId("sub-self")
                .type("SUB_WORKFLOW")
                .agent("parent-flow")
                .build();

        IllegalArgumentException selfError = assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(
                        parentDefinition(),
                        self,
                        parentRequest(new HashMap<>()),
                        Map.of()));
        assertEquals("SUB_WORKFLOW_SELF_REFERENCE_FORBIDDEN", selfError.getMessage());

        Map<String, Object> depthMetadata = new HashMap<>();
        depthMetadata.put("subWorkflowDepth", 4);
        IllegalStateException depthError = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(
                        parentDefinition(),
                        subWorkflowNode(Map.of()),
                        parentRequest(depthMetadata),
                        Map.of()));
        assertEquals("SUB_WORKFLOW_MAX_DEPTH_EXCEEDED", depthError.getMessage());
        verify(chat, never()).chat(any(), anyString());
    }

    @Test
    void configuredInputKeyUsesParentGraphState() {
        OpsAgentDefinitionQueryGateway definitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsChatApplicationService chat = mock(OpsChatApplicationService.class);
        OpsSubWorkflowNodeExecutor executor = new OpsSubWorkflowNodeExecutor(definitions, provider(chat));
        when(definitions.resolveForProject("child-flow", null, false, "project-1"))
                .thenReturn(childDefinition(1, "hash-v1"));
        missingChildRun(chat);
        when(chat.chat(any(OpsAgentChatRequest.class), eq("user-1")))
                .thenReturn(OpsAgentChatResponse.builder().content("ok").build());

        executor.execute(
                parentDefinition(),
                subWorkflowNode(Map.of("inputKey", "preparedInput")),
                parentRequest(new HashMap<>()),
                Map.of("preparedInput", "prepared child input"));

        ArgumentCaptor<OpsAgentChatRequest> request = ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(chat).chat(request.capture(), eq("user-1"));
        assertEquals("prepared child input", request.getValue().getQuery());
    }

    private void missingChildRun(OpsChatApplicationService chat) {
        when(chat.run(anyString(), eq("project-1"))).thenAnswer(invocation -> {
            throw new IllegalArgumentException(
                    "Work Session 不存在或不属于当前项目：" + invocation.getArgument(0));
        });
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OpsChatApplicationService> provider(OpsChatApplicationService chat) {
        ObjectProvider<OpsChatApplicationService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(chat);
        return provider;
    }

    private OpsAgentDefinition parentDefinition() {
        return OpsAgentDefinition.builder()
                .agentId("parent-flow")
                .version(5)
                .definitionHash("parent-hash")
                .definitionKind("SPECIALIZED_WORKFLOW")
                .build();
    }

    private OpsAgentDefinition childDefinition(int version, String hash) {
        return OpsAgentDefinition.builder()
                .agentId("child-flow")
                .version(version)
                .definitionHash(hash)
                .definitionKind("SPECIALIZED_WORKFLOW")
                .engine("GRAPH")
                .build();
    }

    private OpsWorkflowNode subWorkflowNode(Map<String, Object> config) {
        return OpsWorkflowNode.builder()
                .nodeId("sub-1")
                .type("SUB_WORKFLOW")
                .agent("child-flow")
                .config(new HashMap<>(config))
                .build();
    }

    private OpsAgentChatRequest parentRequest(Map<String, Object> metadata) {
        return OpsAgentChatRequest.builder()
                .runId("parent-run")
                .sessionId("parent-session")
                .userId("user-1")
                .projectId("project-1")
                .query("parent query")
                .metadata(metadata)
                .build();
    }
}
