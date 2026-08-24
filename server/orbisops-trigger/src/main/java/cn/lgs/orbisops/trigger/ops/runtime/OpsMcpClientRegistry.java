package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Public MCP client lifecycle facade over cache state and handle operations. */
@Slf4j
@Service
public class OpsMcpClientRegistry {

    private final OpsMcpClientCacheState cacheState;
    private final OpsMcpClientHandleService handleService;

    public OpsMcpClientRegistry() {
        this(OpsMcpClientCacheSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsMcpClientRegistry(OpsMcpClientCacheSettings settings) {
        this(
                new OpsMcpClientCacheState(settings),
                new OpsMcpClientHandleService());
    }

    OpsMcpClientRegistry(
            OpsMcpClientCacheState cacheState,
            OpsMcpClientHandleService handleService) {
        this.cacheState = cacheState;
        this.handleService = handleService;
    }

    public ClientHandle acquire(
            String cacheKey,
            OpsMcpServerConfig config,
            Supplier<McpSyncClient> clientFactory) {
        String key = required(cacheKey, "MCP_CLIENT_CACHE_KEY_REQUIRED");
        if (config == null) {
            throw new IllegalArgumentException("MCP_CLIENT_CONFIG_REQUIRED");
        }
        if (clientFactory == null) {
            throw new IllegalArgumentException("MCP_CLIENT_FACTORY_REQUIRED");
        }
        return cacheState.acquire(key, config, clientFactory);
    }

    public void initialize(ClientHandle handle) {
        handleService.initialize(handle);
    }

    public ToolCallback[] toolCallbacks(ClientHandle handle) {
        return handleService.toolCallbacks(handle);
    }

    public void invalidateAll() {
        cacheState.invalidateAll();
        log.info("MCP 运行时连接缓存已全部失效");
    }

    public boolean invalidate(ClientHandle handle) { return cacheState.invalidate(handle); }

    public void cleanupExpiredClients() {
        cacheState.cleanupExpired();
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    public static final class ClientHandle {
        private final McpSyncClient client;
        private final OpsMcpServerConfig config;
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicBoolean initialized = new AtomicBoolean(false);
        private final AtomicBoolean invalidated = new AtomicBoolean(false);
        private final String cacheKey;
        private volatile long lastUsedAtMillis;

        ClientHandle(
                McpSyncClient client,
                OpsMcpServerConfig config,
                long lastUsedAtMillis) {
            this("", client, config, lastUsedAtMillis);
        }

        ClientHandle(String cacheKey, McpSyncClient client, OpsMcpServerConfig config, long lastUsedAtMillis) {
            this.cacheKey = cacheKey;
            this.client = client;
            this.config = config;
            this.lastUsedAtMillis = lastUsedAtMillis;
        }

        String cacheKey() { return cacheKey; }
        boolean invalidated() { return invalidated.get(); }
        void invalidate() { invalidated.set(true); initialized.set(false); }
        public void assertValid() {
            if (invalidated()) throw new OpsMcpCallFailure(
                    OpsMcpCallFailure.Kind.TRANSPORT_ERROR, "STALE_CLIENT_HANDLE", false);
        }

        public McpSyncClient client() {
            return client;
        }

        public OpsMcpServerConfig config() {
            return config;
        }

        public ReentrantLock lock() {
            return lock;
        }

        public long lastUsedAtMillis() {
            return lastUsedAtMillis;
        }

        public void touch() {
            touch(System.currentTimeMillis());
        }

        void touch(long now) {
            lastUsedAtMillis = now;
        }

        boolean initialized() {
            return initialized.get();
        }

        boolean markInitializing() {
            return initialized.compareAndSet(false, true);
        }

        void resetInitialized() {
            initialized.set(false);
        }
    }
}
