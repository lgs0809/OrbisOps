package cn.lgs.orbisops.application.changepackage;

import java.util.Optional;

/** Durable post-Landing work. Completion records execution, never implies business acceptance. */
public interface ChangeVerificationQueuePort {
    record Binding(String projectId, String workflowId, int version, String definitionHash) {}
    record Task(String eventKey, String projectId, String packageId, int approvedVersion,
                String approvedHash, String landingRunId, String owner, String workflowId,
                int workflowVersion, String workflowHash, String runId, String sessionId,
                String leaseToken, int failures) {}

    int discover(Binding binding, int limit);
    Optional<Task> claim();
    boolean owns(Task task);
    boolean settle(Task task, String status, String reason, int delaySeconds, boolean failedAttempt);
}
