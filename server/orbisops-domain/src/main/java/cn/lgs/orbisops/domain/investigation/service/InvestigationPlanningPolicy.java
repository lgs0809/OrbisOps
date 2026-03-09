package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningPlan;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningSignals;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic datasource planning and guardrail policy for Investigation. */
public final class InvestigationPlanningPolicy {

    public static final String SOURCE_RAG = "rag";
    public static final String SOURCE_ES = "elasticsearch";
    public static final String SOURCE_PROM = "prometheus";
    public static final String SOURCE_MYSQL_SLOW_SQL = "mysql_slow_sql";

    private static final InvestigationPlanningSignalPolicy SIGNALS =
            new InvestigationPlanningSignalPolicy();

    public InvestigationPlanningPlan initialPlan(InitialInput input) {
        InvestigationPlanningSignals signals = input.signals();
        List<InvestigationPlanningTask> tasks = new ArrayList<>();
        List<InvestigationPlanningTask> conditionalTasks = new ArrayList<>();
        List<InvestigationPlanningTask> skippedTasks = new ArrayList<>();
        String intent;
        String reason;

        if (signals.blankQuestion()) {
            intent = "GENERAL_HEALTH_CHECK";
            reason = "未提供具体问题，按巡检场景处理：先查 Prometheus 健康指标；只有需要日志样本或指标异常时才查 ES。";
            tasks.add(task(SOURCE_PROM, "prometheus-agent", "检查实例在线、QPS、5xx 错误率、响应耗时、JVM/CPU。", "巡检优先用数值指标判断是否异常。", 1, null));
            if (Boolean.TRUE.equals(input.includeRecentLogs())) {
                tasks.add(task(SOURCE_ES, "es-log-agent", "补充最近窗口日志摘要和样本，辅助生成巡检上下文。", "调用方要求包含最近日志样本。", 2, null));
            } else {
                conditionalTasks.add(task(SOURCE_ES, "es-log-agent", "如果 Prometheus 发现错误率、实例、资源或延迟异常，再查询日志。", "指标异常时再查日志，避免无意义扫 ES。", 2, "prometheus.status == FOUND && prometheus.evidence contains anomaly"));
            }
            conditionalTasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "如果 Prometheus 发现接口耗时异常或用户问题指向数据库，再查询 MySQL 慢 SQL。", "慢 SQL 只在 DB/延迟线索成立时查询，避免巡检默认扫库。", 2, "prometheus evidence contains latency anomaly"));
            conditionalTasks.add(task(SOURCE_RAG, "rag-knowledge-agent", "如果 ES/Prometheus 出现未知异常或指标含义不明确，再查询运维知识库。", "知识库用于解释和 SOP，不是实时证据源。", 3, "runtime evidence needs explanation"));
        } else if (SIGNALS.isKnowledgeExplanationQuestion(signals)) {
            intent = "KNOWLEDGE_FIRST_INVESTIGATION";
            reason = "问题是在询问指标含义、运维字典、安全边界、模板或排障方法，虽然可能包含指标/SQL/错误码词，但不要求查询实时数据，优先查向量知识库。";
            tasks.add(task(SOURCE_RAG, "rag-knowledge-agent", "检索运维 SOP、指标字典、日志字典、安全边界、通知模板和历史故障案例。", "知识型解释问题优先查 RAG，避免把稳定知识问题误路由到实时数据源。", 1, null));
            skippedTasks.add(task(SOURCE_ES, "es-log-agent", "暂不查询 ES。", "没有实时日志线索。", 2, null));
            skippedTasks.add(task(SOURCE_PROM, "prometheus-agent", "暂不查询 Prometheus。", "用户询问的是知识解释或治理规则，不是当前指标数值。", 3, null));
            skippedTasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "暂不查询 MySQL 慢 SQL。", "没有实时数据库耗时证据诉求。", 3, null));
        } else if (signals.slowSqlSignal() && !SIGNALS.hasStrongLogSignal(signals)) {
            intent = "SQL_FIRST_INVESTIGATION";
            reason = "问题包含 MySQL 慢查询、索引或数据库耗时线索，优先查 MySQL 慢 SQL；必要时再用 Prometheus 判断影响面。过滤条件：" + signals.filtersDescription();
            tasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "读取 mysql.slow_log 或 performance_schema 中的慢 SQL/高耗时 SQL，提取 SQL、耗时、扫描行数和执行次数。", "慢 SQL 线索最直接，先拿数据库侧事实证据。", 1, null));
            conditionalTasks.add(task(SOURCE_PROM, "prometheus-agent", "如果慢 SQL 命中，再查询接口延迟、QPS、错误率和资源指标。", "确认慢 SQL 是否影响线上服务。", 2, "mysql_slow_sql.status == FOUND"));
            conditionalTasks.add(task(SOURCE_RAG, "rag-knowledge-agent", "如果需要解释索引优化或排障 SOP，再查知识库。", "知识库用于补充优化建议和历史案例。", 3, "sql tuning explanation required"));
            skippedTasks.add(task(SOURCE_ES, "es-log-agent", "暂不查询 ES。", "问题没有明确日志/traceId/错误码线索。", 3, null));
        } else if (SIGNALS.hasStrongLogSignal(signals) && !signals.metricSignal()) {
            intent = "LOG_FIRST_INVESTIGATION";
            reason = "问题包含日志/异常/错误码/精确过滤条件，优先查 ES；知识库作为异常解释和 SOP 辅助。过滤条件：" + signals.filtersDescription();
            tasks.add(task(SOURCE_ES, "es-log-agent", "按时间窗口、异常关键词、日志级别、traceId/orderId/URI 等条件查询并提取代表样本。", "日志线索最直接，先拿事实证据。", 1, null));
            if (signals.slowSqlSignal()) {
                conditionalTasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "如果日志出现数据库慢、超时、SQL 或连接池线索，再查 MySQL 慢 SQL。", "日志证据指向数据库后再查慢 SQL。", 2, "elasticsearch.status == FOUND && evidence contains sql"));
            }
            conditionalTasks.add(task(SOURCE_RAG, "rag-knowledge-agent", "如果 ES 查到异常类、错误码或业务关键词，再查对应 SOP/历史案例。", "知识库用于解释日志证据。", 2, "elasticsearch.status == FOUND"));
            skippedTasks.add(task(SOURCE_PROM, "prometheus-agent", "暂不查询 Prometheus。", "问题没有明显指标或趋势诉求，先不查指标。", 3, null));
        } else if (signals.metricSignal() && !SIGNALS.hasStrongLogSignal(signals)) {
            intent = "METRIC_FIRST_INVESTIGATION";
            reason = "问题包含指标/健康/延迟/资源线索，优先查 Prometheus；发现异常后再查 ES。过滤条件：" + signals.filtersDescription();
            tasks.add(task(SOURCE_PROM, "prometheus-agent", "查询实例、QPS、错误率、延迟、JVM/CPU 等指标摘要。", "数值型问题应先查指标。", 1, null));
            conditionalTasks.add(task(SOURCE_ES, "es-log-agent", "如果指标发现错误率、延迟或资源异常，再查询相关日志。", "指标异常需要日志证据解释。", 2, "prometheus.status == FOUND && prometheus.evidence contains anomaly"));
            conditionalTasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "如果指标发现接口耗时异常或问题指向数据库，再查询 MySQL 慢 SQL。", "慢 SQL 是延迟类问题的重要事实源。", 2, "prometheus evidence contains latency anomaly"));
            conditionalTasks.add(task(SOURCE_RAG, "rag-knowledge-agent", "如果指标含义或排障动作不明确，再查知识库。", "知识库提供指标解释和 SOP。", 3, "metric explanation required"));
        } else if (signals.knowledgeSignal() && !signals.logSignal() && !signals.metricSignal()) {
            intent = "KNOWLEDGE_FIRST_INVESTIGATION";
            reason = "问题偏排障方法、指标解释或历史经验，优先查向量知识库。";
            tasks.add(task(SOURCE_RAG, "rag-knowledge-agent", "检索运维 SOP、指标字典、日志字典和历史故障案例。", "知识型问题优先查 RAG。", 1, null));
            skippedTasks.add(task(SOURCE_ES, "es-log-agent", "暂不查询 ES。", "没有实时日志线索。", 2, null));
            skippedTasks.add(task(SOURCE_PROM, "prometheus-agent", "暂不查询 Prometheus。", "没有实时指标诉求。", 3, null));
            skippedTasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "暂不查询 MySQL 慢 SQL。", "没有数据库耗时或 SQL 线索。", 3, null));
        } else {
            intent = "INCIDENT_INVESTIGATION";
            reason = "问题同时可能涉及日志和指标，优先查 Prometheus 和 ES；知识库按证据需要补充。过滤条件：" + signals.filtersDescription();
            tasks.add(task(SOURCE_PROM, "prometheus-agent", "查询指标趋势和资源状态，确认是否存在系统性异常。", "故障类问题需要先判断影响面。", 1, null));
            tasks.add(task(SOURCE_ES, "es-log-agent", "查询 ERROR/WARN 聚合和代表样本，确认日志证据。", "故障类问题需要日志证据。", 1, null));
            if (signals.slowSqlSignal()) {
                tasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "查询慢 SQL/高耗时 SQL，确认是否存在数据库侧瓶颈。", "用户问题包含数据库慢查询或 SQL 线索。", 1, null));
            } else {
                conditionalTasks.add(task(SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "如果 ES/Prometheus 指向数据库耗时，再查 MySQL 慢 SQL。", "慢 SQL 作为延迟类 follow-up 数据源。", 2, "runtime evidence contains db latency"));
            }
            conditionalTasks.add(task(SOURCE_RAG, "rag-knowledge-agent", "如果 ES/Prometheus 出现明确信号，再查询 SOP 和历史案例。", "避免一开始把知识库也查一遍。", 2, "runtime evidence found"));
        }
        filterUnavailable(tasks, input.availableSources());
        filterUnavailable(conditionalTasks, input.availableSources());
        filterUnavailable(skippedTasks, input.availableSources());
        boolean changeRequested = SIGNALS.explicitChangeRequest(signals);
        return plan(intent, reason, changeRequested,
                changeRequested ? value(signals.originalQuestion()) : "",
                tasks, conditionalTasks, skippedTasks);
    }

    public InvestigationPlanningPlan replan(ReplanInput input) {
        Set<String> executedSources = executedSources(input.observations());
        List<InvestigationPlanningTask> tasks = new ArrayList<>();
        List<InvestigationPlanningTask> skippedTasks = new ArrayList<>();
        boolean hasResults = input.observations() != null && !input.observations().isEmpty();
        boolean insufficient = hasInsufficientEvidence(input.observations());
        boolean anomaly = hasAnomalySignal(input.observations());
        boolean dbLatency = hasDatabaseLatencySignal(input.observations()) || input.signals().slowSqlSignal();

        if (SIGNALS.pureKnowledgeQuestion(input.signals()) && executedSources.contains(SOURCE_RAG)) {
            for (String source : input.availableSources()) {
                if (!executedSources.contains(source)) {
                    skippedTasks.add(task(source, defaultAgent(source), "本轮暂不查询 " + source, "纯知识型问题已由 RAG 返回结果，实时数据源没有额外信息增益。", 4, null));
                }
            }
        } else if (!hasResults) {
            addIfNew(tasks, executedSources, SOURCE_PROM, "prometheus-agent", "上一轮没有有效 observation，先查询核心健康指标。", "无结果时先用指标判断影响面。", 1);
            addIfNew(tasks, executedSources, SOURCE_ES, "es-log-agent", "补充 ERROR/WARN 日志和请求样本。", "无结果时需要日志侧事实证据。", 2);
        } else {
            if (dbLatency) {
                addIfNew(tasks, executedSources, SOURCE_MYSQL_SLOW_SQL, "mysql-slow-sql-agent", "实时证据或用户问题指向数据库耗时，查询慢 SQL/高耗时 SQL。", "数据库线索成立，慢 SQL 有信息增益。", 1);
            }
            if ((insufficient || anomaly) && !executedSources.contains(SOURCE_PROM)
                    && (input.signals().metricSignal() || executedSources.contains(SOURCE_ES) || dbLatency)) {
                addIfNew(tasks, executedSources, SOURCE_PROM, "prometheus-agent", "查询指标趋势、实例状态、错误率、延迟和资源指标。", "需要用指标判断影响面或解释日志侧异常。", 2);
            }
            if ((insufficient || anomaly) && !executedSources.contains(SOURCE_ES)
                    && (input.signals().logSignal() || executedSources.contains(SOURCE_PROM) || input.signals().knowledgeSignal())) {
                addIfNew(tasks, executedSources, SOURCE_ES, "es-log-agent", "查询 ERROR/WARN、异常堆栈、traceId/orderId/URI 等日志证据。", "需要用日志解释指标异常或补充事实样本。", 2);
            }
            if ((insufficient || anomaly || input.signals().knowledgeSignal()) && !executedSources.contains(SOURCE_RAG)) {
                addIfNew(tasks, executedSources, SOURCE_RAG, "rag-knowledge-agent", "检索 SOP、指标字典、日志字典和历史故障案例。", "需要知识库解释证据、补齐排障动作或历史案例。", 3);
            }
        }

        Set<String> plannedSources = tasks.stream().map(InvestigationPlanningTask::source).collect(Collectors.toSet());
        for (String source : input.availableSources()) {
            if (!executedSources.contains(source) && !plannedSources.contains(source)) {
                skippedTasks.add(task(source, defaultAgent(source), "本轮暂不查询 " + source, "主 Agent 判断本轮信息增益不足。", 4, null));
            }
        }
        filterUnavailable(tasks, input.availableSources());
        filterUnavailable(skippedTasks, input.availableSources());
        String reason = tasks.isEmpty()
                ? SIGNALS.pureKnowledgeQuestion(input.signals())
                ? "第 " + input.round() + "/" + input.maxRounds() + " 轮复盘：纯知识型问题已完成 RAG 检索，不继续查询实时数据源。"
                : "第 " + input.round() + "/" + input.maxRounds() + " 轮复盘后未发现新的高价值数据源。"
                : "第 " + input.round() + "/" + input.maxRounds() + " 轮根据 observation 和证据缺口追加数据源。";
        return plan(tasks.isEmpty() ? "STOP" : "REPLAN_CONTINUE", reason, null, null, tasks, new ArrayList<>(), skippedTasks);
    }

    public InvestigationPlanningPlan applyGuardrails(GuardrailInput input) {
        if (input.llmPlan() == null || input.fallbackPlan() == null || input.signals() == null) {
            return input.llmPlan();
        }
        InvestigationPlanningPlan guarded = input.llmPlan();
        if (SIGNALS.pureKnowledgeQuestion(input.signals())) {
            guarded = guardedCopy(input, "知识型问题无实时验证诉求，限制为 RAG-only；原 LLM 理由：");
        } else if (SIGNALS.hasStrongLogSignal(input.signals())
                && !input.signals().metricSignal()
                && containsSource(input.fallbackPlan().tasks(), SOURCE_ES)
                && (!List.of(SOURCE_ES).equals(planSources(input.llmPlan().tasks()))
                || !Optional.ofNullable(input.llmPlan().conditionalTasks()).orElse(List.of()).isEmpty())) {
            guarded = guardedCopy(input, "显式日志排查请求必须先查 ES，RAG/Prometheus/MySQL 只能按证据条件补查；原 LLM 理由：");
        } else if (input.signals().slowSqlSignal()
                && !SIGNALS.hasStrongLogSignal(input.signals())
                && !containsSource(input.llmPlan().tasks(), SOURCE_MYSQL_SLOW_SQL)) {
            guarded = guardedCopy(input, "慢 SQL 信号必须先查询 MySQL Slow SQL；原 LLM 计划未包含数据库事实源，已按数据源边界修正。原 LLM 理由：");
        }
        return enforceExplicitChangeIntent(guarded, input.signals());
    }

    public InvestigationPlanningPlan allSourcesPlan(AllSourcesInput input) {
        List<InvestigationPlanningTask> tasks = orderedSources(input.availableSources()).stream()
                .map(source -> task(source, defaultAgent(source), "全量工具 baseline 查询 " + source + " 数据源。", "对比实验模式：不做主 Agent 智能路由，所有可用数据源都立即查询。", 1, "planner-mode=all_sources"))
                .toList();
        return plan("ALL_SOURCES_BASELINE",
                "全量工具 baseline：用户问题为 `" + value(input.signals().originalQuestion()) + "`；该模式用于评测智能路由相对普通工具 Agent 的收益。",
                false, "", tasks, new ArrayList<>(), new ArrayList<>());
    }

    public InvestigationPlanningPlan allSourcesReplanStop(InvestigationPlanningPlan previousPlan) {
        Boolean changeRequested = previousPlan == null ? null : previousPlan.changeRequested();
        String changeIntent = previousPlan != null && hasText(previousPlan.changeIntent())
                ? previousPlan.changeIntent()
                : null;
        return plan("STOP", "全量工具 baseline 已在首轮查询全部可用数据源，不再追加 replan 任务。",
                changeRequested, changeIntent, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public static String normalizeSource(String source) {
        if (!hasText(source)) return "";
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pgvector", "vector", "knowledge", "knowledge_base" -> SOURCE_RAG;
            case "es", "elastic", "logs", "log" -> SOURCE_ES;
            case "prom", "metrics", "metric" -> SOURCE_PROM;
            case "mysql", "slow_sql", "mysql-slow-sql", "mysql_slow_log" -> SOURCE_MYSQL_SLOW_SQL;
            default -> normalized;
        };
    }

    private InvestigationPlanningPlan guardedCopy(GuardrailInput input, String reasonPrefix) {
        InvestigationPlanningPlan fallback = input.fallbackPlan();
        InvestigationPlanningPlan llm = input.llmPlan();
        Boolean changeRequested = llm.changeRequested() != null ? llm.changeRequested() : fallback.changeRequested();
        String changeIntent = hasText(llm.changeIntent()) ? llm.changeIntent() : fallback.changeIntent();
        return plan(fallback.intent(), reasonPrefix + value(llm.reason()), changeRequested, changeIntent,
                new ArrayList<>(fallback.tasks()), new ArrayList<>(fallback.conditionalTasks()), new ArrayList<>(fallback.skippedTasks()));
    }

    private InvestigationPlanningPlan enforceExplicitChangeIntent(
            InvestigationPlanningPlan source,
            InvestigationPlanningSignals signals) {
        if (source == null || !SIGNALS.explicitChangeRequest(signals)) {
            return source;
        }
        return plan(source.intent(), source.reason(), true, value(signals.originalQuestion()),
                new ArrayList<>(Optional.ofNullable(source.tasks()).orElse(List.of())),
                new ArrayList<>(Optional.ofNullable(source.conditionalTasks()).orElse(List.of())),
                new ArrayList<>(Optional.ofNullable(source.skippedTasks()).orElse(List.of())));
    }

    private Set<String> executedSources(List<InvestigationPlanningObservation> observations) {
        return Optional.ofNullable(observations).orElse(List.of()).stream()
                .filter(observation -> observation != null && hasText(observation.source()))
                .map(observation -> normalizeSource(observation.source()))
                .collect(Collectors.toSet());
    }

    private boolean hasInsufficientEvidence(List<InvestigationPlanningObservation> observations) {
        return Optional.ofNullable(observations).orElse(List.of()).stream().anyMatch(observation -> observation != null
                && ("NOT_FOUND".equalsIgnoreCase(observation.status()) || "INSUFFICIENT".equalsIgnoreCase(observation.status())
                || (observation.gaps() != null && !observation.gaps().isEmpty())));
    }

    private boolean hasAnomalySignal(List<InvestigationPlanningObservation> observations) {
        return containsAny(resultText(observations), "异常", "风险", "error", "5xx", "down", "warn", "timeout", "超时", "错误率", "slow");
    }

    private boolean hasDatabaseLatencySignal(List<InvestigationPlanningObservation> observations) {
        return containsAny(resultText(observations), "mysql", "sql", "慢查询", "slow query", "slow sql", "数据库", "db", "连接池", "索引", "rows_examined", "全表扫描", "延迟", "耗时", "timeout", "超时");
    }

    private String resultText(List<InvestigationPlanningObservation> observations) {
        return Optional.ofNullable(observations).orElse(List.of()).stream().filter(observation -> observation != null)
                .map(observation -> String.join(" ", value(observation.source()), value(observation.status()), value(observation.summary()),
                        String.join(" ", Optional.ofNullable(observation.evidence()).orElse(List.of())),
                        String.join(" ", Optional.ofNullable(observation.gaps()).orElse(List.of())),
                        String.join(" ", Optional.ofNullable(observation.suggestedAdjustments()).orElse(List.of()))))
                .collect(Collectors.joining("\n")).toLowerCase(Locale.ROOT);
    }

    private void addIfNew(List<InvestigationPlanningTask> tasks, Set<String> executedSources, String source,
                          String agent, String goal, String reason, int priority) {
        if (executedSources.contains(source) || tasks.stream().anyMatch(task -> source.equals(task.source()))) return;
        tasks.add(task(source, agent, goal, reason, priority, "main-replan"));
    }

    private List<String> planSources(List<InvestigationPlanningTask> tasks) {
        return Optional.ofNullable(tasks).orElse(List.of()).stream().filter(task -> task != null)
                .map(InvestigationPlanningTask::source).map(InvestigationPlanningPolicy::normalizeSource)
                .filter(InvestigationPlanningPolicy::hasText).distinct().toList();
    }

    private boolean containsSource(List<InvestigationPlanningTask> tasks, String source) {
        return Optional.ofNullable(tasks).orElse(List.of()).stream()
                .anyMatch(task -> task != null && source.equals(normalizeSource(task.source())));
    }

    private void filterUnavailable(List<InvestigationPlanningTask> tasks, Set<String> availableSources) {
        tasks.removeIf(task -> task == null || !availableSources.contains(task.source()));
    }

    private List<String> orderedSources(Set<String> availableSources) {
        Set<String> normalized = Optional.ofNullable(availableSources).orElse(Set.of()).stream()
                .map(InvestigationPlanningPolicy::normalizeSource)
                .filter(InvestigationPlanningPolicy::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> ordered = new ArrayList<>();
        List.of(SOURCE_RAG, SOURCE_ES, SOURCE_PROM, SOURCE_MYSQL_SLOW_SQL).stream()
                .filter(normalized::remove)
                .forEach(ordered::add);
        normalized.stream().sorted().forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private InvestigationPlanningPlan plan(String intent, String reason, Boolean changeRequested, String changeIntent,
                                           List<InvestigationPlanningTask> tasks, List<InvestigationPlanningTask> conditionalTasks,
                                           List<InvestigationPlanningTask> skippedTasks) {
        return new InvestigationPlanningPlan(intent, reason, changeRequested, changeIntent, tasks, conditionalTasks, skippedTasks);
    }

    private InvestigationPlanningTask task(String source, String agent, String goal, String reason, Integer priority, String condition) {
        return new InvestigationPlanningTask(source, agent, goal, reason, priority, condition);
    }

    private String defaultAgent(String source) {
        return switch (source) {
            case SOURCE_ES -> "es-log-agent";
            case SOURCE_PROM -> "prometheus-agent";
            case SOURCE_RAG -> "rag-knowledge-agent";
            case SOURCE_MYSQL_SLOW_SQL -> "mysql-slow-sql-agent";
            default -> source + "-agent";
        };
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String keyword : keywords) if (text.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record InitialInput(InvestigationPlanningSignals signals, Boolean includeRecentLogs,
                               Set<String> availableSources) {
        public InitialInput { availableSources = new LinkedHashSet<>(availableSources); }
    }

    public record ReplanInput(InvestigationPlanningSignals signals,
                              List<InvestigationPlanningObservation> observations,
                              int round, int maxRounds, Set<String> availableSources) {
        public ReplanInput { availableSources = new LinkedHashSet<>(availableSources); }
    }

    public record GuardrailInput(InvestigationPlanningPlan llmPlan,
                                 InvestigationPlanningPlan fallbackPlan,
                                 InvestigationPlanningSignals signals) { }

    public record AllSourcesInput(InvestigationPlanningSignals signals, Set<String> availableSources) {
        public AllSourcesInput { availableSources = new HashSet<>(availableSources); }
    }
}
