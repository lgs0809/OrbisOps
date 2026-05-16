package cn.lgs.orbisops.trigger.ops;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed operational signals from one user question. */
public record OpsQuestionContext(String originalQuestion,
                                 String normalizedQuestion,
                                 String loweredQuestion,
                                 boolean blankQuestion,
                                 boolean hasLogSignal,
                                 boolean hasMetricSignal,
                                 boolean hasSlowSqlSignal,
                                 boolean hasKnowledgeSignal,
                                 List<String> traceIds,
                                 List<String> entityIds,
                                 List<String> uris,
                                 List<String> logLevels,
                                 List<String> errorCodes,
                                 List<String> keywords) {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
            "(?i)(?:traceId|trace_id|trace-id)[:=：\\s]+([A-Za-z0-9._:-]{6,})");
    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile(
            "(?i)(?:request|resource|job|task|event|incident|run|session|deployment|instance|correlation)[_-]?id[:=：\\s]+([A-Za-z0-9._:-]{4,})");
    private static final Pattern URI_PATTERN = Pattern.compile(
            "(/(?:api(?:/v\\d+)?|v\\d+|actuator)(?:/[A-Za-z0-9_.{}:-]+)+)");
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile(
            "(?i)\\b(?:(?:ERR|ERROR|BIZ|SYS|SYSTEM)[-_]?[A-Z0-9]+(?:[-_][A-Z0-9]+)*|E[-_]?\\d[A-Z0-9]*(?:[-_][A-Z0-9]+)*)\\b");
    private static final Pattern LOGGER_PATTERN = Pattern.compile("\\b[a-zA-Z_]\\w*(?:\\.[a-zA-Z_]\\w*){2,}\\b");

    public static OpsQuestionContext from(String question) {
        String normalized = question == null ? "" : question.trim();
        String lowered = normalized.toLowerCase(Locale.ROOT);
        boolean blank = !StringUtils.hasText(normalized);

        List<String> traceIds = findAll(TRACE_ID_PATTERN, normalized);
        List<String> entityIds = findAll(ENTITY_ID_PATTERN, normalized);
        List<String> uris = findAll(URI_PATTERN, normalized);
        List<String> errorCodes = findAll(ERROR_CODE_PATTERN, normalized);
        List<String> logLevels = detectLevels(lowered);
        List<String> keywords = detectKeywords(normalized, lowered);

        boolean logSignal = !traceIds.isEmpty()
                || !entityIds.isEmpty()
                || !uris.isEmpty()
                || !errorCodes.isEmpty()
                || !logLevels.isEmpty()
                || containsAny(lowered, "exception", "stack", "error", "warn", "日志", "错误码", "异常", "堆栈", "报错");
        boolean metricSignal = containsAny(lowered,
                "qps", "p95", "p99", "4xx", "5xx", "延迟", "耗时", "响应时间", "平均响应", "错误率", "错误预算",
                "cpu", "jvm", "gc", "heap", "内存", "线程", "threads", "连接池", "连接数", "target", " up ", "实例", "实例在线",
                "健康", "巡检", "报警", "告警", "吞吐量", "throughput", "运行摘要", "状态摘要", "首页", "运行状态", "资源风险",
                "指标", "慢", "timeout", "超时");
        boolean slowSqlSignal = containsAny(lowered,
                "slow sql", "slow query", "慢 sql", "慢sql", "慢查询", "sql 慢", "sql慢", "sql 是否慢", "数据库慢", "db 慢",
                "mysql 慢", "mysql慢", "高耗时 sql", "sql digest", "digest", "performance_schema", "rows_examined",
                "query_time", "rows_sent", "filesort", "order by", "group by", "索引失效", "缺索引", "全表扫描", "sql 证据");
        boolean knowledgeSignal = containsAny(lowered,
                "怎么", "如何", "为什么", "原因", "排查", "sop", "runbook", "指标含义", "历史", "案例", "知识库", "解释",
                "是什么", "能不能", "可以", "应该", "规则", "建议", "自动", "安全", "只读", "边界", "策略", "模板", "包含");

        return new OpsQuestionContext(normalized, normalized, lowered, blank, logSignal, metricSignal, slowSqlSignal, knowledgeSignal,
                traceIds, entityIds, uris, logLevels, errorCodes, keywords);
    }

    public String primaryUri() {
        return uris.isEmpty() ? null : uris.get(0);
    }

    public boolean hasRuntimeFilter() {
        return !traceIds.isEmpty() || !entityIds.isEmpty() || !uris.isEmpty()
                || !logLevels.isEmpty() || !errorCodes.isEmpty();
    }

    public List<String> esMustPhrases() {
        List<String> phrases = new ArrayList<>();
        phrases.addAll(traceIds);
        phrases.addAll(entityIds);
        phrases.addAll(uris);
        phrases.addAll(errorCodes);
        return distinct(phrases);
    }

    public List<String> esShouldPhrases() {
        return distinct(keywords);
    }

    public String describeFilters() {
        List<String> parts = new ArrayList<>();
        if (!traceIds.isEmpty()) parts.add("traceId=" + String.join(",", traceIds));
        if (!entityIds.isEmpty()) parts.add("entityId=" + String.join(",", entityIds));
        if (!uris.isEmpty()) parts.add("uri=" + String.join(",", uris));
        if (!logLevels.isEmpty()) parts.add("level=" + String.join(",", logLevels));
        if (!errorCodes.isEmpty()) parts.add("errorCode=" + String.join(",", errorCodes));
        if (!keywords.isEmpty()) parts.add("keyword=" + String.join(",", keywords));
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }

    private static List<String> findAll(Pattern pattern, String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            if (StringUtils.hasText(value)) values.add(trimPunctuation(value));
        }
        return new ArrayList<>(values);
    }

    private static List<String> detectLevels(String lowered) {
        List<String> levels = new ArrayList<>();
        if (containsAny(lowered, "error", "异常", "报错")) levels.add("ERROR");
        if (containsAny(lowered, "warn", "warning", "告警", "报警")) levels.add("WARN");
        return levels;
    }

    private static List<String> detectKeywords(String original, String lowered) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : List.of(
                "服务", "实例", "接口", "数据库", "缓存", "消息队列", "配置", "发布", "回滚",
                "降级", "限流", "熔断", "幂等", "超时", "错误", "告警")) {
            if (original.contains(keyword) || lowered.contains(keyword.toLowerCase(Locale.ROOT))) {
                keywords.add(keyword);
            }
        }
        Matcher loggerMatcher = LOGGER_PATTERN.matcher(original);
        while (loggerMatcher.find()) keywords.add(loggerMatcher.group());
        return distinct(keywords);
    }

    private static List<String> distinct(List<String> values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) set.add(value);
        }
        return new ArrayList<>(set);
    }

    private static String trimPunctuation(String value) {
        return value == null ? "" : value.replaceAll("[,，。;；)）\\]}]+$", "");
    }

    private static boolean containsAny(String value, String... keywords) {
        if (value == null) return false;
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }
}
