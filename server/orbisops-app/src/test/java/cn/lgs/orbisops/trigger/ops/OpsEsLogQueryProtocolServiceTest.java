package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsEsLogQueryProtocolServiceTest {

    @Test
    void executeBuildsElasticsearchDslAndParsesTypedResult() throws Exception {
        AtomicReference<String> requestUrl = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicLong requestTimeout = new AtomicLong();
        OpsEsLogQueryProtocolService service = new OpsEsLogQueryProtocolService(
                (url, body, timeoutSeconds) -> {
                    requestUrl.set(url);
                    requestBody.set(body);
                    requestTimeout.set(timeoutSeconds);
                    return JSONObject.parseObject("""
                            {
                              "hits": {
                                "total": {"value": 2},
                                "hits": [
                                  {"_source": {
                                    "@timestamp": "2026-07-27T00:00:00Z",
                                    "level": "ERROR",
                                    "logger_name": "cn.example.OrderService",
                                    "message": "trace-1 join failed"
                                  }}
                                ]
                              },
                              "aggregations": {
                                "levels": {"buckets": [
                                  {"key": "ERROR", "doc_count": 1},
                                  {"key": "WARN", "doc_count": 1}
                                ]},
                                "top_loggers": {"buckets": [
                                  {"key": "cn.example.OrderService", "doc_count": 2}
                                ]}
                              }
                            }
                            """);
                });

        OpsEsLogQueryProtocolService.Result result = service.execute(
                new OpsEsLogQueryProtocolService.Input(
                        "http://127.0.0.1:9200",
                        "ops-log-*",
                        7,
                        2,
                        15,
                        true,
                        List.of("ERROR"),
                        List.of("trace-1"),
                        List.of("join failed")));

        assertEquals("http://127.0.0.1:9200/ops-log-*/_search", requestUrl.get());
        assertEquals(7L, requestTimeout.get());
        JSONObject query = JSONObject.parseObject(requestBody.get());
        assertEquals(2, query.getIntValue("size"));
        assertEquals("desc", query.getJSONArray("sort")
                .getJSONObject(0)
                .getJSONObject("@timestamp")
                .getString("order"));
        JSONObject bool = query.getJSONObject("query").getJSONObject("bool");
        JSONArray filters = bool.getJSONArray("filter");
        assertEquals("now-15m", filters.getJSONObject(0)
                .getJSONObject("range")
                .getJSONObject("@timestamp")
                .getString("gte"));
        assertEquals("ERROR", filters.getJSONObject(1)
                .getJSONObject("terms")
                .getJSONArray("level.keyword")
                .getString(0));
        assertEquals(1, bool.getJSONArray("must").size());
        assertEquals(1, bool.getJSONArray("should").size());
        assertFalse(bool.containsKey("minimum_should_match"));
        assertEquals("level.keyword", query.getJSONObject("aggs")
                .getJSONObject("levels")
                .getJSONObject("terms")
                .getString("field"));
        assertEquals("logger_name.keyword", query.getJSONObject("aggs")
                .getJSONObject("top_loggers")
                .getJSONObject("terms")
                .getString("field"));

        assertEquals(2L, result.totalLogs());
        assertEquals(Long.valueOf(1L), result.levelCounts().get("ERROR"));
        assertEquals(Long.valueOf(1L), result.levelCounts().get("WARN"));
        assertEquals(Long.valueOf(0L), result.levelCounts().get("INFO"));
        assertEquals(1, result.topLoggers().size());
        assertEquals("cn.example.OrderService", result.topLoggers().get(0).key());
        assertEquals(2L, result.topLoggers().get(0).count());
        assertEquals(1, result.recentLogs().size());
        assertEquals("ERROR", result.recentLogs().get(0).level());
        assertEquals("trace-1 join failed", result.recentLogs().get(0).message());
    }

    @Test
    void shouldOnlyQueryRequiresOneMatchAndSupportsNumericTotalHits() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        OpsEsLogQueryProtocolService service = new OpsEsLogQueryProtocolService(
                (url, body, timeoutSeconds) -> {
                    requestBody.set(body);
                    return JSONObject.parseObject("""
                            {
                              "hits": {"total": 7, "hits": []}
                            }
                            """);
                });

        OpsEsLogQueryProtocolService.Result result = service.execute(
                new OpsEsLogQueryProtocolService.Input(
                        "http://es",
                        "logs",
                        5,
                        8,
                        30,
                        false,
                        List.of(),
                        List.of(),
                        List.of("order failed")));

        JSONObject query = JSONObject.parseObject(requestBody.get());
        assertEquals(0, query.getIntValue("size"));
        JSONObject bool = query.getJSONObject("query").getJSONObject("bool");
        assertFalse(bool.containsKey("must"));
        assertEquals(1, bool.getIntValue("minimum_should_match"));
        assertEquals(7L, result.totalLogs());
        assertEquals(Long.valueOf(0L), result.levelCounts().get("ERROR"));
        assertEquals(Long.valueOf(0L), result.levelCounts().get("WARN"));
        assertEquals(Long.valueOf(0L), result.levelCounts().get("INFO"));
        assertTrue(result.topLoggers().isEmpty());
        assertTrue(result.recentLogs().isEmpty());
    }

    @Test
    void executePropagatesTheOriginalTransportFailure() {
        IllegalStateException failure = new IllegalStateException("HTTP 503 unavailable");
        OpsEsLogQueryProtocolService service = new OpsEsLogQueryProtocolService(
                (url, body, timeoutSeconds) -> {
                    throw failure;
                });

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.execute(new OpsEsLogQueryProtocolService.Input(
                        "http://es",
                        "logs",
                        5,
                        8,
                        15,
                        true,
                        List.of(),
                        List.of(),
                        List.of())));

        assertSame(failure, thrown);
    }
}
