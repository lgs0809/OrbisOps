package cn.lgs.orbisops.application.skill;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillCanaryReviewApplicationServiceTest {
    @Test void failuresArePersistedWithExponentialBackoffAndNeverRetriedInsideTheSameClaim() {
        var store=mock(SkillCanaryReviewPort.class);var model=mock(SkillCanaryReviewModelPort.class);
        when(store.validForModel(any(),anyLong())).thenReturn(true);
        var clock=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
        var service=new SkillCanaryReviewApplicationService(store,model,clock);
        when(model.review(anyString())).thenThrow(new IllegalStateException("unavailable"));
        for(int attempt=1;attempt<=12;attempt++) {
            var claim=new SkillCanaryReviewPort.Claim("id","lease","{}",attempt);
            when(store.claim(1000)).thenReturn(Optional.of(claim));
            service.replay(1);
            verify(store).retry(eq(claim),longThat(t->t>=31_000 && t<=3_601_000),eq("IllegalStateException"));
        }
        verify(model,times(12)).review("{}");verify(store,never()).complete(any(),any(),anyLong());
    }
    @Test void providerCooldownIsPreservedWithoutBlockingOtherWork() {
        var store=mock(SkillCanaryReviewPort.class);var model=mock(SkillCanaryReviewModelPort.class);
        var claim=new SkillCanaryReviewPort.Claim("id","lease","{}",1);
        when(store.claim(1000)).thenReturn(Optional.of(claim),Optional.empty());
        when(store.validForModel(any(),anyLong())).thenReturn(true);
        when(model.review("{}")).thenThrow(new SkillCanaryReviewModelPort.RetryableFailure("limited",300_000));
        new SkillCanaryReviewApplicationService(store,model,Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC)).replay(4);
        verify(store).retry(claim,301_000,"RetryableFailure");verify(model,times(1)).review("{}");
    }
    @Test void sourceOrOwnerRevokedAfterClaimNeverCallsTheModelOrRequeuesTheOldInput() {
        var store=mock(SkillCanaryReviewPort.class);var model=mock(SkillCanaryReviewModelPort.class);
        var claim=new SkillCanaryReviewPort.Claim("id","lease","{}",9);
        when(store.claim(1000)).thenReturn(Optional.of(claim));
        when(store.validForModel(claim,1000)).thenReturn(false);
        new SkillCanaryReviewApplicationService(store,model,Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC)).replay(1);
        verifyNoInteractions(model);verify(store,never()).retry(any(),anyLong(),anyString());verify(store,never()).complete(any(),any(),anyLong());
    }
    @Test void unknownIsNotSafeAndAnAttributionNeedsEvidence() {
        assertFalse(new SkillCanaryReviewPort.Decision("UNKNOWN","NONE",List.of(),"Missing evidence").conclusive());
        assertThrows(IllegalArgumentException.class,()->new SkillCanaryReviewPort.Decision("SAFE","CANDIDATE",List.of(),"No receipts"));
        assertThrows(IllegalArgumentException.class,()->new SkillCanaryReviewPort.Decision("SAFE","NONE",null,"Missing list"));
    }
}
