package cn.lgs.orbisops.application.skill;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillCanaryReviewApplicationServiceTest {
    @Test void failuresArePersistedWithExponentialBackoffAndNeverRetriedInsideTheSameClaim() {
        var store=mock(SkillCanaryReviewPort.class);var model=mock(SkillCanaryReviewModelPort.class);
        var clock=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
        var service=new SkillCanaryReviewApplicationService(store,model,clock);
        when(model.review(anyString())).thenThrow(new IllegalStateException("unavailable"));
        for(int attempt=1;attempt<=5;attempt++) {
            var claim=new SkillCanaryReviewPort.Claim("id","lease","{}",attempt);
            when(store.claim(1000)).thenReturn(Optional.of(claim));
            service.replay(1);
            verify(store).retry(claim,1000+(30_000L<<Math.min(3,attempt-1)),"IllegalStateException");
        }
        verify(model,times(5)).review("{}");verify(store,never()).complete(any(),any());
    }
    @Test void providerCooldownIsPreservedWithoutBlockingOtherWork() {
        var store=mock(SkillCanaryReviewPort.class);var model=mock(SkillCanaryReviewModelPort.class);
        var claim=new SkillCanaryReviewPort.Claim("id","lease","{}",1);
        when(store.claim(1000)).thenReturn(Optional.of(claim),Optional.empty());
        when(model.review("{}")).thenThrow(new SkillCanaryReviewModelPort.RetryableFailure("limited",300_000));
        new SkillCanaryReviewApplicationService(store,model,Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC)).replay(4);
        verify(store).retry(claim,301_000,"RetryableFailure");verify(model,times(1)).review("{}");
    }
    @Test void unknownIsNotSafeAndAnAttributionNeedsEvidence() {
        assertFalse(new SkillCanaryReviewPort.Decision("UNKNOWN","NONE",List.of(),"Missing evidence").conclusive());
        assertThrows(IllegalArgumentException.class,()->new SkillCanaryReviewPort.Decision("SAFE","CANDIDATE",List.of(),"No receipts"));
        assertThrows(IllegalArgumentException.class,()->new SkillCanaryReviewPort.Decision("SAFE","NONE",null,"Missing list"));
    }
}
