package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsPrometheusQueryProtocolServiceTest {

    @Test
    void executeBuildsPromQlAndCalculatesTypedMetrics() throws Exception {
        List<String> queries = new ArrayList<>();
        List<Long> timeouts = new ArrayList<>();
        AtomicInteger cancellationChecks = new AtomicInteger();
        OpsPrometheusQueryProtocolService service =
                new OpsPrometheusQueryProtocolService((url, timeoutSeconds) -> {
                    String query = query(url);
                    queries.add(query);
                    timeouts.add(timeoutSeconds);
                    if (query.startsWith("up{")) {
                        return vector("""
                                [
                                  {"metric":{"instance":"app-1"},"value":[1,"1"]},
                                  {"metric":{"instance":"app-2"},"value":[1,"0"]}
                                ]
                                """);
                    }
                    if (query.contains("http_server_requests_seconds_sum")) {
                        return vector("""
                                [
                                  {"metric":{"uri":"/api/group.buy[1]"},"value":[1,"0.123"]},
                                  {"metric":{"uri":"/api/other"},"value":[1,"0.05"]}
                                ]
                                """);
                    }
                    if (query.contains("status=~")) {
                        return vector("""
                                [{"metric":{},"value":[1,"0.1"]}]
                                """);
                    }
                    if (query.contains("jvm_memory_used_bytes")) {
                        return vector("""
                                [{"metric":{},"value":[1,"75.126"]}]
                                """);
                    }
                    if (query.contains("process_cpu_usage")) {
                        return vector("""
                                [{"metric":{},"value":[1,"30.124"]}]
                                """);
                    }
                    return vector("""
                            [
                              {"metric":{"uri":"/api/other","method":"GET","status":"200"},"value":[1,"2"]},
                              {"metric":{"uri":"/api/group.buy[1]","method":"POST","status":"500"},"value":[1,"3"]}
                            ]
                            """);
                });

        OpsPrometheusQueryProtocolService.Result result = service.execute(
                new OpsPrometheusQueryProtocolService.Input(
                        "http://127.0.0.1:9090",
                        "order-service",
                        7,
                        "5m",
                        "/api/group.buy[1]"),
                cancellationChecks::incrementAndGet);

        assertEquals(6, queries.size());
        assertTrue(timeouts.stream().allMatch(timeout -> timeout == 7L));
        assertEquals(12, cancellationChecks.get());
        assertTrue(queries.get(0).equals("up{job=\"order-service\"}"));
        assertTrue(queries.stream().anyMatch(query -> query.contains(
                "http_server_requests_seconds_count{job=\"order-service\",")));
        assertTrue(queries.stream().anyMatch(query -> query.contains(
                "uri!=\"/actuator/prometheus\"")));
        assertTrue(queries.stream().anyMatch(query -> query.contains(
                "/api/group\\.buy\\[1\\]")));
        assertTrue(queries.stream().anyMatch(query -> query.contains(
                "status=~\"5..\"")));
        assertTrue(queries.stream().anyMatch(query -> query.contains(
                "area=\"heap\"")));

        assertEquals(2, result.instanceTotal());
        assertEquals(1, result.instanceUp());
        assertEquals(5D, result.totalQps());
        assertEquals(0.1D, result.errorQps());
        assertEquals(2D, result.errorRate());
        assertEquals(75.13D, result.heapMemoryUsagePercent());
        assertEquals(30.12D, result.processCpuUsagePercent());
        assertEquals(2, result.endpointMetrics().size());
        assertEquals("/api/group.buy[1]", result.endpointMetrics().get(0).uri());
        assertEquals("POST", result.endpointMetrics().get(0).method());
        assertEquals("500", result.endpointMetrics().get(0).status());
        assertEquals(3D, result.endpointMetrics().get(0).qps());
        assertEquals(123D, result.endpointMetrics().get(0).avgResponseMs());
        assertEquals("/api/other", result.endpointMetrics().get(1).uri());
        assertEquals(2D, result.endpointMetrics().get(1).qps());
        assertEquals(50D, result.endpointMetrics().get(1).avgResponseMs());
    }

    @Test
    void prometheusErrorResponsePreservesFailureAndPostCallCancellation() {
        AtomicInteger cancellationChecks = new AtomicInteger();
        OpsPrometheusQueryProtocolService service =
                new OpsPrometheusQueryProtocolService((url, timeoutSeconds) ->
                        JSONObject.parseObject("""
                                {"status":"error","error":"bad query"}
                                """));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.execute(
                        new OpsPrometheusQueryProtocolService.Input(
                                "http://prometheus",
                                "",
                                5,
                                "5m",
                                null),
                        cancellationChecks::incrementAndGet));

        assertEquals("Prometheus 查询失败：bad query", error.getMessage());
        assertEquals(2, cancellationChecks.get());
    }

    @Test
    void transportFailureIsRethrownWithoutPostCallCancellation() {
        IllegalArgumentException failure = new IllegalArgumentException("network failed");
        AtomicInteger cancellationChecks = new AtomicInteger();
        OpsPrometheusQueryProtocolService service =
                new OpsPrometheusQueryProtocolService((url, timeoutSeconds) -> {
                    throw failure;
                });

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> service.execute(
                        new OpsPrometheusQueryProtocolService.Input(
                                "http://prometheus",
                                "job",
                                5,
                                "5m",
                                null),
                        cancellationChecks::incrementAndGet));

        assertSame(failure, thrown);
        assertEquals(1, cancellationChecks.get());
    }

    private JSONObject vector(String result) {
        return JSONObject.parseObject("""
                {"status":"success","data":{"resultType":"vector","result":%s}}
                """.formatted(result.trim()));
    }

    private String query(String url) {
        String rawQuery = URI.create(url).getRawQuery();
        String encoded = rawQuery.substring("query=".length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
