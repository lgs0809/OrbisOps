package cn.lgs.orbisops.application.toolset;

import cn.lgs.orbisops.domain.toolset.service.LocalRedisPolicy;

import java.util.List;

/** Application service for bounded read-only Redis diagnostics. */
public class LocalRedisApplicationService {

    private final LocalRedisExecutionPort executionPort;
    private final LocalRedisPolicy policy;

    public LocalRedisApplicationService(LocalRedisExecutionPort executionPort) {
        this(executionPort, new LocalRedisPolicy());
    }

    LocalRedisApplicationService(
            LocalRedisExecutionPort executionPort,
            LocalRedisPolicy policy) {
        if (executionPort == null || policy == null) {
            throw new IllegalArgumentException(
                    "LOCAL_REDIS_DEPENDENCY_REQUIRED");
        }
        this.executionPort = executionPort;
        this.policy = policy;
    }

    public String info() {
        return text(executionPort.info());
    }

    public String get(Object key) {
        return text(executionPort.get(policy.requiredReadKey(
                key,
                "redis_get 必须提供 key")));
    }

    public long ttl(Object key) {
        Long ttl = executionPort.ttl(policy.requiredReadKey(
                key,
                "redis_ttl 必须提供 key"));
        return ttl == null ? -2L : ttl;
    }

    public String type(Object key) {
        return text(executionPort.type(policy.requiredReadKey(
                key,
                "redis_type 必须提供 key")));
    }

    public List<String> scan(Object pattern, Object limit, int maxRows) {
        String controlledPattern = policy.scanPattern(pattern);
        int boundedLimit = policy.limit(limit, maxRows, 50);
        List<String> keys = executionPort.scan(
                controlledPattern,
                boundedLimit);
        return keys == null
                ? List.of()
                : keys.stream()
                .filter(key -> key != null)
                .limit(boundedLimit)
                .toList();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
