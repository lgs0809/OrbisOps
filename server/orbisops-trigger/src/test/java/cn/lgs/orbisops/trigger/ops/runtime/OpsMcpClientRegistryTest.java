package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OpsMcpClientRegistryTest {

    @Test
    void sameCacheKeyMustReuseClientAndInitializeOnlyOnce() {
        OpsMcpClientRegistry registry = new OpsMcpClientRegistry(
                new OpsMcpClientCacheSettings(600L, 60_000L));
        McpSyncClient client = mock(McpSyncClient.class);
        AtomicInteger created = new AtomicInteger();
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("search-mcp")
                .projectId("project-1")
                .mcpId("mcp-1")
                .build();

        OpsMcpClientRegistry.ClientHandle first = registry.acquire(
                "cache-key", config, () -> {
                    created.incrementAndGet();
                    return client;
                });
        OpsMcpClientRegistry.ClientHandle second = registry.acquire(
                "cache-key", config, () -> {
                    created.incrementAndGet();
                    return client;
                });
        registry.initialize(first);
        registry.initialize(second);

        assertSame(first, second);
        org.junit.jupiter.api.Assertions.assertEquals(1, created.get());
        verify(client, times(1)).initialize();
    }

    @Test
    void invalidateAllMustCloseCachedClients() {
        OpsMcpClientRegistry registry = new OpsMcpClientRegistry(
                new OpsMcpClientCacheSettings(600L, 60_000L));
        McpSyncClient client = mock(McpSyncClient.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .name("search-mcp")
                .projectId("project-1")
                .mcpId("mcp-1")
                .build();
        registry.acquire("cache-key", config, () -> client);

        registry.invalidateAll();

        verify(client).close();
    }
}
