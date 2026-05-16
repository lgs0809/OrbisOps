package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryApplicationService;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL;

/**
 * ReAct-style MySQL slow SQL sub-agent.
 */
@Service
public class MySqlSlowSqlOpsSubAgent extends AbstractOpsSubAgent {

    private final OpsSubAgentDecisionService decisionService;
    private final MySqlSlowSqlQueryApplicationService queryService;
    private final OpsRunCancellationRegistry cancellationRegistry;
    private final OpsMySqlSlowSqlResponseProjector responseProjector;
    private final OpsMySqlSlowSqlSettings settings;
    private final OpsSubAgentRequestPolicy requestPolicy;
    private final OpsMySqlSlowSqlQueryFactory queryFactory;
    private OpsAuthoritativeDatasourceEvidenceProjector authoritativeEvidenceProjector =
            new OpsAuthoritativeDatasourceEvidenceProjector();

    @Autowired
    public MySqlSlowSqlOpsSubAgent(
            OpsSubAgentDecisionService decisionService,
            MySqlSlowSqlQueryApplicationService queryService,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsMySqlSlowSqlSettings settings) {
        this(decisionService,
                queryService,
                cancellationRegistry,
                new OpsMySqlSlowSqlResponseProjector(),
                settings,
                new OpsSubAgentRequestPolicy(),
                new OpsMySqlSlowSqlQueryFactory());
    }

    MySqlSlowSqlOpsSubAgent(
            OpsSubAgentDecisionService decisionService,
            MySqlSlowSqlQueryApplicationService queryService,
            OpsRunCancellationRegistry cancellationRegistry,
            OpsMySqlSlowSqlResponseProjector responseProjector,
            OpsMySqlSlowSqlSettings settings,
            OpsSubAgentRequestPolicy requestPolicy,
            OpsMySqlSlowSqlQueryFactory queryFactory) {
        this.decisionService = decisionService;
        this.queryService = queryService;
        this.cancellationRegistry = cancellationRegistry;
        this.responseProjector = responseProjector == null ? new OpsMySqlSlowSqlResponseProjector() : responseProjector;
        this.settings = settings == null ? OpsMySqlSlowSqlSettings.defaults() : settings;
        this.requestPolicy = requestPolicy == null ? new OpsSubAgentRequestPolicy() : requestPolicy;
        this.queryFactory = queryFactory == null ? new OpsMySqlSlowSqlQueryFactory() : queryFactory;
    }

    @Autowired
    void setAuthoritativeEvidenceProjector(OpsAuthoritativeDatasourceEvidenceProjector projector) {
        this.authoritativeEvidenceProjector = projector == null
                ? new OpsAuthoritativeDatasourceEvidenceProjector()
                : projector;
    }

    @Override
    public String source() {
        return SOURCE_MYSQL_SLOW_SQL;
    }

    @Override
    public String agentId() {
        return "mysql-slow-sql-agent";
    }

    @Override
    public String displayName() {
        return "MySQL 慢 SQL 子 Agent";
    }

    @Override
    public String capability() {
        return "查询 mysql.slow_log 和 performance_schema 中的慢 SQL、高耗时 SQL、扫描行数、执行次数和 SQL 摘要。";
    }

    @Override
    public OpsAnalysisResponseDTO.InvestigationResultDTO investigate(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                             OpsAgentRunRequestDTO request,
                                                                             OpsAnalysisResponseDTO response,
                                                                             OpsQuestionContext questionContext) {
        int maxIterations = subAgentMaxIterations(request);
        OpsAgentRunRequestDTO currentRequest = request;
        OpsAnalysisResponseDTO.InvestigationResultDTO latestResult = null;
        List<String> evidence = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = new ArrayList<>();
        String previousObservation = "";

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            assertNotCanceled(currentRequest);
            latestResult = investigateOnce(task, currentRequest, response, questionContext, previousObservation, iteration, maxIterations);
            assertNotCanceled(currentRequest);
            addAll(evidence, latestResult.getEvidence());
            addAll(attempts, latestResult.getAttempts());
            addAll(gaps, latestResult.getGaps());
            addAll(adjustments, latestResult.getSuggestedAdjustments());

            if (!shouldRetryWithinSubAgent(latestResult)) {
                return withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, false, maxIterations);
            }
            if (iteration == maxIterations) {
                return withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, true, maxIterations);
            }

            previousObservation = loopObservation(latestResult, iteration, maxIterations);
            currentRequest = requestPolicy.expandRange(currentRequest);
            attempts.add(attempt("LOOP iteration=" + (iteration + 1) + "/" + maxIterations
                            + ", nextRange=now-" + currentRequest.getRangeMinutes() + "m..now",
                    0,
                    "OBSERVE 后 MySQL 慢 SQL 子 Agent 自主扩大时间窗口并继续查询"));
        }

        return latestResult == null
                ? result(task, STATUS_ERROR, "MySQL 慢 SQL 子 Agent 未执行任何查询。", evidence, attempts, gaps, adjustments, false, 0D)
                : withLoopAggregates(latestResult, evidence, attempts, gaps, adjustments, true, maxIterations);
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO investigateOnce(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                                  OpsAgentRunRequestDTO request,
                                                                                  OpsAnalysisResponseDTO response,
                                                                                  OpsQuestionContext questionContext,
                                                                                  String previousObservation,
                                                                                  int iteration,
                                                                                  int maxIterations) {
        OpsSubAgentDecision decision = decisionService.decide(SOURCE_MYSQL_SLOW_SQL, task, request, questionContext, """
                MySQL 慢 SQL 数据源存放数据库侧高耗时 SQL、扫描行数、返回行数、执行次数和 SQL 摘要。
                优先用 mysql.slow_log 获取时间窗口内真实慢 SQL；如果 slow_log 不可用，可用 performance_schema.events_statements_summary_by_digest 查看累计高耗时 SQL。
                不适合回答接口错误堆栈、业务日志样本或服务实例状态。
                """, previousObservation);
        OpsAgentRunRequestDTO effectiveRequest = requestPolicy.applyRangeDecision(request, decision.rangeMinutes());
        List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        attempts.add(attempt("THINK iteration=" + iteration + "/" + maxIterations
                        + ", queryFocus=" + value(decision.queryFocus())
                        + ", rangeMinutes=" + effectiveRequest.getRangeMinutes()
                        + ", reason=" + decision.reason(),
                0,
                decision.llmGenerated() ? "LLM mysql-slow-sql-agent 生成查询策略" : "规则 mysql-slow-sql-agent 查询策略"));

        if (!settings.enabled()) {
            response.setMysqlSlowSqlStatus(status("MySQL Slow SQL", "mysql.slow_log/performance_schema", false, "MySQL 慢 SQL 子 Agent 已关闭。"));
            gaps.add("orbisops.mysql-slow-sql.enabled=false，未查询慢 SQL。");
            adjustments.add("需要数据库慢查询证据时开启 ops.mysql-slow-sql.enabled。");
            return result(task, STATUS_BLOCKED, "MySQL 慢 SQL 子 Agent 未启用。", evidence, attempts, gaps, adjustments, false, 0D);
        }

        OpsMySqlSlowSqlResponseProjector.Projection outcome = querySlowSql(effectiveRequest);
        attempts.add(attempt(outcome.queryDescription(), outcome.samples().size(), outcome.message()));
        response.setMysqlSlowSqlStatus(status("MySQL Slow SQL", outcome.sourceName(), outcome.available(), outcome.message()));
        response.setSlowSqlSamples(outcome.samples());
        response.setSlowSqlSummary(outcome.summary());

        if (!outcome.available()) {
            gaps.add("MySQL 慢 SQL 数据源不可用，无法获取数据库侧慢查询证据。");
            adjustments.add("检查 slow_query_log/log_output=TABLE、performance_schema、数据库权限和连接池配置。");
            return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_BLOCKED, "MySQL 慢 SQL 子 Agent 被阻塞：" + outcome.message(), false, 0D, iteration, maxIterations);
        }

        authoritativeEvidenceProjector.record(
                effectiveRequest,
                OpsAuthoritativeDatasourceEvidenceProjector.MYSQL_SLOW_SQL,
                outcome.sourceName(),
                OpsAuthoritativeDatasourceEvidenceProjector.summary(
                        "rangeMinutes", effectiveRequest.getRangeMinutes(),
                        "slowSqlSummary", outcome.summary(),
                        "slowSqlSamples", outcome.samples()));

        if (outcome.samples().isEmpty()) {
            evidence.add("最近 " + effectiveRequest.getRangeMinutes() + " 分钟未发现超过 " + settings.thresholdMs() + "ms 的慢 SQL。");
            gaps.add("当前窗口没有命中慢 SQL，无法证明数据库是瓶颈。");
            adjustments.add("扩大时间窗口到 " + Math.min(effectiveRequest.getRangeMinutes() * 2, 240) + " 分钟，或确认 MySQL slow_query_log 是否开启。");
            return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_NOT_FOUND, "MySQL 当前窗口未发现慢 SQL。", true, 0.35D, iteration, maxIterations);
        }

        OpsAnalysisResponseDTO.SlowSqlSummaryDTO summary = response.getSlowSqlSummary();
        evidence.add("命中慢 SQL/高耗时 SQL " + outcome.samples().size() + " 条，最高耗时 " + value(summary.getMaxQueryTimeMs()) + "ms，平均耗时 " + value(summary.getAvgQueryTimeMs()) + "ms。");
        evidence.add("Top SQL：" + outcome.samples().stream()
                .limit(3)
                .map(sample -> abbreviate(value(sample.getSqlText()), 160) + " | timeMs=" + value(sample.getQueryTimeMs()) + " | rowsExamined=" + value(sample.getRowsExamined()))
                .collect(Collectors.joining("; ")));
        if (summary.getRowsExamined() != null && summary.getRowsExamined() > 10000) {
            evidence.add("扫描行数合计 " + summary.getRowsExamined() + "，存在索引失效或范围过大的风险。");
        }
        return reviewedResult(task, effectiveRequest, questionContext, evidence, attempts, gaps, adjustments, STATUS_FOUND, "MySQL 已找到慢 SQL 或高耗时 SQL 证据。", false, 0.82D, iteration, maxIterations);
    }

    private OpsMySqlSlowSqlResponseProjector.Projection querySlowSql(
            OpsAgentRunRequestDTO request) {
        assertNotCanceled(request);
        MySqlSlowSqlQueryResult result = queryService.query(queryFactory.create(request, settings));
        assertNotCanceled(request);
        return responseProjector.project(result);
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO reviewedResult(OpsAnalysisResponseDTO.InvestigationTaskDTO task,
                                                                                 OpsAgentRunRequestDTO request,
                                                                                 OpsQuestionContext questionContext,
                                                                                 List<String> evidence,
                                                                                 List<OpsAnalysisResponseDTO.InvestigationAttemptDTO> attempts,
                                                                                 List<String> gaps,
                                                                                 List<String> adjustments,
                                                                                 String status,
                                                                                 String summary,
                                                                                 Boolean shouldRetry,
                                                                                 Double confidence,
                                                                                 int iteration,
                                                                                 int maxIterations) {
        OpsAgentReview fallback = new OpsAgentReview(false, status, summary, gaps, adjustments, Boolean.TRUE.equals(shouldRetry) && iteration < maxIterations, confidence);
        OpsAgentReview review = decisionService.review(SOURCE_MYSQL_SLOW_SQL, task, request, questionContext, observation(status, summary, evidence, gaps, adjustments, iteration, maxIterations), fallback);
        if (review.llmGenerated()) {
            attempts.add(attempt("REFLECT status=" + review.status() + ", confidence=" + review.confidence(), evidence.size(), "LLM mysql-slow-sql-agent 复盘真实 SQL observation"));
        }
        return result(task, review.status(), review.summary(), evidence, attempts, review.gaps(), review.suggestedAdjustments(), review.shouldRetry(), review.confidence());
    }

    private String observation(String status, String summary, List<String> evidence, List<String> gaps, List<String> adjustments, int iteration, int maxIterations) {
        return "iteration=" + iteration + "/" + maxIterations
                + "\nremainingIterations=" + Math.max(0, maxIterations - iteration)
                + "\nstatus=" + status + "\nsummary=" + summary + "\nevidence=" + String.join(" | ", evidence)
                + "\ngaps=" + String.join(" | ", gaps)
                + "\nadjustments=" + String.join(" | ", adjustments);
    }

    private void assertNotCanceled(OpsAgentRunRequestDTO request) {
        cancellationRegistry.assertNotCanceled(request == null ? null : request.getRunId());
    }

}
