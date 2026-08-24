package cn.lgs.orbisops.application.episode;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.domain.security.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundModelRecoveryServiceTest {
    private final BackgroundModelRecoveryPort store=mock(BackgroundModelRecoveryPort.class);
    private final AuthorizeProjectAccessUseCase access=mock(AuthorizeProjectAccessUseCase.class);
    private final AdminUserCatalogPort accounts=mock(AdminUserCatalogPort.class);
    private BackgroundModelRecoveryPort.Candidate candidate(String id) {
        return new BackgroundModelRecoveryPort.Candidate(BackgroundModelRecoveryPort.Family.EPISODE_TURN,id,"p","s","owner","RETRY_EXHAUSTED",8,"EPISODE_MODEL_FAILED","hash","TURN");
    }
    private BackgroundModelRecoveryService service(boolean ready) {
        return new BackgroundModelRecoveryService(store,c->ready,new cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPolicy(access,accounts),Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC));
    }
    private void owner(Integer status) {
        when(accounts.findByUserId("owner")).thenReturn(new AdminUserAccount(1L,"owner","original-owner","ignored",AdminUserRole.USER,status,null,null));
        when(store.restore(any(),anyLong(),anyString(),any())).thenAnswer(i->{((Runnable)i.getArgument(3)).run();return true;});
    }
    @Test void oneTickRestoresAtMostOneAndRetainsOriginalOwnerAndCount() {
        owner(1);when(store.candidates(any(),eq(""),eq(""),anyString(),eq(20))).thenReturn(List.of(candidate("1"),candidate("2")));
        assertEquals(1,service(true).recoverNext());
        verify(store,times(1)).restore(eq(candidate("1")),longThat(d->d>=1_000+3_600_000),eq("SYSTEM_BACKGROUND_MODEL_RECOVERY"),any());
        verify(access).requireAccess("p","original-owner","owner",false);
    }
    @Test void disabledModelNeverRequeuesOrInvokesAnOwnerPrivilegeSubstitute() {
        when(store.candidates(any(),anyString(),anyString(),anyString(),anyInt())).thenReturn(List.of(candidate("1")));
        assertEquals(0,service(false).recoverNext());verify(store,never()).restore(any(),anyLong(),anyString(),any());verifyNoInteractions(accounts,access);
    }
    @Test void revokedOwnerStaysStoppedAndNextCandidateCanBeConsidered() {
        owner(0);when(store.candidates(any(),anyString(),anyString(),anyString(),anyInt())).thenReturn(List.of(candidate("1")));
        assertEquals(0,service(true).recoverNext());verifyNoInteractions(access);
    }
    @Test void explicitRecoveryCannotSelectAnotherProjectAndDoesNotResetCounters() {
        owner(1);when(store.candidates(candidate("1").family(),"p","1","",1)).thenReturn(List.of(candidate("1")));
        assertFalse(service(true).recover("other",candidate("1").family(),"1","admin"));
        assertTrue(service(true).recover("p",candidate("1").family(),"1","admin"));
        verify(store).restore(eq(candidate("1")),anyLong(),eq("admin"),any());
    }    @Test void legitimatePrivateMemoryRecoveryChecksTheEnabledOriginalOwnerWithoutInventingAProject() {
        owner(1);var privateTask=new BackgroundModelRecoveryPort.Candidate(BackgroundModelRecoveryPort.Family.MEMORY,"private", "","s","owner","FAILED",9,"TEMPORARY_MODEL_FAILURE","frozen","SEMANTIC");
        when(store.candidates(eq(BackgroundModelRecoveryPort.Family.MEMORY),eq(""),eq(""),anyString(),eq(20))).thenReturn(List.of(privateTask));
        var service=service(true);assertEquals(0,service.recoverNext());assertEquals(0,service.recoverNext());assertEquals(1,service.recoverNext());
        verify(accounts).findByUserId("owner");verifyNoInteractions(access);
        verify(store).restore(eq(privateTask),anyLong(),eq("SYSTEM_BACKGROUND_MODEL_RECOVERY"),any());
    }

}
