package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationFollowUpDecision;
import cn.lgs.orbisops.domain.investigation.model.InvestigationFollowUpTask;
import cn.lgs.orbisops.domain.investigation.model.InvestigationObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationQuestionSignals;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic business policy for conditional and fallback investigation routing. */
public final class InvestigationFollowUpPolicy {

    public static final String SOURCE_ES = "elasticsearch";
    public static final String SOURCE_PROM = "prometheus";
    public static final String SOURCE_RAG = "rag";
    public static final String SOURCE_MYSQL_SLOW_SQL = "mysql_slow_sql";

    public InvestigationFollowUpDecision decide(Input input) {
        if (input == null) throw new IllegalArgumentException("INVESTIGATION_FOLLOW_UP_INPUT_REQUIRED");
        List<InvestigationFollowUpTask> tasks = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> knownSources = new LinkedHashSet<>(input.executedSources());
        knownSources.addAll(input.queuedSources());

        addConditionalTasks(input, knownSources, tasks, notes);
        addFallbackTasks(input, knownSources, tasks, notes);
        return new InvestigationFollowUpDecision(tasks, notes);
    }

    public boolean shouldStopAfterKnowledgeObservation(String planIntent,
                                                       InvestigationObservation latest,
                                                       InvestigationQuestionSignals signals) {
        return latest != null
                && SOURCE_RAG.equals(latest.source())
                && pureKnowledgeQuestion(planIntent, signals);
    }

    public boolean pureKnowledgeQuestion(String planIntent,
                                         InvestigationQuestionSignals signals) {
        if (signals == null) return false;
        String question = signals.loweredQuestion();
        boolean realtimeAsk = containsAny(question,
                "最近", "当前", "现在", "线上", "趋势", "升高", "上涨", "下降",
                "怎么样", "多少", "是否发生", "是否存在", "有没有发生",
                "帮我看", "看一下", "查日志", "日志证据", "影响面");
        boolean explicitKnowledgeAsk = containsAny(question,
                "指标含义", "含义", "解释", "是什么", "知识库", "sop", "runbook",
                "历史", "案例", "如何", "怎么", "能不能", "可以", "应该",
                "规则", "建议", "自动", "安全", "只读", "边界", "策略", "模板", "包含");
        boolean knowledgeIntent = signals.knowledgeSignal()
                || "KNOWLEDGE_FIRST_INVESTIGATION".equalsIgnoreCase(text(planIntent));
        return knowledgeIntent
                && !signals.explicitRuntimeFilter()
                && !realtimeAsk
                && explicitKnowledgeAsk;
    }

    private void addConditionalTasks(Input input,
                                     Set<String> knownSources,
                                     List<InvestigationFollowUpTask> tasks,
                                     List<String> notes) {
        if (input.conditionalTasks().isEmpty()) return;
        if (pureKnowledgeQuestion(input.planIntent(), input.questionSignals())) {
            notes.add("知识型问题无实时验证诉求，跳过条件实时数据源。");
            return;
        }
        for (InvestigationFollowUpTask task : input.conditionalTasks()) {
            if (task == null || knownSources.contains(task.source())) continue;
            if (shouldActivateConditionalTask(task, input.observations())) {
                tasks.add(task);
                knownSources.add(task.source());
                notes.add("条件任务被激活，追加 " + task.agent() + "：" + task.reason());
            }
        }
    }

    private void addFallbackTasks(Input input,
                                  Set<String> knownSources,
                                  List<InvestigationFollowUpTask> tasks,
                                  List<String> notes) {
        InvestigationObservation result = input.latestObservation();
        if (result == null || result.found() || result.blocked()) return;
        InvestigationQuestionSignals signals = input.questionSignals();
        if (SOURCE_ES.equals(result.source())
                && !knownSources.contains(SOURCE_PROM)
                && (signals.metricSignal() || signals.knowledgeSignal())) {
            add(tasks, knownSources, task(
                    SOURCE_PROM,
                    "prometheus-agent",
                    "日志证据不足，改查实例、QPS、错误率、延迟和资源指标。",
                    "子 Agent 已报告日志证据不足，主 Agent 调整到指标侧确认影响面。",
                    2,
                    "elasticsearch evidence insufficient"));
            notes.add("ES 证据不足，主 Agent 追加 prometheus-agent 判断影响面。");
        }
        if (SOURCE_PROM.equals(result.source())
                && !knownSources.contains(SOURCE_ES)
                && (signals.logSignal() || signals.knowledgeSignal())) {
            add(tasks, knownSources, task(
                    SOURCE_ES,
                    "es-log-agent",
                    "指标信号不足，改查 ERROR/WARN 和请求日志样本。",
                    "子 Agent 已报告指标信号不足，主 Agent 调整到日志侧找事实证据。",
                    2,
                    "prometheus evidence insufficient"));
            notes.add("Prometheus 证据不足，主 Agent 追加 es-log-agent 查事实日志。");
        }
        if ((SOURCE_ES.equals(result.source()) || SOURCE_PROM.equals(result.source()))
                && input.mysqlSlowSqlEnabled()
                && !knownSources.contains(SOURCE_MYSQL_SLOW_SQL)
                && (signals.slowSqlSignal() || hasDatabaseLatencySignal(result))) {
            add(tasks, knownSources, task(
                    SOURCE_MYSQL_SLOW_SQL,
                    "mysql-slow-sql-agent",
                    "实时证据指向数据库耗时，查询 MySQL 慢 SQL/高耗时 SQL。",
                    "主 Agent 根据日志/指标 observation 判断慢 SQL 可能有信息增益。",
                    2,
                    "runtime evidence contains db latency"));
            notes.add("实时证据指向数据库耗时，主 Agent 追加 mysql-slow-sql-agent。");
        }
        if ((SOURCE_ES.equals(result.source()) || SOURCE_PROM.equals(result.source()))
                && !knownSources.contains(SOURCE_RAG)
                && signals.knowledgeSignal()) {
            add(tasks, knownSources, task(
                    SOURCE_RAG,
                    "rag-knowledge-agent",
                    "实时证据不足时检索 SOP、指标字典和历史案例。",
                    "问题包含解释/排查诉求，主 Agent 补充知识库证据。",
                    3,
                    "runtime evidence insufficient and knowledge requested"));
            notes.add("问题包含解释/排查诉求，主 Agent 追加 rag-knowledge-agent 补充 SOP/案例。");
        }
    }

    private boolean shouldActivateConditionalTask(InvestigationFollowUpTask task,
                                                  List<InvestigationObservation> results) {
        boolean hasRuntimeFound = results.stream().anyMatch(result ->
                result != null
                        && (SOURCE_ES.equals(result.source()) || SOURCE_PROM.equals(result.source()))
                        && result.found()
                        && hasAnomalySignal(result));
        boolean hasEsFound = results.stream().anyMatch(result ->
                result != null && SOURCE_ES.equals(result.source()) && result.found());
        boolean hasPromAnomaly = results.stream().anyMatch(result ->
                result != null
                        && SOURCE_PROM.equals(result.source())
                        && result.found()
                        && hasAnomalySignal(result));
        boolean hasDbLatencySignal = results.stream().anyMatch(this::hasDatabaseLatencySignal);

        if (SOURCE_ES.equals(task.source())) return hasPromAnomaly;
        if (SOURCE_MYSQL_SLOW_SQL.equals(task.source())) return hasDbLatencySignal || hasPromAnomaly;
        if (SOURCE_RAG.equals(task.source())) return hasRuntimeFound || hasEsFound;
        return false;
    }

    private boolean hasAnomalySignal(InvestigationObservation result) {
        if (result == null) return false;
        String value = (result.summary() + " " + String.join(" ", result.evidence()))
                .toLowerCase(Locale.ROOT);
        return containsAny(value,
                "异常", "风险", "error", "5xx", "down", "error 日志",
                "warn", "timeout", "超时", "错误率");
    }

    private boolean hasDatabaseLatencySignal(InvestigationObservation result) {
        if (result == null) return false;
        return containsAny(result.searchableText(),
                "mysql", "sql", "慢查询", "slow query", "slow sql", "数据库", "db",
                "连接池", "索引", "rows_examined", "全表扫描", "延迟", "耗时",
                "timeout", "超时");
    }

    private void add(List<InvestigationFollowUpTask> tasks,
                     Set<String> knownSources,
                     InvestigationFollowUpTask task) {
        tasks.add(task);
        knownSources.add(task.source());
    }

    private InvestigationFollowUpTask task(String source,
                                           String agent,
                                           String goal,
                                           String reason,
                                           int priority,
                                           String condition) {
        return new InvestigationFollowUpTask(source, agent, goal, reason, priority, condition);
    }

    private boolean containsAny(String value, String... keywords) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record Input(String planIntent,
                        List<InvestigationFollowUpTask> conditionalTasks,
                        List<InvestigationObservation> observations,
                        InvestigationObservation latestObservation,
                        Set<String> executedSources,
                        Set<String> queuedSources,
                        InvestigationQuestionSignals questionSignals,
                        boolean mysqlSlowSqlEnabled) {
        public Input {
            planIntent = planIntent == null ? "" : planIntent.trim();
            conditionalTasks = conditionalTasks == null ? List.of() : List.copyOf(conditionalTasks);
            observations = observations == null ? List.of() : List.copyOf(observations);
            executedSources = executedSources == null ? Set.of() : Set.copyOf(executedSources);
            queuedSources = queuedSources == null ? Set.of() : Set.copyOf(queuedSources);
            if (questionSignals == null) {
                throw new IllegalArgumentException("INVESTIGATION_QUESTION_SIGNALS_REQUIRED");
            }
        }
    }
}
