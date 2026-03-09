package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalObservabilityProtocolBoundaryArchitectureTest {

    private static final String TOOLSET =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/toolset/";

    @Test
    void prometheusAndElasticsearchProtocolsMustStayOutsideFacadeAndHttpTransport() throws IOException {
        String facade = read(TOOLSET + "OpsLocalOpsAdapterService.java");
        String prometheus = read(TOOLSET + "OpsLocalPrometheusAdapter.java");
        String elasticsearch = read(TOOLSET + "OpsLocalElasticsearchAdapter.java");
        String prometheusHandler = read(TOOLSET + "OpsPrometheusLocalToolExecutionHandler.java");
        String elasticsearchHandler = read(TOOLSET + "OpsElasticsearchLocalToolExecutionHandler.java");
        String http = read(TOOLSET + "OpsLocalHttpTransport.java");

        assertAll(
                () -> assertTrue(facade.contains("handlerRegistry.execute(")),
                () -> assertTrue(prometheusHandler.contains("adapter.execute(toolName, arguments)")),
                () -> assertTrue(elasticsearchHandler.contains("adapter.execute(toolName, arguments)")),
                () -> assertFalse(facade.contains("prometheus_range_query")),
                () -> assertFalse(facade.contains("elk_search")),
                () -> assertFalse(facade.contains("/api/v1/")),
                () -> assertFalse(facade.contains("ELK_INDEX_NOT_ALLOWED")),
                () -> assertFalse(facade.contains("ELK_TIME_RANGE_REQUIRED")),
                () -> assertTrue(prometheus.contains("/api/v1/query?query=")),
                () -> assertTrue(prometheus.contains("/api/v1/query_range?query=")),
                () -> assertTrue(prometheus.contains("/api/v1/label/")),
                () -> assertTrue(prometheus.contains("/api/v1/series?match[]=")),
                () -> assertTrue(prometheus.contains("PROMETHEUS_STEP_INVALID")),
                () -> assertTrue(prometheus.contains("LongSupplier epochSeconds")),
                () -> assertTrue(prometheus.contains("URLEncoder.encode(")),
                () -> assertTrue(prometheus.contains("http.get(")),
                () -> assertFalse(prometheus.contains("HttpClient")),
                () -> assertFalse(prometheus.contains("HttpRequest")),
                () -> assertFalse(prometheus.contains("com.alibaba.fastjson")),
                () -> assertFalse(prometheus.contains("LocalMySqlApplicationService")),
                () -> assertFalse(prometheus.contains("LocalRedisApplicationService")),
                () -> assertTrue(elasticsearch.contains("JSON.toJSONString(body)")),
                () -> assertTrue(elasticsearch.contains("assertAllowedIndex(index)")),
                () -> assertTrue(elasticsearch.contains("requiredTimeRange(args)")),
                () -> assertTrue(elasticsearch.contains("ELK_INDEX_WHITELIST_REQUIRED")),
                () -> assertTrue(elasticsearch.contains("ELK_INDEX_NOT_ALLOWED")),
                () -> assertTrue(elasticsearch.contains("ELK_TIME_RANGE_REQUIRED")),
                () -> assertTrue(elasticsearch.contains("case \"elk_search\"")),
                () -> assertTrue(elasticsearch.contains("case \"elk_aggregate_errors\"")),
                () -> assertTrue(elasticsearch.contains("case \"elk_trace_lookup\"")),
                () -> assertTrue(elasticsearch.contains("case \"elk_log_context\"")),
                () -> assertTrue(elasticsearch.contains("http.postJson(")),
                () -> assertFalse(elasticsearch.contains("HttpClient")),
                () -> assertFalse(elasticsearch.contains("HttpRequest")),
                () -> assertFalse(elasticsearch.contains("LocalMySqlApplicationService")),
                () -> assertFalse(elasticsearch.contains("LocalRedisApplicationService")),
                () -> assertFalse(http.contains("/api/v1/")),
                () -> assertFalse(http.contains("ELK_INDEX_NOT_ALLOWED")),
                () -> assertFalse(http.contains("ELK_TIME_RANGE_REQUIRED")),
                () -> assertFalse(http.contains("prometheus_range_query")),
                () -> assertFalse(http.contains("elk_search")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
