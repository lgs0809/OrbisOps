package cn.lgs.orbisops.application.agent;

public class OpsMainAgentActionException extends RuntimeException {

    private final OpsActionStatus status;
    private final OpsFailureDescriptor failure;

    public OpsMainAgentActionException(OpsActionStatus status, OpsFailureDescriptor failure) {
        super(failure == null ? "MAIN_AGENT_ACTION_FAILED" : failure.reasonCode());
        this.status = status == null ? OpsActionStatus.FAILED : status;
        this.failure = failure;
    }

    public OpsActionStatus status() {
        return status;
    }

    public OpsFailureDescriptor failure() {
        return failure;
    }
}
