package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunIdentityPort;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunStartCommand;
import cn.lgs.orbisops.domain.worksession.run.adapter.repository.IWorkSessionRunRepository;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionCheckpoint;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionParticipantRole;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryCandidate;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRecoveryDecision;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunClaim;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStart;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkSessionRunApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T03:00:00Z");

    @Test
    void beginRequiresPinnedAgentAndRejectsUnknownHarness() {
        WorkSessionRunApplicationService service = service(new FakeRepository());
        WorkSessionRunStartCommand missingVersion = command(null, "PROJECT_PRE_APPROVAL");
        WorkSessionRunStartCommand unknownHarness = command(7, "CLIENT_LANDING");

        assertThrows(IllegalArgumentException.class, () -> service.begin(missingVersion));
        assertThrows(IllegalArgumentException.class, () -> service.begin(unknownHarness));
    }

    @Test
    void beginCreatesDurableClaimManifestAndInitialCheckpoint() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);

        WorkSessionRunClaim claim = service.begin(command(7, "PROJECT_PRE_APPROVAL"));

        assertEquals("run-1", claim.runId());
        assertEquals("attempt-1", claim.attemptId());
        assertEquals(1L, claim.fencingToken());
        assertTrue(claim.runManifestHash().length() >= 32);
        assertEquals("agent-1", repository.start.manifest().get("agentId"));
        assertEquals(7, repository.start.manifest().get("agentVersion"));
        assertEquals("RUN_CLAIMED", repository.checkpoints.get(0).checkpointType());
        assertEquals(repository.start.manifestHash(), repository.checkpoints.get(0).payload().get("manifestHash"));
    }

    @Test
    void contextBundleBindingPreservesInitialManifestAndChangesHash() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);
        WorkSessionRunClaim claim = service.begin(command(7, "PROJECT_PRE_APPROVAL"));
        String initialHash = claim.runManifestHash();
        String promptHash = String.valueOf(repository.snapshot.manifest().get("promptHash"));

        String updatedHash = service.bindContextBundle(claim, Map.of(
                "contextBundleId", "ctx-1",
                "contextBundleHash", "ctx-hash",
                "usedSkillVersionRefs", List.of(Map.of("skillId", "skill-1")),
                "toolsetBoundaryHash", "toolset-hash"));

        assertNotEquals(initialHash, updatedHash);
        assertEquals(promptHash, repository.snapshot.manifest().get("promptHash"));
        assertEquals("ctx-1", repository.snapshot.manifest().get("contextBundleId"));
        assertEquals("CONTEXT_BUNDLE_BOUND", repository.checkpoints.get(1).checkpointType());
    }

    @Test
    void staleClaimCannotAllocateCheckpointOrFinish() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);
        WorkSessionRunClaim claim = service.begin(command(7, "PROJECT_PRE_APPROVAL"));
        repository.checkpointSequence = 0L;

        IllegalStateException checkpoint = assertThrows(IllegalStateException.class,
                () -> service.checkpoint(claim, "TOOL_FINISHED", Map.of("resultId", "result-1")));
        repository.finishAccepted = false;
        IllegalStateException finish = assertThrows(IllegalStateException.class,
                () -> service.finish(claim, "SUCCEEDED", "", Map.of("content", "done")));

        assertEquals("WORK_SESSION_CHECKPOINT_FENCED：运行租约已失效或已请求取消",
                checkpoint.getMessage());
        assertEquals("WORK_SESSION_FINISH_CAS_FAILED：运行状态已变化", finish.getMessage());
    }

    @Test
    void repeatedCheckpointDeliveryKeyMustReturnExistingSequence() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);
        WorkSessionRunClaim claim = service.begin(command(7, "PROJECT_PRE_APPROVAL"));

        long first = service.checkpoint(
                claim, "TOOL_EXECUTION_COMPLETED", Map.of("projectionId", "projection-1"),
                "tool-completion-checkpoint:projection-1");
        long second = service.checkpoint(
                claim, "TOOL_EXECUTION_COMPLETED", Map.of("projectionId", "projection-1"),
                "tool-completion-checkpoint:projection-1");

        assertEquals(first, second);
        assertEquals(2, repository.checkpoints.size());
    }

    @Test
    void cancellationUsesSnapshotVersionAndTerminalRunIsNotCancelable() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);
        service.begin(command(7, "PROJECT_PRE_APPROVAL"));

        assertTrue(service.requestCancel("run-1", "p1", "u1", "stop"));
        assertEquals(1L, repository.cancelExpectedVersion);
        assertEquals("u1", repository.cancelActor);

        repository.snapshot = copy(repository.snapshot, WorkSessionRunStatus.SUCCEEDED);
        assertFalse(service.requestCancel("run-1", "p1", "u1", "again"));
    }

    @Test
    void approvalWaitReleasesLeaseAndUsesDedicatedResumeAuthorization() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);
        WorkSessionRunClaim claim = service.begin(command(7, "PROJECT_PRE_APPROVAL"));
        repository.roles.put("observer", WorkSessionParticipantRole.OBSERVER);
        repository.roles.put("editor", WorkSessionParticipantRole.EDITOR);

        service.suspendForApproval(claim);

        assertEquals(WorkSessionRunStatus.WAITING_APPROVAL, repository.snapshot.status());
        assertEquals("", repository.snapshot.leaseToken());
        assertEquals(null, repository.snapshot.leaseExpiresAt());
        assertThrows(IllegalStateException.class,
                () -> service.resume("run-1", "p1", "editor"));
        assertThrows(SecurityException.class,
                () -> service.resumeApproval("run-1", "p1", "observer"));
        assertEquals("run-1", service.resumeApproval("run-1", "p1", "editor").runId());
        assertEquals("run-1", service.resumeApproval("run-1", "p1", "u1").runId());
    }

    @Test
    void approvalResumeRejectsWrongOuterRunState() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);
        service.begin(command(7, "PROJECT_PRE_APPROVAL"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.resumeApproval("run-1", "p1", "u1"));

        assertTrue(failure.getMessage().startsWith("WORK_SESSION_NOT_WAITING_APPROVAL"));
    }

    @Test
    void observerCanReadButOnlyEditorOrOwnerCanResumeAndCancel() {
        FakeRepository repository = new FakeRepository();
        WorkSessionRunApplicationService service = service(repository);
        service.begin(command(7, "PROJECT_PRE_APPROVAL"));
        repository.snapshot = copy(repository.snapshot, WorkSessionRunStatus.RECOVERABLE);
        repository.roles.put("observer", WorkSessionParticipantRole.OBSERVER);
        repository.roles.put("editor", WorkSessionParticipantRole.EDITOR);

        service.assertActorCanRead("run-1", "p1", "observer");
        assertThrows(SecurityException.class,
                () -> service.resume("run-1", "p1", "observer"));
        assertEquals("run-1", service.resume("run-1", "p1", "editor").runId());
        assertThrows(SecurityException.class,
                () -> service.requestCancelForActor("run-1", "observer", "stop"));
        assertTrue(service.requestCancelForActor("run-1", "editor", "stop"));
    }

    @Test
    void recoveryUsesAuthoritativeCompletionAndReadOnlySemanticsBeforeRequiringReview() {
        FakeRepository repository = new FakeRepository();
        repository.recoveryCandidates = List.of(
                new WorkSessionRecoveryCandidate("run-safe", "p1", "attempt-safe", 2L, 0, 0, 0, 0),
                new WorkSessionRecoveryCandidate("run-completed", "p1", "attempt-completed", 3L, 1, 1, 1, 0),
                new WorkSessionRecoveryCandidate("run-read", "p1", "attempt-read", 4L, 1, 0, 0, 0),
                new WorkSessionRecoveryCandidate("run-risk", "p1", "attempt-risk", 5L, 1, 0, 0, 1));
        WorkSessionRunApplicationService service = service(repository);

        List<WorkSessionRecoveryDecision> decisions = service.recoverExpiredLeases(10);

        assertEquals(List.of(
                        WorkSessionRunStatus.RECOVERABLE,
                        WorkSessionRunStatus.RECOVERABLE,
                        WorkSessionRunStatus.RECOVERABLE,
                        WorkSessionRunStatus.RECOVERY_REVIEW_REQUIRED),
                decisions.stream().map(WorkSessionRecoveryDecision::status).toList());
        assertEquals("LEASE_EXPIRED_BEFORE_TOOL_EXECUTION", decisions.get(0).reasonCode());
        assertEquals("LEASE_EXPIRED_AFTER_AUTHORITATIVE_TOOL_COMPLETION", decisions.get(1).reasonCode());
        assertEquals("LEASE_EXPIRED_DURING_READ_ONLY_TOOL", decisions.get(2).reasonCode());
        assertEquals("TOOL_EXECUTION_REPLAY_NOT_SAFE", decisions.get(3).reasonCode());
    }

    @Test
    void mapperBindsTypedClaimAndRestoresPinnedResumeRequest() {
        OpsWorkSessionRunMapper mapper = new OpsWorkSessionRunMapper();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1")
                .projectId("p1")
                .sessionId("s1")
                .userId("u1")
                .metadata(new LinkedHashMap<>())
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .version(7)
                .definitionHash("definition-hash")
                .build();
        WorkSessionRunClaim claim = new WorkSessionRunClaim(
                "run-1", "p1", "attempt-1", "claim-key", 3L, 1L, "manifest-hash");

        mapper.bindStart(request, definition, null, OpsExecutionHarness.PROJECT_PRE_APPROVAL, claim);
        WorkSessionRunClaim mapped = mapper.claim(request);
        OpsAgentChatRequest resumed = mapper.resumeRequest(new WorkSessionRunSnapshot(
                "run-1", "p1", "s1", "u1", "agent-1", 7, "definition-hash",
                "PROJECT_PRE_APPROVAL", WorkSessionRunStatus.RECOVERABLE, "attempt-old",
                4L, 2L, "", "", null, false, Map.of(), "manifest-hash",
                Map.of("query", "排查失败", "trustedObserveOnly", true, "metadata", Map.of("source", "chat")), Map.of(), "",
                NOW.minusSeconds(60), NOW));

        assertEquals(claim, mapped);
        assertEquals("agent-1", resumed.getAgentDefinitionId());
        assertEquals(7, resumed.getAgentVersion());
        assertEquals("PROJECT_PRE_APPROVAL", resumed.getMetadata().get("executionHarness"));
        assertEquals("attempt-old", resumed.getMetadata().get("resumedFromAttemptId"));
        assertTrue(resumed.getTrustedObserveOnly());
        assertEquals(true, mapper.startCommand(resumed, definition, null,
                OpsExecutionHarness.PROJECT_PRE_APPROVAL).requestPayload().get("trustedObserveOnly"));
    }

    private WorkSessionRunApplicationService service(FakeRepository repository) {
        return new WorkSessionRunApplicationService(
                repository,
                new FixedIdentity(),
                Duration.ofSeconds(90));
    }

    private WorkSessionRunStartCommand command(Integer version, String harness) {
        return new WorkSessionRunStartCommand(
                "run-1", "p1", "s1", "u1", "agent-1", version,
                version == null ? "" : "definition-hash", harness, 1, "harness-hash",
                "GRAPH", "graph-adapter", "model-1", "profile-1", 2L,
                Map.of("instruction", "investigate", "nodes", List.of(), "edges", List.of()),
                Map.of("query", "check", "projectId", "p1", "agentId", "agent-1", "agentVersion", version == null ? 0 : version),
                Map.of("query", "check", "sessionId", "s1", "metadata", Map.of("source", "chat")),
                Map.of("mcpSnapshotRefs", List.of("mcp-1"), "projectConfigVersion", 3));
    }

    private WorkSessionRunSnapshot copy(WorkSessionRunSnapshot source, WorkSessionRunStatus status) {
        return new WorkSessionRunSnapshot(
                source.runId(), source.projectId(), source.sessionId(), source.owner(), source.agentId(),
                source.agentVersion(), source.agentDefinitionHash(), source.executionHarness(), status,
                source.currentAttemptId(), source.stateVersion(), source.fencingToken(), source.workerId(),
                source.leaseToken(), source.leaseExpiresAt(), source.cancelRequested(), source.manifest(),
                source.manifestHash(), source.requestPayload(), source.responsePayload(), source.errorMessage(),
                source.createdAt(), source.updatedAt());
    }

    private static final class FixedIdentity implements WorkSessionRunIdentityPort {
        @Override public String newAttemptId() { return "attempt-1"; }
        @Override public String newLeaseToken() { return "claim-key"; }
        @Override public String workerId() { return "worker-1"; }
        @Override public Instant now() { return NOW; }
    }

    private static final class FakeRepository implements IWorkSessionRunRepository {
        private WorkSessionRunStart start;
        private WorkSessionRunSnapshot snapshot;
        private final List<WorkSessionCheckpoint> checkpoints = new ArrayList<>();
        private final Map<String, Long> checkpointDeliveries = new LinkedHashMap<>();
        private final Map<String, WorkSessionParticipantRole> roles = new LinkedHashMap<>();
        private long checkpointSequence = 1L;
        private boolean bindAccepted = true;
        private boolean heartbeatAccepted = true;
        private boolean finishAccepted = true;
        private boolean cancelAccepted = true;
        private long cancelExpectedVersion;
        private String cancelActor;
        private List<WorkSessionRecoveryCandidate> recoveryCandidates = List.of();

        @Override
        public WorkSessionRunClaim claim(WorkSessionRunStart start, WorkSessionCheckpoint initialCheckpoint) {
            this.start = start;
            this.checkpoints.add(initialCheckpoint);
            this.snapshot = new WorkSessionRunSnapshot(
                    start.runId(), start.projectId(), start.sessionId(), start.actor(), start.agentId(),
                    start.agentVersion(), start.agentDefinitionHash(), start.executionHarness(),
                    WorkSessionRunStatus.RUNNING, start.attemptId(), 1L, 1L, start.workerId(),
                    start.leaseToken(), start.leaseExpiresAt(), false, start.manifest(), start.manifestHash(),
                    start.requestPayload(), Map.of(), "", start.startedAt(), start.startedAt());
            return new WorkSessionRunClaim(
                    start.runId(), start.projectId(), start.attemptId(), start.leaseToken(),
                    1L, 1L, start.manifestHash());
        }

        @Override public Optional<WorkSessionRunSnapshot> find(String runId, String projectId) {
            return Optional.ofNullable(snapshot);
        }

        @Override public Optional<WorkSessionRunSnapshot> findByRunId(String runId) {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public boolean bindManifest(
                WorkSessionRunClaim claim,
                Map<String, Object> manifest,
                String manifestHash,
                WorkSessionCheckpoint checkpoint,
                Instant updatedAt) {
            if (!bindAccepted) return false;
            checkpoints.add(checkpoint);
            snapshot = new WorkSessionRunSnapshot(
                    snapshot.runId(), snapshot.projectId(), snapshot.sessionId(), snapshot.owner(),
                    snapshot.agentId(), snapshot.agentVersion(), snapshot.agentDefinitionHash(),
                    snapshot.executionHarness(), snapshot.status(), snapshot.currentAttemptId(),
                    snapshot.stateVersion() + 1, snapshot.fencingToken(), snapshot.workerId(),
                    snapshot.leaseToken(), snapshot.leaseExpiresAt(), snapshot.cancelRequested(),
                    manifest, manifestHash, snapshot.requestPayload(), snapshot.responsePayload(),
                    snapshot.errorMessage(), snapshot.createdAt(), updatedAt);
            return true;
        }

        @Override public boolean heartbeat(WorkSessionRunClaim claim, Instant leaseExpiresAt, Instant updatedAt) {
            return heartbeatAccepted;
        }

        @Override
        public boolean suspendForApproval(WorkSessionRunClaim claim, Instant updatedAt) {
            if (snapshot == null || snapshot.status() != WorkSessionRunStatus.RUNNING) return false;
            snapshot = new WorkSessionRunSnapshot(
                    snapshot.runId(), snapshot.projectId(), snapshot.sessionId(), snapshot.owner(),
                    snapshot.agentId(), snapshot.agentVersion(), snapshot.agentDefinitionHash(),
                    snapshot.executionHarness(), WorkSessionRunStatus.WAITING_APPROVAL, snapshot.currentAttemptId(),
                    snapshot.stateVersion() + 1, snapshot.fencingToken(), snapshot.workerId(),
                    "", null, snapshot.cancelRequested(), snapshot.manifest(), snapshot.manifestHash(),
                    snapshot.requestPayload(), snapshot.responsePayload(), snapshot.errorMessage(),
                    snapshot.createdAt(), updatedAt);
            return true;
        }

        @Override public long appendCheckpoint(WorkSessionRunClaim claim, WorkSessionCheckpoint checkpoint) {
            if (checkpointSequence > 0) checkpoints.add(checkpoint);
            return checkpointSequence;
        }

        @Override
        public long appendCheckpoint(
                WorkSessionRunClaim claim,
                WorkSessionCheckpoint checkpoint,
                String deliveryKey) {
            if (deliveryKey == null || deliveryKey.isBlank()) {
                return appendCheckpoint(claim, checkpoint);
            }
            Long existing = checkpointDeliveries.get(deliveryKey);
            if (existing != null) return existing;
            long sequence = appendCheckpoint(claim, checkpoint);
            if (sequence > 0L) checkpointDeliveries.put(deliveryKey, sequence);
            return sequence;
        }

        @Override
        public boolean finish(
                WorkSessionRunClaim claim,
                WorkSessionRunStatus status,
                Map<String, Object> responsePayload,
                String errorMessage,
                Instant updatedAt) {
            return finishAccepted;
        }

        @Override
        public boolean requestCancel(
                String runId,
                String projectId,
                long expectedVersion,
                String actor,
                String reason,
                Instant updatedAt) {
            cancelExpectedVersion = expectedVersion;
            cancelActor = actor;
            return cancelAccepted;
        }

        @Override public boolean cancelRequested(String runId, String projectId) { return false; }

        @Override
        public Optional<WorkSessionParticipantRole> participantRole(
                String runId,
                String projectId,
                String actor) {
            return Optional.ofNullable(roles.get(actor));
        }

        @Override public List<WorkSessionRecoveryCandidate> findExpiredLeases(int limit, Instant now) {
            return recoveryCandidates;
        }

        @Override
        public boolean markRecovery(
                WorkSessionRecoveryCandidate candidate,
                WorkSessionRecoveryDecision decision,
                Instant updatedAt) {
            return true;
        }
    }
}
