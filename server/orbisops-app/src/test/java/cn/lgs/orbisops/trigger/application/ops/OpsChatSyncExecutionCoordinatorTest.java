package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatSyncExecutionCoordinatorTest {

    @SuppressWarnings("unchecked")
    private ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession() {
        return mock(ExecuteWorkSessionUseCase.class);
    }

    @Test
    void disabledTimeoutShouldExecuteDirectly() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder().runId("run-1").build();
        OpsAgentChatResponse expected = OpsAgentChatResponse.builder().content("ok").build();
        when(workSession.execute(request)).thenReturn(expected);
        OpsChatSyncExecutionCoordinator coordinator = new OpsChatSyncExecutionCoordinator(
                workSession,
                mock(GraphEventApplicationService.class),
                new OpsChatRuntimeSettings(0L));

        OpsAgentChatResponse actual = coordinator.execute(request);

        assertSame(expected, actual);
        verify(workSession).execute(request);
    }

    @Test
    void runtimeFailureShouldBeUnwrappedFromFuture() {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        IllegalStateException failure = new IllegalStateException("runtime-failed");
        when(workSession.execute(any(OpsAgentChatRequest.class))).thenThrow(failure);
        OpsChatSyncExecutionCoordinator coordinator = new OpsChatSyncExecutionCoordinator(
                workSession,
                mock(GraphEventApplicationService.class),
                new OpsChatRuntimeSettings(1L));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> coordinator.execute(
                OpsAgentChatRequest.builder().runId("run-2").build()));

        assertSame(failure, thrown);
        assertEquals("runtime-failed", thrown.getMessage());
    }

    @Test
    void timedOutRequestMustRunLateCompletionWhenBackgroundWorkSessionFinishes() throws Exception {
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> workSession =
                workSession();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder().runId("run-late").build();
        OpsAgentChatResponse completedResponse = OpsAgentChatResponse.builder().content("completed").build();
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch releaseExecution = new CountDownLatch(1);
        CountDownLatch lateCompletionCalled = new CountDownLatch(1);
        AtomicReference<OpsAgentChatResponse> lateResponse = new AtomicReference<>();
        when(workSession.execute(request)).thenAnswer(invocation -> {
            executionStarted.countDown();
            assertTrue(releaseExecution.await(5, TimeUnit.SECONDS));
            return completedResponse;
        });
        OpsChatSyncExecutionCoordinator coordinator = new OpsChatSyncExecutionCoordinator(
                workSession,
                mock(GraphEventApplicationService.class),
                new OpsChatRuntimeSettings(1L));

        OpsAgentChatResponse timeout = coordinator.execute(request, response -> {
            lateResponse.set(response);
            lateCompletionCalled.countDown();
        });

        assertTrue(executionStarted.await(1, TimeUnit.SECONDS));
        assertEquals("TIMEOUT", String.valueOf(timeout.getMetadata().get("status")));
        releaseExecution.countDown();
        assertTrue(lateCompletionCalled.await(2, TimeUnit.SECONDS));
        assertSame(completedResponse, lateResponse.get());
    }
}
