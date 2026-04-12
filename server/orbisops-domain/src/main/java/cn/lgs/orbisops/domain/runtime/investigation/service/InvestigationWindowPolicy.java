package cn.lgs.orbisops.domain.runtime.investigation.service;

import cn.lgs.orbisops.domain.runtime.investigation.model.InvestigationWindow;

import java.util.Map;

/** Enforces monotonic and bounded datasource query-window expansion across retries. */
public final class InvestigationWindowPolicy {

    public static final int DEFAULT_RANGE_MINUTES = 15;
    public static final int MAX_RETRY_RANGE_MINUTES = 240;

    private static final Map<String, Integer> PROMETHEUS_WINDOW_RANK = Map.of(
            "1m", 1,
            "3m", 2,
            "5m", 3,
            "10m", 4,
            "15m", 5,
            "30m", 6,
            "1h", 7);

    public InvestigationWindow applyLogDecision(
            InvestigationWindow current,
            Integer decisionRangeMinutes,
            Boolean includeRecentLogs) {
        InvestigationWindow safe = safe(current);
        int candidate = decisionRangeMinutes == null
                ? safe.rangeMinutes()
                : decisionRangeMinutes;
        Boolean effectiveIncludeRecentLogs = includeRecentLogs == null
                ? safe.includeRecentLogs()
                : includeRecentLogs;
        return new InvestigationWindow(
                Math.max(safe.rangeMinutes(), candidate),
                safe.prometheusWindow(),
                effectiveIncludeRecentLogs);
    }

    public InvestigationWindow expandRange(
            InvestigationWindow current,
            Boolean includeRecentLogs) {
        InvestigationWindow safe = safe(current);
        int expanded = Math.min(
                Math.max(
                        safe.rangeMinutes() * 2,
                        safe.rangeMinutes() + DEFAULT_RANGE_MINUTES),
                MAX_RETRY_RANGE_MINUTES);
        return new InvestigationWindow(
                expanded,
                safe.prometheusWindow(),
                includeRecentLogs);
    }

    public InvestigationWindow applyPrometheusWindowDecision(
            InvestigationWindow current,
            String candidateWindow) {
        InvestigationWindow safe = safe(current);
        return new InvestigationWindow(
                safe.rangeMinutes(),
                longerPrometheusWindow(safe.prometheusWindow(), candidateWindow),
                safe.includeRecentLogs());
    }

    public InvestigationWindow expandPrometheusWindow(InvestigationWindow current) {
        InvestigationWindow safe = safe(current);
        return new InvestigationWindow(
                safe.rangeMinutes(),
                nextPrometheusWindow(safe.prometheusWindow()),
                safe.includeRecentLogs());
    }

    public InvestigationWindow safe(InvestigationWindow current) {
        return current == null
                ? new InvestigationWindow(DEFAULT_RANGE_MINUTES, "", null)
                : current;
    }

    private String longerPrometheusWindow(String current, String candidate) {
        if (!hasText(candidate)) return current;
        if (!hasText(current)) return candidate;
        return rank(candidate) >= rank(current) ? candidate : current;
    }

    private String nextPrometheusWindow(String current) {
        return switch (hasText(current) ? current : "5m") {
            case "1m" -> "3m";
            case "3m" -> "5m";
            case "5m" -> "10m";
            case "10m" -> "15m";
            case "15m" -> "30m";
            case "30m" -> "1h";
            case "1h" -> "1h";
            default -> "15m";
        };
    }

    private int rank(String window) {
        return PROMETHEUS_WINDOW_RANK.getOrDefault(window == null ? "" : window, 0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
