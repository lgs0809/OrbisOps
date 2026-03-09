package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.repair.OpsControlledCodeToolService;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsCodeToolExecutionDispatchHandlerTest {

    @Test
    void shouldDispatchCodeToolWithRequestScopeMergedIntoArguments() {
        OpsControlledCodeToolService code = mock(OpsControlledCodeToolService.class);
        when(code.bash(org.mockito.ArgumentMatchers.anyMap(), eq("alice")))
                .thenReturn(Map.of("status", "SUCCEEDED"));
        OpsCodeToolExecutionDispatchHandler handler = new OpsCodeToolExecutionDispatchHandler(
                provider(code), provider(null));

        Object output = handler.dispatch(target("code_bash"), request());

        assertEquals("SUCCEEDED", ((Map<?, ?>) output).get("status"));
        verify(code).bash(argThat(input ->
                "project-1".equals(input.get("projectId"))
                        && "pwd".equals(input.get("command"))), eq("alice"));
    }

    @Test
    void missingCodeAdapterMustFailClosed() {
        OpsCodeToolExecutionDispatchHandler handler = new OpsCodeToolExecutionDispatchHandler(
                provider(null), provider(mock(OpsRepairWorkspaceService.class)));
        assertThrows(IllegalStateException.class, () -> handler.dispatch(target("code_read"), request()));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "code.repair", toolName, "CODE_REPAIR", "MEDIUM",
                false, true, false, false, false);
    }

    private ToolExecutionRequest request() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "code.repair", "code_bash",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of("command", "pwd"),
                "session-1", "run-1", Map.of("projectId", "project-1"), Map.of());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
