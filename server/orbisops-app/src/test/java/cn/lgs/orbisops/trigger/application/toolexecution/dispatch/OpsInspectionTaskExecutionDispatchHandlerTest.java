package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.api.dto.TaskScheduleRequestDTO;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.application.config.TaskScheduleApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsInspectionTaskExecutionDispatchHandlerTest {

    @Test
    void shouldPreserveStructuredTaskParametersAndGenericAgentDefault() {
        TaskScheduleApplicationService schedules = mock(TaskScheduleApplicationService.class);
        when(schedules.create(org.mockito.ArgumentMatchers.any(TaskScheduleRequestDTO.class))).thenReturn(true);
        OpsInspectionTaskExecutionDispatchHandler handler =
                new OpsInspectionTaskExecutionDispatchHandler(provider(schedules));

        Object result = handler.dispatch(target("inspection_task_create"), request(Map.of(
                "taskName", "浏览器巡检",
                "taskParam", Map.of(
                        "prompt", "检查接口错误率与慢 SQL。",
                        "rangeMinutes", 30,
                        "promWindow", "30m"))));

        assertEquals("SUCCEEDED", ((Map<?, ?>) result).get("status"));
        verify(schedules).create(argThat(command ->
                "generic-ops-react-agent".equals(command.getAgentId())
                        && "检查接口错误率与慢 SQL。".equals(command.getTaskParam())
                        && Integer.valueOf(30).equals(command.getRangeMinutes())
                        && "30m".equals(command.getPromWindow())));
    }

    @Test
    void missingScheduleBoundaryMustFailClosed() {
        OpsInspectionTaskExecutionDispatchHandler handler =
                new OpsInspectionTaskExecutionDispatchHandler(provider(null));
        assertThrows(IllegalStateException.class, () ->
                handler.dispatch(target("inspection_task_list"), request(Map.of())));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "inspection.task", toolName, "INSPECTION_TASK", "MEDIUM",
                false, false, true, false, false);
    }

    private ToolExecutionRequest request(Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "inspection.task", "inspection_task_create",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, arguments,
                "session-1", "run-1", Map.of("projectId", "project-1"), Map.of());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
