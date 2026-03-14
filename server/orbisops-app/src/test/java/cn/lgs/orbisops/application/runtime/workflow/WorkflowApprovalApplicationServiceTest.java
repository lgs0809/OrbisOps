package cn.lgs.orbisops.application.runtime.workflow;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowApprovalApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Test
    void issuePersistsOnlyActionHashesAndResolvesBothDecisions() {
        FakeRepository repository = new FakeRepository();
        WorkflowApprovalApplicationService service = service(repository, NOW);

        var issued = service.issue(command(Duration.ofMinutes(30)));
        WorkflowApprovalRecord stored = repository.byApproval.get(issued.record().approvalId());

        assertNotEquals(issued.approveAction(), stored.approveActionHash());
        assertNotEquals(issued.rejectAction(), stored.rejectActionHash());
        assertEquals(64, stored.approveActionHash().length());
        assertEquals(64, stored.rejectActionHash().length());
        assertEquals(64, stored.waitTokenHash().length());
        assertEquals(WorkflowApprovalRecord.Decision.APPROVE,
                service.resolveAction(issued.approveAction()).decision());
        assertEquals(WorkflowApprovalRecord.Decision.REJECT,
                service.resolveAction(issued.rejectAction()).decision());
    }

    @Test
    void firstDecisionWinsAndOppositeSecondDecisionIsRejected() {
        FakeRepository repository = new FakeRepository();
        WorkflowApprovalApplicationService service = service(repository, NOW);
        var issued = service.issue(command(Duration.ofMinutes(30)));

        WorkflowApprovalRecord approved = service.decide(
                service.resolveAction(issued.approveAction()), "user-1");

        assertEquals(WorkflowApprovalRecord.Status.APPROVED, approved.status());
        assertEquals("user-1", approved.decidedBy());
        SecurityException duplicate = assertThrows(SecurityException.class,
                () -> service.resolveAction(issued.rejectAction()));
        assertTrue(duplicate.getMessage().startsWith("WORKFLOW_APPROVAL_ALREADY_DECIDED"));
    }

    @Test
    void expiredActionBecomesTerminalBeforeDecision() {
        FakeRepository repository = new FakeRepository();
        WorkflowApprovalApplicationService issuer = service(repository, NOW);
        var issued = issuer.issue(command(Duration.ofMinutes(1)));
        WorkflowApprovalApplicationService later = service(repository, NOW.plusSeconds(61));

        SecurityException expired = assertThrows(SecurityException.class,
                () -> later.resolveAction(issued.approveAction()));

        assertEquals("WORKFLOW_APPROVAL_EXPIRED", expired.getMessage());
        assertEquals(WorkflowApprovalRecord.Status.EXPIRED,
                repository.byApproval.get(issued.record().approvalId()).status());
    }

    @Test
    void sameRunNodeCannotSilentlyCreateSecondAuthorityRecord() {
        FakeRepository repository = new FakeRepository();
        WorkflowApprovalApplicationService service = service(repository, NOW);
        service.issue(command(Duration.ofMinutes(30)));

        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> service.issue(command(Duration.ofMinutes(30))));

        assertTrue(duplicate.getMessage().startsWith("WORKFLOW_APPROVAL_ALREADY_EXISTS"));
        assertEquals(1, repository.byApproval.size());
    }

    private WorkflowApprovalApplicationService service(FakeRepository repository, Instant now) {
        return new WorkflowApprovalApplicationService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private WorkflowApprovalApplicationService.IssueCommand command(Duration ttl) {
        return new WorkflowApprovalApplicationService.IssueCommand(
                "run-1", "project-1", "approval-node", "channel-1", "room-1",
                "Approve production rollout", ttl);
    }

    private static final class FakeRepository implements WorkflowApprovalRepositoryPort {
        private final Map<String, WorkflowApprovalRecord> byApproval = new LinkedHashMap<>();

        @Override public void ensureSchema() {
        }

        @Override
        public Optional<WorkflowApprovalRecord> findByRunNode(String runId, String nodeId) {
            return byApproval.values().stream()
                    .filter(item -> item.runId().equals(runId) && item.nodeId().equals(nodeId))
                    .findFirst();
        }

        @Override
        public Optional<WorkflowApprovalRecord> findCurrentByRun(String runId) {
            return byApproval.values().stream()
                    .filter(item -> item.runId().equals(runId))
                    .reduce((left, right) -> right);
        }

        @Override
        public Optional<WorkflowApprovalRecord> findByActionHash(String actionHash) {
            return byApproval.values().stream()
                    .filter(item -> item.approveActionHash().equals(actionHash)
                            || item.rejectActionHash().equals(actionHash))
                    .findFirst();
        }

        @Override
        public boolean insert(WorkflowApprovalRecord record) {
            if (findByRunNode(record.runId(), record.nodeId()).isPresent()) return false;
            byApproval.put(record.approvalId(), record);
            return true;
        }

        @Override
        public boolean rotateActionsIfWaiting(String approvalId,
                                              String approveActionHash,
                                              String rejectActionHash,
                                              Instant expiresAt) {
            WorkflowApprovalRecord current = byApproval.get(approvalId);
            if (current == null || current.status() != WorkflowApprovalRecord.Status.WAITING) return false;
            byApproval.put(approvalId, copy(current, current.status(), approveActionHash, rejectActionHash,
                    expiresAt, current.decidedBy(), current.decidedAt()));
            return true;
        }

        @Override
        public boolean decideIfWaiting(String approvalId,
                                       WorkflowApprovalRecord.Decision decision,
                                       String actor,
                                       Instant decidedAt) {
            WorkflowApprovalRecord current = byApproval.get(approvalId);
            if (current == null || current.status() != WorkflowApprovalRecord.Status.WAITING
                    || current.expired(decidedAt)) return false;
            WorkflowApprovalRecord.Status status = decision == WorkflowApprovalRecord.Decision.APPROVE
                    ? WorkflowApprovalRecord.Status.APPROVED
                    : WorkflowApprovalRecord.Status.REJECTED;
            byApproval.put(approvalId, copy(current, status, current.approveActionHash(), current.rejectActionHash(),
                    current.expiresAt(), actor, decidedAt));
            return true;
        }

        @Override
        public boolean expireIfWaiting(String approvalId, Instant expiredAt) {
            WorkflowApprovalRecord current = byApproval.get(approvalId);
            if (current == null || current.status() != WorkflowApprovalRecord.Status.WAITING) return false;
            byApproval.put(approvalId, copy(current, WorkflowApprovalRecord.Status.EXPIRED,
                    current.approveActionHash(), current.rejectActionHash(), current.expiresAt(), "", expiredAt));
            return true;
        }

        private WorkflowApprovalRecord copy(WorkflowApprovalRecord current,
                                            WorkflowApprovalRecord.Status status,
                                            String approveActionHash,
                                            String rejectActionHash,
                                            Instant expiresAt,
                                            String decidedBy,
                                            Instant decidedAt) {
            return new WorkflowApprovalRecord(
                    current.approvalId(), current.runId(), current.projectId(), current.nodeId(),
                    current.waitTokenHash(), approveActionHash, rejectActionHash, status,
                    current.channelId(), current.target(), current.requestSummary(),
                    current.requestedAt(), expiresAt, decidedBy, decidedAt);
        }
    }
}
