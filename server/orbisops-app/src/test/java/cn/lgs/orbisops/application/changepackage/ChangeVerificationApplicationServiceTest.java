package cn.lgs.orbisops.application.changepackage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
class ChangeVerificationApplicationServiceTest {
    final ChangeVerificationQueuePort queue=mock(ChangeVerificationQueuePort.class);
    final ChangeVerificationDispatchPort dispatch=mock(ChangeVerificationDispatchPort.class);
    final ChangeVerificationQueuePort.Task task=new ChangeVerificationQueuePort.Task("event","p","cp",3,"hash","landing",
            "creator","wf",5,"def","run","session","lease",3);
    void claimed() {when(queue.claim()).thenReturn(Optional.of(task),Optional.empty());when(queue.owns(task)).thenReturn(true);}
    @Test void transientFailureIsDurableExponentialBackoff() {
        claimed();when(dispatch.advance(task)).thenThrow(new IllegalStateException("provider-unavailable"));
        new ChangeVerificationApplicationService(queue,dispatch).replay(Map.of("p","wf"));
        verify(queue).settle(task,"PENDING","IllegalStateException",80,true);
    }
    @Test void revokedOwnerRechecksAuthorizationWithoutTransientFailureOrDispatch() {
        claimed();when(dispatch.advance(task)).thenThrow(new SecurityException("PROJECT_ACCESS_FORBIDDEN:p"));
        new ChangeVerificationApplicationService(queue,dispatch).replay(Map.of("p","wf"));
        verify(queue).settle(task,"BLOCKED","PROJECT_ACCESS_FORBIDDEN:p",60,false);
    }
    @Test void removingBindingPreventsQueuedLaunch() {
        claimed();new ChangeVerificationApplicationService(queue,dispatch).replay(Map.of());
        verifyNoInteractions(dispatch);verify(queue).settle(task,"BLOCKED","AUTOMATIC_VERIFICATION_BINDING_REMOVED",60,false);
    }
    @Test void expiredClaimCannotDispatch() {
        claimed();when(queue.owns(task)).thenReturn(false);
        new ChangeVerificationApplicationService(queue,dispatch).replay(Map.of("p","wf"));
        verify(dispatch,never()).advance(any());verify(queue,never()).settle(any(),any(),any(),anyInt(),anyBoolean());
    }
}
