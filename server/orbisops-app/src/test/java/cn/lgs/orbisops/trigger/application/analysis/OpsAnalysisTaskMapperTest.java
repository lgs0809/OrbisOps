package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.application.analysis.AnalysisTaskDetail;
import cn.lgs.orbisops.domain.analysis.model.AnalysisFeedbackType;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskIncident;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisTaskMapperTest {

    private final OpsAnalysisTaskMapper mapper = new OpsAnalysisTaskMapper();

    @Test
    void preservesTaskDetailAndFeedbackHttpContracts() {
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        AnalysisTaskView task = new AnalysisTaskView(
                "run-1", "project-1", "session-1", "user-1", "agent-1", 3,
                "a".repeat(64), "PROJECT_PRE_APPROVAL", "SUCCEEDED", "ALERTMANAGER", "ALERT",
                "分析告警", Map.of("content", "分析完成"), "", "", now, now.plusSeconds(1));
        AnalysisTaskFeedback feedback = new AnalysisTaskFeedback(
                "analysis-feedback-1", "project-1", "run-1", AnalysisFeedbackType.HELPFUL,
                "有效", "reviewer-1", now.plusSeconds(2));
        AnalysisTaskDetail detail = new AnalysisTaskDetail(
                task,
                List.of(new GraphEvent(
                        "run-1", "", 1, "ROOT_CAUSE", "node-1", "AGENT", "investigator", "runtime",
                        "COMPLETE", "连接池耗尽", "", "", null, Map.of())),
                List.of(Map.of("evidenceId", "evidence-1")),
                List.of(Map.of("resultId", "result-1")),
                List.of(Map.of("packageId", "package-1")),
                List.of(new AnalysisTaskIncident(
                        "incident-1", "订单错误率升高", "OPEN", "HIGH", "order-service", 3, now, now)),
                List.of(Map.of("skillId", "logs-search")),
                List.of(feedback),
                List.of("连接池耗尽"),
                List.of(),
                List.of("缺少慢查询证据"),
                List.of("调整连接池"),
                true,
                "分析完成");

        Map<String, Object> taskView = mapper.taskView(task);
        assertEquals("ALERTMANAGER", taskView.get("source"));
        assertEquals("ALERT", taskView.get("taskType"));
        assertEquals("分析告警", taskView.get("goal"));
        assertEquals("a".repeat(64), taskView.get("agentDefinitionHash"));

        Map<String, Object> detailView = mapper.detailView(detail);
        assertEquals("分析完成", detailView.get("summary"));
        assertEquals(true, detailView.get("evidenceSufficient"));
        assertEquals(1, ((List<?>) detailView.get("events")).size());
        assertEquals(1, ((List<?>) detailView.get("incidents")).size());
        Map<?, ?> feedbackHistory = (Map<?, ?>) ((List<?>) detailView.get("feedback")).get(0);
        assertTrue(feedbackHistory.containsKey("commentText"));
        assertTrue(feedbackHistory.containsKey("createdBy"));

        Map<String, Object> feedbackView = mapper.feedbackView(feedback);
        assertEquals("HELPFUL", feedbackView.get("feedbackType"));
        assertEquals("有效", feedbackView.get("comment"));
        assertEquals("reviewer-1", feedbackView.get("actor"));
    }
}
