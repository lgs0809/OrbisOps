package cn.lgs.orbisops.trigger.ops.toolset;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLocalPrometheusAdapterTest {

    @Test
    void rangeQueryMustBuildDeterministicEncodedRequest() {
        List<HttpRequest> requests = new ArrayList<>();
        OpsLocalAdapterSettings settings = settings();
        OpsLocalHttpTransport http = new OpsLocalHttpTransport(
                settings,
                request -> {
                    requests.add(request);
                    return "ok";
                });
        OpsLocalPrometheusAdapter adapter = new OpsLocalPrometheusAdapter(
                settings,
                http,
                () -> 10_000L);

        Map<String, Object> result = adapter.execute(
                "prometheus_range_query",
                new OpsLocalToolArguments(Map.of(
                        "query", "rate(http_requests_total[5m])",
                        "rangeMinutes", 10,
                        "step", "30s")));

        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals("prometheus", result.get("adapter"));
        String uri = requests.get(0).uri().toString();
        assertTrue(uri.startsWith("http://prom/api/v1/query_range?query="));
        assertTrue(uri.contains("start=9400"));
        assertTrue(uri.contains("end=10000"));
        assertTrue(uri.contains("step=30s"));
    }

    @Test
    void labelValuesMustPreserveDefaultLabelAndHistoricalQueryRequirement() {
        List<HttpRequest> requests = new ArrayList<>();
        OpsLocalAdapterSettings settings = settings();
        OpsLocalPrometheusAdapter adapter = new OpsLocalPrometheusAdapter(
                settings,
                new OpsLocalHttpTransport(settings, request -> {
                    requests.add(request);
                    return "ok";
                }),
                () -> 100L);

        adapter.execute(
                "prometheus_label_values",
                new OpsLocalToolArguments(Map.of("query", "up")));

        assertEquals("http://prom/api/v1/label/__name__/values",
                requests.get(0).uri().toString());
        assertThrows(IllegalArgumentException.class,
                () -> adapter.execute(
                        "prometheus_label_values",
                        new OpsLocalToolArguments(Map.of())));
    }

    @Test
    void invalidOrOutOfRangeStepMustFailBeforeTransport() {
        OpsLocalAdapterSettings settings = settings();
        OpsLocalPrometheusAdapter adapter = new OpsLocalPrometheusAdapter(
                settings,
                new OpsLocalHttpTransport(settings, request -> "unused"),
                () -> 100L);

        IllegalArgumentException format = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.execute(
                        "prometheus_range_query",
                        new OpsLocalToolArguments(Map.of(
                                "query", "up",
                                "step", "fast"))));
        IllegalArgumentException range = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.execute(
                        "prometheus_range_query",
                        new OpsLocalToolArguments(Map.of(
                                "query", "up",
                                "step", "2h"))));

        assertTrue(format.getMessage().contains("PROMETHEUS_STEP_INVALID"));
        assertTrue(range.getMessage().contains("PROMETHEUS_STEP_INVALID"));
    }

    private OpsLocalAdapterSettings settings() {
        return new OpsLocalAdapterSettings(
                "http://prom",
                "http://es",
                "logs",
                "logs",
                "./logs",
                "./",
                3,
                200,
                4096);
    }
}
