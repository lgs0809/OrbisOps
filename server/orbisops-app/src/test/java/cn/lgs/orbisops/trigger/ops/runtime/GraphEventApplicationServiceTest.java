package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.adapter.repository.IGraphEventRepository;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEventDraft;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphEventApplicationServiceTest {

    @Test
    void persistedAppendOwnsSequenceAndIsPublishedToSubscribers() {
        FakeRepository repository = new FakeRepository();
        repository.persist = true;
        GraphEventApplicationService service = new GraphEventApplicationService(repository, 10);
        List<GraphEvent> observed = new ArrayList<>();
        service.subscribe(observed::add);

        GraphEvent event = service.publish("run-1", "analysis-1", "NODE_FINISHED", null,
                "SUCCEEDED", "done", null, null, 3L, Map.of("step", 1));

        assertEquals(1L, event.sequence());
        assertEquals(List.of(event), observed);
        assertEquals(List.of(event), service.list("run-1"));
    }

    @Test
    void unavailableRepositoryUsesPerScopeFallbackSequenceAndBoundedMemory() {
        FakeRepository repository = new FakeRepository();
        GraphEventApplicationService service = new GraphEventApplicationService(repository, 2);

        GraphEvent first = service.publishRunEvent("run-1", "analysis-1", "RUN_STARTED", "RUNNING", "start");
        GraphEvent second = service.publishRunEvent("run-1", "analysis-1", "NODE_STARTED", "RUNNING", "node");
        GraphEvent third = service.publishRunEvent("run-1", "analysis-1", "RUN_FINISHED", "SUCCEEDED", "done");
        GraphEvent other = service.publishRunEvent("run-2", "analysis-2", "RUN_STARTED", "RUNNING", "start");

        assertEquals(1L, first.sequence());
        assertEquals(2L, second.sequence());
        assertEquals(3L, third.sequence());
        assertEquals(1L, other.sequence());
        assertEquals(List.of(second, third), service.list("run-1"));
    }

    @Test
    void repositoryFailureDoesNotPolluteMemoryOrNotifySubscribers() {
        FakeRepository repository = new FakeRepository();
        repository.failure = new IllegalStateException("database unavailable");
        GraphEventApplicationService service = new GraphEventApplicationService(repository, 10);
        AtomicInteger notifications = new AtomicInteger();
        service.subscribe(event -> notifications.incrementAndGet());

        assertThrows(IllegalStateException.class,
                () -> service.publishRunEvent("run-1", "analysis-1", "RUN_STARTED", "RUNNING", "start"));
        repository.failure = null;

        assertTrue(service.list("run-1").isEmpty());
        assertEquals(0, notifications.get());
    }

    @Test
    void subscriberFailureDoesNotAffectDurablePublication() {
        FakeRepository repository = new FakeRepository();
        repository.persist = true;
        GraphEventApplicationService service = new GraphEventApplicationService(repository, 10);
        AtomicInteger successfulSubscriber = new AtomicInteger();
        service.subscribe(event -> {
            throw new IllegalStateException("subscriber failed");
        });
        service.subscribe(event -> successfulSubscriber.incrementAndGet());

        GraphEvent event = service.publishRunEvent(
                "run-1", "analysis-1", "RUN_FINISHED", "SUCCEEDED", "done");

        assertEquals(1, successfulSubscriber.get());
        assertEquals(List.of(event), repository.events);
    }

    private static final class FakeRepository implements IGraphEventRepository {
        private final List<GraphEvent> events = new ArrayList<>();
        private boolean persist;
        private RuntimeException failure;

        @Override
        public Optional<GraphEvent> append(GraphEventDraft draft) {
            if (failure != null) throw failure;
            if (!persist) return Optional.empty();
            long sequence = events.stream()
                    .filter(event -> event.sequenceKey().equals(draft.sequenceKey()))
                    .mapToLong(GraphEvent::sequence)
                    .max()
                    .orElse(0L) + 1L;
            GraphEvent event = draft.materialize(sequence);
            events.add(event);
            return Optional.of(event);
        }

        @Override
        public List<GraphEvent> list(String scopeId, long afterSequence, int limit) {
            if (failure != null) throw failure;
            if (!persist) return List.of();
            return events.stream()
                    .filter(event -> scopeId.equals(event.runId()) || scopeId.equals(event.analysisId()))
                    .filter(event -> event.sequence() > afterSequence)
                    .limit(limit)
                    .toList();
        }
    }
}
