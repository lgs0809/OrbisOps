package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.trigger.http.sse.OpsSseExecutionTemplate;
import cn.lgs.orbisops.trigger.http.sse.OpsSseStreamSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpsGraphEventSseService {

    private final GraphEventApplicationService graphEventService;
    private final OpsSseExecutionTemplate sseTemplate;

    public OpsGraphEventSseService(
            GraphEventApplicationService graphEventService,
            OpsSseExecutionTemplate sseTemplate) {
        if (graphEventService == null) {
            throw new IllegalArgumentException("GRAPH_EVENT_APPLICATION_SERVICE_REQUIRED");
        }
        if (sseTemplate == null) {
            throw new IllegalArgumentException("SSE_EXECUTION_TEMPLATE_REQUIRED");
        }
        this.graphEventService = graphEventService;
        this.sseTemplate = sseTemplate;
    }

    public SseEmitter stream(String runId, HttpServletResponse response) {
        OpsSseStreamSession session = sseTemplate.open(
                response,
                0L,
                OpsSseExecutionTemplate.Lifecycle.defaults());
        Set<Long> sentSequences = ConcurrentHashMap.newKeySet();
        AutoCloseable subscription = graphEventService.subscribe(event -> {
            if (session.isOpen() && matches(runId, event)) {
                sendOnce(session, event, sentSequences);
            }
        });
        session.registerResource(subscription);
        for (GraphEvent event : graphEventService.list(runId)) {
            if (!sendOnce(session, event, sentSequences)) break;
        }
        return session.emitter();
    }

    private boolean matches(String runId, GraphEvent event) {
        return StringUtils.hasText(runId)
                && (runId.equals(event.runId()) || runId.equals(event.analysisId()));
    }

    private boolean sendOnce(
            OpsSseStreamSession session,
            GraphEvent event,
            Set<Long> sentSequences) {
        Long sequence = event.sequence();
        if (sequence != null && !sentSequences.add(sequence)) return true;
        return session.trySend(SseEmitter.event()
                .id(String.valueOf(event.sequence()))
                .name(event.eventType())
                .data(event));
    }
}
