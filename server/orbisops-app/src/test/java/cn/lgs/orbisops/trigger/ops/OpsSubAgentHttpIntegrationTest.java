package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OpsSubAgentHttpIntegrationTest {

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    public void tearDown() {
        servers.forEach(server -> server.stop(0));
        servers.clear();
    }

    @Test
    public void shouldReadElasticsearchLogsFromHttpDatasource() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        HttpServer server = startServer(exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, """
                    {
                      "hits": {
                        "total": {"value": 2},
                        "hits": [
                          {"_source": {"@timestamp": "2026-05-08T10:00:00Z", "level": "ERROR", "logger_name": "cn.example.OrderService", "message": "traceId abc123XYZ789 join failed"}},
                          {"_source": {"@timestamp": "2026-05-08T10:00:01Z", "level": "WARN", "logger_name": "cn.example.OrderService", "message": "lock retry"}}
                        ]
                      },
                      "aggregations": {
                        "levels": {"buckets": [{"key": "ERROR", "doc_count": 1}, {"key": "WARN", "doc_count": 1}]},
                        "top_loggers": {"buckets": [{"key": "cn.example.OrderService", "doc_count": 2}]}
                      }
                    }
                    """);
        });

        EsLogOpsSubAgent subAgent = new EsLogOpsSubAgent(
                decisionService(),
                new OpsRunCancellationRegistry(),
                new OpsEsLogQueryProtocolService(),
                new OpsEsLogSettings(baseUrl(server), "order-service-log-*", 2, 2),
                new OpsSubAgentRequestPolicy(),
                new OpsEsLogResponseProjector());

        OpsAnalysisResponseDTO response = responseShell();
        OpsAnalysisResponseDTO.InvestigationResultDTO result = subAgent.investigate(
                task(OpsMainAgentPlanner.SOURCE_ES, "es-log-agent"),
                request("traceId:abc123XYZ789 /api/demo-project/join 报错 ERROR"),
                response,
                OpsQuestionContext.from("traceId:abc123XYZ789 /api/demo-project/join 报错 ERROR"));

        assertEquals("FOUND", result.getStatus());
        assertEquals(Boolean.TRUE, response.getElasticsearchStatus().getAvailable());
        assertEquals(Long.valueOf(2), response.getLogSummary().getTotalLogs());
        assertEquals(Long.valueOf(1), response.getLogSummary().getErrorLogs());
        assertEquals(2, response.getRecentLogs().size());
        assertFalse(requestBodies.isEmpty());
        assertTrue(requestBodies.get(0).contains("abc123XYZ789"));
        assertTrue(requestBodies.get(0).contains("match_phrase"));
    }

    @Test
    public void shouldReadPrometheusMetricsFromHttpDatasource() throws Exception {
        List<String> queries = new ArrayList<>();
        HttpServer server = startServer(exchange -> {
            String query = queryParam(exchange.getRequestURI(), "query");
            queries.add(query);
            if (query.startsWith("up{")) {
                writeJson(exchange, prometheusVector("""
                        [{"metric":{"job":"order-service","instance":"app:8091"},"value":[1715152800,"1"]}]
                        """));
            } else if (query.contains("http_server_requests_seconds_sum")) {
                writeJson(exchange, prometheusVector("""
                        [{"metric":{"uri":"/api/demo-project/join"},"value":[1715152800,"0.123"]}]
                        """));
            } else if (query.contains("status=~")) {
                writeJson(exchange, prometheusVector("""
                        [{"metric":{},"value":[1715152800,"0.1"]}]
                        """));
            } else if (query.contains("jvm_memory_used_bytes")) {
                writeJson(exchange, prometheusVector("""
                        [{"metric":{},"value":[1715152800,"40"]}]
                        """));
            } else if (query.contains("process_cpu_usage")) {
                writeJson(exchange, prometheusVector("""
                        [{"metric":{},"value":[1715152800,"30"]}]
                        """));
            } else {
                writeJson(exchange, prometheusVector("""
                        [{"metric":{"uri":"/api/demo-project/join","method":"POST","status":"200"},"value":[1715152800,"5"]}]
                        """));
            }
        });

        PrometheusMetricOpsSubAgent subAgent = new PrometheusMetricOpsSubAgent(
                decisionService(),
                new OpsRunCancellationRegistry(),
                new OpsPrometheusQueryProtocolService(),
                new OpsPrometheusSettings(baseUrl(server), "order-service", 2),
                new OpsSubAgentRequestPolicy(),
                new OpsPrometheusResponseProjector());

        OpsAnalysisResponseDTO response = responseShell();
        OpsAnalysisResponseDTO.InvestigationResultDTO result = subAgent.investigate(
                task(OpsMainAgentPlanner.SOURCE_PROM, "prometheus-agent"),
                request("检查 /api/demo-project/join QPS 和错误率"),
                response,
                OpsQuestionContext.from("检查 /api/demo-project/join QPS 和错误率"));

        assertEquals("FOUND", result.getStatus());
        assertEquals(Boolean.TRUE, response.getPrometheusStatus().getAvailable());
        assertEquals(Integer.valueOf(1), response.getMetricSummary().getInstanceTotal());
        assertEquals(Integer.valueOf(1), response.getMetricSummary().getInstanceUp());
        assertEquals(Double.valueOf(5D), response.getMetricSummary().getTotalQps());
        assertEquals(Double.valueOf(2D), response.getMetricSummary().getErrorRate());
        assertEquals(1, response.getEndpointMetrics().size());
        assertTrue(queries.stream().anyMatch(query -> query.contains("http_server_requests_seconds_count")));
    }

    private OpsSubAgentDecisionService decisionService() {
        return new OpsSubAgentDecisionService(
                null,
                OpsSubAgentDecisionSettings.legacyConstructorDefaults());
    }

    private HttpServer startServer(ThrowingHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                byte[] body = e.getMessage().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String queryParam(URI uri, String key) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null) {
            return "";
        }
        for (String part : rawQuery.split("&")) {
            int split = part.indexOf('=');
            if (split > 0 && key.equals(part.substring(0, split))) {
                return URLDecoder.decode(part.substring(split + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private String prometheusVector(String resultJson) {
        return """
                {"status":"success","data":{"resultType":"vector","result":%s}}
                """.formatted(resultJson.trim());
    }

    private OpsAgentRunRequestDTO request(String question) {
        return OpsAgentRunRequestDTO.builder()
                .rangeMinutes(15)
                .promWindow("5m")
                .includeRecentLogs(true)
                .question(question)
                .maxRounds(2)
                .build();
    }

    private OpsAnalysisResponseDTO responseShell() {
        return OpsAnalysisResponseDTO.builder()
                .logSummary(OpsAnalysisResponseDTO.LogSummaryDTO.builder()
                        .totalLogs(0L)
                        .errorLogs(0L)
                        .warnLogs(0L)
                        .build())
                .metricSummary(OpsAnalysisResponseDTO.MetricSummaryDTO.builder()
                        .instanceTotal(0)
                        .instanceUp(0)
                        .totalQps(0D)
                        .errorRate(0D)
                        .heapMemoryUsagePercent(0D)
                        .processCpuUsagePercent(0D)
                        .build())
                .recentLogs(new ArrayList<>())
                .endpointMetrics(new ArrayList<>())
                .build();
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(String source, String agent) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(agent)
                .goal("查询 " + source)
                .reason("测试子 agent HTTP 查询")
                .priority(1)
                .build();
    }

    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
