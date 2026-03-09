package cn.lgs.orbisops.application.worksession.run;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkSessionRunControlUseCaseTest {

    @Test
    void actorReadAuthorizesBeforeLoadingRun() {
        @SuppressWarnings("unchecked")
        WorkSessionRunControlPort<String, String> runs = mock(WorkSessionRunControlPort.class);
        WorkSessionLocalCancellationPort local = mock(WorkSessionLocalCancellationPort.class);
        @SuppressWarnings("unchecked")
        WorkSessionResumeSchedulerPort<String> scheduler = mock(WorkSessionResumeSchedulerPort.class);
        WorkSessionRunControlUseCase<String, String> useCase =
                new WorkSessionRunControlUseCase<>(runs, local, scheduler);
        when(runs.get("run-1", "project-1")).thenReturn("view");

        String result = useCase.getForActor("run-1", "project-1", "alice");

        assertEquals("view", result);
        InOrder order = inOrder(runs);
        order.verify(runs).assertActorCanRead("run-1", "project-1", "alice");
        order.verify(runs).get("run-1", "project-1");
    }

    @Test
    void resumeAuthorizesAndBuildsRequestBeforeScheduling() {
        @SuppressWarnings("unchecked")
        WorkSessionRunControlPort<String, Object> runs = mock(WorkSessionRunControlPort.class);
        WorkSessionLocalCancellationPort local = mock(WorkSessionLocalCancellationPort.class);
        @SuppressWarnings("unchecked")
        WorkSessionResumeSchedulerPort<Object> scheduler = mock(WorkSessionResumeSchedulerPort.class);
        WorkSessionRunControlUseCase<String, Object> useCase =
                new WorkSessionRunControlUseCase<>(runs, local, scheduler);
        Object request = new Object();
        when(runs.resumeRequest("run-1", "project-1", "alice")).thenReturn(request);

        WorkSessionResumeAccepted result = useCase.resume("run-1", "project-1", "alice");

        assertEquals("RESUME_SCHEDULED", result.status());
        assertTrue(result.message().contains("安全检查点"));
        InOrder order = inOrder(runs, scheduler);
        order.verify(runs).resumeRequest("run-1", "project-1", "alice");
        order.verify(scheduler).schedule(request);
    }

    @Test
    void persistedCancelMarksLocalRegistryOnlyAfterSuccess() {
        @SuppressWarnings("unchecked")
        WorkSessionRunControlPort<String, String> runs = mock(WorkSessionRunControlPort.class);
        WorkSessionLocalCancellationPort local = mock(WorkSessionLocalCancellationPort.class);
        @SuppressWarnings("unchecked")
        WorkSessionResumeSchedulerPort<String> scheduler = mock(WorkSessionResumeSchedulerPort.class);
        WorkSessionRunControlUseCase<String, String> useCase =
                new WorkSessionRunControlUseCase<>(runs, local, scheduler);
        when(runs.requestCancel("run-1", "project-1", "alice", "stop")).thenReturn(true);
        when(runs.requestCancel("run-2", "project-1", "alice", "stop")).thenReturn(false);

        assertTrue(useCase.requestCancel("run-1", "project-1", "alice", "stop"));
        assertFalse(useCase.requestCancel("run-2", "project-1", "alice", "stop"));

        InOrder order = inOrder(runs, local);
        order.verify(runs).requestCancel("run-1", "project-1", "alice", "stop");
        order.verify(local).markCanceled("run-1");
        order.verify(runs).requestCancel("run-2", "project-1", "alice", "stop");
        verify(local, never()).markCanceled("run-2");
    }

    @Test
    void missingProjectUsesActorScopedDurableCancel() {
        @SuppressWarnings("unchecked")
        WorkSessionRunControlPort<String, String> runs = mock(WorkSessionRunControlPort.class);
        WorkSessionLocalCancellationPort local = mock(WorkSessionLocalCancellationPort.class);
        @SuppressWarnings("unchecked")
        WorkSessionResumeSchedulerPort<String> scheduler = mock(WorkSessionResumeSchedulerPort.class);
        WorkSessionRunControlUseCase<String, String> useCase =
                new WorkSessionRunControlUseCase<>(runs, local, scheduler);
        when(runs.requestCancelForActor("run-1", "alice", "stop")).thenReturn(true);

        assertTrue(useCase.requestCancel("run-1", "", "alice", "stop"));

        verify(runs).requestCancelForActor("run-1", "alice", "stop");
        verify(local).markCanceled("run-1");
    }

    @Test
    void localCancelValidatesRunIdAndMarksRegistry() {
        @SuppressWarnings("unchecked")
        WorkSessionRunControlPort<String, String> runs = mock(WorkSessionRunControlPort.class);
        WorkSessionLocalCancellationPort local = mock(WorkSessionLocalCancellationPort.class);
        @SuppressWarnings("unchecked")
        WorkSessionResumeSchedulerPort<String> scheduler = mock(WorkSessionResumeSchedulerPort.class);
        WorkSessionRunControlUseCase<String, String> useCase =
                new WorkSessionRunControlUseCase<>(runs, local, scheduler);

        assertTrue(useCase.cancelLocal(" run-1 "));
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.cancelLocal(" "));

        verify(local).markCanceled("run-1");
        assertEquals("runId 不能为空", error.getMessage());
    }
}
