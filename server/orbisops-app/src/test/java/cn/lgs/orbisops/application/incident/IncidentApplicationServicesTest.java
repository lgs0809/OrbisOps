package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertSignal;
import cn.lgs.orbisops.domain.incident.model.DiagnosisResult;
import cn.lgs.orbisops.domain.incident.model.IncidentDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentApplicationServicesTest {

    @Test
    void createUsesAuthenticatedActorAndOneUnitOfWork() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        CountingTransactions transactions = new CountingTransactions();
        IncidentSnapshot saved = snapshot("incident_fixed", IncidentStatus.OPEN, "");
        when(repository.create(any(IncidentDraft.class))).thenReturn(saved);
        when(repository.find("incident_fixed")).thenReturn(Optional.of(saved));
        when(repository.appendTimeline(any())).thenReturn(Optional.of(timeline("incident_fixed", "CREATE")));
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "incident_fixed", transactions);

        IncidentSnapshot result = service.create(new CreateIncidentCommand(
                "project-a",
                "database unavailable",
                "",
                "warning",
                "mysql",
                "manual",
                "connection refused",
                Map.of(),
                Map.of(),
                List.of("mysql")), "alice");

        assertEquals("incident_fixed", result.incidentId());
        assertEquals(1, transactions.count);
        ArgumentCaptor<IncidentDraft> draft = ArgumentCaptor.forClass(IncidentDraft.class);
        verify(repository).create(draft.capture());
        assertEquals(IncidentStatus.OPEN, draft.getValue().status());
        ArgumentCaptor<IncidentAuditEvent> auditEvent = ArgumentCaptor.forClass(IncidentAuditEvent.class);
        verify(audit).record(auditEvent.capture());
        assertEquals("alice", auditEvent.getValue().actor());
        assertEquals("create", auditEvent.getValue().action());
    }

    @Test
    void onlyLifecycleCloseOrReopenCanBeChangedManually() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        CountingTransactions transactions = new CountingTransactions();
        IncidentSnapshot before = snapshot("incident-1", IncidentStatus.RESOLVED, "");
        IncidentSnapshot after = snapshot("incident-1", IncidentStatus.CLOSED, "");
        when(repository.find("incident-1")).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.updateStatus("incident-1", IncidentStatus.CLOSED)).thenReturn(after);
        when(repository.appendTimeline(any())).thenReturn(Optional.of(timeline("incident-1", "INCIDENT_CLOSED")));
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "unused", transactions);

        IncidentSnapshot result = service.updateStatus(
                "incident-1", "closed", "alice", "validated and closed");

        assertEquals(IncidentStatus.CLOSED, result.status());
        assertEquals(1, transactions.count);
        verify(repository).updateStatus("incident-1", IncidentStatus.CLOSED);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus("incident-1", "investigating", "alice", "invalid"));
    }

    @Test
    void assignOwnerChangesResponsibilityWithoutChangingIncidentStatus() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        CountingTransactions transactions = new CountingTransactions();
        IncidentSnapshot before = snapshot("incident-1", IncidentStatus.ACTION_REQUIRED, "run-1", "");
        IncidentSnapshot after = snapshot("incident-1", IncidentStatus.ACTION_REQUIRED, "run-1", "bob");
        when(repository.find("incident-1")).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.assignOwner("incident-1", "bob")).thenReturn(after);
        when(repository.appendTimeline(any())).thenReturn(Optional.of(timeline("incident-1", "INCIDENT_OWNER_ASSIGNED")));
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "unused", transactions);

        IncidentSnapshot result = service.assignOwner("incident-1", "bob", "alice");

        assertEquals("bob", result.ownerUserId());
        assertEquals(IncidentStatus.ACTION_REQUIRED, result.status());
        assertEquals(1, transactions.count);
        verify(repository).assignOwner("incident-1", "bob");
        ArgumentCaptor<IncidentAuditEvent> auditEvent = ArgumentCaptor.forClass(IncidentAuditEvent.class);
        verify(audit).record(auditEvent.capture());
        assertEquals("assign-owner", auditEvent.getValue().action());
    }

    @Test
    void collaborationWritesAuditedWatcherRelationAndCommentFacts() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        IncidentSnapshot source = snapshot("incident-1", IncidentStatus.ACTION_REQUIRED, "run-1");
        IncidentSnapshot related = snapshot("incident-2", IncidentStatus.OPEN, "");
        when(repository.find("incident-1")).thenReturn(Optional.of(source));
        when(repository.find("incident-2")).thenReturn(Optional.of(related));
        when(repository.watchers("incident-1")).thenReturn(List.of());
        when(repository.relations("incident-1")).thenReturn(List.of());
        when(repository.appendTimeline(any())).thenReturn(Optional.of(timeline("incident-1", "COMMENT")));
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "unused", new CountingTransactions());

        service.addWatcher("incident-1", "bob", "alice");
        service.relate("incident-1", "incident-2", "alice");
        service.addComment("incident-1", "已确认数据库连接数持续上升", "alice");

        verify(repository).addWatcher("incident-1", "bob", "alice");
        verify(repository).addRelation("incident-1", "incident-2", "RELATED", "alice");
        ArgumentCaptor<cn.lgs.orbisops.domain.incident.model.IncidentTimelineDraft> timelineDraft =
                ArgumentCaptor.forClass(cn.lgs.orbisops.domain.incident.model.IncidentTimelineDraft.class);
        verify(repository, org.mockito.Mockito.atLeast(4)).appendTimeline(timelineDraft.capture());
        assertTrue(timelineDraft.getAllValues().stream().anyMatch(item -> "INCIDENT_WATCHER_ADDED".equals(item.eventType())));
        assertTrue(timelineDraft.getAllValues().stream().anyMatch(item -> "INCIDENT_RELATED".equals(item.eventType())));
        assertTrue(timelineDraft.getAllValues().stream().anyMatch(item -> "COMMENT".equals(item.eventType())));
    }

    @Test
    void relatedIncidentsMustStayInsideSameProject() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        when(repository.find("incident-1")).thenReturn(Optional.of(snapshot("incident-1", IncidentStatus.OPEN, "")));
        when(repository.find("incident-2")).thenReturn(Optional.of(snapshotForProject("incident-2", "project-b")));
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "unused", new CountingTransactions());

        SecurityException error = assertThrows(SecurityException.class,
                () -> service.relate("incident-1", "incident-2", "alice"));

        assertEquals("INCIDENT_RELATION_PROJECT_MISMATCH", error.getMessage());
    }

    @Test
    void userTimelineCannotSynthesizeSystemVerificationFacts() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        when(repository.find("incident-1")).thenReturn(Optional.of(snapshot("incident-1", IncidentStatus.OPEN, "")));
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "unused", new CountingTransactions());

        SecurityException error = assertThrows(SecurityException.class, () -> service.appendUserTimeline(
                "incident-1",
                new AppendIncidentTimelineCommand(
                        "VERIFICATION_SUCCEEDED", "fake", "fake", "INCIDENT", "incident-1", Map.of()),
                "alice"));

        assertEquals("INCIDENT_USER_TIMELINE_EVENT_FORBIDDEN:VERIFICATION_SUCCEEDED", error.getMessage());
    }

    @Test
    void helpfulFeedbackRequiresVerifiedCurrentOccurrence() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        when(repository.timeline("incident-1", 300)).thenReturn(List.of(
                timeline("incident-1", "VERIFICATION_INSUFFICIENT"),
                timeline("incident-1", "VERIFICATION_SUCCEEDED")));
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "unused", new CountingTransactions());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.confirmHelpful("incident-1", "alice"));

        assertEquals("INCIDENT_HELPFUL_REQUIRES_VERIFIED_RESOLUTION", error.getMessage());
    }

    @Test
    void recurringIncidentReusesStableIdentity() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        CountingTransactions transactions = new CountingTransactions();
        IncidentCommandApplicationService service = new IncidentCommandApplicationService(
                repository, audit, () -> "unused", transactions);
        CreateIncidentCommand command = new CreateIncidentCommand(
                "project-a", "schedule anomaly", "OPEN", "WARNING", "payment", "SCHEDULE",
                "error rate high", Map.of(), Map.of("scheduleId", 7), List.of("prometheus"));
        ArgumentCaptor<IncidentDraft> draft = ArgumentCaptor.forClass(IncidentDraft.class);
        when(repository.create(any())).thenAnswer(invocation -> {
            IncidentDraft value = invocation.getArgument(0);
            return snapshot(value.incidentId(), IncidentStatus.OPEN, "");
        });
        when(repository.find(any(String.class))).thenReturn(Optional.empty());

        IncidentSnapshot created = service.openRecurring("SCHEDULE:7:prometheus", command, "schedule");

        verify(repository).create(draft.capture());
        assertEquals(draft.getValue().incidentId(), created.incidentId());
        assertTrue(created.incidentId().startsWith("incident_"));
    }

    @Test
    void recoveryAlertEntersVerificationWithinOneUnitOfWork() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentAuditPort audit = mock(IncidentAuditPort.class);
        CountingTransactions transactions = new CountingTransactions();
        IncidentSnapshot opened = snapshot("incident-alert", IncidentStatus.OPEN, "");
        when(repository.upsertAlert(any(IncidentAlertDraft.class))).thenReturn(Optional.of(opened));
        when(repository.updateStatus(any(String.class), eq(IncidentStatus.VERIFYING)))
                .thenAnswer(invocation -> snapshot(invocation.getArgument(0), IncidentStatus.VERIFYING, ""));
        when(repository.find(any(String.class)))
                .thenAnswer(invocation -> Optional.of(snapshot(
                        invocation.getArgument(0), IncidentStatus.VERIFYING, "")));
        when(repository.appendTimeline(any())).thenReturn(Optional.of(timeline("incident-alert", "ALERT_TRIGGERED")));
        IncidentAlertApplicationService service = new IncidentAlertApplicationService(
                repository, audit, transactions);

        Optional<IncidentSnapshot> result = service.ingest(new IncidentAlertSignal(
                42L,
                7L,
                "high error rate",
                "project-a",
                "alertmanager",
                "RECOVERY_RESOLVED",
                "rule-7:fingerprint-1",
                "fingerprint-1",
                "HighErrorRate",
                "warning",
                "payment",
                "",
                "recovered",
                "{}"));

        assertEquals(IncidentStatus.VERIFYING, result.orElseThrow().status());
        assertEquals(1, transactions.count);
        ArgumentCaptor<IncidentAlertDraft> draft = ArgumentCaptor.forClass(IncidentAlertDraft.class);
        verify(repository).upsertAlert(draft.capture());
        assertEquals("project-a:rule-7:fingerprint-1", draft.getValue().projectDedupKey());
        verify(repository).updateStatus(draft.getValue().incidentId(), IncidentStatus.VERIFYING);
    }

    @Test
    void detailUsesAuthoritativeStructuredDiagnosisFromLinkedRun() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        IncidentDiagnosisQueryPort diagnoses = mock(IncidentDiagnosisQueryPort.class);
        IncidentSnapshot incident = snapshot("incident-1", IncidentStatus.INVESTIGATING, "run-1");
        IncidentRunSnapshot run = new IncidentRunSnapshot(
                "run-1", "SUCCEEDED", "", "2026-08-08 10:00:00", "2026-08-08 10:01:00", 60000L);
        DiagnosisResult diagnosis = new DiagnosisResult(
                "数据库连接池耗尽",
                List.of("支付请求失败率升高"),
                List.of(new DiagnosisResult.Fact(
                        "fact-1",
                        "连接池使用率达到 95%",
                        List.of(new DiagnosisResult.EvidenceRef("prometheus", "result-1", "hash-1")))),
                List.of(new DiagnosisResult.Inference("高概率连接池耗尽", List.of("fact-1"))),
                List.of(),
                List.of(),
                List.of("扩大连接池前先确认数据库容量"),
                List.of(),
                DiagnosisResult.EvidenceCompleteness.COMPLETE,
                DiagnosisResult.Confidence.HIGH,
                true,
                "前往执行中心审阅处置方案");
        when(repository.find("incident-1")).thenReturn(Optional.of(incident));
        when(repository.timeline("incident-1", 300)).thenReturn(List.of());
        when(repository.runs("incident-1")).thenReturn(List.of(run));
        when(diagnoses.latestByRunIds(List.of("run-1"))).thenReturn(Optional.of(diagnosis));
        IncidentQueryApplicationService service = new IncidentQueryApplicationService(repository, null, diagnoses);

        IncidentDetailProjection detail = service.detail("incident-1").orElseThrow();

        assertEquals("数据库连接池耗尽", detail.diagnosis().summary());
        assertEquals(DiagnosisResult.EvidenceCompleteness.COMPLETE, detail.diagnosis().evidenceCompleteness());
    }

    @Test
    void queryProjectsStatusBeforeFilteringAndClampsLimit() {
        IIncidentRepository repository = mock(IIncidentRepository.class);
        when(repository.list("project-a", null, 200)).thenReturn(List.of());
        IncidentQueryApplicationService service = new IncidentQueryApplicationService(repository);

        service.list(" project-a ", "open", 999);

        verify(repository).list("project-a", null, 200);
    }

    private IncidentSnapshot snapshot(String id, IncidentStatus status, String runId) {
        return snapshot(id, status, runId, "");
    }

    private IncidentSnapshot snapshot(String id, IncidentStatus status, String runId, String ownerUserId) {
        return snapshotForProject(id, "project-a", status, runId, ownerUserId);
    }

    private IncidentSnapshot snapshotForProject(String id, String projectId) {
        return snapshotForProject(id, projectId, IncidentStatus.OPEN, "", "");
    }

    private IncidentSnapshot snapshotForProject(
            String id,
            String projectId,
            IncidentStatus status,
            String runId,
            String ownerUserId) {
        return new IncidentSnapshot(
                1L,
                id,
                projectId,
                "database unavailable",
                status,
                "WARN",
                "mysql",
                "MANUAL",
                "",
                "",
                runId,
                ownerUserId,
                "detail",
                "{}",
                "{}",
                1,
                "[]",
                "", "", "", "", "", "", "");
    }

    private IncidentTimelineEntry timeline(String incidentId, String type) {
        return new IncidentTimelineEntry(
                1L, incidentId, type, "title", "detail", "alice",
                "INCIDENT", incidentId, "{}", "2026-07-23 15:00:00");
    }

    private static final class CountingTransactions implements IncidentTransactionPort {
        private int count;

        @Override
        public <T> T required(Supplier<T> action) {
            count++;
            return action.get();
        }
    }
}
