package cn.lgs.orbisops.application.mcp;

import java.util.List;
import java.util.Locale;

final class McpDiscoveryScoringPolicy {

    double score(McpProjectToolDescriptor tool, McpDiscoverySelectionRequest request) {
        if (tool == null) throw new IllegalArgumentException("PROJECT_MCP_DEFINITION_REQUIRED");
        if (request == null) throw new IllegalArgumentException("MCP_DISCOVERY_COMMAND_REQUIRED");
        String haystack = (tool.toolName() + " " + tool.resourceType() + " "
                + String.join(" ", tool.allowedActions())).toLowerCase(Locale.ROOT);
        String capability = text(request.capability()).toLowerCase(Locale.ROOT);
        double score = 0.2D;
        if (!capability.isBlank() && haystack.contains(capability)) score += 0.3D;
        score += signalScore(haystack, capabilitySignals(capability), 0.45D);
        score += requestSignalScore(haystack, text(request.userRequest()).toLowerCase(Locale.ROOT));
        if (tool.readOnly()) score += 0.15D;
        if (tool.riskLevel().lowRisk()) score += 0.1D;
        if (request.preferReadOnly() && tool.readOnly()) score += 0.1D;
        return score;
    }

    private List<String> capabilitySignals(String capability) {
        return switch (text(capability).replace('-', '_')) {
            case "metric_query", "metrics", "metric" -> List.of("prometheus", "grafana", "metric", "query_range", "query_instant");
            case "log_query", "logs", "log" -> List.of("elasticsearch", "opensearch", "log", "search_index");
            case "trace_query", "traces", "trace" -> List.of("trace", "jaeger", "tempo", "skywalking");
            case "database_query", "database", "sql" -> List.of("mysql", "postgresql", "database", "select", "explain");
            case "cache_query", "cache" -> List.of("redis", "cache", "ttl", "scan_namespace");
            case "message_queue_query", "message_queue", "queue" -> List.of("rabbitmq", "kafka", "queue", "consumer");
            default -> text(capability).isBlank() ? List.of() : List.of(capability);
        };
    }

    private double requestSignalScore(String haystack, String userRequest) {
        if (text(userRequest).isBlank()) return 0D;
        double score = 0D;
        if (containsAny(userRequest, "指标", "metric", "prometheus", "grafana", "5xx", "qps", "延迟", "latency")) {
            score += signalScore(haystack, List.of("prometheus", "grafana", "metric", "query_range", "query_instant"), 0.4D);
        }
        if (containsAny(userRequest, "日志", "错误日志", "log", "elasticsearch", "opensearch", "es ")) {
            score += signalScore(haystack, List.of("elasticsearch", "opensearch", "log", "search_index"), 0.4D);
        }
        if (containsAny(userRequest, "trace", "链路", "调用链", "jaeger", "tempo", "skywalking")) {
            score += signalScore(haystack, List.of("trace", "jaeger", "tempo", "skywalking"), 0.4D);
        }
        if (containsAny(userRequest, "数据库", "sql", "mysql", "postgresql", "慢查询", "慢 sql")) {
            score += signalScore(haystack, List.of("mysql", "postgresql", "database", "select", "explain"), 0.4D);
        }
        if (containsAny(userRequest, "redis", "缓存", "cache", "ttl", "key")) {
            score += signalScore(haystack, List.of("redis", "cache", "ttl", "scan_namespace"), 0.4D);
        }
        if (containsAny(userRequest, "rabbitmq", "kafka", "消息队列", "消费积压", "queue", "consumer")) {
            score += signalScore(haystack, List.of("rabbitmq", "kafka", "queue", "consumer"), 0.4D);
        }
        return score;
    }

    private double signalScore(String haystack, List<String> signals, double matchedScore) {
        if (text(haystack).isBlank() || signals == null || signals.isEmpty()) return 0D;
        return signals.stream().anyMatch(signal -> !text(signal).isBlank() && haystack.contains(signal))
                ? matchedScore : 0D;
    }

    private boolean containsAny(String value, String... candidates) {
        if (text(value).isBlank() || candidates == null) return false;
        for (String candidate : candidates) {
            if (!text(candidate).isBlank() && value.contains(candidate)) return true;
        }
        return false;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
