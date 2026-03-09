package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisFeedbackRepository;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisTaskReadRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisFeedbackType;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskIncident;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskSnapshot;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisTaskApplicationServicesTest {

    @Test
    void listUsesSharedRunReadModelAndFiltersNormalizedSource() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        tasks.rows = List.of(snapshot(
                "run-alert-1",
                "project-1",
                "session-1",
                Map.of("query", "分析告警", "metadata", Map.of("triggerSource", "ALERTMANAGER")),
                Map.of("content", "发现订单错误率升高"),
                "SUCCEEDED"));
        FakeFeedbackRepository feedback = new FakeFeedbackRepository();
        AnalysisTaskQueryApplicationService service = query(tasks, feedback, new FakeSupplement());

        List<AnalysisTaskView> result = service.list(
                "project-1", "ALERTMANAGER", "SUCCEEDED", 500);

        assertEquals(1, result.size());
        assertEquals("ALERT", result.get(0).taskType());
        assertEquals("分析告警", result.get(0).goal());
        assertEquals(200, tasks.lastLimit);
        assertEquals("SUCCEEDED", tasks.lastStatus);
    }

    @Test
    void detailFailsClosedForAnotherProjectAndAggregatesTypedSupplements() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        tasks.row = snapshot(
                "run-1", "project-1", "session-1",
                Map.of("query", "查日志"), Map.of("content", "分析完成"), "SUCCEEDED");
        tasks.incidents = List.of(new AnalysisTaskIncident(
                "incident-1", "订单错误率升高", "OPEN", "HIGH", "order-service", 3,
                Instant.parse("2026-07-23T00:00:00Z"), Instant.parse("2026-07-23T00:05:00Z")));
        FakeFeedbackRepository feedback = new FakeFeedbackRepository();
        feedback.items = List.of(new AnalysisTaskFeedback(
                "analysis-feedback-1", "project-1", "run-1", AnalysisFeedbackType.HELPFUL,
                "有效", "reviewer-1", Instant.parse("2026-07-23T00:06:00Z")));
        FakeSupplement supplement = new FakeSupplement();
        supplement.events = List.of(new GraphEvent(
                "run-1", "", 1, "ROOT_CAUSE", "node-1", "AGENT", "investigator", "runtime",
                "COMPLETE", "数据库连接池耗尽", "", "", null, Map.of()));
        supplement.evidence = List.of(Map.of("evidenceId", "evidence-1"));
        supplement.toolResults = List.of(Map.of("resultId", "result-1"));
        supplement.changePackages = List.of(Map.of("packageId", "package-1"));
        supplement.skillUsages = List.of(Map.of("skillId", "logs-search"));
        AnalysisTaskQueryApplicationService service = query(tasks, feedback, supplement);

        AnalysisTaskDetail detail = service.detail("project-1", "run-1");

        assertEquals("分析完成", detail.summary());
        assertTrue(detail.evidenceSufficient());
        assertEquals(List.of("数据库连接池耗尽"), detail.hypotheses());
        assertEquals(1, detail.incidents().size());
        assertEquals(1, detail.feedback().size());
        assertEquals("session-1", supplement.lastSessionId);

        assertThrows(IllegalArgumentException.class,
                () -> service.detail("project-2", "run-1"));
    }

    @Test
    void feedbackPersistsTypedRecordThenReconcilesOutcomeAndAudits() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        tasks.row = snapshot(
                "run-1", "project-1", "session-1",
                Map.of("query", "查日志"), Map.of("content", "分析完成"), "SUCCEEDED");
        FakeFeedbackRepository feedback = new FakeFeedbackRepository();
        FakeOutcome outcome = new FakeOutcome();
        FakeAudit audit = new FakeAudit();
        FakeTransaction transaction = new FakeTransaction();
        AnalysisTaskQueryApplicationService queries = query(tasks, feedback, new FakeSupplement());
        AnalysisTaskFeedbackApplicationService service = new AnalysisTaskFeedbackApplicationService(
                queries,
                feedback,
                outcome,
                audit,
                () -> "analysis-feedback-fixed",
                Clock.fixed(Instant.parse("2026-07-23T00:10:00Z"), ZoneOffset.UTC),
                transaction);

        AnalysisTaskFeedback saved = service.record(
                "project-1", "run-1", "INSUFFICIENT_EVIDENCE", "缺少日志样本", "reviewer-1");

        assertEquals("analysis-feedback-fixed", saved.feedbackId());
        assertEquals(AnalysisFeedbackType.INSUFFICIENT_EVIDENCE, saved.feedbackType());
        assertEquals(saved, feedback.saved);
        assertEquals(1, transaction.calls);
        assertTrue(outcome.negative);
        assertFalse(outcome.evidenceSufficient);
        assertEquals("project-1", outcome.projectId);
        assertEquals("run-1", outcome.runId);
        assertEquals(saved, audit.feedback);
        assertEquals("run-1", audit.task.runId());
    }

    private AnalysisTaskQueryApplicationService query(
            FakeTaskRepository tasks,
            FakeFeedbackRepository feedback,
            FakeSupplement supplement) {
        return new AnalysisTaskQueryApplicationService(tasks, feedback, supplement);
    }

    private AnalysisTaskSnapshot snapshot(
            String runId,
            String projectId,
            String sessionId,
            Map<String, Object> request,
            Map<String, Object> response,
            String status) {
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        return new AnalysisTaskSnapshot(
                runId, projectId, sessionId, "user-1", "agent-1", 3,
                "a".repeat(64), "PROJECT_PRE_APPROVAL", status,
                request, response, "", now, now.plusSeconds(1));
    }

    private static final class FakeTaskRepository implements IAnalysisTaskReadRepository {
        private List<AnalysisTaskSnapshot> rows = List.of();
        private AnalysisTaskSnapshot row;
        private List<AnalysisTaskIncident> incidents = List.of();
        private String lastStatus;
        private int lastLimit;

        @Override
        public List<AnalysisTaskSnapshot> list(String projectId, String status, int limit) {
            lastStatus = status;
            lastLimit = limit;
            return rows.stream().filter(item -> item.projectId().equals(projectId)).toList();
        }

        @Override
        public Optional<AnalysisTaskSnapshot> find(String projectId, String runId) {
            return Optional.ofNullable(row)
                    .filter(item -> item.projectId().equals(projectId) && item.runId().equals(runId));
        }

        @Override
        public List<AnalysisTaskIncident> findIncidents(String projectId, String runId) {
            return incidents;
        }
    }

    private static final class FakeFeedbackRepository implements IAnalysisFeedbackRepository {
        private List<AnalysisTaskFeedback> items = new ArrayList<>();
        private AnalysisTaskFeedback saved;

        @Override
        public AnalysisTaskFeedback save(AnalysisTaskFeedback feedback) {
            saved = feedback;
            items = new ArrayList<>(items);
            items.add(feedback);
            return feedback;
        }

        @Override
        public List<AnalysisTaskFeedback> list(String projectId, String runId) {
            return items.stream()
                    .filter(item -> item.projectId().equals(projectId) && item.runId().equals(runId))
                    .toList();
        }
    }

    private static final class FakeSupplement implements AnalysisTaskSupplementPort {
        private List<GraphEvent> events = List.of();
        private List<Map<String, Object>> evidence = List.of();
        private List<Map<String, Object>> toolResults = List.of();
        private List<Map<String, Object>> changePackages = List.of();
        private List<Map<String, Object>> skillUsages = List.of();
        private String lastSessionId;

        @Override
        public List<GraphEvent> events(String runId, int limit) {
            return events;
        }

        @Override
        public List<Map<String, Object>> evidence(String projectId, String runId, int limit) {
            return evidence;
        }

        @Override
        public List<Map<String, Object>> toolResults(String projectId, String runId, int limit) {
            return toolResults;
        }

        @Override
        public List<Map<String, Object>> changePackages(String projectId, String sessionId, int limit) {
            lastSessionId = sessionId;
            return changePackages;
        }

        @Override
        public List<Map<String, Object>> skillUsages(String projectId, String runId) {
            return skillUsages;
        }
    }

    private static final class FakeOutcome implements AnalysisTaskOutcomePort {
        private String projectId;
        private String runId;
        private boolean negative;
        private boolean evidenceSufficient;

        @Override
        public void reconcile(
                String projectId,
                String runId,
                boolean negativeFeedback,
                boolean evidenceSufficient) {
            this.projectId = projectId;
            this.runId = runId;
            this.negative = negativeFeedback;
            this.evidenceSufficient = evidenceSufficient;
        }
    }

    private static final class FakeAudit implements AnalysisTaskAuditPort {
        private AnalysisTaskView task;
        private AnalysisTaskFeedback feedback;

        @Override
        public void recordFeedback(AnalysisTaskView task, AnalysisTaskFeedback feedback) {
            this.task = task;
            this.feedback = feedback;
        }
    }

    private static final class FakeTransaction implements AnalysisTaskTransactionPort {
        private int calls;

        @Override
        public <T> T required(Supplier<T> action) {
            calls++;
            return action.get();
        }
    }
}
