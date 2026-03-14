package cn.lgs.orbisops.application.runtime.workflow;

import java.time.Instant;
import java.util.Optional;

public interface WorkflowApprovalRepositoryPort {

    void ensureSchema();

    Optional<WorkflowApprovalRecord> findByRunNode(String runId, String nodeId);

    Optional<WorkflowApprovalRecord> findCurrentByRun(String runId);

    Optional<WorkflowApprovalRecord> findByActionHash(String actionHash);

    boolean insert(WorkflowApprovalRecord record);

    boolean rotateActionsIfWaiting(String approvalId,
                                   String approveActionHash,
                                   String rejectActionHash,
                                   Instant expiresAt);

    boolean decideIfWaiting(String approvalId,
                            WorkflowApprovalRecord.Decision decision,
                            String actor,
                            Instant decidedAt);

    boolean expireIfWaiting(String approvalId, Instant expiredAt);
}
