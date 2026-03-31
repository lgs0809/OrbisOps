package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.domain.evidence.model.ToolResultPage;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.application.evidence.OpsToolResultMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsToolResultExecutionDispatchHandlerTest {

    @Test
    void shouldReadThroughTypedApplicationAndPreserveScope() {
        ToolResultApplicationService results = mock(ToolResultApplicationService.class);
        when(results.read("result-1", "project-1", "alice", 2, 5))
                .thenReturn(new ToolResultPage(
                        "result-1", 2, 5, 8, List.of("c", "d"), "a".repeat(64)));
        OpsToolResultExecutionDispatchHandler handler = new OpsToolResultExecutionDispatchHandler(
                results, new OpsToolResultMapper());

        Object output = handler.dispatch(target("tool_result_read"), request(Map.of(
                "resultId", "result-1", "offset", 2, "limit", 5)));

        assertEquals(List.of("c", "d"), ((Map<?, ?>) output).get("lines"));
        verify(results).read("result-1", "project-1", "alice", 2, 5);
    }

    @Test
    void authorizationFailureMustPropagate() {
        ToolResultApplicationService results = mock(ToolResultApplicationService.class);
        when(results.read("result-1", "project-1", "alice", 0, 100))
                .thenThrow(new SecurityException("forbidden"));
        OpsToolResultExecutionDispatchHandler handler = new OpsToolResultExecutionDispatchHandler(
                results, new OpsToolResultMapper());

        assertThrows(SecurityException.class, () ->
                handler.dispatch(target("tool_result_read"), request(Map.of("resultId", "result-1"))));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "tool_result", toolName, "TOOL_RESULT", "LOW",
                true, false, false, false, false);
    }

    private ToolExecutionRequest request(Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "tool_result", "tool_result_read",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, arguments,
                "session-1", "run-1",
                Map.of("projectId", "project-1", "userId", "alice"), Map.of());
    }
}
