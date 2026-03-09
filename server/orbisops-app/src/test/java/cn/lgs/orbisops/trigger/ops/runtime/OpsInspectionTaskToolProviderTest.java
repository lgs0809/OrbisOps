package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsInspectionTaskToolProviderTest {

    @Test
    void noArgProviderBuildsSchemaWithoutExecutionService() {
        ToolCallback callback = new OpsInspectionTaskToolProvider()
                .build("project-1", "tester", "run-1", "agent-1");

        assertEquals("ManageInspectionTask", callback.getToolDefinition().name());
        assertTrue(callback.getToolDefinition().description()
                .contains("统一 ToolsetRouter"));
    }

    @Test
    void constructorBoundExecutionServiceReceivesStatusAndDefaultAgent() {
        OpsToolExecutionService executionService = mock(OpsToolExecutionService.class);
        when(executionService.execute(anyMap(), eq("tester")))
                .thenReturn(Map.of("status", "SUCCEEDED"));
        ToolCallback callback = new OpsInspectionTaskToolProvider(executionService)
                .build("project-1", "tester", "run-1", "agent-1");

        String output = callback.call("{\"action\":\"enable\",\"id\":12}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(executionService).execute(request.capture(), eq("tester"));
        assertTrue(output.contains("SUCCEEDED"));
        assertEquals("project-1", request.getValue().get("projectId"));
        assertEquals("tester", request.getValue().get("userId"));
        assertEquals("run-1", request.getValue().get("runId"));
        assertEquals("inspection.task", request.getValue().get("toolsetId"));
        assertEquals("inspection_task_status", request.getValue().get("toolName"));
        assertEquals("PRE_APPROVAL_WORKFLOW", request.getValue().get("executionScope"));
        Map<String, Object> arguments =
                (Map<String, Object>) request.getValue().get("arguments");
        assertEquals("project-1", arguments.get("projectId"));
        assertEquals("agent-1", arguments.get("agentId"));
        assertEquals(1, arguments.get("status"));
        assertEquals(12, ((Number) arguments.get("id")).intValue());
    }
}
