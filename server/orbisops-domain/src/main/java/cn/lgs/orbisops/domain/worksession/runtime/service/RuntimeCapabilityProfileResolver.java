package cn.lgs.orbisops.domain.worksession.runtime.service;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;

/** Pure Runtime Stage to maximum capability mapping. Execution style is orthogonal. */
public final class RuntimeCapabilityProfileResolver {

    public CapabilityProfile resolve(AgentExecutionStage stage) {
        AgentExecutionStage safeStage = stage == null
                ? AgentExecutionStage.INVESTIGATE
                : stage;
        return switch (safeStage) {
            case LANDING -> CapabilityProfile.PROD_FULL;
            case PREPARE -> CapabilityProfile.TEST_FULL;
            case INVESTIGATE -> CapabilityProfile.PROD_DIAGNOSTIC;
        };
    }
}
