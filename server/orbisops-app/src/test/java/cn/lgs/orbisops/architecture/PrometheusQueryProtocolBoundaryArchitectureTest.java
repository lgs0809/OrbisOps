package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusQueryProtocolBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION_OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void prometheusSubAgentDelegatesProtocolSettingsProjectionAndWindowPolicy() throws IOException {
        String subAgent = read(OPS + "PrometheusMetricOpsSubAgent.java");
        String protocol = read(OPS + "OpsPrometheusQueryProtocolService.java");
        String settings = read(OPS + "OpsPrometheusSettings.java");
        String projector = read(OPS + "OpsPrometheusResponseProjector.java");
        String configuration = read(APPLICATION_OPS + "OpsDatasourceSubAgentConfiguration.java");

        assertAll(
                () -> assertTrue(subAgent.contains("OpsPrometheusQueryProtocolService metricQueryService")),
                () -> assertTrue(subAgent.contains("OpsPrometheusSettings settings")),
                () -> assertTrue(subAgent.contains("OpsSubAgentRequestPolicy requestPolicy")),
                () -> assertTrue(subAgent.contains("OpsPrometheusResponseProjector responseProjector")),
                () -> assertTrue(subAgent.contains("metricQueryService.execute(")),
                () -> assertTrue(subAgent.contains("new OpsPrometheusQueryProtocolService.Input(")),
                () -> assertTrue(subAgent.contains("requestPolicy.applyPromWindowDecision(")),
                () -> assertTrue(subAgent.contains("requestPolicy.expandPromWindow(")),
                () -> assertTrue(subAgent.contains("responseProjector.project(queryResult)")),
                () -> assertFalse(subAgent.contains("@Value")),
                () -> assertFalse(subAgent.contains("projectEndpointMetrics(")),
                () -> assertFalse(subAgent.contains("EndpointMetricDTO.builder()")),
                () -> assertFalse(subAgent.contains("private boolean hasMetricAnomaly(")),
                () -> assertFalse(subAgent.contains("private String longerPromWindow(")),
                () -> assertFalse(subAgent.contains("private String nextPromWindow(")),
                () -> assertFalse(subAgent.contains("private int promWindowRank(")),
                () -> assertFalse(subAgent.contains("com.alibaba.fastjson")),
                () -> assertFalse(subAgent.contains("JSONObject")),
                () -> assertFalse(subAgent.contains("JSONArray")),
                () -> assertFalse(subAgent.contains("URLEncoder")),
                () -> assertFalse(subAgent.contains("java.net.http")),
                () -> assertFalse(subAgent.contains("HttpClient")),
                () -> assertFalse(subAgent.contains("HttpRequest")),
                () -> assertFalse(subAgent.contains("HttpResponse")),
                () -> assertFalse(subAgent.contains("URI.create")),
                () -> assertFalse(subAgent.contains("StandardCharsets")),
                () -> assertTrue(subAgent.lines().count() <= 285),
                () -> assertTrue(settings.contains("public record OpsPrometheusSettings(")),
                () -> assertTrue(settings.contains("public static OpsPrometheusSettings defaults()")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(projector.contains("record Projection(")),
                () -> assertTrue(projector.contains("EndpointMetricDTO.builder()")),
                () -> assertTrue(projector.contains("metrics.getErrorRate() > 1D")),
                () -> assertTrue(projector.contains("metrics.getHeapMemoryUsagePercent() > 80D")),
                () -> assertTrue(projector.contains("metrics.getProcessCpuUsagePercent() > 80D")),
                () -> assertFalse(projector.contains("org.springframework")),
                () -> assertFalse(projector.contains("@Value")),
                () -> assertTrue(configuration.contains("OpsPrometheusSettings opsPrometheusSettings(")),
                () -> assertTrue(configuration.contains("${orbisops.prometheus-url:http://127.0.0.1:9090}")),
                () -> assertTrue(configuration.contains("${orbisops.prometheus-job:}")),
                () -> assertTrue(configuration.contains("${orbisops.prometheus-timeout-seconds:5}")),
                () -> assertTrue(protocol.contains("record Input(")),
                () -> assertTrue(protocol.contains("record Result(")),
                () -> assertTrue(protocol.contains("record EndpointMetric(")),
                () -> assertTrue(protocol.contains("interface Transport")),
                () -> assertTrue(protocol.contains("interface CancellationCheck")),
                () -> assertTrue(protocol.contains("cancellationCheck.check()")),
                () -> assertTrue(protocol.contains("com.alibaba.fastjson")),
                () -> assertTrue(protocol.contains("URLEncoder.encode(")),
                () -> assertTrue(protocol.contains("HttpClient")),
                () -> assertTrue(protocol.contains("HttpRequest")),
                () -> assertTrue(protocol.contains("HttpResponse")),
                () -> assertTrue(protocol.contains("URI.create")),
                () -> assertTrue(protocol.contains("StandardCharsets.UTF_8")),
                () -> assertTrue(protocol.contains("\"/api/v1/query?query=\"")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertFalse(protocol.contains("@Component")),
                () -> assertFalse(protocol.contains("@Value")),
                () -> assertFalse(protocol.contains("@Autowired")),
                () -> assertFalse(protocol.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(protocol.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(protocol.contains("OpsQuestionContext")),
                () -> assertFalse(protocol.contains("OpsSubAgentDecision")),
                () -> assertFalse(protocol.contains("OpsRunCancellationRegistry")),
                () -> assertTrue(protocol.lines().count() <= 330));
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
