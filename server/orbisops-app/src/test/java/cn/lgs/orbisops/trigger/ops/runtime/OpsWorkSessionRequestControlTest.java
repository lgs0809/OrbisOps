package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkSessionRequestControlTest {

    @Test
    void normalizeCreatesCanonicalSessionRunUserAndMetadata() {
        OpsWorkSessionRequestControl control = control();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .query("问题")
                .build();

        OpsAgentChatRequest normalized = control.normalize(request);

        assertSame(request, normalized);
        assertTrue(normalized.getSessionId().startsWith("ops-session-"));
        assertTrue(normalized.getRunId().startsWith("chat-" + normalized.getSessionId() + "-"));
        assertEquals("web-user", normalized.getUserId());
        assertEquals(normalized.getRunId(), normalized.getMetadata().get("runId"));
    }

    @Test
    void normalizeUsesMetadataRunIdAndRejectsBlankQuery() {
        OpsWorkSessionRequestControl control = control();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .query("问题")
                .sessionId("session-1")
                .metadata(new HashMap<>(Map.of("runId", "  durable-run-1  ")))
                .build();

        OpsAgentChatRequest normalized = control.normalize(request);

        assertEquals("durable-run-1", normalized.getRunId());
        assertEquals("durable-run-1", normalized.getMetadata().get("runId"));
        assertEquals("query 不能为空",
                assertThrows(IllegalArgumentException.class,
                        () -> control.normalize(new OpsAgentChatRequest()))
                        .getMessage());
    }

    @Test
    void suppliedDefinitionMustMatchSelectedProject() {
        OpsWorkSessionRequestControl control = control();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .build();
        OpsAgentChatRequest request = request();
        request.setProjectId("project-1");
        request.setAgentDefinition(definition);

        assertSame(definition, control.resolveDefinition(request));

        request.setProjectId("");
        assertEquals("运行 Agent 前必须选择 projectId",
                assertThrows(IllegalArgumentException.class,
                        () -> control.resolveDefinition(request))
                        .getMessage());
        request.setProjectId("project-2");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> control.resolveDefinition(request))
                .getMessage().contains("不能在项目 project-2 中运行"));
    }

    @Test
    void registryResolutionPreservesVersionPreviewAndProjectCoordinates() {
        OpsAgentDefinitionQueryGateway definitionRegistry = mock(OpsAgentDefinitionQueryGateway.class);
        OpsWorkSessionRequestControl control = new OpsWorkSessionRequestControl(
                definitionRegistry,
                mock(OpsRunCancellationRegistry.class),
                mock(OpsWorkSessionRunAdapter.class));
        OpsAgentChatRequest request = request();
        request.setAgentDefinitionId("agent-1");
        request.setAgentVersion(7);
        request.setPreviewDraft(true);
        request.setProjectId("project-1");
        OpsAgentDefinition resolved = OpsAgentDefinition.builder().agentId("agent-1").build();
        when(definitionRegistry.resolveForProject(
                "agent-1", 7, true, "project-1"))
                .thenReturn(resolved);

        assertSame(resolved, control.resolveDefinition(request));
        verify(definitionRegistry).resolveForProject(
                "agent-1", 7, true, "project-1");
    }

    @Test
    void cancellationChecksRegistryAndDurableAttempt() {
        OpsAgentDefinitionQueryGateway definitionRegistry = mock(OpsAgentDefinitionQueryGateway.class);
        OpsRunCancellationRegistry cancellationRegistry = mock(OpsRunCancellationRegistry.class);
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        OpsWorkSessionRequestControl control = new OpsWorkSessionRequestControl(
                definitionRegistry, cancellationRegistry, runService);
        OpsAgentChatRequest request = request();
        request.setProjectId("project-1");

        control.assertNotCanceled(request);

        verify(cancellationRegistry).assertNotCanceled("run-1");
        verify(runService, never()).cancelRequested("run-1", "project-1");

        request.getMetadata().put(OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt-1");
        when(runService.cancelRequested("run-1", "project-1")).thenReturn(true);
        assertEquals("Work Session 已收到持久化取消请求",
                assertThrows(OpsRunCanceledException.class,
                        () -> control.assertNotCanceled(request))
                        .getMessage());
    }

    @Test
    void finishAndMarkFinishedDelegateToDurableServices() {
        OpsRunCancellationRegistry cancellationRegistry = mock(OpsRunCancellationRegistry.class);
        OpsWorkSessionRunAdapter runService = mock(OpsWorkSessionRunAdapter.class);
        OpsWorkSessionRequestControl control = new OpsWorkSessionRequestControl(
                mock(OpsAgentDefinitionQueryGateway.class),
                cancellationRegistry,
                runService);
        OpsAgentChatRequest request = request();
        OpsAgentChatResponse response = OpsAgentChatResponse.builder().content("回答").build();

        control.finish(request, "FAILED", "error");
        control.finish(request, "SUCCEEDED", "", response);
        control.markFinished(request);

        verify(runService).finish(request, "FAILED", "error");
        verify(runService).finish(request, "SUCCEEDED", "", response);
        verify(cancellationRegistry).markFinished("run-1");
    }

    @Test
    void lifecycleCoordinatorDelegatesRequestControlProtocol() {
        Set<String> forbiddenMethods = Set.of(
                "normalize",
                "assertChatNotCanceled",
                "finishDurableRun",
                "markRunFinished",
                "resolveDefinition",
                "assertProjectScope");
        Set<String> coordinatorMethods = Arrays.stream(
                        OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> coordinatorFields = Arrays.stream(
                        OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(coordinatorMethods::contains));
        assertFalse(coordinatorFields.contains("definitionRegistry"));
        assertFalse(coordinatorFields.contains("cancellationRegistry"));
        assertTrue(coordinatorFields.contains("preparationCoordinator"));
    }

    private OpsWorkSessionRequestControl control() {
        return new OpsWorkSessionRequestControl(
                mock(OpsAgentDefinitionQueryGateway.class),
                mock(OpsRunCancellationRegistry.class),
                mock(OpsWorkSessionRunAdapter.class));
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .query("问题")
                .metadata(new HashMap<>())
                .build();
    }
}
