package cn.lgs.orbisops.infrastructure.adapter.memory;

import cn.lgs.orbisops.application.memory.HotMemoryClearPort;
import cn.lgs.orbisops.application.memory.HotMemoryQueryPort;
import cn.lgs.orbisops.application.memory.HotMemoryReplacePort;
import cn.lgs.orbisops.application.memory.HotMemoryWritePort;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Redis implementation of the bounded hot-message window. */
@Slf4j
@Repository
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(
        prefix = "orbisops.chat.memory",
        name = "hot-store",
        havingValue = "redis")
public class RedisHotMemoryAdapter implements
        HotMemoryWritePort,
        HotMemoryQueryPort,
        HotMemoryReplacePort,
        HotMemoryClearPort {

    private final StringRedisTemplate redisTemplate;
    private final long ttlMinutes;

    public RedisHotMemoryAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${orbisops.chat.memory.hot-ttl-minutes:30}")
            long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttlMinutes = Math.max(1L, ttlMinutes);
    }

    @Override
    public void append(MemoryMessageView message, int bufferSize) {
        if (message == null || text(message.sessionId()).isBlank()) return;
        String key = key(message.sessionId());
        try {
            redisTemplate.opsForList().rightPush(key, encode(message));
            redisTemplate.opsForList().trim(
                    key,
                    -Math.max(2, bufferSize),
                    -1);
            redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
        } catch (RuntimeException error) {
            log.warn("Redis 热记忆写入失败：{}", error.getMessage());
        }
    }

    @Override
    public List<MemoryMessageView> recent(String sessionId, int limit) {
        String session = text(sessionId);
        if (session.isBlank()) return List.of();
        try {
            List<String> rows = redisTemplate.opsForList().range(
                    key(session),
                    -Math.max(1, limit),
                    -1);
            if (rows == null || rows.isEmpty()) return List.of();
            return rows.stream()
                    .map(this::decode)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException error) {
            log.warn("Redis 热记忆读取失败：{}", error.getMessage());
            return List.of();
        }
    }

    @Override
    public void replace(
            String sessionId,
            List<ColdMemoryMessageSnapshot> messages,
            int maxMessages) {
        String session = text(sessionId);
        if (session.isBlank()) return;
        String key = key(session);
        try {
            redisTemplate.delete(key);
            if (messages == null || messages.isEmpty()) return;
            List<String> rows = messages.stream()
                    .filter(message -> message != null)
                    .skip(Math.max(
                            0,
                            messages.size() - Math.max(2, maxMessages)))
                    .map(this::view)
                    .map(this::encode)
                    .toList();
            if (rows.isEmpty()) return;
            redisTemplate.opsForList().rightPushAll(key, rows);
            redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
        } catch (RuntimeException error) {
            log.warn("Redis 热记忆替换失败：{}", error.getMessage());
        }
    }

    @Override
    public void clear(String sessionId) {
        String session = text(sessionId);
        if (session.isBlank()) return;
        try {
            redisTemplate.delete(key(session));
        } catch (RuntimeException error) {
            log.warn("Redis 热记忆清理失败：{}", error.getMessage());
        }
    }

    private String encode(MemoryMessageView message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", text(message.sessionId()));
        row.put("userId", text(message.userId()));
        row.put("role", text(message.role()));
        row.put("content", text(message.content()));
        row.put("createdAt", text(message.createdAt()));
        row.put("metadata", message.metadata() == null
                ? Map.of()
                : message.metadata());
        return JSON.toJSONString(row);
    }

    private MemoryMessageView decode(String row) {
        String json = text(row);
        if (json.isBlank() || !json.startsWith("{")) return null;
        try {
            Map<String, Object> source = JSON.parseObject(json);
            if (source == null) return null;
            return new MemoryMessageView(
                    text(source.get("sessionId")),
                    text(source.get("userId")),
                    text(source.get("role")),
                    text(source.get("content")),
                    text(source.get("createdAt")),
                    metadata(source.get("metadata")));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Map<String, Object> metadata(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, field) -> {
            if (key != null && field != null) {
                result.put(String.valueOf(key), field);
            }
        });
        return result;
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

    private String key(String sessionId) {
        return "ops:chat:memory:" + sessionId;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
