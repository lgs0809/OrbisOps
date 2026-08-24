package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.run.model.*;
import cn.lgs.orbisops.domain.worksession.run.service.WorkSessionRunPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpsWorkSessionCanceledRecoveryPolicyTest {
    private final WorkSessionRunPolicy policy = new WorkSessionRunPolicy();

    @Test void canceledSilentTaskCannotBecomeReplayable() {
        var result = policy.recoveryDecision(new WorkSessionRecoveryCandidate(
                "r", "p", "a", 1, 0, 0, 0, 0, true));
        assertEquals(WorkSessionRunStatus.CANCELED, result.status());
    }

    @Test void canceledReadOnlyTaskCannotBecomeReplayable() {
        var result = policy.recoveryDecision(new WorkSessionRecoveryCandidate(
                "r", "p", "a", 1, 1, 0, 0, 0, true));
        assertEquals(WorkSessionRunStatus.CANCELED, result.status());
    }

    @Test void cancellationDoesNotEraseUncertainWrite() {
        var result = policy.recoveryDecision(new WorkSessionRecoveryCandidate(
                "r", "p", "a", 1, 1, 0, 0, 1, true));
        assertEquals(WorkSessionRunStatus.RECOVERY_REVIEW_REQUIRED, result.status());
    }
}
