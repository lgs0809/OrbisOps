package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpsWorkSessionPreparationLeaseTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
    private final OpsWorkSessionRequestControl control = mock(OpsWorkSessionRequestControl.class);
    private final OpsRuntimeConversationContextCoordinator conversation = mock(OpsRuntimeConversationContextCoordinator.class);
    private final OpsAgentChatRequest request = OpsAgentChatRequest.builder()
            .runId("prepare-run").projectId("project-1").mode("AGENT").query("自然语言任务")
            .metadata(new LinkedHashMap<>()).build();

    @AfterEach void cleanup() { scheduler.shutdownNow(); Thread.interrupted(); }

    private OpsWorkSessionPreparationCoordinator coordinator() {
        var router = mock(OpsAgentRuntimeRuleRouter.class);
        var definition = OpsAgentDefinition.builder().agentId("agent-1").version(1)
                .definitionHash("hash-1").projectId("project-1").engine(OpsUnifiedAgentEngineAdapter.KEY)
                .instruction("test").capabilities(List.of()).mcpIds(List.of()).nodes(List.of()).build();
        when(control.normalize(request)).thenReturn(request);
        when(control.resolveDefinition(request)).thenReturn(definition);
        when(router.plan(request, definition)).thenReturn(OpsRuntimeExecutionPlan.builder()
                .mode("AGENT").engine(OpsUnifiedAgentEngineAdapter.KEY).adapterKey("test").build());
        doAnswer(call -> {
            request.getMetadata().put(OpsWorkSessionClaimMetadata.ATTEMPT_ID, "attempt-1");
            request.getMetadata().put(OpsWorkSessionClaimMetadata.LEASE_TOKEN, "lease-1");
            return null;
        }).when(runs).begin(any(), any(), any(), any());
        var assembly = new OpsWorkSessionRuntimeAssembly(router, mock(OpsTelemetryService.class), runs,
                control, mock(OpsWorkSessionFinalizer.class), mock(OpsAnalysisRuntimeStateManager.class),
                mock(OpsRuntimeEventJournal.class), conversation, mock(OpsRuntimeSkillLearningCoordinator.class),
                mock(OpsRuntimeEngineDispatcher.class), () -> null);
        return new OpsWorkSessionPreparationCoordinator(assembly,
                new OpsWorkSessionLeaseHeartbeat(runs, Duration.ofMillis(10), scheduler));
    }

    @Test void renewsClaimDuringSilentQuestionPreparationAndStopsAfterReturning() throws Exception {
        var coordinator = coordinator();
        var renewals = new CountDownLatch(3);
        var calls = new AtomicInteger();
        doAnswer(call -> { calls.incrementAndGet(); renewals.countDown(); return null; })
                .when(runs).heartbeat(any());
        doAnswer(call -> { assertTrue(renewals.await(2, TimeUnit.SECONDS)); return null; })
                .when(conversation).prepareMainQuestion(any(), any(), any(), any(), any(), anyLong());
        assertSame(request, coordinator.prepare(request, event -> {}).request());
        verify(runs).bindContextBundle(request);
        int finishedCalls = calls.get();
        Thread.sleep(60);
        assertEquals(finishedCalls, calls.get());
        assertTrue(finishedCalls >= 3);
    }

    @Test void lostClaimDuringPreparationCannotBindContextOrProceedToEngine() {
        var coordinator = coordinator();
        var calls = new AtomicInteger();
        doAnswer(call -> {
            if (calls.incrementAndGet() > 1) throw new IllegalStateException("WORK_SESSION_LEASE_LOST");
            return null;
        }).when(runs).heartbeat(any());
        doAnswer(call -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
            return null;
        }).when(conversation).prepareMainQuestion(any(), any(), any(), any(), any(), anyLong());
        assertEquals("WORK_SESSION_LEASE_LOST", assertThrows(IllegalStateException.class,
                () -> coordinator.prepare(request, event -> {})).getMessage());
        verify(runs, never()).bindContextBundle(any());
        verify(control).finish(request, "FAILED", "WORK_SESSION_LEASE_LOST");
        verify(control).markFinished(request);
    }
}
