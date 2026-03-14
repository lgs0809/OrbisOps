package cn.lgs.orbisops.application.runtime.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Authoritative durable ledger for one HUMAN_APPROVAL node. Opaque action values are never persisted. */
public final class WorkflowApprovalApplicationService {

    private final WorkflowApprovalRepositoryPort repository;
    private final Clock clock;

    public WorkflowApprovalApplicationService(WorkflowApprovalRepositoryPort repository) {
        this(repository, Clock.systemUTC());
    }

    WorkflowApprovalApplicationService(WorkflowApprovalRepositoryPort repository, Clock clock) {
        if (repository == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_REPOSITORY_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_CLOCK_REQUIRED");
        this.repository = repository;
        this.clock = clock;
    }

    public Optional<WorkflowApprovalRecord> find(String runId, String nodeId) {
        return repository.findByRunNode(required(runId, "WORKFLOW_APPROVAL_RUN_ID_REQUIRED"),
                required(nodeId, "WORKFLOW_APPROVAL_NODE_ID_REQUIRED"));
    }

    public Optional<WorkflowApprovalRecord> findCurrent(String runId) {
        return repository.findCurrentByRun(required(runId, "WORKFLOW_APPROVAL_RUN_ID_REQUIRED"));
    }

    public IssuedApproval issue(IssueCommand command) {
        if (command == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_ISSUE_COMMAND_REQUIRED");
        Duration ttl = command.ttl() == null ? Duration.ofMinutes(30) : command.ttl();
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("WORKFLOW_APPROVAL_TTL_INVALID");
        }
        Optional<WorkflowApprovalRecord> existing = repository.findByRunNode(command.runId(), command.nodeId());
        if (existing.isPresent()) {
            WorkflowApprovalRecord current = existing.get();
            if (current.expired(clock.instant()) && current.status() == WorkflowApprovalRecord.Status.WAITING) {
                repository.expireIfWaiting(current.approvalId(), clock.instant());
                throw new SecurityException("WORKFLOW_APPROVAL_EXPIRED");
            }
            throw new IllegalStateException("WORKFLOW_APPROVAL_ALREADY_EXISTS:" + current.status().name());
        }

        String approvalId = "workflow-approval-" + UUID.randomUUID();
        ActionPair actions = actionPair();
        Instant now = clock.instant();
        WorkflowApprovalRecord record = new WorkflowApprovalRecord(
                approvalId,
                command.runId(),
                command.projectId(),
                command.nodeId(),
                hash("workflow-wait:" + approvalId),
                hash(actions.approveAction()),
                hash(actions.rejectAction()),
                WorkflowApprovalRecord.Status.WAITING,
                command.channelId(),
                command.target(),
                command.requestSummary(),
                now,
                now.plus(ttl),
                "",
                null);
        if (!repository.insert(record)) {
            WorkflowApprovalRecord raced = repository.findByRunNode(command.runId(), command.nodeId())
                    .orElseThrow(() -> new IllegalStateException("WORKFLOW_APPROVAL_INSERT_CONFLICT"));
            if (raced.status() != WorkflowApprovalRecord.Status.WAITING || raced.expired(now)) {
                throw new IllegalStateException("WORKFLOW_APPROVAL_INSERT_CONFLICT:" + raced.status().name());
            }
            ActionPair rotated = actionPair();
            Instant rotatedExpiry = now.plus(ttl);
            if (!repository.rotateActionsIfWaiting(
                    raced.approvalId(), hash(rotated.approveAction()), hash(rotated.rejectAction()), rotatedExpiry)) {
                throw new IllegalStateException("WORKFLOW_APPROVAL_ACTION_ROTATION_CONFLICT");
            }
            WorkflowApprovalRecord refreshed = repository.findByRunNode(command.runId(), command.nodeId())
                    .orElseThrow(() -> new IllegalStateException("WORKFLOW_APPROVAL_STATE_LOST"));
            return new IssuedApproval(refreshed, rotated.approveAction(), rotated.rejectAction());
        }
        return new IssuedApproval(record, actions.approveAction(), actions.rejectAction());
    }

    public ResolvedAction resolveAction(String opaqueAction) {
        String actionHash = hashRequired(opaqueAction);
        WorkflowApprovalRecord record = repository.findByActionHash(actionHash)
                .orElseThrow(() -> new SecurityException("WORKFLOW_APPROVAL_ACTION_INVALID"));
        Instant now = clock.instant();
        if (record.expired(now) && record.status() == WorkflowApprovalRecord.Status.WAITING) {
            repository.expireIfWaiting(record.approvalId(), now);
            throw new SecurityException("WORKFLOW_APPROVAL_EXPIRED");
        }
        if (record.status() != WorkflowApprovalRecord.Status.WAITING) {
            throw new SecurityException("WORKFLOW_APPROVAL_ALREADY_DECIDED:" + record.status().name());
        }
        WorkflowApprovalRecord.Decision decision;
        if (record.approveActionHash().equals(actionHash)) {
            decision = WorkflowApprovalRecord.Decision.APPROVE;
        } else if (record.rejectActionHash().equals(actionHash)) {
            decision = WorkflowApprovalRecord.Decision.REJECT;
        } else {
            throw new SecurityException("WORKFLOW_APPROVAL_ACTION_INVALID");
        }
        return new ResolvedAction(record, decision);
    }

    public void invalidate(String approvalId) {
        repository.expireIfWaiting(required(approvalId, "WORKFLOW_APPROVAL_ID_REQUIRED"), clock.instant());
    }

    public WorkflowApprovalRecord decide(ResolvedAction resolved, String actor) {
        if (resolved == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_ACTION_REQUIRED");
        return decide(resolved.record(), resolved.decision(), actor);
    }

    public WorkflowApprovalRecord decide(WorkflowApprovalRecord record,
                                         WorkflowApprovalRecord.Decision decision,
                                         String actor) {
        if (record == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_RECORD_REQUIRED");
        if (decision == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_DECISION_REQUIRED");
        String safeActor = required(actor, "WORKFLOW_APPROVAL_ACTOR_REQUIRED");
        if (!repository.decideIfWaiting(record.approvalId(), decision, safeActor, clock.instant())) {
            throw new SecurityException("WORKFLOW_APPROVAL_DECISION_CONFLICT");
        }
        return repository.findByRunNode(record.runId(), record.nodeId())
                .orElseThrow(() -> new IllegalStateException("WORKFLOW_APPROVAL_STATE_LOST"));
    }

    private ActionPair actionPair() {
        return new ActionPair(opaqueAction(), opaqueAction());
    }

    private String opaqueAction() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashRequired(String value) {
        String normalized = required(value, "WORKFLOW_APPROVAL_ACTION_REQUIRED");
        if (normalized.length() < 32 || normalized.length() > 256) {
            throw new SecurityException("WORKFLOW_APPROVAL_ACTION_INVALID");
        }
        return hash(normalized);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("WORKFLOW_APPROVAL_HASH_UNAVAILABLE", failure);
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record IssueCommand(String runId,
                               String projectId,
                               String nodeId,
                               String channelId,
                               String target,
                               String requestSummary,
                               Duration ttl) {
        public IssueCommand {
            runId = required(runId, "WORKFLOW_APPROVAL_RUN_ID_REQUIRED");
            projectId = required(projectId, "WORKFLOW_APPROVAL_PROJECT_ID_REQUIRED");
            nodeId = required(nodeId, "WORKFLOW_APPROVAL_NODE_ID_REQUIRED");
            channelId = text(channelId);
            target = text(target);
            requestSummary = text(requestSummary);
        }
    }

    public record IssuedApproval(WorkflowApprovalRecord record,
                                 String approveAction,
                                 String rejectAction) {
        public IssuedApproval {
            if (record == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_RECORD_REQUIRED");
            approveAction = required(approveAction, "WORKFLOW_APPROVAL_APPROVE_ACTION_REQUIRED");
            rejectAction = required(rejectAction, "WORKFLOW_APPROVAL_REJECT_ACTION_REQUIRED");
        }
    }

    public record ResolvedAction(WorkflowApprovalRecord record,
                                 WorkflowApprovalRecord.Decision decision) {
        public ResolvedAction {
            if (record == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_RECORD_REQUIRED");
            if (decision == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_DECISION_REQUIRED");
        }
    }

    private record ActionPair(String approveAction, String rejectAction) {
    }
}
