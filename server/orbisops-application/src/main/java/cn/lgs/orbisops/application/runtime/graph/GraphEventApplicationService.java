package cn.lgs.orbisops.application.runtime.graph;

import cn.lgs.orbisops.domain.runtime.graph.adapter.repository.IGraphEventRepository;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEventDraft;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEventNodeDescriptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class GraphEventApplicationService {

    private final IGraphEventRepository repository;
    private final Map<String, Deque<GraphEvent>> memoryEvents = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<GraphEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicLong> fallbackRunSequences = new ConcurrentHashMap<>();
    private final int maxMemoryEvents;

    public GraphEventApplicationService(
            IGraphEventRepository repository,
            int maxMemoryEvents) {
        if (repository == null) throw new IllegalArgumentException("GRAPH_EVENT_REPOSITORY_REQUIRED");
        this.repository = repository;
        this.maxMemoryEvents = Math.max(1, maxMemoryEvents);
    }

    public GraphEvent publish(
            String runId,
            String analysisId,
            String eventType,
            GraphEventNodeDescriptor node,
            String status,
            String summary,
            String startedAt,
            String finishedAt,
            Long durationMs,
            Map<String, Object> payload) {
        GraphEventDraft draft = new GraphEventDraft(
                runId,
                analysisId,
                eventType,
                node == null ? null : node.graphNodeId(),
                node == null ? null : node.graphNodeType(),
                node == null ? null : node.graphAgent(),
                null,
                status,
                summary,
                startedAt,
                finishedAt,
                durationMs,
                payload);
        GraphEvent event = repository.append(draft)
                .orElseGet(() -> draft.materialize(fallbackSequence(draft.sequenceKey())));
        appendMemory(event);
        notifySubscribers(event);
        return event;
    }

    public GraphEvent publishRunEvent(
            String runId,
            String analysisId,
            String eventType,
            String status,
            String summary) {
        return publish(runId, analysisId, eventType, null, status, summary,
                null, null, null, Map.of());
    }

    public List<GraphEvent> list(String scopeId) {
        return list(scopeId, 0L, 1000);
    }

    public List<GraphEvent> list(String scopeId, long afterSequence, int limit) {
        String scope = text(scopeId);
        if (scope.isBlank()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 2000));
        long safeAfter = Math.max(0L, afterSequence);
        List<GraphEvent> persisted = repository.list(scope, safeAfter, safeLimit);
        if (persisted != null && !persisted.isEmpty()) return List.copyOf(persisted);
        Deque<GraphEvent> events = memoryEvents.get(scope);
        if (events == null) return List.of();
        synchronized (events) {
            return events.stream()
                    .filter(event -> event.sequence() > safeAfter)
                    .limit(safeLimit)
                    .toList();
        }
    }

    public AutoCloseable subscribe(Consumer<GraphEvent> subscriber) {
        if (subscriber == null) return () -> {
        };
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    private long fallbackSequence(String key) {
        return fallbackRunSequences.computeIfAbsent(key, ignored -> new AtomicLong())
                .incrementAndGet();
    }

    private void appendMemory(GraphEvent event) {
        String key = event.sequenceKey();
        if (key.isBlank()) return;
        Deque<GraphEvent> events = memoryEvents.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (events) {
            events.addLast(event);
            while (events.size() > maxMemoryEvents) events.removeFirst();
        }
    }

    private void notifySubscribers(GraphEvent event) {
        for (Consumer<GraphEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException ignored) {
                // Subscriber failure must not affect durable publication.
            }
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
