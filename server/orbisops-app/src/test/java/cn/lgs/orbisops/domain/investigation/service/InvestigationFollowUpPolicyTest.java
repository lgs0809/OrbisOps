package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationFollowUpDecision;
import cn.lgs.orbisops.domain.investigation.model.InvestigationFollowUpTask;
import cn.lgs.orbisops.domain.investigation.model.InvestigationObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationQuestionSignals;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cn.lgs.orbisops.domain.investigation.service.InvestigationFollowUpPolicy.SOURCE_ES;
import static cn.lgs.orbisops.domain.investigation.service.InvestigationFollowUpPolicy.SOURCE_MYSQL_SLOW_SQL;
import static cn.lgs.orbisops.domain.investigation.service.InvestigationFollowUpPolicy.SOURCE_PROM;
import static cn.lgs.orbisops.domain.investigation.service.InvestigationFollowUpPolicy.SOURCE_RAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationFollowUpPolicyTest {

    private final InvestigationFollowUpPolicy policy = new InvestigationFollowUpPolicy();

    @Test
    void pureKnowledgeQuestionSkipsRealtimeConditionalTasksAndStopsAfterRag() {
        InvestigationQuestionSignals signals = signals(
                "如何解释错误预算规则和只读边界",
                false,
                false,
                false,
                true,
                false);
        InvestigationObservation rag = observation(
                SOURCE_RAG,
                "FOUND",
                "已找到 SOP",
                List.of("只读边界说明"),
                List.of());

        InvestigationFollowUpDecision decision = policy.decide(input(
                "KNOWLEDGE_FIRST_INVESTIGATION",
                List.of(task(SOURCE_ES, "es-log-agent", 1)),
                List.of(rag),
                rag,
                Set.of(SOURCE_RAG),
                Set.of(),
                signals,
                true));

        assertTrue(decision.tasks().isEmpty());
        assertEquals(List.of("知识型问题无实时验证诉求，跳过条件实时数据源。"), decision.notes());
        assertTrue(policy.shouldStopAfterKnowledgeObservation(
                "KNOWLEDGE_FIRST_INVESTIGATION",
                rag,
                signals));
    }

    @Test
    void explicitRuntimeFilterPreventsKnowledgeOnlyClassification() {
        InvestigationQuestionSignals signals = signals(
                "解释 traceid 对应日志的错误规则",
                true,
                false,
                false,
                true,
                true);

        assertFalse(policy.pureKnowledgeQuestion(
                "KNOWLEDGE_FIRST_INVESTIGATION",
                signals));
    }

    @Test
    void prometheusAnomalyActivatesConditionalElasticsearchAndMysqlTasks() {
        InvestigationObservation prom = observation(
                SOURCE_PROM,
                "FOUND",
                "错误率升高并出现 timeout",
                List.of("5xx=8%"),
                List.of());

        InvestigationFollowUpDecision decision = policy.decide(input(
                "RUNTIME_INVESTIGATION",
                List.of(
                        task(SOURCE_ES, "es-log-agent", 2),
                        task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", 3),
                        task(SOURCE_RAG, "rag-knowledge-agent", 4)),
                List.of(prom),
                prom,
                Set.of(SOURCE_PROM),
                Set.of(),
                signals("检查当前错误率", false, true, false, false, false),
                true));

        assertEquals(
                List.of(SOURCE_ES, SOURCE_MYSQL_SLOW_SQL, SOURCE_RAG),
                decision.tasks().stream().map(InvestigationFollowUpTask::source).toList());
        assertEquals(3, decision.notes().size());
    }

    @Test
    void insufficientElasticsearchAddsPrometheusMysqlAndRagFallbacks() {
        InvestigationObservation es = observation(
                SOURCE_ES,
                "INSUFFICIENT",
                "日志显示数据库连接池耗时",
                List.of("sql timeout"),
                List.of("缺少指标和慢 SQL 样本"));

        InvestigationFollowUpDecision decision = policy.decide(input(
                "RUNTIME_INVESTIGATION",
                List.of(),
                List.of(es),
                es,
                Set.of(SOURCE_ES),
                Set.of(),
                signals("排查数据库慢 SQL 并解释原因", true, true, true, true, false),
                true));

        assertEquals(
                List.of(SOURCE_PROM, SOURCE_MYSQL_SLOW_SQL, SOURCE_RAG),
                decision.tasks().stream().map(InvestigationFollowUpTask::source).toList());
        assertEquals(3, decision.notes().size());
    }

    @Test
    void queuedAndExecutedSourcesAreNeverAddedAgain() {
        InvestigationObservation es = observation(
                SOURCE_ES,
                "INSUFFICIENT",
                "数据库耗时",
                List.of("sql timeout"),
                List.of());

        InvestigationFollowUpDecision decision = policy.decide(input(
                "RUNTIME_INVESTIGATION",
                List.of(task(SOURCE_PROM, "prometheus-agent", null)),
                List.of(es),
                es,
                Set.of(SOURCE_ES, SOURCE_MYSQL_SLOW_SQL),
                Set.of(SOURCE_PROM, SOURCE_RAG),
                signals("排查慢 SQL 并解释", true, true, true, true, false),
                true));

        assertTrue(decision.tasks().isEmpty());
    }

    @Test
    void mysqlFallbackHonorsFeatureFlag() {
        InvestigationObservation prom = observation(
                SOURCE_PROM,
                "INSUFFICIENT",
                "数据库连接池延迟",
                List.of("sql timeout"),
                List.of());

        InvestigationFollowUpDecision decision = policy.decide(input(
                "RUNTIME_INVESTIGATION",
                List.of(),
                List.of(prom),
                prom,
                Set.of(SOURCE_PROM),
                Set.of(SOURCE_ES, SOURCE_RAG),
                signals("检查数据库耗时", false, true, true, false, false),
                false));

        assertTrue(decision.tasks().isEmpty());
    }

    private InvestigationFollowUpPolicy.Input input(
            String intent,
            List<InvestigationFollowUpTask> conditionalTasks,
            List<InvestigationObservation> observations,
            InvestigationObservation latest,
            Set<String> executed,
            Set<String> queued,
            InvestigationQuestionSignals signals,
            boolean mysqlEnabled) {
        return new InvestigationFollowUpPolicy.Input(
                intent,
                conditionalTasks,
                observations,
                latest,
                executed,
                queued,
                signals,
                mysqlEnabled);
    }

    private InvestigationObservation observation(
            String source,
            String status,
            String summary,
            List<String> evidence,
            List<String> gaps) {
        return new InvestigationObservation(source, status, summary, evidence, gaps);
    }

    private InvestigationQuestionSignals signals(
            String question,
            boolean log,
            boolean metric,
            boolean slowSql,
            boolean knowledge,
            boolean runtimeFilter) {
        return new InvestigationQuestionSignals(
                question,
                log,
                metric,
                slowSql,
                knowledge,
                runtimeFilter);
    }

    private InvestigationFollowUpTask task(String source, String agent, Integer priority) {
        return new InvestigationFollowUpTask(
                source,
                agent,
                "goal",
                "reason",
                priority,
                "condition");
    }
}
