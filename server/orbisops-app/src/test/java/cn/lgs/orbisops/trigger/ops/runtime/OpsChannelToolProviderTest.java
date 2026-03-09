package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelToolProviderTest {

    @Test
    void callbackUsesUnifiedToolExecutionServiceWithCanonicalRun() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        ChannelQueryService channelQuery = mock(ChannelQueryService.class);
        when(channelQuery.list("project-1")).thenReturn(java.util.List.of(Map.of("channelId", "channel-1")));
        when(executionService.execute(anyMap(), eq("user-1"))).thenReturn(Map.of("status", "SUCCEEDED", "resultId", "result-1"));
        OpsChannelToolProvider provider = new OpsChannelToolProvider(
                executionService, channelQuery);

        assertTrue(provider.available("project-1"));
        ToolCallback callback = provider.build("project-1", "user-1", "run-1");
        callback.call("{\"action\":\"send\",\"channelId\":\"channel-1\",\"target\":\"room-1\",\"content\":\"完成\"}");

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(executionService).execute(request.capture(), eq("user-1"));
        assertEquals("project-1", request.getValue().get("projectId"));
        assertEquals("run-1", request.getValue().get("runId"));
        assertEquals("channel.notification", request.getValue().get("toolsetId"));
        assertEquals("channel_send", request.getValue().get("toolName"));
    }

    @Test
    void unavailableChannelStoreDoesNotExposeOptionalTool() {
        ChannelQueryService channelQuery = mock(ChannelQueryService.class);
        when(channelQuery.list("project-1")).thenThrow(new IllegalStateException("db down"));

        assertFalse(new OpsChannelToolProvider(
                mock(OpsToolExecutionService.class), channelQuery).available("project-1"));
    }
}
