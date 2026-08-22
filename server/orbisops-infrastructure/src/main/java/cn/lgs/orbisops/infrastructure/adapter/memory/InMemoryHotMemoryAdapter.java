package cn.lgs.orbisops.infrastructure.adapter.memory;

import cn.lgs.orbisops.application.memory.HotMemoryClearPort;
import cn.lgs.orbisops.application.memory.HotMemoryQueryPort;
import cn.lgs.orbisops.application.memory.HotMemoryReplacePort;
import cn.lgs.orbisops.application.memory.HotMemoryWritePort;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory hot-message window used when Redis is not selected. */
@Repository
@ConditionalOnProperty(
        prefix = "orbisops.chat.memory",
        name = "hot-store",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryHotMemoryAdapter implements
        HotMemoryWritePort,
        HotMemoryQueryPort,
        HotMemoryReplacePort,
        HotMemoryClearPort {

    private final Map<String, SessionBucket> sessions =
            new ConcurrentHashMap<>();
    private final long ttlMinutes;

    public InMemoryHotMemoryAdapter(
            @Value("${orbisops.chat.memory.hot-ttl-minutes:30}")
            long ttlMinutes) {
        this.ttlMinutes = Math.max(1L, ttlMinutes);
    }

    @Override
    public void append(MemoryMessageView message, int bufferSize) {
        if (message == null || text(message.sessionId()).isBlank()) return;
        long now = System.currentTimeMillis();
        SessionBucket bucket = sessions.computeIfAbsent(
                message.sessionId(),
                ignored -> new SessionBucket(now));
        synchronized (bucket) {
            if (bucket.expired(now, ttlMinutes)) bucket.messages.clear();
            bucket.touch(now);
            bucket.messages.addLast(message);
            trim(bucket.messages, bufferSize);
        }
    }

    @Override
    public List<MemoryMessageView> recent(String sessionId, int limit) {
        String session = text(sessionId);
        if (session.isBlank()) return List.of();
        SessionBucket bucket = sessions.get(session);
        if (bucket == null) return List.of();
        long now = System.currentTimeMillis();
        synchronized (bucket) {
            if (bucket.expired(now, ttlMinutes)) {
                sessions.remove(session, bucket);
                return List.of();
            }
            bucket.touch(now);
            List<MemoryMessageView> snapshot =
                    new ArrayList<>(bucket.messages);
            int from = Math.max(
                    0,
                    snapshot.size() - Math.max(1, limit));
            return List.copyOf(snapshot.subList(from, snapshot.size()));
        }
    }

    @Override
    public void replace(
            String sessionId,
            List<ColdMemoryMessageSnapshot> messages,
            int maxMessages) {
        String session = text(sessionId);
        if (session.isBlank()) return;
        long now = System.currentTimeMillis();
        SessionBucket bucket = sessions.computeIfAbsent(
                session,
                ignored -> new SessionBucket(now));
        synchronized (bucket) {
            bucket.messages.clear();
            if (messages != null) {
                messages.stream()
                        .filter(message -> message != null)
                        .map(this::view)
                        .forEach(bucket.messages::addLast);
            }
            bucket.touch(now);
            trim(bucket.messages, maxMessages);
        }
    }

    @Override
    public void clear(String sessionId) {
        String session = text(sessionId);
        if (!session.isBlank()) sessions.remove(session);
    }

    private MemoryMessageView view(ColdMemoryMessageSnapshot message) {
        return new MemoryMessageView(
                message.sessionId(),
                message.userId(),
                message.role(),
                message.content(),
                message.createdAt(),
                message.metadata());
    }

    private void trim(Deque<MemoryMessageView> messages, int limit) {
        int bounded = Math.max(2, limit);
        while (messages.size() > bounded) messages.removeFirst();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SessionBucket {
        private final Deque<MemoryMessageView> messages = new ArrayDeque<>();
        private long lastAccessAt;

        private SessionBucket(long now) {
            this.lastAccessAt = now;
        }

        private void touch(long now) {
            this.lastAccessAt = now;
        }

        private boolean expired(long now, long ttlMinutes) {
            return now - lastAccessAt > ttlMinutes * 60_000L;
        }
    }
}
