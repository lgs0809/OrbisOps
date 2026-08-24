package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Concurrent cache, expiration, replacement, and close owner for MCP clients. */
@Slf4j
final class OpsMcpClientCacheState {

    private final Map<String, OpsMcpClientRegistry.ClientHandle> clients =
            new ConcurrentHashMap<>();
    private final OpsMcpClientCacheSettings settings;
    private final LongSupplier clock;

    OpsMcpClientCacheState(OpsMcpClientCacheSettings settings) {
        this(settings, System::currentTimeMillis);
    }

    OpsMcpClientCacheState(
            OpsMcpClientCacheSettings settings,
            LongSupplier clock) {
        this.settings = settings == null ? OpsMcpClientCacheSettings.defaults() : settings;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    OpsMcpClientRegistry.ClientHandle acquire(
            String key,
            OpsMcpServerConfig config,
            Supplier<McpSyncClient> clientFactory) {
        long now = clock.getAsLong();
        OpsMcpClientRegistry.ClientHandle cached = clients.get(key);
        if (cached != null && !expired(cached, now)) {
            cached.touch(now);
            return cached;
        }
        if (cached != null && clients.remove(key, cached)) {
            closeQuietly(cached);
        }
        return clients.compute(key, (ignored, existing) -> {
            if (existing != null && !expired(existing, now)) {
                existing.touch(now);
                return existing;
            }
            if (existing != null) {
                closeQuietly(existing);
            }
            McpSyncClient client = clientFactory.get();
            if (client == null) {
                throw new IllegalStateException("MCP_CLIENT_FACTORY_RETURNED_NULL");
            }
            return new OpsMcpClientRegistry.ClientHandle(key, client, config, now);
        });
    }

    int invalidateAll() {
        int count = clients.size();
        clients.forEach((ignored, cached) -> invalidate(cached));
        return count;
    }

    boolean invalidate(OpsMcpClientRegistry.ClientHandle handle) {
        if (handle == null) return false;
        handle.invalidate();
        // A late failure from an old handle must not remove a replacement under the same key.
        if (!clients.remove(handle.cacheKey(), handle)) return false;
        closeQuietly(handle);
        return true;
    }

    int cleanupExpired() {
        int before = clients.size();
        long now = clock.getAsLong();
        clients.entrySet().removeIf(entry -> closeIfExpired(entry.getValue(), now));
        return Math.max(0, before - clients.size());
    }

    int size() {
        return clients.size();
    }

    private boolean closeIfExpired(
            OpsMcpClientRegistry.ClientHandle cached,
            long now) {
        if (cached == null || !expired(cached, now) || !cached.lock().tryLock()) {
            return false;
        }
        try {
            if (!expired(cached, now)) {
                return false;
            }
            cached.invalidate();
            closeQuietly(cached.client());
            return true;
        } finally {
            cached.lock().unlock();
        }
    }

    private boolean expired(
            OpsMcpClientRegistry.ClientHandle cached,
            long now) {
        return cached.invalidated() || now - cached.lastUsedAtMillis() > settings.ttlMillis();
    }

    private void closeQuietly(OpsMcpClientRegistry.ClientHandle cached) {
        if (cached == null) {
            return;
        }
        cached.invalidate();
        cached.lock().lock();
        try {
            cached.invalidate();
            closeQuietly(cached.client());
        } finally {
            cached.lock().unlock();
        }
    }

    private void closeQuietly(McpSyncClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception error) {
            log.debug("关闭 MCP 连接失败，已忽略：{}", error.getMessage());
        }
    }
}
