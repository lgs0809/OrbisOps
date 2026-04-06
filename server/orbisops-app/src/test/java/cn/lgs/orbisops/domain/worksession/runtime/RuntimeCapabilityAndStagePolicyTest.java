package cn.lgs.orbisops.domain.worksession.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import cn.lgs.orbisops.domain.worksession.runtime.service.AgentStageTransitionPolicy;
import cn.lgs.orbisops.domain.worksession.runtime.service.RuntimeCapabilityProfileResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeCapabilityAndStagePolicyTest {

    private final RuntimeCapabilityProfileResolver profiles = new RuntimeCapabilityProfileResolver();
    private final AgentStageTransitionPolicy stages = new AgentStageTransitionPolicy();

    @Test
    void runtimeProfileDependsOnlyOnTrustedStage() {
        assertEquals(CapabilityProfile.PROD_DIAGNOSTIC,
                profiles.resolve(AgentExecutionStage.INVESTIGATE));
        assertEquals(CapabilityProfile.TEST_FULL,
                profiles.resolve(AgentExecutionStage.PREPARE));
        assertEquals(CapabilityProfile.PROD_FULL,
                profiles.resolve(AgentExecutionStage.LANDING));
    }

    @Test
    void directTransitionToLandingIsForbiddenWithoutApprovedNewRun() {
        assertThrows(SecurityException.class, () -> stages.verifyNoDirectTransition(
                AgentExecutionStage.PREPARE,
                AgentExecutionStage.LANDING));
        assertThrows(SecurityException.class, () -> stages.verifyNewLandingRun(false, true));
        assertThrows(SecurityException.class, () -> stages.verifyNewLandingRun(true, false));
    }
}
