package cn.lgs.orbisops.trigger.ops.toolset;

import com.alibaba.fastjson.JSON;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Elasticsearch local tool protocol and index/time policy adapter. */
public final class OpsLocalElasticsearchAdapter {

    private final OpsLocalAdapterSettings settings;
    private final OpsLocalHttpTransport http;

    public OpsLocalElasticsearchAdapter(
            OpsLocalAdapterSettings settings,
            OpsLocalHttpTransport http) {
        if (settings == null) throw new IllegalArgumentException("LOCAL_ADAPTER_SETTINGS_REQUIRED");
        if (http == null) throw new IllegalArgumentException("LOCAL_HTTP_TRANSPORT_REQUIRED");
        this.settings = settings;
        this.http = http;
    }

    public Map<String, Object> execute(
            String toolName,
            OpsLocalToolArguments args) {
        String index = args.text("index", settings.elasticsearchIndex());
        if (index.isBlank()) {
            throw new IllegalArgumentException(
                    "Elasticsearch 查询必须提供 index 或配置 ops.elasticsearch-index");
        }
        assertAllowedIndex(index);
        Map<String, Object> body = requestBody(toolName, args);
        String response = http.postJson(
                settings.elasticsearchUrl() + "/" + encodePath(index) + "/_search",
                JSON.toJSONString(body));
        return Map.of(
                "status", "SUCCEEDED",
                "adapter", "elasticsearch",
                "toolName", toolName,
                "index", index,
                "response", response);
    }

    Map<String, Object> requestBody(
            String toolName,
            OpsLocalToolArguments args) {
        int size = args.boundedInt("size", 1, settings.maxRows(), 20);
        Map<String, Object> timeRange = requiredTimeRange(args);
        return switch (toolName) {
            case "elk_search", "logs_search" -> Map.of(
                    "size", size,
                    "query", boolQuery(args.text("query", "*"), timeRange));
            case "elk_aggregate_errors" -> Map.of(
                    "size", 0,
                    "query", boolQuery(
                            args.text("query", "level:ERROR OR message:ERROR"),
                            timeRange),
                    "aggs", Map.of(
                            "top_errors", Map.of(
                                    "terms", Map.of(
                                            "field", args.text("field", "message.keyword"),
                                            "size", size))));
            case "elk_trace_lookup" -> Map.of(
                    "size", size,
                    "query", Map.of(
                            "bool", Map.of(
                                    "must", List.of(
                                            Map.of("term", Map.of(
                                                    args.text("traceField", "traceId"),
                                                    args.required("traceId", "trace lookup 必须提供 traceId"))),
                                            Map.of("range", Map.of("@timestamp", timeRange))))));
            case "elk_log_context" -> Map.of(
                    "size", size,
                    "query", boolQuery(args.text("query", "*"), timeRange),
                    "sort", List.of(Map.of(
                            "@timestamp", Map.of("order", "desc"))));
            default -> throw new IllegalArgumentException(
                    "未知 Elasticsearch 工具：" + toolName);
        };
    }

    void assertAllowedIndex(String index) {
        List<String> allowed = csv(settings.elasticsearchIndexWhitelist());
        if (allowed.isEmpty() && !settings.elasticsearchIndex().isBlank()) {
            allowed = List.of(settings.elasticsearchIndex());
        }
        if (allowed.isEmpty()) {
            throw new SecurityException(
                    "ELK_INDEX_WHITELIST_REQUIRED：Elasticsearch 查询必须配置 index whitelist");
        }
        if (!allowed.contains(index)) {
            throw new SecurityException(
                    "ELK_INDEX_NOT_ALLOWED：index 不在白名单内");
        }
    }

    Map<String, Object> requiredTimeRange(OpsLocalToolArguments args) {
        String start = args.text("startTime");
        String end = args.text("endTime");
        if (!start.isBlank() && !end.isBlank()) {
            return Map.of("gte", start, "lte", end);
        }
        String rangeText = args.text("rangeMinutes");
        if (rangeText.isBlank()) {
            throw new IllegalArgumentException(
                    "ELK_TIME_RANGE_REQUIRED：Elasticsearch 查询必须提供 startTime/endTime 或 rangeMinutes");
        }
        int rangeMinutes = args.boundedInt("rangeMinutes", 1, 240, 30);
        return Map.of("gte", "now-" + rangeMinutes + "m", "lte", "now");
    }

    private Map<String, Object> boolQuery(
            String query,
            Map<String, Object> timeRange) {
        return Map.of("bool", Map.of("must", List.of(
                Map.of("query_string", Map.of("query", query)),
                Map.of("range", Map.of("@timestamp", timeRange)))));
    }

    private String encodePath(String value) {
        return value == null ? "" : value.replace("/", "%2F");
    }

    private List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
