package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsSkillMaintenanceWorkerTest {
    @Test void disabledEvolutionDoesNotClaimOrPublishMaintenance() {
        var store=mock(SkillMaintenancePort.class);
        var catalog=mock(SkillCatalogQueryService.class);
        var management=mock(SkillManagementUseCase.class);
        var review=mock(SkillCompressionReviewPort.class);
        var tx=mock(SkillTransactionPort.class);
        var worker=new OpsSkillMaintenanceWorker(store,catalog,management,tx,review,false);
        try {worker.runOnce();verifyNoInteractions(store,catalog,management,review,tx);}
        finally {worker.stop();}
    }
    @Test void backgroundLogsExposeOnlyFixedCodes() {
        assertEquals("SKILL_RETRIEVAL_UNAVAILABLE",OpsSkillBackgroundFailure.code(new IllegalStateException("SKILL_RETRIEVAL_UNAVAILABLE")));
        assertEquals("IllegalStateException",OpsSkillBackgroundFailure.code(new IllegalStateException("SKILL_ERROR api-key=private")));
        assertEquals("IllegalStateException",OpsSkillBackgroundFailure.code(new IllegalStateException()));
    }
}
