package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAutomationControlActionServiceTest {

    @Test
    void inspectionCreationUsesUnifiedToolExecution() {
        OpsToolExecutionService tools = mock(OpsToolExecutionService.class);
        when(tools.execute(anyMap(), eq("user-1"))).thenReturn(Map.of(
                "allowed", true, "resultId", "result-1", "outputHash", "hash-1", "preview", "ok"));
        OpsAutomationControlActionService service = new OpsAutomationControlActionService(tools);

        OpsAgentChatResponse response = service.execute(request(
                "新增巡检任务，任务名为订单巡检，每30分钟执行一次，检查下单错误率"), null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(tools).execute(captor.capture(), eq("user-1"));
        Map<String, Object> toolRequest = captor.getValue();
        assertEquals("inspection.task", toolRequest.get("toolsetId"));
        assertEquals("inspection_task_create", toolRequest.get("toolName"));
        Map<?, ?> arguments = (Map<?, ?>) toolRequest.get("arguments");
        assertEquals("0 */30 * * * ?", arguments.get("cronExpression"));
        assertEquals("订单巡检", arguments.get("taskName"));
        assertEquals("SUCCEEDED", response.getMetadata().get("controlStatus"));
        assertEquals("result-1", response.getMetadata().get("resultId"));
    }

    @Test
    void alertCreationUsesAlertToolset() {
        OpsToolExecutionService tools = mock(OpsToolExecutionService.class);
        when(tools.execute(anyMap(), eq("user-1"))).thenReturn(Map.of(
                "allowed", true, "resultId", "result-2", "outputHash", "hash-2", "preview", "ok"));
        OpsAutomationControlActionService service = new OpsAutomationControlActionService(tools);

        service.execute(request("新增告警触发规则，规则名为严重错误告警，每10分钟检查一次"), null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(tools).execute(captor.capture(), eq("user-1"));
        assertEquals("alert.trigger", captor.getValue().get("toolsetId"));
        assertEquals("alert_trigger_create", captor.getValue().get("toolName"));
    }

    @Test
    void routerBlockIsNotReportedAsSuccess() {
        OpsToolExecutionService tools = mock(OpsToolExecutionService.class);
        when(tools.execute(anyMap(), eq("user-1"))).thenReturn(Map.of(
                "allowed", false, "resultId", "result-blocked", "outputHash", "hash-blocked",
                "reasonCode", "PROJECT_ROLE_REQUIRED"));
        OpsAutomationControlActionService service = new OpsAutomationControlActionService(tools);

        OpsAgentChatResponse response = service.execute(request("删除巡检任务 id 12"), null);

        assertEquals("BLOCKED", response.getMetadata().get("controlStatus"));
        assertEquals("PROJECT_ROLE_REQUIRED", response.getMetadata().get("reasonCode"));
        assertTrue(response.getContent().contains("没有绕过限制"));
    }

    private OpsAgentChatRequest request(String query) {
        return OpsAgentChatRequest.builder()
                .projectId("demo-project")
                .sessionId("session-1")
                .runId("run-1")
                .userId("user-1")
                .agentDefinitionId("project-agent")
                .query(query)
                .build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
