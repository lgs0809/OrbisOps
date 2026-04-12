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
    void presentsRevocationAndNeverInfersNoPriorWritesFromAnErrorAlone() {
        var revoked = policy.present(snapshot(Map.of(), Map.of(), "FAILED", "SKILL_RUNTIME_ACCESS_REVOKED"));
        assertTrue(revoked.errorMessage().contains("Skill 已停用或项目授权已撤回"));
        assertEquals(revoked.errorMessage(), policy.responseSummary(revoked));
        for (String reason : List.of("AGENT_RUN_FAILED", "timeout", "401 unauthorized", "422")) {
            String summary = policy.failureSummary(reason);
            assertTrue(summary.contains("回执"));
            org.junit.jupiter.api.Assertions.assertFalse(summary.contains("没有执行生产变更"));
            org.junit.jupiter.api.Assertions.assertFalse(summary.contains("没有执行工具"));
        }
    }

    @Test
    void capabilityFilteringIsNotAnAttemptedUnauthorizedAction() {
        assertEquals(List.of("调用被拒绝"), policy.statusSummaries(List.of(
                event(1, "RUNTIME_TOOL_AUTHORITY_BLOCKED", "BLOCKED", "工具超出当前 Agent Authority：hidden"),
                event(2, "TOOL_CALL_BLOCKED", "BLOCKED", "调用被拒绝")), "BLOCKED"));
    }

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

    @Test
    void hidesPlatformLandingInstructionBehindAUserSafeGoal() {
        String internal = "You are the platform Landing ReAct runtime.\n"
                + "Return exactly one final status: LANDED, LANDING_FAILED, or NEEDS_REPLAN.";
        AnalysisTaskSnapshot snapshot = snapshot(
                Map.of("query", internal, "metadata", Map.of(
                        "changePackageId", "cp-123",
                        "_trustedTriggerSource", "LANDING")),
                Map.of(),
                "FAILED",
                "Landing runtime failed");

        AnalysisTaskView view = policy.present(snapshot);

        assertEquals("受控变更包 cp-123 的落地运行", view.goal());
        assertEquals("LANDING", view.source());
        assertEquals("LANDING", view.taskType());
        assertEquals("受控变更包 cp-123 的落地运行",
                AnalysisTaskPresentationPolicy.publicGoal(snapshot.request(), "运行详情"));
        assertTrue(AnalysisTaskPresentationPolicy.isInternalRuntimePrompt(internal));
    }

    @Test
    void prefersExplicitPublicGoalOverRuntimeQuery() {
        AnalysisTaskSnapshot snapshot = snapshot(
                Map.of("query", "You are the platform Landing ReAct runtime.",
                        "metadata", Map.of("publicRunGoal", "受控变更包 cp-456 的落地运行")),
                Map.of(),
                "FAILED",
                "");

        assertEquals("受控变更包 cp-456 的落地运行", policy.present(snapshot).goal());
    }

    @Test
    void keepsAuthorityDetailsOutOfUserFacingUnknowns() {
        List<GraphEvent> events = List.of(event(
                1,
                "AUTHORITY_BLOCKED",
                "UNKNOWN",
                "工具超出当前 Agent Authority：UseProjectSkill"));

        assertEquals(List.of("当前运行权限不足，未执行未授权的操作。"),
                policy.statusSummaries(events, "UNKNOWN"));
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
