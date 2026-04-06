package cn.lgs.orbisops.application.worksession.run;

import cn.lgs.orbisops.domain.worksession.run.adapter.repository.IWorkSessionRunRepository;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionCheckpoint;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionParticipantRole;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryCandidate;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryDecision;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStart;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStartDraft;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import cn.lgs.orbisops.domain.worksession.run.service.WorkSessionRunPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkSessionRunApplicationService {

    private final IWorkSessionRunRepository repository;
    private final WorkSessionRunIdentityPort identity;
    private final WorkSessionRunPolicy policy;
    private final Duration leaseDuration;

    public WorkSessionRunApplicationService(
            IWorkSessionRunRepository repository,
            WorkSessionRunIdentityPort identity,
            Duration leaseDuration) {
        this(repository, identity, leaseDuration, new WorkSessionRunPolicy());
    }

    WorkSessionRunApplicationService(
            IWorkSessionRunRepository repository,
            WorkSessionRunIdentityPort identity,
            Duration leaseDuration,
            WorkSessionRunPolicy policy) {
        if (repository == null) throw new IllegalArgumentException("WORK_SESSION_RUN_REPOSITORY_REQUIRED");
        if (identity == null) throw new IllegalArgumentException("WORK_SESSION_RUN_IDENTITY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("WORK_SESSION_RUN_POLICY_REQUIRED");
        this.repository = repository;
        this.identity = identity;
        this.policy = policy;
        this.leaseDuration = leaseDuration == null || leaseDuration.getSeconds() < 30L
                ? Duration.ofSeconds(30L)
                : leaseDuration;
    }

    public WorkSessionRunClaim begin(WorkSessionRunStartCommand command) {
        if (command == null) throw new IllegalArgumentException("WORK_SESSION_START_COMMAND_REQUIRED");
        Instant now = identity.now();
        WorkSessionRunStart start = policy.prepareStart(new WorkSessionRunStartDraft(
                command.runId(), command.projectId(), command.sessionId(), command.actor(),
                command.agentId(), command.agentVersion(), command.agentDefinitionHash(),
                command.executionHarness(), command.harnessVersion(), command.harnessHash(),
                command.engine(), command.adapterKey(), command.modelId(), command.modelProfileId(),
                command.modelProfileVersion(), command.promptIdentity(), command.requestIdentity(),
                command.requestPayload(), command.metadata(), identity.newAttemptId(),
                identity.newLeaseToken(), identity.workerId(), now, now.plus(leaseDuration)));
        WorkSessionCheckpoint initial = policy.checkpoint(
                "RUN_CLAIMED",
                Map.of("manifestHash", start.manifestHash(), "harness", start.executionHarness()),
                now);
        return repository.claim(start, initial);
    }

    public String bindContextBundle(
            WorkSessionRunClaim claim,
            Map<String, Object> contextMetadata) {
        WorkSessionRunClaim requiredClaim = requireClaim(claim);
        WorkSessionRunSnapshot current = get(requiredClaim.runId(), requiredClaim.projectId());
        Map<String, Object> manifest = policy.mergeContextManifest(current, contextMetadata);
        String manifestHash = policy.manifestHash(manifest);
        WorkSessionCheckpoint checkpoint = policy.checkpoint(
                "CONTEXT_BUNDLE_BOUND",
                Map.of(
                        "contextBundleId", text(contextMetadata == null ? null : contextMetadata.get("contextBundleId")),
                        "contextBundleHash", text(contextMetadata == null ? null : contextMetadata.get("contextBundleHash")),
                        "manifestHash", manifestHash),
                identity.now());
        boolean updated = repository.bindManifest(
                requiredClaim, manifest, manifestHash, checkpoint, identity.now());
        if (!updated) {
            throw new IllegalStateException("WORK_SESSION_MANIFEST_CAS_FAILED：运行所有权已变化");
        }
        return manifestHash;
    }

    public void heartbeat(WorkSessionRunClaim claim) {
        if (claim == null) return;
        Instant now = identity.now();
        if (!repository.heartbeat(requireClaim(claim), now.plus(leaseDuration), now)) {
            throw new IllegalStateException("WORK_SESSION_LEASE_LOST：运行租约已失效或已请求取消");
        }
    }

    public void suspendForApproval(WorkSessionRunClaim claim) {
        WorkSessionRunClaim requiredClaim = requireClaim(claim);
        if (!repository.suspendForApproval(requiredClaim, identity.now())) {
            throw new IllegalStateException("WORK_SESSION_APPROVAL_WAIT_CAS_FAILED：运行所有权已变化");
        }
    }

    public long checkpoint(
            WorkSessionRunClaim claim,
            String checkpointType,
            Map<String, Object> payload) {
        return checkpoint(claim, checkpointType, payload, "");
    }

    public long checkpoint(
            WorkSessionRunClaim claim,
            String checkpointType,
            Map<String, Object> payload,
            String deliveryKey) {
        if (claim == null) return 0L;
        WorkSessionCheckpoint checkpoint = policy.checkpoint(checkpointType, payload, identity.now());
        long sequence = repository.appendCheckpoint(
                requireClaim(claim), checkpoint, text(deliveryKey));
        if (sequence <= 0L) {
            throw new IllegalStateException("WORK_SESSION_CHECKPOINT_FENCED：运行租约已失效或已请求取消");
        }
        return sequence;
    }

    public WorkSessionCheckpoint latestCheckpoint(
            String runId,
            String projectId,
            String checkpointTypePrefix) {
        return repository.latestCheckpoint(
                        required(runId, "runId"),
                        required(projectId, "projectId"),
                        text(checkpointTypePrefix))
                .orElseThrow(() -> new IllegalArgumentException(
                        "WORK_SESSION_CHECKPOINT_NOT_FOUND:" + text(checkpointTypePrefix)));
    }

    public void finish(
            WorkSessionRunClaim claim,
            String terminalStatus,
            String errorMessage,
            Map<String, Object> responsePayload) {
        if (claim == null) return;
        WorkSessionRunStatus status = policy.terminalStatus(terminalStatus);
        if (!repository.finish(
                requireClaim(claim), status,
                responsePayload == null ? Map.of() : new LinkedHashMap<>(responsePayload),
                text(errorMessage), identity.now())) {
            throw new IllegalStateException("WORK_SESSION_FINISH_CAS_FAILED：运行状态已变化");
        }
    }

    public boolean requestCancel(
            String runId,
            String projectId,
            String actor,
            String reason) {
        WorkSessionRunSnapshot current = get(
                required(runId, "取消 Work Session 必须提供 runId 和 projectId"),
                required(projectId, "取消 Work Session 必须提供 runId 和 projectId"));
        if (!current.status().cancellable()) return false;
        return repository.requestCancel(
                current.runId(), current.projectId(), current.stateVersion(),
                text(actor), text(reason), identity.now());
    }

    public boolean requestCancelForActor(String runId, String actor, String reason) {
        String normalizedRun = required(runId, "取消 Work Session 必须提供 runId 和 actor");
        String normalizedActor = required(actor, "取消 Work Session 必须提供 runId 和 actor");
        WorkSessionRunSnapshot current = repository.findByRunId(normalizedRun)
                .orElseThrow(() -> new IllegalArgumentException("Work Session 不存在"));
        WorkSessionParticipantRole role = repository.participantRole(
                current.runId(), current.projectId(), normalizedActor).orElse(null);
        if (!policy.actorAllowed(current, normalizedActor, role, true)) {
            throw new SecurityException("WORK_SESSION_CANCEL_FORBIDDEN");
        }
        if (!current.status().cancellable()) return false;
        return repository.requestCancel(
                current.runId(), current.projectId(), current.stateVersion(),
                normalizedActor, text(reason), identity.now());
    }

    public boolean cancelRequested(String runId, String projectId) {
        return repository.cancelRequested(required(runId, "runId"), required(projectId, "projectId"));
    }

    public WorkSessionRunSnapshot get(String runId, String projectId) {
        String normalizedRun = required(runId, "runId");
        String normalizedProject = required(projectId, "projectId");
        return repository.find(normalizedRun, normalizedProject)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Work Session 不存在或不属于当前项目：" + normalizedRun));
    }

    public List<WorkSessionRunSnapshot> listByStatus(WorkSessionRunStatus status, int limit) {
        if (status == null) throw new IllegalArgumentException("WORK_SESSION_STATUS_REQUIRED");
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return List.copyOf(repository.findByStatus(status, boundedLimit));
    }

    public void assertActorCanRead(String runId, String projectId, String actor) {
        WorkSessionRunSnapshot current = get(runId, projectId);
        String normalizedActor = required(actor, "actor");
        WorkSessionParticipantRole role = repository.participantRole(
                current.runId(), current.projectId(), normalizedActor).orElse(null);
        if (!policy.actorAllowed(current, normalizedActor, role, false)) {
            throw new SecurityException("WORK_SESSION_READ_FORBIDDEN");
        }
    }

    public WorkSessionRunSnapshot resume(String runId, String projectId, String actor) {
        WorkSessionRunSnapshot current = requireRecoverable(runId, projectId);
        String normalizedActor = required(actor, "actor");
        WorkSessionParticipantRole role = repository.participantRole(
                current.runId(), current.projectId(), normalizedActor).orElse(null);
        if (!policy.actorAllowed(current, normalizedActor, role, true)) {
            throw new SecurityException("WORK_SESSION_RESUME_FORBIDDEN");
        }
        return current;
    }

    public WorkSessionRunSnapshot resumeApproval(String runId, String projectId, String actor) {
        WorkSessionRunSnapshot current = get(runId, projectId);
        if (current.cancelRequested()) {
            throw new SecurityException("WORK_SESSION_APPROVAL_CANCEL_REQUESTED");
        }
        if (current.status() != WorkSessionRunStatus.WAITING_APPROVAL) {
            throw new IllegalStateException(
                    "WORK_SESSION_NOT_WAITING_APPROVAL：当前状态=" + current.status().name());
        }
        String normalizedActor = required(actor, "actor");
        WorkSessionParticipantRole role = repository.participantRole(
                current.runId(), current.projectId(), normalizedActor).orElse(null);
        if (!policy.actorAllowed(current, normalizedActor, role, true)) {
            throw new SecurityException("WORK_SESSION_APPROVAL_FORBIDDEN");
        }
        return current;
    }

    public WorkSessionRunSnapshot recoverySnapshot(
            String runId,
            String projectId,
            String expiredAttemptId) {
        WorkSessionRunSnapshot current = requireRecoverable(runId, projectId);
        String expectedAttempt = required(expiredAttemptId, "expiredAttemptId");
        if (!expectedAttempt.equals(current.currentAttemptId())) {
            throw new IllegalStateException("WORK_SESSION_RECOVERY_ATTEMPT_DRIFT");
        }
        if (current.cancelRequested()) {
            throw new IllegalStateException("WORK_SESSION_RECOVERY_CANCEL_REQUESTED");
        }
        return current;
    }

    public List<WorkSessionRecoveryDecision> recoverExpiredLeases(int limit) {
        List<WorkSessionRecoveryCandidate> candidates = repository.findExpiredLeases(
                Math.max(1, Math.min(limit, 200)), identity.now());
        List<WorkSessionRecoveryDecision> result = new ArrayList<>();
        for (WorkSessionRecoveryCandidate candidate : candidates) {
            WorkSessionRecoveryDecision decision = policy.recoveryDecision(candidate);
            if (repository.markRecovery(candidate, decision, identity.now())) result.add(decision);
        }
        return List.copyOf(result);
    }

    private WorkSessionRunSnapshot requireRecoverable(String runId, String projectId) {
        WorkSessionRunSnapshot current = get(runId, projectId);
        if (current.status() != WorkSessionRunStatus.RECOVERABLE) {
            throw new IllegalStateException(
                    "WORK_SESSION_NOT_RECOVERABLE：当前状态=" + current.status().name());
        }
        return current;
    }

    private WorkSessionRunClaim requireClaim(WorkSessionRunClaim claim) {
        if (claim == null) throw new IllegalStateException("Work Session 尚未取得持久化租约");
        return claim;
    }

    private String required(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(message.contains("必须") ? message : message + " 不能为空");
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
