package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisApplicationBoundaryArchitectureTest {

    private static final String OPS_APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void analysisApplicationFacadeMustDelegateNormalizationRuntimeMappingAndResponseInitialization()
            throws IOException {
        String facade = read(OPS_APPLICATION + "OpsAnalysisApplicationService.java");
        String snapshotResolver = read(OPS_APPLICATION
                + "OpsAnalysisAgentDefinitionSnapshotResolver.java");
        String normalizer = read(OPS_APPLICATION + "OpsAnalysisRunRequestNormalizer.java");
        String runtimeRequestFactory = read(OPS_APPLICATION + "OpsAnalysisRuntimeRequestFactory.java");
        String responseShellFactory = read(OPS_APPLICATION + "OpsAnalysisResponseShellFactory.java");
        String runtimeProjector = read(OPS_APPLICATION + "OpsAnalysisRuntimeResponseProjector.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsAnalysisRunRequestNormalizer requestNormalizer")),
                () -> assertTrue(facade.contains("OpsAnalysisRuntimeRequestFactory runtimeRequestFactory")),
                () -> assertTrue(facade.contains("OpsAnalysisResponseShellFactory responseShellFactory")),
                () -> assertTrue(facade.contains("OpsAnalysisRuntimeResponseProjector runtimeResponseProjector")),
                () -> assertTrue(facade.contains("responseShellFactory.create(request)")),
                () -> assertTrue(facade.contains("runtimeRequestFactory.create(request, response)")),
                () -> assertTrue(facade.contains("runtimeResponseProjector.project(request, response, runtimeResponse)")),
                () -> assertTrue(facade.contains("requestNormalizer.normalize(request)")),
                () -> assertTrue(facade.contains("OpsEsLogSettings.defaults()")),
                () -> assertTrue(facade.contains("OpsPrometheusSettings.defaults()")),
                () -> assertTrue(facade.contains("@Autowired")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("com.alibaba.fastjson")),
                () -> assertFalse(facade.contains("StringUtils")),
                () -> assertFalse(facade.contains("DateTimeFormatter")),
                () -> assertFalse(facade.contains("LocalDateTime")),
                () -> assertFalse(facade.contains("WorkSessionMetadataKeys")),
                () -> assertFalse(facade.contains("OpsQuestionContext")),
                () -> assertFalse(facade.contains("AgentExecutionStepDTO.builder()")),
                () -> assertFalse(facade.contains("DataSourceStatusDTO.builder()")),
                () -> assertFalse(facade.contains("LogSummaryDTO.builder()")),
                () -> assertFalse(facade.contains("MetricSummaryDTO.builder()")),
                () -> assertFalse(facade.contains("SlowSqlSummaryDTO.builder()")),
                () -> assertTrue(facade.lines().count() <= 100),
                () -> assertTrue(snapshotResolver.contains("OpsAgentDefinitionQueryGateway agentDefinitions")),
                () -> assertTrue(snapshotResolver.contains("JSON.parseObject(")),
                () -> assertTrue(snapshotResolver.contains("JSON.toJSONString(")),
                () -> assertTrue(snapshotResolver.contains("AGENT_DEFINITION_HASH_MISMATCH")),
                () -> assertFalse(snapshotResolver.contains("org.springframework")),
                () -> assertTrue(normalizer.contains("OpsAgentRunRequestDTO.builder()")),
                () -> assertTrue(normalizer.contains("运行运维 Agent 前必须选择 projectId")),
                () -> assertTrue(normalizer.contains("normalizePromWindow(")),
                () -> assertFalse(normalizer.contains("org.springframework")),
                () -> assertFalse(normalizer.contains("com.alibaba.fastjson")),
                () -> assertTrue(runtimeRequestFactory.contains("WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST")),
                () -> assertTrue(runtimeRequestFactory.contains("OpsQuestionContext.from(question)")),
                () -> assertTrue(runtimeRequestFactory.contains("OpsAgentChatRequest.builder()")),
                () -> assertFalse(runtimeRequestFactory.contains("org.springframework")),
                () -> assertTrue(responseShellFactory.contains("OpsEsLogSettings esLogSettings")),
                () -> assertTrue(responseShellFactory.contains("OpsPrometheusSettings prometheusSettings")),
                () -> assertTrue(responseShellFactory.contains("esLogSettings.endpoint()")),
                () -> assertTrue(responseShellFactory.contains("prometheusSettings.baseUrl()")),
                () -> assertTrue(responseShellFactory.contains("DataSourceStatusDTO.builder()")),
                () -> assertTrue(responseShellFactory.contains("LogSummaryDTO.builder()")),
                () -> assertTrue(responseShellFactory.contains("MetricSummaryDTO.builder()")),
                () -> assertTrue(responseShellFactory.contains("SlowSqlSummaryDTO.builder()")),
                () -> assertFalse(responseShellFactory.contains("org.springframework")),
                () -> assertTrue(runtimeProjector.contains("AgentExecutionStepDTO.builder()")),
                () -> assertTrue(runtimeProjector.contains("durationMillis(event)")),
                () -> assertTrue(runtimeProjector.contains("response.setMarkdownReport(runtimeResponse.getContent())")),
                () -> assertFalse(runtimeProjector.contains("org.springframework")));
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
