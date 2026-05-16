package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpToolProviderDecompositionTest {

    @Test
    void compatibilityFacadeDelegatesEachResponsibilityToDedicatedService() {
        OpsMcpToolCallbackAssembler assembler = mock(OpsMcpToolCallbackAssembler.class);
        OpsProgressiveMcpInvocationService progressive = mock(OpsProgressiveMcpInvocationService.class);
        OpsMcpRuntimeInvoker runtime = mock(OpsMcpRuntimeInvoker.class);
        OpsMcpToolProvider provider = new OpsMcpToolProvider(assembler, progressive, runtime);
        OpsMcpServerConfig config = config();
        ToolCallback callback = mock(ToolCallback.class);

        when(assembler.assemble(List.of(config))).thenReturn(List.of(callback));
        when(progressive.invoke(eq(config), eq("{}"), any())).thenReturn("ok");
        when(runtime.inspectDefinition(config, "search_logs"))
                .thenReturn(Map.of("toolName", "search_logs"));
        when(runtime.inspectDefinitions(config))
                .thenReturn(List.of(Map.of("toolName", "search_logs")));

        assertEquals(List.of(callback), provider.buildToolCallbacks(List.of(config)));
        assertEquals("ok", provider.callProgressiveDirect(config, "{}"));
        assertEquals("search_logs",
                provider.inspectRemoteToolDefinition(config, "search_logs").get("toolName"));
        assertEquals(1, provider.inspectRemoteToolDefinitions(config).size());
        provider.invalidateAll();

        verify(assembler).assemble(List.of(config));
        verify(progressive).invoke(eq(config), eq("{}"), any());
        verify(runtime).inspectDefinition(config, "search_logs");
        verify(runtime).inspectDefinitions(config);
        verify(runtime).invalidateAll();
    }

    @Test
    void runtimeInvokerDelegatesSessionProtocolAndInvalidationBoundaries() {
        OpsMcpRemoteClientAdapter clients = mock(OpsMcpRemoteClientAdapter.class);
        OpsMcpRemoteInvocationAdapter invocations = mock(OpsMcpRemoteInvocationAdapter.class);
        OpsMcpRuntimeInvoker runtime = new OpsMcpRuntimeInvoker(clients, invocations);
        OpsMcpServerConfig config = config();
        OpsMcpRemoteClientAdapter.Session session = mock(OpsMcpRemoteClientAdapter.Session.class);
        ToolCallback callback = mock(ToolCallback.class);

        when(clients.open(config)).thenReturn(session);
        when(clients.find(config, "search_logs")).thenReturn(callback);
        when(invocations.serialized(session, callback)).thenReturn(callback);
        when(invocations.invoke(config, "search_logs", "{}" )).thenReturn("remote-ok");
        when(clients.inspectDefinition(config, "search_logs"))
                .thenReturn(Map.of("toolName", "search_logs"));
        when(clients.inspectDefinitions(config))
                .thenReturn(List.of(Map.of("toolName", "search_logs")));
        when(invocations.summarizeInput("{}" )).thenReturn("{}");

        assertEquals(session, runtime.open(config));
        assertEquals(callback, runtime.find(config, "search_logs"));
        assertEquals(callback, runtime.serialized(session, callback));
        assertEquals("remote-ok", runtime.invoke(config, "search_logs", "{}"));
        assertEquals("search_logs", runtime.inspectDefinition(config, "search_logs").get("toolName"));
        assertEquals(1, runtime.inspectDefinitions(config).size());
        assertEquals("{}", runtime.summarizeInput("{}"));
        runtime.invalidateAll();

        verify(clients).invalidateAll();
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder()
                .name("ops-mcp")
                .projectId("project-1")
                .mcpId("mcp-1")
                .allowedTools(List.of("search_logs"))
                .build();
    }
}
