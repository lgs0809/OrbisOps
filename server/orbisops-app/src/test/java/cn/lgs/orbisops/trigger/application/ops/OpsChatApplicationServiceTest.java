package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsMainAgentActionHandler;
import cn.lgs.orbisops.application.agent.OpsMainAgentCoordinator;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.model.ModelAvailabilitySnapshot;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatApplicationServiceTest {

    @SuppressWarnings("unchecked")
    private static ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent>
    workSession() {
        return mock(ExecuteWorkSessionUseCase.class);
    }

    @Test
    void shouldUseProjectPreApprovalAgentForOpsInvestigation() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(sessionService.ensureForChat(any())).thenReturn(OpsChatSession.builder()
                .sessionId("chat-session-1")
                .title("hello")
                .build());
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenReturn(OpsAgentChatResponse.builder()
                .sessionId("chat-session-1")
                .content("ok")
                .build());
        OpsChatApplicationService service = service(workSession, sessionService, workspace);

        service.chat(OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .query("查一下最近 5 分钟 Prometheus 5xx 指标")
                .mode("MULTI_TURN")
                .engine("CHAT")
                .build(), "");

        ArgumentCaptor<OpsAgentChatRequest> captor = ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(workSession).execute(captor.capture());
        OpsAgentChatRequest actual = captor.getValue();
        assertEquals("AGENT", actual.getMode());
        assertEquals("generic-ops-react-agent", actual.getAgentDefinitionId());
        assertEquals("demo-project", actual.getProjectId());
        assertNull(actual.getAgentDefinition());
    }

    @Test
    void shouldKeepExplicitAgentDefinitionId() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenReturn(OpsAgentChatResponse.builder()
                .content("ok")
                .build());
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        OpsChatApplicationService service = service(
                workSession,
                mock(OpsChatSessionService.class),
                workspace);

        service.adminChat(OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .query("按预置运维 Agent 跑")
                .agentDefinitionId("demo-ops-agent")
                .engine("GRAPH")
                .build());

        ArgumentCaptor<OpsAgentChatRequest> captor = ArgumentCaptor.forClass(OpsAgentChatRequest.class);
        verify(workSession).execute(captor.capture());
        OpsAgentChatRequest actual = captor.getValue();
        assertEquals("demo-ops-agent", actual.getAgentDefinitionId());
        assertEquals("GRAPH", actual.getEngine());
        assertNull(actual.getAgentDefinition());
    }

    @Test
    void adminChatEstablishesDurableSessionBeforeWorkSessionExecution() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(sessionService.ensureForChat(any())).thenReturn(OpsChatSession.builder()
                .sessionId("admin-chat-session-1")
                .projectId("demo-project")
                .agentId("demo-ops-agent")
                .title("run")
                .build());
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenReturn(OpsAgentChatResponse.builder()
                .sessionId("admin-chat-session-1")
                .content("ok")
                .build());
        OpsChatApplicationService service = service(workSession, sessionService, workspace);

        service.adminChat(OpsAgentChatRequest.builder()
                .sessionId("admin-chat-session-1")
                .runId("admin-chat-run-1")
                .projectId("demo-project")
                .agentDefinitionId("demo-ops-agent")
                .query("run")
                .build());

        InOrder order = org.mockito.Mockito.inOrder(sessionService, workSession);
        order.verify(sessionService).ensureForChat(any(OpsAgentChatRequest.class));
        order.verify(workSession).execute(any(OpsAgentChatRequest.class));
        verify(sessionService).touch(eq("admin-chat-session-1"), anyString(), eq("ok"));
    }

    @Test
    void prepareStreamRequestAssignsRunIdAndCancelMarksRegistry() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        OpsRunCancellationRegistry cancellationRegistry = new OpsRunCancellationRegistry();
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        OpsChatApplicationService service = service(
                workSession,
                sessionService,
                workspace,
                cancellationRegistry);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .query("查一下最近错误日志")
                .mode("AGENT")
                .build();

        service.prepareStreamRequest(request, "alice");
        service.cancelRun(request.getRunId());

        assertTrue(request.getRunId().startsWith("chat-"));
        assertTrue(cancellationRegistry.isCanceled(request.getRunId()));
    }

    @Test
    void shouldNotBlockBeforeWorkSessionWhenModelUnavailable() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        doThrow(new IllegalStateException("model unavailable"))
                .when(availability).assertChatAvailable(anyString());
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(sessionService.ensureForChat(any())).thenReturn(OpsChatSession.builder()
                .sessionId("chat-session-1")
                .title("hello")
                .build());
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenReturn(OpsAgentChatResponse.builder()
                .sessionId("chat-session-1")
                .content("你好，我可以帮你做运维分析。")
                .build());
        OpsChatApplicationService service = service(
                workSession,
                sessionService,
                workspace,
                availability);

        OpsAgentChatResponse response = service.chat(OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .query("你好")
                .mode("AGENT")
                .build(), "alice");
        assertEquals("你好，我可以帮你做运维分析。", response.getContent());
        verify(workSession).execute(any(OpsAgentChatRequest.class));
    }

    @Test
    void syncChatTimeoutKeepsDurableRunAliveAndReturnsResumeHandle() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService graphEventService = mock(GraphEventApplicationService.class);
        OpsRunCancellationRegistry cancellationRegistry = new OpsRunCancellationRegistry();
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(sessionService.ensureForChat(any())).thenReturn(OpsChatSession.builder()
                .sessionId("chat-session-timeout")
                .title("timeout")
                .build());
        when(sessionService.canWrite("chat-session-timeout", "alice")).thenReturn(true);
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return OpsAgentChatResponse.builder().content("late").build();
        });
        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                sessionService,
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                graphEventService,
                cancellationRegistry,
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, graphEventService, 1L));

        OpsAgentChatResponse response = service.chat(OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .sessionId("chat-session-timeout")
                .query("排查一下")
                .mode("AGENT")
                .build(), "alice");

        assertEquals("SYNC_TIMEOUT_GUARD", response.getEngine());
        assertEquals("TIMEOUT", response.getMetadata().get("status"));
        assertFalse(cancellationRegistry.isCanceled(
                String.valueOf(response.getMetadata().get("runId"))));
        assertTrue(response.getContent().contains("没有被取消"));
        verify(graphEventService).publishRunEvent(
                anyString(),
                anyString(),
                eq("WORK_SESSION_TIMEOUT"),
                eq("TIMEOUT"),
                anyString());
    }

    @Test
    void streamPreparationDoesNotInvokeBusinessIntentRouter() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(sessionService.get("session-1")).thenReturn(Optional.empty());
        when(workSession.execute(any(OpsAgentChatRequest.class), any())).thenReturn(
                OpsAgentChatResponse.builder().content("ok").build());
        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                sessionService,
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                mock(OpsRunCancellationRegistry.class),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .sessionId("session-1")
                .projectId("demo-project")
                .query("查询错误日志")
                .build();

        service.prepareStreamRequest(request, "alice");
        assertEquals("AGENT", request.getMode());
        service.executeStream(request, event -> {
        });

        assertEquals("AGENT", request.getMode());
        verify(workSession).execute(eq(request), any());
    }

    @Test
    void workSessionInputFailureUsesUnifiedMainAgentOutcome() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(sessionService.ensureForChat(any())).thenReturn(OpsChatSession.builder()
                .sessionId("session-1")
                .title("failure")
                .build());
        when(workSession.execute(any(OpsAgentChatRequest.class)))
                .thenThrow(new IllegalArgumentException("EVIDENCE_REQUIRED"));
        OpsChatApplicationService service = service(workSession, sessionService, workspace);

        OpsAgentChatResponse response = service.chat(OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .query("生成方案")
                .build(), "alice");

        assertEquals("MAIN_AGENT_COORDINATOR", response.getEngine());
        assertEquals("NEEDS_INPUT", response.getMetadata().get("status"));
        assertEquals("EVIDENCE_REQUIRED", response.getMetadata().get("reasonCode"));
        assertEquals(true, response.getMetadata().get("failed"));
    }

    @Test
    void compoundNaturalLanguageRequestIsHandledByOneAgentRun() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenAnswer(invocation -> {
            OpsAgentChatRequest request = invocation.getArgument(0);
            assertEquals("导入排障 Skill，然后查询错误日志", request.getQuery());
            return OpsAgentChatResponse.builder().content("任务完成").build();
        });

        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                mock(OpsChatSessionService.class),
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                mock(OpsRunCancellationRegistry.class),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));

        OpsAgentChatResponse response = service.adminChat(OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .userId("alice")
                .query("导入排障 Skill，然后查询错误日志")
                .build());

        assertEquals("任务完成", response.getContent());
        verify(workSession, times(1)).execute(any(OpsAgentChatRequest.class));
    }

    @Test
    void capabilityManagementLanguageDoesNotInvokeLegacyFixedControlPath() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenReturn(
                OpsAgentChatResponse.builder().content("由 Agent 通过 Skill/Tool 完成").build());

        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                mock(OpsChatSessionService.class),
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                new OpsRunCancellationRegistry(),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));

        OpsAgentChatResponse response = service.adminChat(OpsAgentChatRequest.builder()
                .runId("parent-run-1")
                .projectId("demo-project")
                .userId("alice")
                .query("导入 Skill，接入 MCP，然后查询日志")
                .build());

        assertEquals("由 Agent 通过 Skill/Tool 完成", response.getContent());
        verify(workSession, times(1)).execute(any(OpsAgentChatRequest.class));
    }

    @Test
    void oneAgentRunOwnsCancellationInsteadOfIntentStepSequencing() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenThrow(
                new OpsRunCanceledException("用户取消"));

        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                mock(OpsChatSessionService.class),
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                new OpsRunCancellationRegistry(),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));

        OpsAgentChatResponse response = service.adminChat(
                OpsAgentChatRequest.builder()
                        .runId("parent-run-cancel")
                        .projectId("demo-project")
                        .userId("alice")
                        .query("导入 Skill，然后查询日志")
                        .build());
        assertEquals("FAILED", response.getMetadata().get("status"));
        verify(workSession, times(1)).execute(any(OpsAgentChatRequest.class));
    }

    @Test
    void streamingCompoundRequestStartsOneAgentStreamWithoutLegacyControlPrelude() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(workSession.execute(any(OpsAgentChatRequest.class), any())).thenAnswer(invocation -> {
            OpsAgentChatRequest request = invocation.getArgument(0);
            assertEquals("导入 Skill，然后查询日志", request.getQuery());
            return OpsAgentChatResponse.builder().content("日志查询完成").build();
        });

        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                mock(OpsChatSessionService.class),
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                new OpsRunCancellationRegistry(),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));

        OpsAgentChatResponse response = service.executeAdminStream(
                OpsAgentChatRequest.builder()
                        .runId("parent-run-stream")
                        .projectId("demo-project")
                        .userId("alice")
                        .query("导入 Skill，然后查询日志")
                        .build(),
                event -> {
                });

        assertEquals("日志查询完成", response.getContent());
        verify(workSession, times(1)).execute(any(OpsAgentChatRequest.class), any());
    }

    @Test
    void capabilityRequestPreservesTrustedPrincipalAndProjectScopeInSingleAgentRun() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService graphEvents = mock(GraphEventApplicationService.class);
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "alice", "user-1", "jwt-1", AdminAuthService.SCOPE_ADMIN, false);
        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenAnswer(invocation -> {
            OpsAgentChatRequest actual = invocation.getArgument(0);
            assertEquals("demo-project", actual.getProjectId());
            assertEquals(principal, actual.getMetadata().get(OpsTrustedRequestMetadata.AUTH_PRINCIPAL));
            assertEquals("导入 Skill，然后接入 MCP", actual.getQuery());
            return OpsAgentChatResponse.builder().content("能力任务已处理").build();
        });

        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                mock(OpsChatSessionService.class),
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                graphEvents,
                new OpsRunCancellationRegistry(),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, graphEvents, 90L));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("parent-control-run")
                .projectId("demo-project")
                .userId("alice")
                .query("导入 Skill，然后接入 MCP")
                .metadata(new java.util.LinkedHashMap<>(Map.of(
                        OpsTrustedRequestMetadata.AUTH_PRINCIPAL, principal)))
                .build();

        OpsAgentChatResponse response = service.adminChat(request);

        assertEquals("能力任务已处理", response.getContent());
        verify(workSession, times(1)).execute(any(OpsAgentChatRequest.class));
    }

    @Test
    void rememberLanguageReachesOneAgentRunAndDoesNotWriteMemoryDuringPreparation() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsChatSessionService sessionService = mock(OpsChatSessionService.class);
        ProjectDefinitionApplicationService workspace = mock(ProjectDefinitionApplicationService.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);

        when(workspace.exists("demo-project")).thenReturn(true);
        when(workspace.defaultAgentId("demo-project")).thenReturn("generic-ops-react-agent");
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenAnswer(invocation -> {
            OpsAgentChatRequest actual = invocation.getArgument(0);
            assertEquals("记住订单索引是 order-*，然后查最近十分钟错误日志", actual.getQuery());
            return OpsAgentChatResponse.builder().content("日志排查完成").build();
        });

        OpsChatApplicationService service = new OpsChatApplicationService(
                workSession,
                sessionService,
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                new OpsRunCancellationRegistry(),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("remember-work-run")
                .projectId("demo-project")
                .userId("alice")
                .query("记住订单索引是 order-*，然后查最近十分钟错误日志")
                .build();

        OpsAgentChatResponse response = service.adminChat(request);

        verify(workSession, times(1)).execute(any(OpsAgentChatRequest.class));
        assertEquals("日志排查完成", response.getContent());
        assertEquals(null, request.getMetadata().get("_explicitMemoryApplied"));
        assertEquals(null, request.getMetadata().get("memoryWriteResult"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void modelStatusMustTreatUsableDatabaseManagedModelAsChatAvailable() {
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        when(availability.snapshot()).thenReturn(new ModelAvailabilitySnapshot(
                false, "", "", "", "", "", "", "",
                false, false, false, false, false, false, false, false,
                false, false, false, "fallback unavailable"));
        OpsConfiguredChatModelAvailabilityService configured = mock(OpsConfiguredChatModelAvailabilityService.class);
        when(configured.status()).thenReturn(Map.of(
                "enabledConfiguredModelCount", 1,
                "usableConfiguredModelCount", 1,
                "configuredModelAvailable", true));
        OpsChatApplicationService service = service(
                mock(ExecuteWorkSessionUseCase.class),
                mock(OpsChatSessionService.class),
                mock(ProjectDefinitionApplicationService.class),
                availability);
        ReflectionTestUtils.setField(service, "configuredChatModelAvailability", configured);

        Map<String, Object> status = service.modelStatus();

        assertEquals(Boolean.FALSE, status.get("fallbackChatAvailable"));
        assertEquals(Boolean.TRUE, status.get("configuredModelAvailable"));
        assertEquals(Boolean.TRUE, status.get("chatAvailable"));
    }

    private static OpsChatApplicationService service(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession,
            OpsChatSessionService sessionService,
            ProjectDefinitionApplicationService workspace) {
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        return new OpsChatApplicationService(
                workSession,
                sessionService,
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                mock(OpsRunCancellationRegistry.class),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));
    }

    private static OpsChatApplicationService service(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession,
            OpsChatSessionService sessionService,
            ProjectDefinitionApplicationService workspace,
            OpsRunCancellationRegistry cancellationRegistry) {
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        return new OpsChatApplicationService(
                workSession,
                sessionService,
                mock(ModelAvailabilityPort.class),
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                cancellationRegistry,
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));
    }

    private static OpsChatApplicationService service(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession,
            OpsChatSessionService sessionService,
            ProjectDefinitionApplicationService workspace,
            ModelAvailabilityPort availability) {
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        return new OpsChatApplicationService(
                workSession,
                sessionService,
                availability,
                mock(OpsAgentLlmClient.class),
                workspace,
                events,
                mock(OpsRunCancellationRegistry.class),
                mock(cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter.class),
                coordinator(workSession, events, 90L));
    }

    private static OpsMainAgentCoordinator coordinator(
            ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession,
            GraphEventApplicationService events,
            long timeoutSeconds) {
        OpsChatRuntimeActionHandler runtimeHandler =
                new OpsChatRuntimeActionHandler(
                        workSession,
                        events,
                        new OpsChatRuntimeSettings(timeoutSeconds));
        List<OpsMainAgentActionHandler> handlers = List.of(runtimeHandler);
        return new OpsMainAgentCoordinator(handlers);
    }
}
