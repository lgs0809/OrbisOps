package cn.lgs.orbisops.application.worksession;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkSessionSuspensionTest {
    @Test void explicitSuspensionReturnsWaitingWithoutCallingFailureOrSuccessFinalizers() {
        @SuppressWarnings("unchecked")
        WorkSessionLifecyclePort<String,String,String,String> port = mock(WorkSessionLifecyclePort.class);
        var pending = new IllegalStateException("typed pending signal fixture");
        when(port.prepare("request",null)).thenReturn(WorkSessionPreparation.ready("context"));
        doThrow(pending).when(port).execute("context",null);
        when(port.isSuspension(pending)).thenReturn(true);
        when(port.suspend("context",pending,null)).thenReturn("waiting response");
        assertEquals("waiting response",new ExecuteWorkSessionUseCase<>(port).execute("request"));
        verify(port,never()).fail(any(),any(),any());
        verify(port,never()).succeed(any(),any());
        verify(port,never()).cancel(any(),any(),any());
    }
}
