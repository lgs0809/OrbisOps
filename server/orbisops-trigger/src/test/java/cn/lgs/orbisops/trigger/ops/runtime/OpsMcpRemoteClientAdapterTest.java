package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpRemoteClientAdapterTest {

    @Test
    void openMustAcquireInitializeAndSnapshotCallbacksThroughRegistry() {
        OpsMcpClientRegistry registry = mock(OpsMcpClientRegistry.class);
        OpsMcpClientFactory factory = mock(OpsMcpClientFactory.class);
        OpsMcpClientRegistry.ClientHandle handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder().name("search-mcp").build();
        ToolCallback callback = callback("search_logs", "Search logs", "{\"type\":\"object\"}");
        when(factory.cacheKey(config)).thenReturn("cache-key");
        when(registry.acquire(eq("cache-key"), eq(config), any())).thenReturn(handle);
        when(registry.toolCallbacks(handle)).thenReturn(new ToolCallback[]{callback});
        OpsMcpRemoteClientAdapter adapter = new OpsMcpRemoteClientAdapter(registry, factory);

        OpsMcpRemoteClientAdapter.Session session = adapter.open(config);

        assertEquals(1, session.callbacks().length);
        assertEquals("search_logs", session.callbacks()[0].getToolDefinition().name());
        verify(factory).cacheKey(config);
        verify(registry).acquire(eq("cache-key"), eq(config), any());
        verify(registry).initialize(handle);
        verify(registry).toolCallbacks(handle);
    }

    @Test
    void catalogReconnectsOnceAfterPeerSessionLossButNeverReplaysBusinessOpen() {
        var registry = mock(OpsMcpClientRegistry.class);
        var factory = mock(OpsMcpClientFactory.class);
        var handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        var config = OpsMcpServerConfig.builder().name("catalog").build();
        when(factory.cacheKey(config)).thenReturn("key");
        when(registry.acquire(eq("key"), eq(config), any())).thenReturn(handle);
        when(registry.toolCallbacks(handle)).thenReturn(new ToolCallback[]{callback("read", "read", "{}")});
        var lost = new OpsMcpCallFailure(OpsMcpCallFailure.Kind.TRANSPORT_ERROR, "SESSION_NOT_FOUND", false);
        org.mockito.Mockito.doThrow(lost).doNothing().when(registry).initialize(handle);
        var adapter = new OpsMcpRemoteClientAdapter(registry, factory);
        assertEquals("read", adapter.inspectDefinition(config, "read").get("toolName"));
        verify(registry, org.mockito.Mockito.times(2)).initialize(handle);
        verify(registry).invalidate(handle);
        org.mockito.Mockito.doThrow(lost).when(registry).initialize(handle);
        assertThrows(OpsMcpCallFailure.class, () -> adapter.open(config));
        verify(registry, org.mockito.Mockito.times(3)).initialize(handle);
        assertThrows(OpsMcpCallFailure.class, () -> adapter.inspectDefinitions(config));
        verify(registry, org.mockito.Mockito.times(5)).initialize(handle);
    }

    @Test
    void sessionMustDefensivelyCopyCallbackSnapshot() {
        OpsMcpClientRegistry.ClientHandle handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        ToolCallback original = callback("search_logs", "Search logs", "{\"type\":\"object\"}");
        ToolCallback replacement = callback("replace", "Replace", "{\"type\":\"object\"}");
        ToolCallback[] callbacks = new ToolCallback[]{original};

        OpsMcpRemoteClientAdapter.Session session =
                new OpsMcpRemoteClientAdapter.Session(handle, callbacks);
        callbacks[0] = replacement;
        ToolCallback[] firstRead = session.callbacks();
        firstRead[0] = replacement;

        assertEquals("search_logs", session.callbacks()[0].getToolDefinition().name());
        assertNotSame(firstRead, session.callbacks());
    }

    @Test
    void inspectDefinitionMustProjectRemoteSchemaAndRejectInvalidJson() {
        OpsMcpClientRegistry registry = mock(OpsMcpClientRegistry.class);
        OpsMcpClientFactory factory = mock(OpsMcpClientFactory.class);
        OpsMcpClientRegistry.ClientHandle handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder().name("search-mcp").build();
        when(factory.cacheKey(config)).thenReturn("cache-key");
        when(registry.acquire(eq("cache-key"), eq(config), any())).thenReturn(handle);
        when(registry.toolCallbacks(handle)).thenReturn(new ToolCallback[]{
                callback("search_logs", "Search logs", "{\"type\":\"object\",\"required\":[\"query\"]}")});
        OpsMcpRemoteClientAdapter adapter = new OpsMcpRemoteClientAdapter(registry, factory);

        Map<String, Object> definition = adapter.inspectDefinition(config, "search_logs");

        assertEquals("search_logs", definition.get("toolName"));
        assertEquals("Search logs", definition.get("description"));
        assertEquals("REMOTE_MCP_TOOL_DEFINITION", definition.get("schemaSource"));
        assertTrue(definition.get("inputSchema") instanceof Map<?, ?>);

        when(registry.toolCallbacks(handle)).thenReturn(new ToolCallback[]{
                callback("broken", "Broken", "not-json")});
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.inspectDefinition(config, "broken"));
        assertTrue(error.getMessage().startsWith("MCP_TOOL_SCHEMA_INVALID"));
    }

    @Test
    void inspectDefinitionsMustSkipIncompleteCallbacksAndFailWhenCatalogEmpty() {
        OpsMcpClientRegistry registry = mock(OpsMcpClientRegistry.class);
        OpsMcpClientFactory factory = mock(OpsMcpClientFactory.class);
        OpsMcpClientRegistry.ClientHandle handle = mock(OpsMcpClientRegistry.ClientHandle.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder().name("search-mcp").build();
        when(factory.cacheKey(config)).thenReturn("cache-key");
        when(registry.acquire(eq("cache-key"), eq(config), any())).thenReturn(handle);
        when(registry.toolCallbacks(handle)).thenReturn(new ToolCallback[]{
                callback("search_logs", "Search logs", "{\"type\":\"object\"}"),
                malformedCallback()});
        OpsMcpRemoteClientAdapter adapter = new OpsMcpRemoteClientAdapter(registry, factory);

        List<Map<String, Object>> definitions = adapter.inspectDefinitions(config);

        assertEquals(1, definitions.size());
        assertEquals("search_logs", definitions.get(0).get("toolName"));

        when(registry.toolCallbacks(handle)).thenReturn(new ToolCallback[]{
                malformedCallback()});
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.inspectDefinitions(config));
        assertTrue(error.getMessage().startsWith("MCP_TOOL_DISCOVERY_EMPTY"));
    }

    @Test
    void invalidateMustDelegateToRegistry() {
        OpsMcpClientRegistry registry = mock(OpsMcpClientRegistry.class);
        OpsMcpRemoteClientAdapter adapter = new OpsMcpRemoteClientAdapter(
                registry,
                mock(OpsMcpClientFactory.class));

        adapter.invalidateAll();

        verify(registry).invalidateAll();
    }

    private ToolCallback callback(String name, String description, String schema) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(schema)
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }

            @Override
            public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                return "ok";
            }
        };
    }

    private ToolCallback malformedCallback() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                throw new IllegalArgumentException("malformed callback");
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                return "unreachable";
            }

            @Override
            public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                return "unreachable";
            }
        };
    }
}
