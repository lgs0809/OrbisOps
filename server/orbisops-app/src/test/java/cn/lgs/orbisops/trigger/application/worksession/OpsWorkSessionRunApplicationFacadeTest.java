package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkSessionRunApplicationFacadeTest {

    @Test
    void actorEventQueryAuthorizesBeforeReadingJournal() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        OpsWorkSessionRunApplicationFacade facade = new OpsWorkSessionRunApplicationFacade(
                runs,
                new OpsRunCancellationRegistry(),
                request -> {
                },
                events);
        List<GraphEvent> expected = List.of(mock(GraphEvent.class));
        when(events.list("run-1", 7L, 20)).thenReturn(expected);

        List<GraphEvent> result = facade.eventsForActor(
                "run-1",
                7L,
                20,
                "project-1",
                "alice");

        assertEquals(expected, result);
        InOrder order = inOrder(runs, events);
        order.verify(runs).assertActorCanRead("run-1", "project-1", "alice");
        order.verify(events).list("run-1", 7L, 20);
    }

    @Test
    void resumeSchedulesAuthorizedRequestAndReturnsStableAcknowledgement() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1")
                .projectId("project-1")
                .build();
        when(runs.resumeRequest("run-1", "project-1", "alice")).thenReturn(request);
        List<OpsAgentChatRequest> scheduled = new ArrayList<>();
        OpsWorkSessionRunApplicationFacade facade = new OpsWorkSessionRunApplicationFacade(
                runs,
                new OpsRunCancellationRegistry(),
                scheduled::add,
                events);

        Map<String, Object> result = facade.resume("run-1", "project-1", "alice");

        assertEquals(List.of(request), scheduled);
        assertEquals("RESUME_SCHEDULED", result.get("status"));
        assertTrue(String.valueOf(result.get("message")).contains("安全检查点"));
    }

    @Test
    void durableCancelControlsLocalCancellationMarker() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        OpsRunCancellationRegistry registry = new OpsRunCancellationRegistry();
        when(runs.requestCancel("run-1", "project-1", "alice", "stop")).thenReturn(true);
        when(runs.requestCancel("run-2", "project-1", "alice", "stop")).thenReturn(false);
        OpsWorkSessionRunApplicationFacade facade = new OpsWorkSessionRunApplicationFacade(
                runs,
                registry,
                request -> {
                },
                events);

        assertTrue(facade.requestCancel("run-1", "project-1", "alice", "stop"));
        assertFalse(facade.requestCancel("run-2", "project-1", "alice", "stop"));

        assertTrue(registry.isCanceled("run-1"));
        assertFalse(registry.isCanceled("run-2"));
    }

    @Test
    void projectRunQueryVerifiesExistenceBeforeReadingEvents() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        GraphEventApplicationService events = mock(GraphEventApplicationService.class);
        when(runs.get("run-1", "project-1")).thenReturn(Map.of("runId", "run-1"));
        OpsWorkSessionRunApplicationFacade facade = new OpsWorkSessionRunApplicationFacade(
                runs,
                new OpsRunCancellationRegistry(),
                request -> {
                },
                events);

        facade.events("run-1", 0L, 10, "project-1");

        InOrder order = inOrder(runs, events);
        order.verify(runs).get("run-1", "project-1");
        order.verify(events).list("run-1", 0L, 10);
        verify(runs, never()).assertActorCanRead("run-1", "project-1", "");
    }
}
