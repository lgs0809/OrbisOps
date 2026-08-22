package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpsMainAgentPlannerTest {

    @Test
    public void shouldRouteBlankQuestionToPrometheusFirst() {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(""), OpsQuestionContext.from(""));

        assertEquals("GENERAL_HEALTH_CHECK", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_RAG));
    }

    @Test
    public void shouldUseOnlySkillsConfiguredByCurrentPlannerNode() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent":"METRIC_FIRST_INVESTIGATION",
                          "reason":"查询指标",
                          "tasks":[{"routeKey":"prometheus","agent":"prometheus-agent","goal":"检查实例","reason":"需要实时指标","priority":1,"condition":null}],
                          "conditionalTasks":[],
                          "skippedTasks":[]
                        }
                        """));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);
        String choices = """
                {"choices":[{"routeKey":"prometheus","targetDescription":"查询当前项目 Prometheus"}]}
                """;

        planner.plan(
                request("检查支付服务实例"),
                OpsQuestionContext.from("检查支付服务实例"),
                choices,
                List.of("payment-ops"));

        verify(llmClient).chatJsonObjectWithEagerSkillContext(
                eq("ops-main-agent-planner"),
                anyString(),
                anyString(),
                eq(List.of("payment-ops")));
    }

    @Test
    public void shouldLetMainAgentIdentifyExplicitChangeIntent() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent":"METRIC_FIRST_INVESTIGATION",
                          "reason":"先确认实例状态，再决定是否创建受控重启提案",
                          "changeRequested":true,
                          "changeIntent":"修复不可用的示例服务实例",
                          "tasks":[{"routeKey":"prometheus","agent":"prometheus-agent","goal":"确认实例 up 指标","reason":"需要实时证据","priority":1,"condition":null}],
                          "conditionalTasks":[],
                          "skippedTasks":[]
                        }
                        """));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);
        String choices = """
                {"routers":[{"choices":[{"routeKey":"prometheus","conditionType":"route_match","condition":"prometheus","targetDescription":"查询当前项目 Prometheus"}]}]}
                """;

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(
                request("查清实例下线原因，如果确认异常就生成修复提案"),
                OpsQuestionContext.from("查清实例下线原因，如果确认异常就生成修复提案"),
                choices,
                List.of());

        assertTrue(plan.getChangeRequested());
        assertEquals("修复不可用的示例服务实例", plan.getChangeIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteLogQuestionToElasticsearchFirst() {
        String question = "traceId:abc123XYZ789 /api/demo-project/join 报错 ERR_LOCK_001";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("LOG_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_ES), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_RAG));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
    }

    @Test
    public void shouldRouteMetricQuestionToPrometheusFirst() {
        String question = "最近接口 p95 延迟和 CPU 趋势";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("METRIC_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
    }

    @Test
    public void shouldRouteUriMetricQuestionToPrometheusFirst() {
        String question = "最近 /api/trade/settlement p99 延迟有没有升高";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("METRIC_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
    }

    @Test
    public void shouldRouteMetricNameWithErrorRateToPrometheusFirst() {
        String question = "callback_error_rate 或回调错误率最近是否上升";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("METRIC_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteMetricAnomalyQuestionToPrometheusFirst() {
        String question = "最近实例在线、CPU 和 JVM heap 使用率是否异常";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("METRIC_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteRuntimeSummaryWithoutLogsToPrometheusFirst() {
        String question = "生成首页运行摘要，不需要最近日志";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("METRIC_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteAverageResponseQuestionToPrometheusFirst() {
        String question = "结算回调平均响应时间最近 30 分钟怎么样";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("METRIC_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteMetricDictionaryQuestionToRagFirst() {
        String question = "Prometheus 里的 QPS、P95、P99 指标含义是什么，查知识库解释一下";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
    }

    @Test
    public void shouldRouteDatasourceBoundaryQuestionToRagOnly() {
        String question = "RAG、ES、Prometheus、MySQL 慢 SQL 的数据源边界是什么";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL));
    }

    @Test
    public void shouldRouteSafetyPolicyQuestionToRagOnly() {
        String question = "如果 CPU 高，AI 能不能自动扩容，还是只能给建议";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
    }

    @Test
    public void shouldRouteKnowledgeQuestionToRagOnly() {
        String question = "如何设计示例运维 Runbook 和历史案例沉淀";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
    }

    @Test
    public void shouldRouteFailureSopQuestionToRagOnly() {
        String question = "示例锁单失败一般怎么排查，先从运维知识库找 SOP";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL));
    }

    @Test
    public void shouldRouteSlowSqlQuestionToMysqlSlowSqlFirst() {
        String question = "MySQL 慢查询很多，帮我看一下是不是索引失效或者全表扫描";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("SQL_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_RAG));
    }

    @Test
    public void shouldRouteSqlEvidenceQuestionToMysqlSlowSqlFirst() {
        String question = "数据库慢查询是否和索引失效有关，给我 SQL 证据和优化建议";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("SQL_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteSqlDigestQuestionToMysqlSlowSqlFirst() {
        String question = "performance_schema 里 trade_order 高耗时 SQL digest";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("SQL_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL), sources(plan.getTasks()));
    }

    @Test
    public void shouldGuardrailRowsSentLlmPlanToMysqlSlowSql() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent": "KNOWLEDGE_FIRST_INVESTIGATION",
                          "reason": "先确认 rows_sent 语义",
                          "tasks": [{"source":"rag","agent":"rag-knowledge-agent","goal":"解释 rows_sent","reason":"知识解释","priority":1,"condition":null}],
                          "conditionalTasks": [],
                          "skippedTasks": []
                        }
                        """));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);

        String question = "rows_sent 很高的 MySQL 查询有没有风险";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(request(question), OpsQuestionContext.from(question));

        assertEquals("SQL_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL), sources(plan.getTasks()));
        assertTrue(plan.getReason().contains("慢 SQL 信号"));
    }

    @Test
    public void shouldGuardrailPureKnowledgeLlmPlanToRagOnly() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent": "KNOWLEDGE_FIRST_INVESTIGATION",
                          "reason": "先查知识库，同时准备确认线上影响",
                          "tasks": [{"source":"rag","agent":"rag-knowledge-agent","goal":"查错误码字典","reason":"知识解释","priority":1,"condition":null}],
                          "conditionalTasks": [{"source":"elasticsearch","agent":"es-log-agent","goal":"查当前日志","reason":"确认线上是否发生","priority":2,"condition":"if rag insufficient"}],
                          "skippedTasks": []
                        }
                        """));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);

        String question = "ERR_LOCK_001 一般代表什么，历史案例里怎么处理";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
        assertTrue(plan.getConditionalTasks().isEmpty());
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
    }

    @Test
    public void shouldGuardrailExplicitLogOnlyLlmPlanToEsFirst() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent": "INCIDENT_INVESTIGATION",
                          "reason": "一次性查询所有数据源更全面",
                          "tasks": [
                            {"source":"rag","agent":"rag-knowledge-agent","goal":"查 SOP","reason":"补充经验","priority":1,"condition":null},
                            {"source":"prometheus","agent":"prometheus-agent","goal":"查指标","reason":"确认影响面","priority":1,"condition":null},
                            {"source":"elasticsearch","agent":"es-log-agent","goal":"查日志","reason":"用户要日志","priority":1,"condition":null},
                            {"source":"mysql_slow_sql","agent":"mysql-slow-sql-agent","goal":"查慢 SQL","reason":"排查数据库","priority":1,"condition":null}
                          ],
                          "conditionalTasks": [],
                          "skippedTasks": []
                        }
                        """));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);

        String question = "只读检查 demo-project 最近 10 分钟错误日志，不要生成变更，不要执行生产写操作";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(request(question), OpsQuestionContext.from(question));

        assertEquals("LOG_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_ES), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_RAG));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
        assertTrue(plan.getReason().contains("显式日志排查请求"));
    }

    @Test
    public void shouldGuardrailGraphScopedLogOnlyLlmPlanToEsFirst() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent": "INCIDENT_INVESTIGATION",
                          "reason": "Graph 中也全查",
                          "tasks": [
                            {"routeKey":"rag","agent":"rag-knowledge-agent","goal":"查 SOP","reason":"补充经验","priority":1,"condition":null},
                            {"routeKey":"prometheus","agent":"prometheus-agent","goal":"查指标","reason":"确认影响面","priority":1,"condition":null},
                            {"routeKey":"elasticsearch","agent":"es-log-agent","goal":"查日志","reason":"用户要日志","priority":1,"condition":null}
                          ],
                          "conditionalTasks": [],
                          "skippedTasks": []
                        }
                        """));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);
        String graphChoices = """
                {"routers":[{"choices":[
                  {"routeKey":"rag","conditionType":"route_match","condition":"rag","targetDescription":"知识库"},
                  {"routeKey":"prometheus","conditionType":"route_match","condition":"prometheus","targetDescription":"指标"},
                  {"routeKey":"elasticsearch","conditionType":"route_match","condition":"elasticsearch","targetDescription":"日志"}
                ]}]}
                """;

        String question = "只读检查 demo-project 最近 10 分钟错误日志";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(request(question), OpsQuestionContext.from(question), graphChoices);

        assertEquals("LOG_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_ES), sources(plan.getTasks()));
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_RAG));
        assertTrue(plan.getReason().contains("显式日志排查请求"));
    }

    @Test
    public void shouldRouteAlertTriageSopToRagOnly() {
        String question = "告警转运维分析任务的 triage SOP 怎么写";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteMultimodalIngestionQuestionToRagOnly() {
        String question = "带图片的运维文档如何做 OCR、图生文和 embedding 入库";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("KNOWLEDGE_FIRST_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_RAG), sources(plan.getTasks()));
    }

    @Test
    public void shouldRouteMixedIncidentToPrometheusAndElasticsearch() {
        String question = "支付接口变慢并且报错 ERROR";
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner().plan(request(question), OpsQuestionContext.from(question));

        assertEquals("INCIDENT_INVESTIGATION", plan.getIntent());
        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM, OpsMainAgentPlanner.SOURCE_ES), sources(plan.getTasks()));
        assertEquals(Integer.valueOf(1), plan.getTasks().get(0).getPriority());
        assertEquals(Integer.valueOf(1), plan.getTasks().get(1).getPriority());
        assertTrue(sources(plan.getConditionalTasks()).contains(OpsMainAgentPlanner.SOURCE_RAG));
    }

    @Test
    public void shouldSupportAllSourcesBaselinePlannerMode() {
        String question = "示例锁单失败一般怎么排查";
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(
                null,
                new OpsMainAgentPlannerSettings(false, "all_sources"));

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(request(question), OpsQuestionContext.from(question));

        assertEquals("ALL_SOURCES_BASELINE", plan.getIntent());
        assertEquals(List.of(
                OpsMainAgentPlanner.SOURCE_RAG,
                OpsMainAgentPlanner.SOURCE_ES,
                OpsMainAgentPlanner.SOURCE_PROM,
                OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL), sources(plan.getTasks()));
        assertTrue(plan.getConditionalTasks().isEmpty());
        assertTrue(plan.getSkippedTasks().isEmpty());
    }

    @Test
    public void shouldFallbackStrictPlannerWhenLlmJsonIsInvalid() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection())).thenReturn(new JSONObject());
        doThrow(new OpsLlmDegradationException("ops-main-agent-planner", "planner JSON 校验失败"))
                .when(llmClient).rejectDegradation(eq("ops-main-agent-planner"), contains("planner JSON 校验失败"));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                planner.plan(request("支付回调报错，帮我查日志和指标"), OpsQuestionContext.from("支付回调报错，帮我查日志和指标"));

        assertNotNull(plan);
        assertFalse(plan.getTasks().isEmpty());
        verify(llmClient).rejectDegradation(eq("ops-main-agent-planner"), contains("planner JSON 校验失败"));
    }

    @Test
    public void shouldStopReplanForPureKnowledgeQuestionAfterRagResult() {
        String question = "示例锁单失败一般怎么排查，先从运维知识库找 SOP";
        OpsMainAgentPlanner planner = planner();

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.replan(
                request(question),
                OpsQuestionContext.from(question),
                null,
                List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(OpsMainAgentPlanner.SOURCE_RAG)
                        .agent("rag-knowledge-agent")
                        .status("FOUND")
                        .summary("命中锁单失败 SOP")
                        .evidence(List.of("SOP：先检查库存锁、事务超时和重试状态。"))
                        .gaps(List.of("没有实时日志证据。"))
                        .build()),
                2,
                3);

        assertEquals("STOP", plan.getIntent());
        assertTrue(plan.getTasks().isEmpty());
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL));
    }

    @Test
    public void shouldStopReplanForMetricDictionaryQuestionAfterRagResult() {
        String question = "Prometheus 里的 QPS、P95、P99 指标含义是什么，查知识库解释一下";
        OpsMainAgentPlanner planner = planner();

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.replan(
                request(question),
                OpsQuestionContext.from(question),
                null,
                List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(OpsMainAgentPlanner.SOURCE_RAG)
                        .agent("rag-knowledge-agent")
                        .status("FOUND")
                        .summary("命中指标字典")
                        .evidence(List.of("QPS 是每秒请求数，P95/P99 是分位延迟。"))
                        .build()),
                2,
                3);

        assertEquals("STOP", plan.getIntent());
        assertTrue(plan.getTasks().isEmpty());
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_PROM));
        assertTrue(sources(plan.getSkippedTasks()).contains(OpsMainAgentPlanner.SOURCE_ES));
    }

    @Test
    public void shouldKeepExecutedSourceWhenGraphFeedbackChoicesAllowRequery() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObjectWithEagerSkillContext(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(JSON.parseObject("""
                        {
                          "intent":"REPLAN_CONTINUE",
                          "reason":"Prometheus observation 缺少实例维度，需要按回边补查。",
                          "tasks":[{"routeKey":"prometheus","agent":"prometheus-agent","goal":"按实例维度补查 5xx 与 P99。","reason":"补齐指标证据缺口。","priority":1,"condition":"main-replan"}],
                          "conditionalTasks":[],
                          "skippedTasks":[]
                        }
                        """));
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(llmClient);

        String graphChoices = """
                {"routers":[{"routerNodeId":"review-router","inputKey":"review_decision","choices":[{"routeOutputHint":"tasks[].routeKey=prometheus maps to needs:prometheus","runtime":{"remainingRounds":1,"exhausted":false}}]}]}
                """;
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.replan(
                request("最近 join 接口 5xx 升高，继续补齐指标证据"),
                OpsQuestionContext.from("最近 join 接口 5xx 升高，继续补齐指标证据"),
                null,
                List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source(OpsMainAgentPlanner.SOURCE_PROM)
                        .agent("prometheus-agent")
                        .status("INSUFFICIENT")
                        .summary("已有指标摘要，但缺少实例维度。")
                        .gaps(List.of("缺少实例维度 5xx 和 P99。"))
                        .build()),
                2,
                3,
                graphChoices);

        assertEquals(List.of(OpsMainAgentPlanner.SOURCE_PROM), sources(plan.getTasks()));
    }

    @Test
    public void shouldSplitEsMustAndShouldPhrases() {
        OpsQuestionContext context = OpsQuestionContext.from("traceId:abc123XYZ789 requestId:REQ202605070001 /api/services/checkout 服务 超时 ERROR");

        assertTrue(context.esMustPhrases().contains("abc123XYZ789"));
        assertTrue(context.esMustPhrases().contains("REQ202605070001"));
        assertTrue(context.esMustPhrases().contains("/api/services/checkout"));
        assertFalse(context.esMustPhrases().contains("服务"));
        assertTrue(context.esShouldPhrases().contains("服务"));
        assertTrue(context.esShouldPhrases().contains("超时"));
    }

    @Test
    public void constructorBoundSubAgentsDefineAllSourcesCatalog() {
        OpsSubAgent custom = mock(OpsSubAgent.class);
        when(custom.source()).thenReturn("custom_source");
        when(custom.agentId()).thenReturn("custom-agent");
        when(custom.capability()).thenReturn("Custom evidence source");
        OpsMainAgentPlanner planner = new OpsMainAgentPlanner(
                null,
                new OpsMainAgentPlannerSettings(false, "all_sources"),
                List.of(custom));

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(
                request("检查全部可用数据源"),
                OpsQuestionContext.from("检查全部可用数据源"));

        assertEquals(List.of("custom_source"), sources(plan.getTasks()));
    }

    private OpsMainAgentPlanner planner() {
        return new OpsMainAgentPlanner(
                null,
                new OpsMainAgentPlannerSettings(false, "smart"));
    }

    private OpsAgentRunRequestDTO request(String question) {
        return OpsAgentRunRequestDTO.builder()
                .rangeMinutes(15)
                .promWindow("5m")
                .includeRecentLogs(false)
                .question(question)
                .maxRounds(3)
                .build();
    }

    private List<String> sources(List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return tasks.stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .collect(Collectors.toList());
    }
}
