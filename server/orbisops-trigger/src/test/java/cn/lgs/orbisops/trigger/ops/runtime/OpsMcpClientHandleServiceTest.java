package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OpsMcpClientHandleServiceTest {

    @Test
    void initializationFailureResetsStateAndAllowsRetry() {
        McpSyncClient client = mock(McpSyncClient.class);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("init failed");
            }
            return null;
        }).when(client).initialize();
        OpsMcpClientRegistry.ClientHandle handle =
                new OpsMcpClientRegistry.ClientHandle(client, config(), 1_000L);
        OpsMcpClientHandleService service = new OpsMcpClientHandleService();

        assertThrows(IllegalStateException.class, () -> service.initialize(handle));
        service.initialize(handle);

        verify(client, times(2)).initialize();
    }

    @Test
    void nullHandleFailsWithStableCode() {
        OpsMcpClientHandleService service = new OpsMcpClientHandleService();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.initialize(null));
        org.junit.jupiter.api.Assertions.assertEquals(
                "MCP_CLIENT_HANDLE_REQUIRED",
                error.getMessage());
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder()
                .name("search-mcp")
                .projectId("project-1")
                .mcpId("mcp-1")
                .build();
    }
}
