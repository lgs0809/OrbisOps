package cn.lgs.orbisops.application.mcp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class McpDiscoveryModelMapper {

    private final McpJsonCodec jsonCodec;

    McpDiscoveryModelMapper(McpJsonCodec jsonCodec) {
        if (jsonCodec == null) throw new IllegalArgumentException("MCP_JSON_CODEC_REQUIRED");
        this.jsonCodec = jsonCodec;
    }

    Map<String, Object> scoreTool(Map<String, Object> tool,
                                  String capability,
                                  Map<String, Object> request) {
        Map<String, Object> data = toolSummary(tool);
        double score = 0.2D;
        String haystack = (text(tool.get("toolName")) + " "
                + text(tool.get("resourceType")) + " "
                + text(tool.get("description")) + " "
                + encode(tool.get("allowedActions"))).toLowerCase(Locale.ROOT);
        String needle = text(capability).toLowerCase(Locale.ROOT);
        if (!needle.isBlank() && haystack.contains(needle)) score += 0.3D;
        score += signalScore(haystack, capabilitySignals(needle), 0.45D);
        String userRequest = text(request.get("userRequest"),
                text(request.get("query"), text(request.get("request")))).toLowerCase(Locale.ROOT);
        score += requestSignalScore(haystack, userRequest);
        if (Boolean.TRUE.equals(tool.get("readOnly"))) score += 0.15D;
        if ("LOW".equalsIgnoreCase(text(tool.get("riskLevel")))) score += 0.1D;
        if (!text(request.get("preferReadOnly")).isBlank() && Boolean.TRUE.equals(tool.get("readOnly"))) {
            score += 0.1D;
        }
        data.put("score", score);
        return data;
    }

    Map<String, Object> toolSummary(Map<String, Object> tool) {
        Map<String, Object> safe = tool == null ? Map.of() : tool;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolId", text(safe.get("toolId"), text(safe.get("mcpId"))));
        data.put("toolName", text(safe.get("toolName"), text(safe.get("mcpName"))));
        data.put("resourceType", text(safe.get("resourceType")));
        data.put("allowedActions", safe.getOrDefault("allowedActions", List.of()));
        data.put("riskLevel", text(safe.get("riskLevel"), "HIGH"));
        data.put("readOnly", safe.getOrDefault("readOnly", false));
        data.put("status", text(safe.get("status"), "ENABLED"));
        data.put("description", text(safe.get("description")));
        return data;
    }

    Map<String, Object> remoteToolMetadata(Map<String, Object> tool, String remoteToolName) {
        if (tool == null || text(remoteToolName).isBlank()) return Map.of();
        Map<String, Object> direct = remoteToolMetadataFrom(tool, remoteToolName);
        if (!direct.isEmpty()) return direct;
        if (tool.get("transportConfig") instanceof Map<?, ?> config) {
            return remoteToolMetadataFrom(map(config), remoteToolName);
        }
        return Map.of();
    }

    String schemaHash(String projectId,
                      String mcpId,
                      String toolId,
                      String effectiveToolName,
                      Map<String, Object> schema,
                      Map<String, Object> remoteMetadata) {
        String canonical = encode(canonicalize(schemaHashInput(
                projectId, mcpId, toolId, effectiveToolName, schema, remoteMetadata)));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    Map<String, Object> schemaHashInput(String projectId,
                                        String mcpId,
                                        String toolId,
                                        String effectiveToolName,
                                        Map<String, Object> schema,
                                        Map<String, Object> remoteMetadata) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", text(projectId));
        input.put("mcpId", text(mcpId));
        input.put("toolId", text(toolId));
        input.put("toolName", text(effectiveToolName));
        input.put("resourceType", text(schema == null ? null : schema.get("resourceType")));
        input.put("transportType", text(schema == null ? null : schema.get("transportType")));
        if (schema != null && schema.get("outputSchema") != null) input.put("outputSchema", schema.get("outputSchema"));
        Map<String, Object> remote = remoteMetadata == null ? Map.of() : remoteMetadata;
        input.put("description", !text(schema == null ? null : schema.get("description")).isBlank()
                ? text(schema.get("description"))
                : text(first(remote, "description", "desc", "summary")));
        input.put("inputSchema", schema != null && schema.containsKey("schema")
                ? schema.get("schema")
                : first(remote, "inputSchema", "input_schema", "schema", "parameters"));
        return input;
    }

    Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sorted = new TreeMap<>();
            source.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : iterable) list.add(canonicalize(item));
            return list;
        }
        return value == null ? "" : value;
    }

    Map<String, Object> jsonMap(Object value) {
        if (value instanceof Map<?, ?> source) return map(source);
        String serialized = text(value);
        if (serialized.isBlank()) return new LinkedHashMap<>();
        try {
            Object decoded = jsonCodec.decode(serialized);
            return decoded instanceof Map<?, ?> source ? map(source) : new LinkedHashMap<>();
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
    }

    Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    List<String> strings(Object value) {
        Object source = value;
        if (value instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                try {
                    source = jsonCodec.decode(trimmed);
                } catch (RuntimeException ignored) {
                    source = List.of();
                }
            } else {
                List<String> values = new ArrayList<>();
                for (String item : trimmed.split("[,;\\s]+")) {
                    String normalized = item.trim();
                    if (!normalized.isBlank()) values.add(normalized);
                }
                return List.copyOf(values);
            }
        }
        if (source instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                String normalized = text(item);
                if (!normalized.isBlank()) values.add(normalized);
            }
            return List.copyOf(values);
        }
        return List.of();
    }

    boolean hasAnyKey(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) return false;
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null && !text(source.get(key)).isBlank()) return true;
        }
        return false;
    }

    Object first(Map<?, ?> source, String... keys) {
        if (source == null) return null;
        for (String key : keys) {
            if (source.containsKey(key)) return source.get(key);
        }
        return null;
    }

    Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    String encode(Object value) {
        return jsonCodec.encode(value);
    }

    boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        if (normalized.isBlank()) return fallback;
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized) || "y".equalsIgnoreCase(normalized);
    }

    int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    double doubleValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(text(value, "0"));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    String text(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private Map<String, Object> remoteToolMetadataFrom(Map<String, Object> source, String remoteToolName) {
        Object remoteTools = source.get("remoteTools");
        if (remoteTools instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> candidate
                        && remoteToolName.equals(text(first(candidate, "toolName", "remoteToolName", "name")))) {
                    return map(candidate);
                }
            }
        }
        Object schemas = source.get("toolSchemas");
        if (schemas instanceof Map<?, ?> schemaMap && schemaMap.get(remoteToolName) instanceof Map<?, ?> schema) {
            return map(schema);
        }
        Object metadata = source.get("remoteToolMetadata");
        if (metadata instanceof Map<?, ?> metadataMap && metadataMap.get(remoteToolName) instanceof Map<?, ?> entry) {
            return map(entry);
        }
        return Map.of();
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
}
