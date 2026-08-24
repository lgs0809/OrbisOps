package cn.lgs.orbisops.application.changepackage;

public interface ChangeVerificationDispatchPort {
    ChangeVerificationQueuePort.Binding binding(String projectId, String workflowId);
    /** PENDING tracks an existing Run; COMPLETED/FAILED are execution outcomes, not business verdicts. */
    String advance(ChangeVerificationQueuePort.Task task);
}
