package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.toolset.OpsLocalOpsAdapterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsLocalToolExecutionDispatchHandlerTest {

    @Test
    void shouldDelegateTypedTargetToLocalAdapter() {
        OpsLocalOpsAdapterService local = mock(OpsLocalOpsAdapterService.class);
        when(local.execute(
                eq("LOCAL_PROMETHEUS"),
                eq("prometheus_query"),
                eq(Map.of("query", "up"))))
                .thenReturn(Map.of("status", "SUCCEEDED"));
        OpsLocalToolExecutionDispatchHandler handler =
                new OpsLocalToolExecutionDispatchHandler(provider(local));

        Object output = handler.dispatch(target(), request());

        assertEquals("SUCCEEDED", ((Map<?, ?>) output).get("status"));
        verify(local).execute(
                eq("LOCAL_PROMETHEUS"),
                eq("prometheus_query"),
                eq(Map.of("query", "up")));
    }

    @Test
    void missingLocalAdapterMustFailClosed() {
        OpsLocalToolExecutionDispatchHandler handler =
                new OpsLocalToolExecutionDispatchHandler(provider(null));
        assertThrows(IllegalStateException.class, () -> handler.dispatch(target(), request()));
    }

    private ToolExecutionTarget target() {
        return new ToolExecutionTarget(
                "observability.prometheus", "prometheus_query", "LOCAL_PROMETHEUS", "LOW",
                true, false, false, false, false);
    }

    private ToolExecutionRequest request() {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "observability.prometheus", "prometheus_query",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of("query", "up"),
                "session-1", "run-1", Map.of("projectId", "project-1"), Map.of());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
