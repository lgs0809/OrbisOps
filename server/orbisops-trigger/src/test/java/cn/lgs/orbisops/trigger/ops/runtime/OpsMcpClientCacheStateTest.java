package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsMcpClientCacheStateTest {

    @Test
    void reusesLiveHandleAndReplacesExpiredClient() {
        AtomicLong now = new AtomicLong(1_000L);
        OpsMcpClientCacheState state = new OpsMcpClientCacheState(
                new OpsMcpClientCacheSettings(1L, 60_000L),
                now::get);
        McpSyncClient firstClient = mock(McpSyncClient.class);
        McpSyncClient secondClient = mock(McpSyncClient.class);
        OpsMcpServerConfig config = config();

        OpsMcpClientRegistry.ClientHandle first =
                state.acquire("key", config, () -> firstClient);
        now.set(1_500L);
        assertSame(first, state.acquire("key", config, () -> secondClient));

        now.set(2_501L);
        OpsMcpClientRegistry.ClientHandle replacement =
                state.acquire("key", config, () -> secondClient);

        assertNotSame(first, replacement);
        assertSame(secondClient, replacement.client());
        verify(firstClient).close();
        assertEquals(1, state.size());
    }

    @Test
    void cleanupClosesOnlyExpiredEntries() {
        AtomicLong now = new AtomicLong(10_000L);
        OpsMcpClientCacheState state = new OpsMcpClientCacheState(
                new OpsMcpClientCacheSettings(1L, 60_000L),
                now::get);
        McpSyncClient client = mock(McpSyncClient.class);
        state.acquire("key", config(), () -> client);

        now.set(11_001L);

        assertEquals(1, state.cleanupExpired());
        assertEquals(0, state.size());
        verify(client).close();
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder()
                .name("search-mcp")
                .projectId("project-1")
                .mcpId("mcp-1")
                .build();
    }
}
