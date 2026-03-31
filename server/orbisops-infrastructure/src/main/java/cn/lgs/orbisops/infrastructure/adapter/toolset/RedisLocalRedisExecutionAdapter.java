package cn.lgs.orbisops.infrastructure.adapter.toolset;

import cn.lgs.orbisops.application.toolset.LocalRedisExecutionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** StringRedisTemplate implementation of local read-only Redis diagnostics. */
@Repository
public class RedisLocalRedisExecutionAdapter
        implements LocalRedisExecutionPort {

    private final StringRedisTemplate redisTemplate;

    public RedisLocalRedisExecutionAdapter(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    public String info() {
        StringRedisTemplate template = requiredTemplate();
        if (template.getConnectionFactory() == null) {
            throw new IllegalStateException("Redis ConnectionFactory 未初始化");
        }
        try (RedisConnection connection =
                     template.getConnectionFactory().getConnection()) {
            Object info = connection.serverCommands().info();
            return info == null ? "" : info.toString();
        }
    }

    @Override
    public String get(String key) {
        return requiredTemplate().opsForValue().get(key);
    }

    @Override
    public Long ttl(String key) {
        return requiredTemplate().getExpire(key);
    }

    @Override
    public String type(String key) {
        return String.valueOf(requiredTemplate().type(key));
    }

    @Override
    public List<String> scan(String pattern, int limit) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(Math.max(1, limit))
                .build();
        try (Cursor<String> cursor = requiredTemplate().scan(options)) {
            while (cursor.hasNext() && keys.size() < Math.max(1, limit)) {
                String key = cursor.next();
                if (key != null) keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    private StringRedisTemplate requiredTemplate() {
        if (redisTemplate == null) {
            throw new IllegalStateException("RedisTemplate 未初始化");
        }
        return redisTemplate;
    }
}
