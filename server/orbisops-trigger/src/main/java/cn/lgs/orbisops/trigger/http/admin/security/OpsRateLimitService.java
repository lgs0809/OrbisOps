package cn.lgs.orbisops.trigger.http.admin.security;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory request limiter for admin/user API protection.
 * It is intentionally local to this node; production clusters can replace it
 * with Redis without changing controller contracts.
 */
@Service
public class OpsRateLimitService {

    private final OpsRateLimitSettings settings;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public OpsRateLimitService(OpsRateLimitSettings settings) {
        if (settings == null) throw new IllegalArgumentException("OPS_RATE_LIMIT_SETTINGS_REQUIRED");
        this.settings = settings;
    }

    public boolean tryAcquire(String username, String path) {
        if (!settings.enabled()) {
            return true;
        }
        cleanup();
        String safeUser = StringUtils.hasText(username) ? username.trim() : "anonymous";
        String safePath = StringUtils.hasText(path) ? path.trim() : "unknown";
        return consume("user:" + safeUser, settings.perUserPerMinute())
                && consume("path:" + safeUser + ":" + safePath, settings.perAgentPathPerMinute());
    }

    private boolean consume(String key, int limit) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowMinute != currentMinute) {
                return new Bucket(currentMinute, new AtomicInteger(0));
            }
            return existing;
        });
        return bucket.count.incrementAndGet() <= limit;
    }

    private void cleanup() {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        if (buckets.size() < 10_000) {
            return;
        }
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Bucket> entry = iterator.next();
            if (currentMinute - entry.getValue().windowMinute > 2) {
                iterator.remove();
            }
        }
    }

    private record Bucket(long windowMinute, AtomicInteger count) {
    }
}
