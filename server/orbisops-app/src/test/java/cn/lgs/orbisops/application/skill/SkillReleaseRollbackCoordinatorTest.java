package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SkillReleaseRollbackCoordinatorTest {
    @Test void oneVerifiedSafetyViolationDoesNotWaitForTwentySamplesOrPretendQuarantineIsRollback() {
        var port=mock(SkillReleasePort.class); var metrics=mock(SkillEffectMetricApplicationService.class);
        var management=mock(SkillManagementUseCase.class); var states=mock(SkillReleaseStateCoordinator.class);
        var release=new SkillReleaseSnapshot("r","c","p","a","s",SkillReleaseStatus.ACTIVE,10,0,"","",1,"hash",Map.of());
        when(port.canaryEvidence(release)).thenReturn(new SkillCanaryEvidence(1,0,0,1,0,1,0,1));
        when(port.claim("r",SkillReleaseStatus.ACTIVE,SkillReleaseStatus.ROLLING_BACK)).thenReturn(true);
        when(management.recoverProjectRelease(any())).thenReturn(new SkillReleaseRecoveryOutcome(false,1,"hash","QUARANTINED"));
        when(states.move(any(),any(),any(),anyString(),anyInt(),anyString(),anyString())).thenReturn(true);
        new SkillReleaseRollbackCoordinator(port,metrics,management,states).rollbackIfDegraded(release);
        verify(states).move(any(),eq(SkillReleaseStatus.ROLLING_BACK),eq(SkillReleaseStatus.DISABLED),
                eq("CANARY_SAFETY_VIOLATION:QUARANTINED"),eq(1),eq("hash"),eq("s"));
        verifyNoInteractions(metrics);
    }
    @Test void unknownReviewsDoNotAttributeEnvironmentFailuresToCandidate() {
        var port=mock(SkillReleasePort.class); var metrics=mock(SkillEffectMetricApplicationService.class);
        var management=mock(SkillManagementUseCase.class); var states=mock(SkillReleaseStateCoordinator.class);
        var release=new SkillReleaseSnapshot("r","c","p","a","s",SkillReleaseStatus.ACTIVE,10,0,"","",1,"hash",Map.of());
        when(port.canaryEvidence(release)).thenReturn(SkillCanaryEvidence.unknown());
        new SkillReleaseRollbackCoordinator(port,metrics,management,states).rollbackIfDegraded(release);
        verify(port,never()).claim(anyString(),any(),any()); verifyNoInteractions(management,states,metrics);
    }
}
