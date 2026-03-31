package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLocalElasticsearchAdapterTest {

    @Test
    void searchMustApplyWhitelistTimeRangeAndEncodedIndexPath() {
        List<HttpRequest> requests = new ArrayList<>();
        OpsLocalAdapterSettings settings = settings("app/logs,other", "app/logs");
        OpsLocalElasticsearchAdapter adapter = new OpsLocalElasticsearchAdapter(
                settings,
                new OpsLocalHttpTransport(settings, request -> {
                    requests.add(request);
                    return "{\"hits\":{}}";
                }));

        Map<String, Object> result = adapter.execute(
                "elk_search",
                new OpsLocalToolArguments(Map.of(
                        "index", "app/logs",
                        "query", "level:ERROR",
                        "rangeMinutes", 15,
                        "size", 30)));

        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals("elasticsearch", result.get("adapter"));
        assertEquals("app/logs", result.get("index"));
        assertEquals("http://es/app%2Flogs/_search",
                requests.get(0).uri().toString());
        assertEquals("POST", requests.get(0).method());
    }

    @Test
    void requestBodyMustPreserveSearchAggregateTraceAndContextContracts() {
        OpsLocalAdapterSettings settings = settings("logs", "logs");
        OpsLocalElasticsearchAdapter adapter = new OpsLocalElasticsearchAdapter(
                settings,
                new OpsLocalHttpTransport(settings, request -> "ok"));

        Map<String, Object> search = adapter.requestBody(
                "elk_search",
                new OpsLocalToolArguments(Map.of(
                        "query", "error",
                        "rangeMinutes", 10,
                        "size", 999)));
        Map<String, Object> aggregate = adapter.requestBody(
                "elk_aggregate_errors",
                new OpsLocalToolArguments(Map.of("rangeMinutes", 10)));
        Map<String, Object> trace = adapter.requestBody(
                "elk_trace_lookup",
                new OpsLocalToolArguments(Map.of(
                        "traceId", "trace-1",
                        "rangeMinutes", 10)));
        Map<String, Object> context = adapter.requestBody(
                "elk_log_context",
                new OpsLocalToolArguments(Map.of("rangeMinutes", 10)));

        assertEquals(200, search.get("size"));
        assertTrue(search.containsKey("query"));
        assertEquals(0, aggregate.get("size"));
        assertTrue(aggregate.containsKey("aggs"));
        assertTrue(trace.toString().contains("trace-1"));
        assertTrue(context.containsKey("sort"));
    }

    @Test
    void whitelistAndTimeRangeMustFailClosed() {
        OpsLocalAdapterSettings settings = settings("logs", "logs");
        OpsLocalElasticsearchAdapter adapter = new OpsLocalElasticsearchAdapter(
                settings,
                new OpsLocalHttpTransport(settings, request -> "unused"));

        IllegalArgumentException noRange = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.execute(
                        "elk_search",
                        new OpsLocalToolArguments(Map.of(
                                "index", "logs",
                                "query", "error"))));
        SecurityException badIndex = assertThrows(
                SecurityException.class,
                () -> adapter.execute(
                        "elk_search",
                        new OpsLocalToolArguments(Map.of(
                                "index", "other",
                                "rangeMinutes", 10))));

        assertTrue(noRange.getMessage().contains("ELK_TIME_RANGE_REQUIRED"));
        assertTrue(badIndex.getMessage().contains("ELK_INDEX_NOT_ALLOWED"));
    }

    @Test
    void explicitStartAndEndMustOverrideRelativeRange() {
        OpsLocalAdapterSettings settings = settings("logs", "logs");
        OpsLocalElasticsearchAdapter adapter = new OpsLocalElasticsearchAdapter(
                settings,
                new OpsLocalHttpTransport(settings, request -> "unused"));

        Map<String, Object> range = adapter.requiredTimeRange(
                new OpsLocalToolArguments(Map.of(
                        "startTime", "2026-07-27T00:00:00Z",
                        "endTime", "2026-07-27T01:00:00Z",
                        "rangeMinutes", 10)));

        assertEquals("2026-07-27T00:00:00Z", range.get("gte"));
        assertEquals("2026-07-27T01:00:00Z", range.get("lte"));
    }

    private OpsLocalAdapterSettings settings(
            String whitelist,
            String defaultIndex) {
        return new OpsLocalAdapterSettings(
                "http://prom",
                "http://es",
                defaultIndex,
                whitelist,
                "./logs",
                "./",
                3,
                200,
                4096);
    }
}
