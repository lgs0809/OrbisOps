package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpPolicySuggestionPort.SuggestionRequest;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpsMcpPolicySuggestionAdapterTest {
    @Test
    @SuppressWarnings("unchecked")
    void optionalSuggestionHasShortDeadlineAndNeverTurnsTimeoutIntoAuthority() {
        var client = mock(OpsAgentLlmClient.class);
        var provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.available()).thenReturn(true);
        when(client.chatJsonObject(anyString(), anyString(), anyString())).thenAnswer(call -> {
            long remaining = OpsNodeDeadlineContext.remainingMillis(240000);
            assertTrue(remaining > 0 && remaining <= 5000);
            throw new IllegalStateException("synthetic transport timeout");
        });
        var adapter = new OpsMcpPolicySuggestionAdapter(provider);
        var request = new SuggestionRequest("p", "m", "t", "read", "hash", true, Map.of());
        assertTrue(adapter.suggest(request).isEmpty());
        assertNull(OpsNodeDeadlineContext.captureDeadline());
        clearInvocations(client);
        assertTrue(OpsNodeDeadlineContext.withDeadline(System.nanoTime() - 1,
                () -> adapter.suggest(request)).isEmpty());
        verify(client, never()).chatJsonObject(anyString(), anyString(), anyString());
        assertNull(OpsNodeDeadlineContext.captureDeadline());
    }
}
