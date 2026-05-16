package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpCallbackPolicyAdapterTest {

    @Test
    void declaredCapabilityMustRespectBlockedNotificationDirectAndWildcardPrecedence() {
        OpsMcpCallbackPolicyAdapter adapter =
                new OpsMcpCallbackPolicyAdapter(mock(OpsToolExecutionPolicy.class));
        ToolCallback callback = callback("query_orders", "Query orders", "result");

        OpsMcpServerConfig blocked = OpsMcpServerConfig.builder()
                .blockedTools(List.of("query_orders"))
                .notificationTools(List.of("query_orders"))
                .toolCapabilities(Map.of("query_orders", "read_only"))
                .build();
        assertEquals("blocked", adapter.declaredCapability(blocked, callback));

        OpsMcpServerConfig notification = OpsMcpServerConfig.builder()
                .notificationTools(List.of("QUERY_ORDERS"))
                .toolCapabilities(Map.of("query_orders", "read_only"))
                .build();
        assertEquals("notification", adapter.declaredCapability(notification, callback));

        OpsMcpServerConfig direct = OpsMcpServerConfig.builder()
                .toolCapabilities(Map.of("query_orders", "read_only", "*", "write"))
                .build();
        assertEquals("read_only", adapter.declaredCapability(direct, callback));

        OpsMcpServerConfig wildcard = OpsMcpServerConfig.builder()
                .toolCapabilities(Map.of("*", "evidence"))
                .build();
        assertEquals("evidence", adapter.declaredCapability(wildcard, callback));
        assertTrue(adapter.matchesTool(List.of("*"), "any_tool"));
    }

    @Test
    void directMcpWithoutExplicitCapabilityFailsClosedRegardlessOfToolText() {
        OpsMcpCallbackPolicyAdapter adapter =
                new OpsMcpCallbackPolicyAdapter(
                        new OpsToolExecutionPolicy(ToolExposureSettings.defaults()));
        ToolCallback harmless = callback("query_orders", "Read-only query orders", "result");
        OpsMcpServerConfig missing = OpsMcpServerConfig.builder().build();
        OpsMcpServerConfig explicitRead = OpsMcpServerConfig.builder()
                .toolCapabilities(Map.of("query_orders", "read_only"))
                .build();

        assertEquals(null, adapter.declaredCapability(missing, harmless));
        assertTrue(!adapter.allowed(harmless, adapter.declaredCapability(missing, harmless)));
        assertTrue(adapter.allowed(harmless, adapter.declaredCapability(explicitRead, harmless)));
        assertTrue(!adapter.allowed(null, "read_only"));
    }

    @Test
    void decorateMustPreserveIdentitySchemaMetadataAndInvocationWhileAppendingOnce() {
        AtomicInteger calls = new AtomicInteger();
        ToolMetadata metadata = ToolMetadata.builder().build();
        ToolCallback delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("query_orders")
                        .description("Query orders")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return metadata;
            }

            @Override
            public String call(String toolInput) {
                calls.incrementAndGet();
                return "ok:" + toolInput;
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                calls.incrementAndGet();
                return "ctx:" + toolInput;
            }
        };
        OpsMcpCallbackPolicyAdapter adapter =
                new OpsMcpCallbackPolicyAdapter(mock(OpsToolExecutionPolicy.class));

        ToolCallback decorated = adapter.decorate(delegate, "read_only");
        ToolDefinition definition = decorated.getToolDefinition();

        assertEquals("query_orders", definition.name());
        assertEquals("{\"type\":\"object\"}", definition.inputSchema());
        assertEquals(metadata, decorated.getToolMetadata());
        assertEquals("ok:{}", decorated.call("{}"));
        assertEquals("ctx:{}", decorated.call("{}", mock(ToolContext.class)));
        assertEquals(2, calls.get());
        assertEquals(1, occurrences(definition.description(), "Ops capability:"));
        assertEquals(1, occurrences(
                adapter.decorate(decorated, "read_only").getToolDefinition().description(),
                "Ops capability:"));
        assertSame(delegate, adapter.decorate(delegate, " "));
    }

    @Test
    void assertAllowedMustDelegateCompatibilityPolicyAndExposeItsBlockedReason() {
        OpsToolExecutionPolicy policy = mock(OpsToolExecutionPolicy.class);
        ToolCallback callback = callback("restart_service", "Restart service", "blocked");
        when(policy.allowTool(callback, "blocked")).thenReturn(false);
        when(policy.blockedReason(callback)).thenReturn("blocked by analysis-only policy");
        OpsMcpCallbackPolicyAdapter adapter = new OpsMcpCallbackPolicyAdapter(policy);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .blockedTools(List.of("restart_service"))
                .build();

        SecurityException error = assertThrows(SecurityException.class,
                () -> adapter.assertAllowed(config, callback));

        assertEquals("blocked by analysis-only policy", error.getMessage());
        verify(policy).allowTool(callback, "blocked");
        verify(policy).blockedReason(callback);
    }

    private ToolCallback callback(String name, String description, String result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(description)
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

    private int occurrences(String text, String token) {
        return text == null || token == null || token.isEmpty()
                ? 0
                : text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
