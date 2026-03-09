package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMemoryToolProviderTest {

    @Test
    void rememberMapsToAtomicMemoryUpsertWithServerBoundIdentity() {
        OpsToolExecutionService execution = mock(OpsToolExecutionService.class);
        when(execution.execute(any(), eq("alice"))).thenReturn(Map.of("status", "COMMITTED"));
        OpsMemoryToolProvider provider = new OpsMemoryToolProvider(execution);
        ToolCallback remember = tool(provider.build("project-1", "alice", "session-1", "run-1"),
                OpsMemoryToolProvider.UPSERT_TOOL);

        remember.call("{\"content\":\"项目使用 JDK 17\"}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(execution).execute(request.capture(), eq("alice"));
        assertEquals("project-1", request.getValue().get("projectId"));
        assertEquals("alice", request.getValue().get("userId"));
        assertEquals("session-1", request.getValue().get("sessionId"));
        assertEquals("run-1", request.getValue().get("runId"));
        assertEquals("memory", request.getValue().get("toolsetId"));
        assertEquals("memory_upsert", request.getValue().get("toolName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.getValue().get("arguments");
        assertEquals("项目使用 JDK 17", arguments.get("content"));
    }

    @Test
    void searchMapsToAtomicMemorySearch() {
        OpsToolExecutionService execution = mock(OpsToolExecutionService.class);
        when(execution.execute(any(), eq("alice"))).thenReturn(Map.of("items", List.of()));
        OpsMemoryToolProvider provider = new OpsMemoryToolProvider(execution);
        ToolCallback search = tool(provider.build("project-1", "alice", "session-1", "run-1"),
                OpsMemoryToolProvider.SEARCH_TOOL);

        search.call("{\"limit\":5}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(execution).execute(request.capture(), eq("alice"));
        assertEquals("memory_search", request.getValue().get("toolName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.getValue().get("arguments");
        assertEquals(5, arguments.get("limit"));
    }

    private ToolCallback tool(List<ToolCallback> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }
}
