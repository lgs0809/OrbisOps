package cn.lgs.orbisops.trigger.http.sse;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.trigger.http.admin.OpsGraphEventSseService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsGraphEventSseServiceTest {

    @Test
    void graphSubscriptionUsesSharedSessionDeduplicatesAndClosesOnce() throws Exception {
        GraphEventApplicationService graphEvents = mock(GraphEventApplicationService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AutoCloseable subscription = mock(AutoCloseable.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<GraphEvent>> subscriber = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        when(graphEvents.subscribe(subscriber.capture())).thenReturn(subscription);
        GraphEvent first = event(1L);
        when(graphEvents.list("run-1")).thenReturn(List.of(first, first));
        OpsSseExecutionTemplate template = new OpsSseExecutionTemplate(
                new OpsSseEventWriter(), timeout -> emitter);
        OpsGraphEventSseService service = new OpsGraphEventSseService(graphEvents, template);

        service.stream("run-1", response);
        subscriber.getValue().accept(first);
        subscriber.getValue().accept(event(2L));
        verify(emitter).onCompletion(completion.capture());
        completion.getValue().run();
        completion.getValue().run();

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(subscription, times(1)).close();
        verify(response).setHeader("X-Accel-Buffering", "no");
    }

    private GraphEvent event(long sequence) {
        return new GraphEvent(
                "run-1", "analysis-1", sequence, "NODE_EVENT",
                "node-1", "TOOL", "agent-1", "runtime",
                "RUNNING", "step", "", "", null, Map.of());
    }
}
