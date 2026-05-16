package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpRemoteInvocationAdapterTest {

    @Test
    void invokeMustSerializeRemoteCallRedactInputAndAuditSuccess() {
        OpsMcpRemoteClientAdapter remoteClientAdapter = mock(OpsMcpRemoteClientAdapter.class);
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        OpsMcpClientRegistry.ClientHandle handle = handle(config());
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("search_logs")
                .description("Search logs")
                .inputSchema("{\"type\":\"object\"}")
                .build());
        when(callback.call("{\"token\":\"secret\",\"limit\":10}"))
                .thenReturn("remote-result");
        OpsMcpRemoteClientAdapter.Session session =
                new OpsMcpRemoteClientAdapter.Session(handle, new ToolCallback[]{callback});
        when(remoteClientAdapter.open(config())).thenReturn(session);
        when(remoteClientAdapter.find(session, "search_logs")).thenReturn(callback);
        OpsMcpRemoteInvocationAdapter adapter = new OpsMcpRemoteInvocationAdapter(
                remoteClientAdapter,
                () -> processManager);

        String result = adapter.invoke(
                config(),
                "search_logs",
                "{\"token\":\"secret\",\"limit\":10}");

        assertEquals("remote-result", result);
        verify(processManager).recordMcpCall(argThat((McpCommands.RuntimeCall call) ->
                "SUCCEEDED".equals(call.status())
                        && "search_logs".equals(call.toolName())
                        && String.valueOf(call.input()).contains("<redacted>")
                        && !String.valueOf(call.input()).contains("secret")));
        verify(handle, atLeastOnce()).touch();
    }

    @Test
    void serializedCallbackMustDropLocalToolContextAndAuditFailure() {
        OpsMcpRemoteClientAdapter remoteClientAdapter = mock(OpsMcpRemoteClientAdapter.class);
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        OpsMcpClientRegistry.ClientHandle handle = handle(config());
        ToolCallback delegate = mock(ToolCallback.class);
        ToolContext context = mock(ToolContext.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("update_config")
                .description("Update config")
                .inputSchema("{\"type\":\"object\"}")
                .build());
        when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());
        when(delegate.call("{\"value\":1}"))
                .thenThrow(new SecurityException("blocked"));
        OpsMcpRemoteClientAdapter.Session session =
                new OpsMcpRemoteClientAdapter.Session(handle, new ToolCallback[]{delegate});
        OpsMcpRemoteInvocationAdapter adapter = new OpsMcpRemoteInvocationAdapter(
                remoteClientAdapter,
                () -> processManager);

        ToolCallback serialized = adapter.serialized(session, delegate);
        SecurityException error = assertThrows(SecurityException.class,
                () -> serialized.call("{\"value\":1}", context));

        assertEquals("blocked", error.getMessage());
        verify(delegate).call("{\"value\":1}");
        verify(delegate, never()).call("{\"value\":1}", context);
        verify(processManager).recordMcpCall(argThat((McpCommands.RuntimeCall call) ->
                "FAILED".equals(call.status())
                        && "update_config".equals(call.toolName())
                        && "blocked".equals(call.output())));
    }

    @Test
    void authorityMustBeRecheckedAfterWaitingForSessionLock() {
        OpsMcpRemoteClientAdapter remoteClientAdapter = mock(OpsMcpRemoteClientAdapter.class);
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        OpsMcpServerConfig config = config();
        config.setAuthorityDeadline(Instant.now().plusSeconds(60));
        OpsMcpClientRegistry.ClientHandle handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        ReentrantLock expiringLock = new ReentrantLock() {
            @Override
            public boolean tryLock(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
                boolean acquired = super.tryLock(timeout, unit);
                if (acquired) config.setAuthorityDeadline(Instant.now().minusSeconds(1));
                return acquired;
            }
        };
        when(handle.lock()).thenReturn(expiringLock);
        when(handle.config()).thenReturn(config);
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("search_logs")
                .description("Search logs")
                .inputSchema("{\"type\":\"object\"}")
                .build());
        OpsMcpRemoteClientAdapter.Session session =
                new OpsMcpRemoteClientAdapter.Session(handle, new ToolCallback[]{delegate});
        OpsMcpRemoteInvocationAdapter adapter = new OpsMcpRemoteInvocationAdapter(
                remoteClientAdapter,
                () -> processManager);

        SecurityException error = assertThrows(SecurityException.class,
                () -> adapter.serialized(session, delegate).call("{}"));

        assertTrue(error.getMessage().contains("EXPIRED_AFTER_MCP_LOCK"));
        verify(delegate, never()).call("{}");
    }

    @Test
    void inputSummaryMustRemainShapeOnlyAndAuditFailureMustNotBreakInvocationError() {
        OpsMcpRemoteClientAdapter remoteClientAdapter = mock(OpsMcpRemoteClientAdapter.class);
        OpsMcpRemoteInvocationAdapter adapter = new OpsMcpRemoteInvocationAdapter(
                remoteClientAdapter,
                () -> {
                    throw new IllegalStateException("audit unavailable");
                });
        OpsMcpClientRegistry.ClientHandle handle = handle(config());
        ToolCallback delegate = callback("search_logs", "ok");
        OpsMcpRemoteClientAdapter.Session session =
                new OpsMcpRemoteClientAdapter.Session(handle, new ToolCallback[]{delegate});

        String summary = adapter.summarizeInput(
                "{\"password\":\"p\",\"tags\":[1,2],\"filter\":{\"env\":\"prod\"},\"query\":\"abc\"}");
        String result = adapter.serialized(session, delegate).call("{}");

        assertTrue(summary.contains("<redacted>"));
        assertTrue(summary.contains("list(size=2)"));
        assertTrue(summary.contains("object(keys=[env])"));
        assertTrue(summary.contains("text(chars=3)"));
        assertEquals("ok", result);
        assertEquals("{unparsedChars=8}", adapter.summarizeInput("not-json"));
    }

    private OpsMcpClientRegistry.ClientHandle handle(OpsMcpServerConfig config) {
        OpsMcpClientRegistry.ClientHandle handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        when(handle.lock()).thenReturn(new ReentrantLock());
        when(handle.config()).thenReturn(config);
        return handle;
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder()
                .name("ops-mcp")
                .projectId("project-1")
                .runId("run-1")
                .agentId("agent-1")
                .nodeId("node-1")
                .mcpId("mcp-1")
                .toolId("tool-1")
                .build();
    }

    private ToolCallback callback(String name, String result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(name)
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                return result;
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return result;
            }
        };
    }
}
