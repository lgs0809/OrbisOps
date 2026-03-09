package cn.lgs.orbisops.domain.analysis.service;

import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskSnapshot;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisTaskPresentationPolicyTest {

    private final AnalysisTaskPresentationPolicy policy = new AnalysisTaskPresentationPolicy();

    @Test
    void normalizesAlertSourceAndPresentsLegacyErrorEnvelopeAsFailed() {
        AnalysisTaskSnapshot alert = snapshot(
                Map.of("query", "分析告警", "metadata", Map.of("triggerSource", "ALERTMANAGER")),
                Map.of("content", "发现订单错误率升高"),
                "SUCCEEDED",
                "");
        AnalysisTaskView alertView = policy.present(alert);
        assertEquals("ALERT", alertView.taskType());
        assertEquals("ALERTMANAGER", alertView.source());
        assertEquals("分析告警", alertView.goal());
        assertEquals("SUCCEEDED", alertView.status());

        AnalysisTaskSnapshot failed = snapshot(
                Map.of("query", "查日志"),
                Map.of("content", "Exception: 422 - {\"detail\":[]}"),
                "SUCCEEDED",
                "");
        AnalysisTaskView failedView = policy.present(failed);
        assertEquals("FAILED", failedView.status());
        assertTrue(failedView.errorMessage().startsWith("模型工具协议校验失败"));
        assertTrue(failedView.technicalError().startsWith("Exception: 422"));
        assertEquals(failedView.errorMessage(), policy.responseSummary(failedView));
    }

    @Test
    void derivesHypothesesUnknownsAndRecommendationsFromTypedEvents() {
        List<GraphEvent> events = List.of(
                event(1, "HYPOTHESIS_CREATED", "RUNNING", "数据库连接池耗尽"),
                event(2, "DIAGNOSIS", "UNKNOWN", "仍缺少慢查询证据"),
                event(3, "CHANGE_PACKAGE", "READY", "建议调整连接池上限"));

        assertEquals(List.of("数据库连接池耗尽", "仍缺少慢查询证据"),
                policy.summaries(events, "HYPOTHESIS", "DIAGNOSIS", "ROOT_CAUSE"));
        assertEquals(List.of("仍缺少慢查询证据"),
                policy.statusSummaries(events, "UNKNOWN", "INSUFFICIENT", "BLOCKED"));
        assertEquals(List.of("建议调整连接池上限"),
                policy.summaries(events, "RECOMMEND", "REPLAN", "CHANGE_PACKAGE"));
    }

    private AnalysisTaskSnapshot snapshot(
            Map<String, Object> request,
            Map<String, Object> response,
            String status,
            String error) {
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        return new AnalysisTaskSnapshot(
                "run-1", "project-1", "session-1", "user-1", "agent-1", 3,
                "a".repeat(64), "PROJECT_PRE_APPROVAL", status,
                request, response, error, now, now.plusSeconds(1));
    }

    private GraphEvent event(long sequence, String type, String status, String summary) {
        return new GraphEvent(
                "run-1", "", sequence, type, "node-1", "AGENT", "investigator", "runtime",
                status, summary, "", "", null, Map.of());
    }
}
