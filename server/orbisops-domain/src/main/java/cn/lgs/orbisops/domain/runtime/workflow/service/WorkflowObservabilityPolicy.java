package cn.lgs.orbisops.domain.runtime.workflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Deterministic evidence rules for published inspection/alert graphs; never infers a root cause. */
public final class WorkflowObservabilityPolicy {
    private static final Set<String> OPERATIONS = Set.of("WINDOW_ALERT", "WINDOW_INSPECTION", "REVIEW_ALERT", "REVIEW_INSPECTION",
            "ALERT_FROM_INSPECTION", "SUMMARIZE_INSPECTION");
    private static final String REQUESTS = "ops04_http_requests_total";
    private static final String ERRORS = "ops04_http_errors_total";
    private static final String BUCKET = "ops04_http_request_duration_seconds_bucket";
    private static final int MIN_SAMPLES = 100;

    public void validate(Map<?, ?> config) {
        if (!OPERATIONS.contains(String.valueOf(config.get("operation")))) fail("OPERATION_INVALID");
        for (String key : List.of("outputKey", "windowKey", "metricsKey", "logsKey", "sqlKey", "explainKey", "reportKey", "investigationKey")) {
            if (config.containsKey(key) && !(config.get(key) instanceof String text && text.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))) {
                fail("CONFIG_KEY_INVALID");
            }
        }
        if (!config.containsKey("outputKey")) fail("OUTPUT_KEY_REQUIRED");
        if (String.valueOf(config.get("operation")).startsWith("REVIEW_")
                && (!config.containsKey("windowKey") || !config.containsKey("metricsKey"))) fail("EVIDENCE_KEYS_REQUIRED");
        if (Set.of("ALERT_FROM_INSPECTION", "SUMMARIZE_INSPECTION").contains(String.valueOf(config.get("operation")))
                && !config.containsKey("reportKey")) fail("REPORT_KEY_REQUIRED");
        if ("SUMMARIZE_INSPECTION".equals(config.get("operation")) && !config.containsKey("investigationKey")) fail("INVESTIGATION_KEY_REQUIRED");
        // SLO values are this version's published defaults, not user/model tool arguments.
        Set<String> allowed = Set.of("operation", "outputKey", "windowKey", "metricsKey", "logsKey", "sqlKey", "explainKey", "reportKey", "investigationKey");
        if (!allowed.containsAll(config.keySet())) fail("UNKNOWN_POLICY_CONFIG");
    }

    public Map<String, Object> alertFromInspection(Map<String, Object> report, String project, String sourceRun) {
        Map<String, Object> window = map(report.get("window"));
        if (!project.equals(window.get("projectId"))) fail("PROJECT_MISMATCH");
        if (!Set.of("UNHEALTHY", "UNREACHABLE").contains(String.valueOf(report.get("status")))) fail("INSPECTION_ANOMALY_REQUIRED");
        if (finite(window.get("endEpoch")) - finite(window.get("startEpoch")) != 300) fail("INSPECTION_WINDOW_INVALID");
        var input = new LinkedHashMap<String, Object>();
        input.put("projectId", project);
        input.put("environment", identity(window.get("environment")));
        input.put("serviceId", identity(window.get("serviceId")));
        input.put("alertTime", Instant.ofEpochSecond(((Number) window.get("endEpoch")).longValue()).toString());
        input.put("alertContent", "A 巡检发现 " + report.get("status") + "；按已发布 B 继续有界只读调查");
        input.put("priorEvidence", Map.of("sourceRunId", sourceRun, "inspectionReport", report,
                "reuseDecision", "WINDOW_DIFFERS", "reason", "保留五分钟巡检证据引用；B 告警窗口不同，按新窗口查询"));
        return input;
    }

    public Map<String, Object> summarizeInspection(Map<String, Object> report, Map<String, Object> investigation) {
        if (!Set.of("UNHEALTHY", "UNREACHABLE").contains(String.valueOf(report.get("status")))) fail("INSPECTION_ANOMALY_REQUIRED");
        if (!"SUCCEEDED".equals(investigation.get("executionStatus"))
                || !(investigation.get("childRunId") instanceof String run) || run.isBlank()
                || !(investigation.get("workflowDefinitionHash") instanceof String hash) || hash.isBlank()) fail("INVESTIGATION_REFERENCE_REQUIRED");
        var result = new LinkedHashMap<>(report);
        result.put("investigation", investigation);
        result.put("nextStep", "已执行有界 B 调查，详见子运行结论与缺口；巡检判定仍只适用于原五分钟窗口");
        return result;
    }

    public Map<String, Object> window(String operation, Map<String, Object> input, String project, Instant now) {
        if (!Set.of("WINDOW_ALERT", "WINDOW_INSPECTION").contains(operation)) fail("OPERATION_INVALID");
        if (project == null || project.isBlank() || !project.equals(input.get("projectId"))) fail("PROJECT_MISMATCH");
        String service = identity(input.get("serviceId")), environment = identity(input.get("environment"));
        long end = now.getEpochSecond(), start = end - 300;
        boolean complete = true;
        long alert = 0;
        if (operation.equals("WINDOW_ALERT")) {
            try { alert = Instant.parse(String.valueOf(input.get("alertTime"))).getEpochSecond(); }
            catch (RuntimeException invalid) { fail("ALERT_TIME_INVALID"); }
            if (alert > end || alert < end - 6 * 86400) fail("ALERT_TIME_OUT_OF_BOUNDS");
            start = alert - 900;
            complete = end >= alert + 900;
            end = Math.min(end, alert + 900);
        }
        var window = new LinkedHashMap<String, Object>();
        window.put("projectId", project);
        window.put("environment", environment);
        window.put("serviceId", service);
        window.put("startEpoch", start);
        window.put("endEpoch", end);
        window.put("startTime", Instant.ofEpochSecond(start).toString());
        window.put("endTime", Instant.ofEpochSecond(end).toString());
        window.put("complete", complete);
        window.put("missingFutureSeconds", operation.equals("WINDOW_ALERT") ? Math.max(0, alert + 900 - end) : 0);
        window.put("policyVersion", "observability-defaults-v1");
        return window;
    }

    public Map<String, Object> review(String operation, Map<String, Object> window, Map<String, Object> metrics,
                                      Map<String, Object> logs, Map<String, Object> sql) {
        if (!Set.of("REVIEW_ALERT", "REVIEW_INSPECTION").contains(operation)) fail("OPERATION_INVALID");
        var gaps = new ArrayList<String>();
        var conflicts = new ArrayList<String>();
        var references = new ArrayList<String>();
        Map<String, Object> m = evidence(window, metrics, "metrics_window", gaps, references);
        Map<String, Object> l = operation.equals("REVIEW_ALERT") ? evidence(window, logs, "logs_window", gaps, references) : Map.of();
        Map<String, Object> s = sql == null || sql.isEmpty() ? Map.of() : evidence(window, sql, "sql_window", gaps, references);
        var facts = metricFacts(window, m, gaps);
        if (!Boolean.TRUE.equals(window.get("complete"))) gaps.add("ALERT_WINDOW_NOT_COMPLETE");
        long samples = ((Number) facts.get("sampleCountLowerBound")).longValue();
        if (samples < MIN_SAMPLES) gaps.add("INSUFFICIENT_REQUEST_SAMPLES");
        boolean needsSql = !l.isEmpty() && finite(l.get("slowSqlCount")) > 0 && (sql == null || sql.isEmpty());
        boolean slowFound = !s.isEmpty() && finite(s.get("slowSqlCount")) > 0;
        if (!l.isEmpty()) {
            if (!Boolean.TRUE.equals(l.get("complete"))) gaps.add("LOG_RESULT_INCOMPLETE");
            double logErrors = finite(l.get("errorCount")), metricErrors = finite(facts.get("errorCount"));
            if (!m.isEmpty() && ((logErrors == 0 && metricErrors > 0) || (logErrors > 0 && metricErrors == 0))) {
                conflicts.add("METRICS_LOGS_ERROR_EVIDENCE_DISAGREES");
            }
            if (!s.isEmpty() && finite(l.get("slowSqlCount")) > 0 && !slowFound) conflicts.add("LOGS_SQL_SLOW_EVIDENCE_DISAGREES");
        }
        if (!s.isEmpty() && !Boolean.TRUE.equals(s.get("complete"))) gaps.add("SQL_RESULT_INCOMPLETE");
        boolean unreachable = "UNREACHABLE".equals(facts.get("reachability"));
        boolean anomaly = finite(facts.get("errorRate")) > .01 || finite(facts.get("p95Seconds")) > 1.;
        String status = unreachable ? "UNREACHABLE" : !conflicts.isEmpty() ? "CONFLICT" : !gaps.isEmpty() ? "INCONCLUSIVE"
                : operation.equals("REVIEW_INSPECTION") ? (anomaly ? "UNHEALTHY" : "HEALTHY")
                : anomaly ? "OBSERVED_ANOMALY" : "NO_OBSERVED_ANOMALY";
        var result = new LinkedHashMap<String, Object>();
        result.put("status", status);
        result.put("window", window);
        result.put("metrics", facts);
        result.put("thresholds", Map.of("minimumRequests", MIN_SAMPLES, "maxErrorRate", .01, "maxP95Seconds", 1.));
        result.put("evidenceReferences", references);
        result.put("evidenceGaps", new ArrayList<>(new java.util.LinkedHashSet<>(gaps)));
        result.put("evidenceConflicts", conflicts);
        result.put("needsSql", needsSql);
        result.put("slowSqlFinding", slowFound ? "FOUND" : needsSql ? "PENDING_READ_ONLY_SQL" : "NOT_ESTABLISHED");
        result.put("rootCauseConfirmed", false);
        result.put("unconnectedMetrics", List.of("CPU", "JVM_GC"));
        result.put("nextStep", unreachable || !conflicts.isEmpty() || !gaps.isEmpty() ? "补齐列出的证据或交人工处理；不执行修复"
                : slowFound ? "已发现同窗慢 SQL；可审查执行计划。关联不证明因果，仍需验证候选原因"
                : anomaly ? "窗口内存在异常；保留候选原因供有授权的调查继续验证" : "仅说明已检查指标在该窗口内未超阈值");
        return result;
    }

    public Map<String, Object> withExplain(Map<String, Object> report, Map<String, Object> window, Map<String, Object> explain) {
        var result = new LinkedHashMap<>(report);
        var gaps = new ArrayList<String>();
        var references = new ArrayList<String>();
        list(report.get("evidenceGaps")).forEach(item -> gaps.add(String.valueOf(item)));
        list(report.get("evidenceReferences")).forEach(item -> references.add(String.valueOf(item)));
        var plan = explain.isEmpty() ? Map.<String,Object>of() : evidence(window, explain, "sql_explain", gaps, references);
        result.put("supplementRoundsUsed", explain.isEmpty() ? 0 : 1);
        result.put("maximumSupplementRounds", 2);
        result.put("explainAvailable", !plan.isEmpty() && plan.get("plan") instanceof Map<?, ?>);
        result.put("evidenceReferences", references);
        result.put("evidenceGaps", new ArrayList<>(new java.util.LinkedHashSet<>(gaps)));
        if (!gaps.isEmpty() && !Set.of("UNREACHABLE", "CONFLICT").contains(String.valueOf(result.get("status")))) result.put("status", "INCONCLUSIVE");
        return result;
    }

    private Map<String,Object> evidence(Map<String,Object> window, Map<String,Object> wrapper, String kind,
            List<String> gaps, List<String> references) {
        return new cn.lgs.orbisops.domain.shared.service.ObservedHttpMetricsPolicy().evidence(window,wrapper,kind,gaps,references);
    }

    private Map<String,Object> metricFacts(Map<String,Object> window, Map<String,Object> metrics, List<String> gaps) {
        return new cn.lgs.orbisops.domain.shared.service.ObservedHttpMetricsPolicy().metricFacts(window,metrics,gaps);
    }

    private String identity(Object raw) {
        if (!(raw instanceof String value) || !value.matches("[A-Za-z0-9_-]{1,100}")) fail("TARGET_IDENTITY_INVALID");
        return String.valueOf(raw);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object raw) { return raw instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of(); }
    private List<?> list(Object raw) { return raw instanceof List<?> value ? value : List.of(); }
    private double finite(Object raw) {
        double value;
        try { value = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw)); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("OBSERVABILITY_NUMBER_INVALID"); }
        if (!Double.isFinite(value)) fail("NUMBER_NOT_FINITE");
        return value;
    }
    private void fail(String reason) { throw new IllegalArgumentException("OBSERVABILITY_" + reason); }
}
