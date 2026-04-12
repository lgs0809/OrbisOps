package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningPlan;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningSignals;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningTask;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationPlanningPolicyTest {

    private final InvestigationPlanningPolicy policy = new InvestigationPlanningPolicy();

    @Test
    void knowledgeExplanationIsRagOnlyEvenWhenQuestionContainsErrorCodeVocabulary() {
        InvestigationPlanningPlan plan = policy.initialPlan(new InvestigationPlanningPolicy.InitialInput(
                signals("错误码字段应该包含什么，如何解释", true, false, false, true, false, false),
                false,
                sources()));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.intent());
        assertEquals(List.of(InvestigationPlanningPolicy.SOURCE_RAG), taskSources(plan.tasks()));
        assertEquals(Set.of(
                InvestigationPlanningPolicy.SOURCE_ES,
                InvestigationPlanningPolicy.SOURCE_PROM,
                InvestigationPlanningPolicy.SOURCE_MYSQL_SLOW_SQL), Set.copyOf(taskSources(plan.skippedTasks())));
    }

    @Test
    void explicitTraceFilterKeepsLogFirstSemantics() {
        InvestigationPlanningPlan plan = policy.initialPlan(new InvestigationPlanningPolicy.InitialInput(
                signals("检查 traceId trace-123 的 ERROR", true, false, false, false, true, false),
                false,
                sources()));

        assertEquals("LOG_FIRST_INVESTIGATION", plan.intent());
        assertEquals(List.of(InvestigationPlanningPolicy.SOURCE_ES), taskSources(plan.tasks()));
        assertTrue(taskSources(plan.conditionalTasks()).contains(InvestigationPlanningPolicy.SOURCE_RAG));
    }

    @Test
    void slowSqlSignalStartsFromDatabaseFactSource() {
        InvestigationPlanningPlan plan = policy.initialPlan(new InvestigationPlanningPolicy.InitialInput(
                signals("检查 MySQL 慢 SQL 和索引", false, false, true, false, false, false),
                false,
                sources()));

        assertEquals("SQL_FIRST_INVESTIGATION", plan.intent());
        assertEquals(List.of(InvestigationPlanningPolicy.SOURCE_MYSQL_SLOW_SQL), taskSources(plan.tasks()));
        assertTrue(taskSources(plan.conditionalTasks()).contains(InvestigationPlanningPolicy.SOURCE_PROM));
    }

    @Test
    void insufficientElasticsearchObservationAddsMetricsAndKnowledge() {
        InvestigationPlanningPlan plan = policy.replan(new InvestigationPlanningPolicy.ReplanInput(
                signals("检查错误日志", true, false, false, false, false, false),
                List.of(new InvestigationPlanningObservation(
                        InvestigationPlanningPolicy.SOURCE_ES,
                        "INSUFFICIENT",
                        "日志证据不足",
                        List.of(),
                        List.of("缺少指标"),
                        List.of("查询 prometheus"))),
                1,
                2,
                sources()));

        assertEquals("REPLAN_CONTINUE", plan.intent());
        assertEquals(List.of(
                InvestigationPlanningPolicy.SOURCE_PROM,
                InvestigationPlanningPolicy.SOURCE_RAG), taskSources(plan.tasks()));
    }

    @Test
    void ragObservationStopsPureKnowledgeReplan() {
        InvestigationPlanningPlan plan = policy.replan(new InvestigationPlanningPolicy.ReplanInput(
                signals("如何解释指标字典", false, false, false, true, false, false),
                List.of(new InvestigationPlanningObservation(
                        InvestigationPlanningPolicy.SOURCE_RAG,
                        "FOUND",
                        "已返回指标字典",
                        List.of("定义"),
                        List.of(),
                        List.of())),
                1,
                2,
                sources()));

        assertEquals("STOP", plan.intent());
        assertTrue(plan.tasks().isEmpty());
        assertTrue(plan.reason().contains("不继续查询实时数据源"));
    }

    @Test
    void knowledgeGuardrailReplacesRealtimeLlmPlanAndPreservesChangeIntent() {
        InvestigationPlanningPlan llmPlan = plan(
                "INCIDENT_INVESTIGATION",
                "LLM wants metrics",
                true,
                "调整告警阈值",
                List.of(task(InvestigationPlanningPolicy.SOURCE_PROM)));
        InvestigationPlanningPlan fallback = plan(
                "KNOWLEDGE_FIRST_INVESTIGATION",
                "knowledge fallback",
                false,
                "",
                List.of(task(InvestigationPlanningPolicy.SOURCE_RAG)));

        InvestigationPlanningPlan guarded = policy.applyGuardrails(new InvestigationPlanningPolicy.GuardrailInput(
                llmPlan,
                fallback,
                signals("告警阈值规则应该如何解释", false, false, false, true, false, false)));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", guarded.intent());
        assertEquals(List.of(InvestigationPlanningPolicy.SOURCE_RAG), taskSources(guarded.tasks()));
        assertTrue(guarded.changeRequested());
        assertEquals("调整告警阈值", guarded.changeIntent());
    }

    @Test
    void explicitChangePackageRequestSurvivesLlmFalseWhileNoLandingRemainsScopedNegation() {
        InvestigationPlanningSignals requestSignals = signals(
                "请排查当前 5xx，然后创建一个用于重启服务的 ChangePackage，包含 restart_service；不要执行 Landing。",
                true, true, false, false, false, false);
        InvestigationPlanningPlan fallback = policy.initialPlan(new InvestigationPlanningPolicy.InitialInput(
                requestSignals, true, sources()));
        InvestigationPlanningPlan llmPlan = plan(
                "INCIDENT_INVESTIGATION",
                "mock model false",
                false,
                "",
                List.of(task(InvestigationPlanningPolicy.SOURCE_PROM)));

        InvestigationPlanningPlan guarded = policy.applyGuardrails(new InvestigationPlanningPolicy.GuardrailInput(
                llmPlan, fallback, requestSignals));

        assertTrue(guarded.changeRequested());
        assertTrue(guarded.changeIntent().contains("ChangePackage"));
        assertTrue(guarded.changeIntent().contains("不要执行 Landing"));
    }

    @Test
    void explicitNoRestartAnalysisOnlyDoesNotBecomeChangeRequest() {
        InvestigationPlanningPlan initial = policy.initialPlan(new InvestigationPlanningPolicy.InitialInput(
                signals("只分析当前 5xx，不要修复，不要重启服务。", true, true, false, false, false, false),
                true,
                sources()));

        assertFalse(initial.changeRequested());
        assertEquals("", initial.changeIntent());
    }

    @Test
    void aliasesAndAllSourcesOrderRemainStable() {
        assertEquals(InvestigationPlanningPolicy.SOURCE_RAG, InvestigationPlanningPolicy.normalizeSource("pgvector"));
        assertEquals(InvestigationPlanningPolicy.SOURCE_ES, InvestigationPlanningPolicy.normalizeSource("ES"));
        assertEquals(InvestigationPlanningPolicy.SOURCE_PROM, InvestigationPlanningPolicy.normalizeSource("metrics"));
        assertEquals(InvestigationPlanningPolicy.SOURCE_MYSQL_SLOW_SQL, InvestigationPlanningPolicy.normalizeSource("slow_sql"));

        InvestigationPlanningPlan plan = policy.allSourcesPlan(new InvestigationPlanningPolicy.AllSourcesInput(
                signals("baseline", false, false, false, false, false, false),
                sources()));
        assertEquals(List.of(
                InvestigationPlanningPolicy.SOURCE_RAG,
                InvestigationPlanningPolicy.SOURCE_ES,
                InvestigationPlanningPolicy.SOURCE_PROM,
                InvestigationPlanningPolicy.SOURCE_MYSQL_SLOW_SQL), taskSources(plan.tasks()));

        InvestigationPlanningPlan stop = policy.allSourcesReplanStop(null);
        assertNull(stop.changeRequested());
        assertNull(stop.changeIntent());
        assertFalse(stop.reason().isBlank());
    }

    private InvestigationPlanningSignals signals(
            String question,
            boolean log,
            boolean metric,
            boolean slowSql,
            boolean knowledge,
            boolean traceId,
            boolean orderId) {
        return new InvestigationPlanningSignals(
                question,
                question.toLowerCase(),
                "filters",
                question.isBlank(),
                log,
                metric,
                slowSql,
                knowledge,
                traceId,
                orderId);
    }

    private Set<String> sources() {
        return new LinkedHashSet<>(List.of(
                InvestigationPlanningPolicy.SOURCE_RAG,
                InvestigationPlanningPolicy.SOURCE_ES,
                InvestigationPlanningPolicy.SOURCE_PROM,
                InvestigationPlanningPolicy.SOURCE_MYSQL_SLOW_SQL));
    }

    private InvestigationPlanningPlan plan(
            String intent,
            String reason,
            Boolean changeRequested,
            String changeIntent,
            List<InvestigationPlanningTask> tasks) {
        return new InvestigationPlanningPlan(
                intent,
                reason,
                changeRequested,
                changeIntent,
                tasks,
                List.of(),
                List.of());
    }

    private InvestigationPlanningTask task(String source) {
        return new InvestigationPlanningTask(source, source + "-agent", "goal", "reason", 1, null);
    }

    private List<String> taskSources(List<InvestigationPlanningTask> tasks) {
        return tasks.stream().map(InvestigationPlanningTask::source).toList();
    }
}
