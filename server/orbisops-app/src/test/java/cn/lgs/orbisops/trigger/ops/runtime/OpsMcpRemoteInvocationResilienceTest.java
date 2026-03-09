package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpRemoteInvocationResilienceTest {

    @Test
    void successMustReturnRawResultButPersistOnlyRedactedAuditOutput() {
        OpsMcpRemoteClientAdapter clients = mock(OpsMcpRemoteClientAdapter.class);
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        OpsMcpClientRegistry.ClientHandle handle = handle();
        ToolCallback callback = mock(ToolCallback.class);
        String remoteResult = "{\"password\":\"value-1\",\"count\":2}";
        when(callback.getToolDefinition()).thenReturn(definition("query"));
        when(callback.call("{}")).thenReturn(remoteResult);
        OpsMcpRemoteInvocationAdapter adapter = adapter(clients, processManager, 3);

        String result = adapter.serialized(
                new OpsMcpRemoteClientAdapter.Session(handle, new ToolCallback[]{callback}),
                callback).call("{}");

        assertEquals(remoteResult, result);
        verify(processManager).recordMcpCall(argThat((McpCommands.RuntimeCall call) ->
                "SUCCEEDED".equals(call.status())
                        && String.valueOf(call.output()).contains("\"password\":\"***\"")
                        && !String.valueOf(call.output()).contains("value-1")));
    }

    @Test
    void thresholdFailureMustOpenCircuitAndRejectWithoutCallingRemoteAgain() {
        OpsMcpRemoteClientAdapter clients = mock(OpsMcpRemoteClientAdapter.class);
        ProgressiveMcpProcessManager processManager = mock(ProgressiveMcpProcessManager.class);
        OpsMcpClientRegistry.ClientHandle handle = handle();
        ToolCallback callback = mock(ToolCallback.class);
        AtomicInteger calls = new AtomicInteger();
        when(callback.getToolDefinition()).thenReturn(definition("query"));
        when(callback.call("{}")).thenAnswer(invocation -> {
            calls.incrementAndGet();
            throw new IllegalStateException("token=value-2");
        });
        OpsMcpRemoteInvocationAdapter adapter = adapter(clients, processManager, 1);
        ToolCallback resilient = adapter.serialized(
                new OpsMcpRemoteClientAdapter.Session(handle, new ToolCallback[]{callback}),
                callback);

        IllegalStateException first = assertThrows(
                IllegalStateException.class,
                () -> resilient.call("{}"));
        IllegalStateException second = assertThrows(
                IllegalStateException.class,
                () -> resilient.call("{}"));

        assertEquals("token=value-2", first.getMessage());
        assertTrue(second.getMessage().startsWith("MCP_CIRCUIT_OPEN:"));
        assertEquals(1, calls.get());
        verify(callback, times(1)).call("{}");
        verify(processManager).recordMcpCall(argThat((McpCommands.RuntimeCall call) ->
                "FAILED".equals(call.status())
                        && "token=***".equals(call.output())));
        verify(processManager).recordMcpCall(argThat((McpCommands.RuntimeCall call) ->
                "REJECTED".equals(call.status())));
    }

    private OpsMcpRemoteInvocationAdapter adapter(
            OpsMcpRemoteClientAdapter clients,
            ProgressiveMcpProcessManager processManager,
            int threshold) {
        OpsMcpRuntimeSloTelemetry telemetry =
                new OpsMcpRuntimeSloTelemetry((MeterRegistry) null);
        OpsMcpInvocationResiliencePolicy resilience =
                new OpsMcpInvocationResiliencePolicy(
                        threshold,
                        Duration.ofSeconds(30),
                        Clock.systemUTC(),
                        telemetry);
        return new OpsMcpRemoteInvocationAdapter(clients, () -> processManager, resilience);
    }

    private OpsMcpClientRegistry.ClientHandle handle() {
        OpsMcpClientRegistry.ClientHandle handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        when(handle.lock()).thenReturn(new ReentrantLock());
        when(handle.config()).thenReturn(OpsMcpServerConfig.builder()
                .name("ops-mcp")
                .mcpId("mcp-1")
                .projectId("project-1")
                .runId("run-1")
                .agentId("agent-1")
                .nodeId("node-1")
                .toolId("tool-1")
                .build());
        return handle;
    }

    private ToolDefinition definition(String name) {
        return ToolDefinition.builder()
                .name(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .build();
    }
}
