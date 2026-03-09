package cn.lgs.orbisops.domain.worksession.runtime.service;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;

/** Trusted Stage transition rules. LANDING is always a new approval-bound run. */
public final class AgentStageTransitionPolicy {

    public AgentExecutionStage preApprovalStage(boolean prepareRequested) {
        return prepareRequested
                ? AgentExecutionStage.PREPARE
                : AgentExecutionStage.INVESTIGATE;
    }

    public void verifyNewLandingRun(
            boolean newRun,
            boolean approvedPackagePresent) {
        if (!newRun) {
            throw new SecurityException("LANDING_REQUIRES_NEW_AGENT_RUN");
        }
        if (!approvedPackagePresent) {
            throw new SecurityException("LANDING_APPROVED_PACKAGE_REQUIRED");
        }
    }

    public void verifyNoDirectTransition(
            AgentExecutionStage current,
            AgentExecutionStage target) {
        if (target == AgentExecutionStage.LANDING) {
            throw new SecurityException("DIRECT_STAGE_TRANSITION_TO_LANDING_FORBIDDEN");
        }
        if (current == AgentExecutionStage.PREPARE
                && target == AgentExecutionStage.INVESTIGATE) {
            throw new SecurityException("AGENT_STAGE_DOWNGRADE_REQUIRES_NEW_RUN");
        }
    }
}
