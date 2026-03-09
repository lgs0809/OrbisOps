package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.trigger.ops.OpsRunCanceledException;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRuntimeContext;
import cn.lgs.orbisops.trigger.ops.runtime.UnifiedAgentRuntime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkSessionExecutionAdapterTest {

    @Test
    void applicationProcessManagerOwnsPrepareExecuteAndSuccessOrder() {
        UnifiedAgentRuntime coordinator =
                mock(UnifiedAgentRuntime.class);
        OpsAgentChatRequest request = request();
        OpsWorkSessionRuntimeContext context =
                new OpsWorkSessionRuntimeContext(request, null, 1L);
        OpsAgentChatResponse response = OpsAgentChatResponse.builder().content("done").build();
        when(coordinator.prepareWorkSession(request, null)).thenReturn(context);
        when(coordinator.succeedWorkSession(context)).thenReturn(response);

        OpsAgentChatResponse actual = useCase(coordinator).execute(request);

        assertSame(response, actual);
        verify(coordinator).prepareWorkSession(request, null);
        verify(coordinator).executeWorkSessionEngine(context);
        verify(coordinator).succeedWorkSession(context);
    }

    @Test
    void terminalPreparationDoesNotExecuteEngine() {
        UnifiedAgentRuntime coordinator =
                mock(UnifiedAgentRuntime.class);
        OpsAgentChatRequest request = request();
        OpsWorkSessionRuntimeContext context =
                new OpsWorkSessionRuntimeContext(request, null, 1L);
        OpsAgentChatResponse response = OpsAgentChatResponse.builder().content("hello").build();
        context.completeTerminal(response);
        when(coordinator.prepareWorkSession(request, null)).thenReturn(context);

        assertSame(response, useCase(coordinator).execute(request));
        verify(coordinator, never()).executeWorkSessionEngine(context);
        verify(coordinator, never()).succeedWorkSession(context);
    }

    @Test
    void failureIsFinalizedThenPropagated() {
        UnifiedAgentRuntime coordinator =
                mock(UnifiedAgentRuntime.class);
        OpsAgentChatRequest request = request();
        OpsWorkSessionRuntimeContext context =
                new OpsWorkSessionRuntimeContext(request, null, 1L);
        IllegalStateException failure = new IllegalStateException("ENGINE_FAILED");
        when(coordinator.prepareWorkSession(request, null)).thenReturn(context);
        doThrow(failure).when(coordinator).executeWorkSessionEngine(context);

        assertSame(failure,
                assertThrows(IllegalStateException.class,
                        () -> useCase(coordinator).execute(request)));
        verify(coordinator).failWorkSession(context, failure);
    }

    @Test
    void cancellationUsesDedicatedTerminalPath() {
        UnifiedAgentRuntime coordinator =
                mock(UnifiedAgentRuntime.class);
        OpsAgentChatRequest request = request();
        OpsWorkSessionRuntimeContext context =
                new OpsWorkSessionRuntimeContext(request, null, 1L);
        OpsRunCanceledException canceled = new OpsRunCanceledException("canceled");
        OpsAgentChatResponse response =
                OpsAgentChatResponse.builder().content("canceled").build();
        when(coordinator.prepareWorkSession(request, null)).thenReturn(context);
        doThrow(canceled).when(coordinator).executeWorkSessionEngine(context);
        when(coordinator.isWorkSessionCancellation(canceled)).thenReturn(true);
        when(coordinator.cancelWorkSession(context, canceled)).thenReturn(response);

        assertSame(response, useCase(coordinator).execute(request));
        verify(coordinator).cancelWorkSession(context, canceled);
        verify(coordinator, never()).failWorkSession(context, canceled);
    }

    @Test
    void productionAdapterCannotDependOnLegacyRuntime() {
        assertFalse(Arrays.stream(OpsWorkSessionExecutionAdapter.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .anyMatch("OpsAgentTaskRuntime"::equals));
        assertFalse(Arrays.stream(OpsWorkSessionExecutionAdapter.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .map(Class::getSimpleName)
                .anyMatch("OpsAgentTaskRuntime"::equals));
    }

    private ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> useCase(
            UnifiedAgentRuntime coordinator) {
        return new ExecuteWorkSessionUseCase<>(
                new OpsWorkSessionExecutionAdapter(coordinator));
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder().query("check errors").build();
    }
}
