package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EsLogQueryProtocolBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION_OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void esSubAgentDelegatesProtocolSettingsAndProjectionToPlainBoundaries() throws IOException {
        String subAgent = read(OPS + "EsLogOpsSubAgent.java");
        String protocol = read(OPS + "OpsEsLogQueryProtocolService.java");
        String settings = read(OPS + "OpsEsLogSettings.java");
        String projector = read(OPS + "OpsEsLogResponseProjector.java");
        String configuration = read(APPLICATION_OPS + "OpsDatasourceSubAgentConfiguration.java");

        assertAll(
                () -> assertTrue(subAgent.contains("OpsEsLogQueryProtocolService logQueryService")),
                () -> assertTrue(subAgent.contains("OpsEsLogSettings settings")),
                () -> assertTrue(subAgent.contains("OpsSubAgentRequestPolicy requestPolicy")),
                () -> assertTrue(subAgent.contains("OpsEsLogResponseProjector responseProjector")),
                () -> assertTrue(subAgent.contains("logQueryService.execute(")),
                () -> assertTrue(subAgent.contains("new OpsEsLogQueryProtocolService.Input(")),
                () -> assertTrue(subAgent.contains("requestPolicy.applyLogDecision(")),
                () -> assertTrue(subAgent.contains("requestPolicy.expandRange(")),
                () -> assertTrue(subAgent.contains("responseProjector.project(queryResult)")),
                () -> assertFalse(subAgent.contains("@Value")),
                () -> assertFalse(subAgent.contains("projectBuckets(")),
                () -> assertFalse(subAgent.contains("projectLogSamples(")),
                () -> assertFalse(subAgent.contains("BucketDTO.builder()")),
                () -> assertFalse(subAgent.contains("LogSampleDTO.builder()")),
                () -> assertFalse(subAgent.contains("com.alibaba.fastjson")),
                () -> assertFalse(subAgent.contains("JSONObject")),
                () -> assertFalse(subAgent.contains("JSONArray")),
                () -> assertFalse(subAgent.contains("java.net.http")),
                () -> assertFalse(subAgent.contains("HttpClient")),
                () -> assertFalse(subAgent.contains("HttpRequest")),
                () -> assertFalse(subAgent.contains("HttpResponse")),
                () -> assertFalse(subAgent.contains("URI.create")),
                () -> assertFalse(subAgent.contains("StandardCharsets")),
                () -> assertTrue(subAgent.lines().count() <= 320),
                () -> assertTrue(settings.contains("public record OpsEsLogSettings(")),
                () -> assertTrue(settings.contains("public static OpsEsLogSettings defaults()")),
                () -> assertTrue(settings.contains("public String endpoint()")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(projector.contains("record Projection(")),
                () -> assertTrue(projector.contains("BucketDTO.builder()")),
                () -> assertTrue(projector.contains("LogSampleDTO.builder()")),
                () -> assertFalse(projector.contains("org.springframework")),
                () -> assertFalse(projector.contains("@Value")),
                () -> assertTrue(configuration.contains("OpsEsLogSettings opsEsLogSettings(")),
                () -> assertTrue(configuration.contains("${orbisops.elasticsearch-url:http://127.0.0.1:9200}")),
                () -> assertTrue(configuration.contains("${orbisops.elasticsearch-index:}")),
                () -> assertTrue(configuration.contains("${orbisops.elasticsearch-timeout-seconds:5}")),
                () -> assertTrue(configuration.contains("${orbisops.elasticsearch-sample-size:8}")),
                () -> assertTrue(protocol.contains("record Input(")),
                () -> assertTrue(protocol.contains("record Result(")),
                () -> assertTrue(protocol.contains("record Bucket(")),
                () -> assertTrue(protocol.contains("record LogSample(")),
                () -> assertTrue(protocol.contains("interface Transport")),
                () -> assertTrue(protocol.contains("com.alibaba.fastjson")),
                () -> assertTrue(protocol.contains("HttpClient")),
                () -> assertTrue(protocol.contains("HttpRequest")),
                () -> assertTrue(protocol.contains("HttpResponse")),
                () -> assertTrue(protocol.contains("URI.create")),
                () -> assertTrue(protocol.contains("StandardCharsets.UTF_8")),
                () -> assertTrue(protocol.contains("\"/_search\"")),
                () -> assertFalse(protocol.contains("@Service")),
                () -> assertFalse(protocol.contains("@Component")),
                () -> assertFalse(protocol.contains("@Value")),
                () -> assertFalse(protocol.contains("@Autowired")),
                () -> assertFalse(protocol.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(protocol.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(protocol.contains("OpsQuestionContext")),
                () -> assertFalse(protocol.contains("OpsSubAgentDecision")),
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
