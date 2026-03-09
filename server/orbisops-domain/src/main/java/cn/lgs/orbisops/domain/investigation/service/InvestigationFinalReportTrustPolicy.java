package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision.Failure.MISSING_INVESTIGATION_GAP;
import static cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision.Failure.MISSING_SOURCE_OR_GAP_SECTION;
import static cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision.Failure.UNEXECUTED_SOURCE_CLAIM;

/** Deterministic evidence-boundary policy for LLM final reports. */
public final class InvestigationFinalReportTrustPolicy {

    public InvestigationFinalReportTrustDecision assess(Input input) {
        String report = input.report();
        String normalized = report.toLowerCase(Locale.ROOT);
        Set<String> executedSources = executedSources(input);
        if (!normalized.contains("数据源") || !normalized.contains("缺口")) {
            return InvestigationFinalReportTrustDecision.rejected(
                    MISSING_SOURCE_OR_GAP_SECTION,
                    List.of(),
                    executedSources);
        }

        List<String> violations = sourceAliases().entrySet().stream()
                .filter(entry -> !executedSources.contains(entry.getKey()))
                .filter(entry -> claimsQueriedEvidence(report, entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!violations.isEmpty()) {
            return InvestigationFinalReportTrustDecision.rejected(
                    UNEXECUTED_SOURCE_CLAIM,
                    violations,
                    executedSources);
        }
        if (hasInvestigationGaps(input.results())
                && !normalized.contains("缺口")) {
            return InvestigationFinalReportTrustDecision.rejected(
                    MISSING_INVESTIGATION_GAP,
                    List.of(),
                    executedSources);
        }
        return InvestigationFinalReportTrustDecision.trusted(executedSources);
    }

    private Set<String> executedSources(Input input) {
        Set<String> sources = new LinkedHashSet<>();
        if (input.evidence().elasticsearch()) {
            sources.add("elasticsearch");
        }
        if (input.evidence().prometheus()) {
            sources.add("prometheus");
        }
        if (input.evidence().mysqlSlowSql()) {
            sources.add("mysql_slow_sql");
        }
        input.results().forEach(result -> {
            String source = canonicalSource(result.source());
            if (!source.isBlank()
                    && !"skipped".equalsIgnoreCase(result.status())) {
                sources.add(source);
            }
        });
        return sources;
    }

    private Map<String, List<String>> sourceAliases() {
        return Map.of(
                "elasticsearch", List.of("elasticsearch", "elk", "日志"),
                "prometheus", List.of("prometheus", "promql", "指标", "监控"),
                "mysql_slow_sql", List.of("mysql", "slow sql", "慢 sql", "慢sql"),
                "rag", List.of("rag", "知识库", "向量库", "故障案例"));
    }

    private boolean claimsQueriedEvidence(
            String report,
            List<String> aliases) {
        String[] sentences = report.split("[。；;\\n]");
        for (String sentence : sentences) {
            String normalized = sentence.toLowerCase(Locale.ROOT);
            boolean mentionsSource = aliases.stream().anyMatch(
                    alias -> normalized.contains(alias.toLowerCase(Locale.ROOT)));
            if (!mentionsSource) {
                continue;
            }
            if (containsAny(normalized, List.of(
                    "未", "没有", "无", "缺口", "不可用", "未接入",
                    "无法", "待补充", "需要补充"))) {
                continue;
            }
            if (containsAny(normalized, List.of(
                    "已查询", "查询结果", "检索到", "证据显示", "日志显示",
                    "指标显示", "promql", "慢sql显示", "慢 sql 显示"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInvestigationGaps(List<Result> results) {
        return results.stream().anyMatch(
                result -> result.hasGaps()
                        || !isSuccessfulStatus(result.status()));
    }

    private boolean isSuccessfulStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return Set.of("FOUND", "SUCCEEDED", "SUCCESS", "OK")
                .contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private String canonicalSource(String source) {
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (value.contains("elastic")
                || value.contains("elk")
                || value.contains("log")) {
            return "elasticsearch";
        }
        if (value.contains("prom")) {
            return "prometheus";
        }
        if (value.contains("mysql") || value.contains("slow")) {
            return "mysql_slow_sql";
        }
        if (value.contains("rag")
                || value.contains("knowledge")
                || value.contains("vector")) {
            return "rag";
        }
        return value;
    }

    private boolean containsAny(String text, List<String> candidates) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return candidates.stream().anyMatch(text::contains);
    }

    public record Evidence(
            boolean elasticsearch,
            boolean prometheus,
            boolean mysqlSlowSql) {
    }

    public record Result(
            String source,
            String status,
            boolean hasGaps) {
    }

    public record Input(
            String report,
            Evidence evidence,
            List<Result> results) {

        public Input {
            report = report == null ? "" : report;
            evidence = evidence == null
                    ? new Evidence(false, false, false)
                    : evidence;
            results = results == null ? List.of() : List.copyOf(results);
        }
    }
}
