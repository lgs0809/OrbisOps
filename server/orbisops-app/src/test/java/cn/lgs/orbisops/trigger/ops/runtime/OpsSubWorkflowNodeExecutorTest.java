package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
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
        committedChildAfterExecution(chat, 7, "hash-v7", "child-result");
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
        committedChildAfterExecution(chat, 3, "hash-v3", "ok");
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
        committedChildAfterExecution(chat, 1, "hash-v1", "ok");
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

    @Test
    void structuredInputRemainsJsonAndChildOutputIsReadFromCommittedRun() {
        var definitions = mock(OpsAgentDefinitionQueryGateway.class);
        var chat = mock(OpsChatApplicationService.class);
        when(definitions.resolveForProject("child-flow", null, false, "project-1")).thenReturn(childDefinition(1,"h1"));
        committedChildAfterExecution(chat,1,"h1","committed answer");
        when(chat.chat(any(),anyString())).thenReturn(OpsAgentChatResponse.builder().content("uncommitted answer").build());
        var input = Map.of("projectId","project-1","priorEvidence",Map.of("count",100,"queryIds",java.util.List.of("q1")));
        var result = new OpsSubWorkflowNodeExecutor(definitions,provider(chat)).execute(parentDefinition(),
                subWorkflowNode(Map.of("inputKey","workflowData_input","structuredOutputKey","investigation")),
                parentRequest(Map.of()), Map.of("workflowData_input",input));
        var request = ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(chat).chat(request.capture(),eq("user-1"));
        assertEquals(input, CanonicalJson.parseObject(request.getValue().getQuery()));
        assertEquals("committed answer", result.get("output"));
        assertEquals("SUCCEEDED", ((Map<?,?>)result.get("workflowData_investigation")).get("executionStatus"));
    }

    @Test
    void configuredMissingInputNeverFallsBackToParentQueryOrStartsChild() {
        var definitions = mock(OpsAgentDefinitionQueryGateway.class);
        var chat = mock(OpsChatApplicationService.class);
        when(definitions.resolveForProject("child-flow", null, false, "project-1")).thenReturn(childDefinition(1,"h1"));
        var error = assertThrows(IllegalArgumentException.class, () -> new OpsSubWorkflowNodeExecutor(definitions,provider(chat))
                .execute(parentDefinition(), subWorkflowNode(Map.of("inputKey","missing")),parentRequest(Map.of()),Map.of()));
        assertEquals("SUB_WORKFLOW_INPUT_MISSING:missing",error.getMessage());
        verify(chat,never()).chat(any(),anyString());
    }

    @Test
    void failedWaitingCanceledOrMissingChildCannotCompleteParentEvenWithSuccessfulText() {
        for (String status : java.util.List.of("FAILED","WAITING_APPROVAL","CANCELED","")) {
            var definitions = mock(OpsAgentDefinitionQueryGateway.class);
            var chat = mock(OpsChatApplicationService.class);
            when(definitions.resolveForProject("child-flow", null, false, "project-1")).thenReturn(childDefinition(1,"h1"));
            when(chat.run(anyString(),eq("project-1"))).thenReturn(Map.of(),Map.of("status",status));
            when(chat.chat(any(),anyString())).thenReturn(OpsAgentChatResponse.builder().content("all good").build());
            var error = assertThrows(IllegalStateException.class, () -> new OpsSubWorkflowNodeExecutor(definitions,provider(chat))
                    .execute(parentDefinition(),subWorkflowNode(Map.of()),parentRequest(Map.of()),Map.of()));
            assertEquals("SUB_WORKFLOW_CHILD_RUN_NOT_SUCCEEDED:"+status,error.getMessage());
        }
    }

    @Test
    void terminalSuccessFromDifferentVersionIsRejected() {
        var definitions = mock(OpsAgentDefinitionQueryGateway.class);
        var chat = mock(OpsChatApplicationService.class);
        when(definitions.resolveForProject("child-flow", null, false, "project-1")).thenReturn(childDefinition(1,"h1"));
        committedChildAfterExecution(chat,2,"h2","wrong version");
        var error = assertThrows(IllegalStateException.class, () -> new OpsSubWorkflowNodeExecutor(definitions,provider(chat))
                .execute(parentDefinition(),subWorkflowNode(Map.of()),parentRequest(Map.of()),Map.of()));
        assertEquals("SUB_WORKFLOW_CHILD_RUN_IDENTITY_CONFLICT",error.getMessage());
    }

    private void committedChildAfterExecution(OpsChatApplicationService chat, int version, String hash, String answer) {
        when(chat.run(anyString(), eq("project-1"))).thenReturn(Map.of(), Map.of(
                "status","SUCCEEDED","agent_id","child-flow","agent_version",version,
                "agent_definition_hash",hash,"response",Map.of("content",answer)));
    }

    @Test
    void longParentIdentitiesFitAllDurableTablesAndKeepDistinctHashSuffixes() {
        var childIds = new java.util.HashSet<String>();
        for (String ending : java.util.List.of("a", "b")) {
            var definitions = mock(OpsAgentDefinitionQueryGateway.class);
            var chat = mock(OpsChatApplicationService.class);
            when(definitions.resolveForProject("child-flow", null, false, "project-1")).thenReturn(childDefinition(1,"h1"));
            committedChildAfterExecution(chat,1,"h1","committed");
            var parent = parentRequest(Map.of());
            parent.setRunId("parent-" + "x".repeat(72) + ending);
            parent.setSessionId("session-" + "y".repeat(72));
            new OpsSubWorkflowNodeExecutor(definitions,provider(chat)).execute(parentDefinition(),subWorkflowNode(Map.of()),parent,Map.of());
            var request = ArgumentCaptor.forClass(OpsAgentChatRequest.class);
            verify(chat).chat(request.capture(),eq("user-1"));
            assertEquals(80,request.getValue().getRunId().length());
            assertEquals(80,request.getValue().getSessionId().length());
            assertTrue(request.getValue().getRunId().matches(".*-sub-[0-9a-f]{16}"));
            childIds.add(request.getValue().getRunId());
        }
        assertEquals(2,childIds.size());
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
