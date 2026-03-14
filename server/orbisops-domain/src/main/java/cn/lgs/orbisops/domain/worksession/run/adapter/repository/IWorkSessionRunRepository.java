package cn.lgs.orbisops.domain.worksession.run.adapter.repository;

import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionCheckpoint;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionParticipantRole;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryCandidate;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryDecision;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStart;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IWorkSessionRunRepository {

    WorkSessionRunClaim claim(WorkSessionRunStart start, WorkSessionCheckpoint initialCheckpoint);

    Optional<WorkSessionRunSnapshot> find(String runId, String projectId);

    Optional<WorkSessionRunSnapshot> findByRunId(String runId);

    default List<WorkSessionRunSnapshot> findByStatus(WorkSessionRunStatus status, int limit) {
        return List.of();
    }

    boolean bindManifest(
            WorkSessionRunClaim claim,
            Map<String, Object> manifest,
            String manifestHash,
            WorkSessionCheckpoint checkpoint,
            Instant updatedAt);

    boolean heartbeat(WorkSessionRunClaim claim, Instant leaseExpiresAt, Instant updatedAt);

    /** Release the active execution lease while retaining a durable human-approval wait point. */
    boolean suspendForApproval(WorkSessionRunClaim claim, Instant updatedAt);

    long appendCheckpoint(WorkSessionRunClaim claim, WorkSessionCheckpoint checkpoint);

    default long appendCheckpoint(
            WorkSessionRunClaim claim,
            WorkSessionCheckpoint checkpoint,
            String deliveryKey) {
        return appendCheckpoint(claim, checkpoint);
    }

    default Optional<WorkSessionCheckpoint> latestCheckpoint(
            String runId,
            String projectId,
            String checkpointTypePrefix) {
        return Optional.empty();
    }

    boolean finish(
            WorkSessionRunClaim claim,
            WorkSessionRunStatus status,
            Map<String, Object> responsePayload,
            String errorMessage,
            Instant updatedAt);

    boolean requestCancel(
            String runId,
            String projectId,
            long expectedVersion,
            String actor,
            String reason,
            Instant updatedAt);

    boolean cancelRequested(String runId, String projectId);

    Optional<WorkSessionParticipantRole> participantRole(
            String runId,
            String projectId,
            String actor);

    List<WorkSessionRecoveryCandidate> findExpiredLeases(int limit, Instant now);

    boolean markRecovery(
            WorkSessionRecoveryCandidate candidate,
            WorkSessionRecoveryDecision decision,
            Instant updatedAt);
}
