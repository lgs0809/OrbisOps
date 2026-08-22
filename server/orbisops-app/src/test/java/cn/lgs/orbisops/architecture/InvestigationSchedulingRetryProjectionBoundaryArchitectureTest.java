package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationSchedulingRetryProjectionBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/investigation/";
    private static final String APPLICATION_OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void unifiedLoopDelegatesSchedulingRetryAndSkippedProjectionToTheirOwners() throws IOException {
        String executor = read(OPS + "OpsInvestigationExecutor.java");
        String loop = read(OPS + "OpsInvestigationLoopService.java");
        String retryAcl = read(OPS + "OpsInvestigationRetryService.java");
        String queue = read(OPS + "OpsInvestigationTaskQueue.java");
        String projector = read(OPS + "OpsInvestigationSkippedSourceProjector.java");
        String executorSettings = read(OPS + "OpsInvestigationExecutorSettings.java");
        String executionConfiguration = read(APPLICATION_OPS + "OpsInvestigationExecutionConfiguration.java");
        String retryPolicy = read(DOMAIN + "service/InvestigationRetryPolicy.java");

        assertAll(
                () -> assertTrue(executor.contains("OpsInvestigationLoopService loopService")),
                () -> assertTrue(executor.contains("OpsInvestigationSkippedSourceProjector skippedSourceProjector")),
                () -> assertTrue(executor.contains("loopService.executeInitial(")),
                () -> assertTrue(executor.contains("loopService.executeFollowUps(")),
                () -> assertTrue(executor.contains("skippedSourceProjector.project(")),
                () -> assertTrue(executor.contains("OpsInvestigationExecutorSettings executorSettings")),
                () -> assertTrue(executor.contains("OpsEsLogSettings esLogSettings")),
                () -> assertTrue(executor.contains("OpsPrometheusSettings prometheusSettings")),
                () -> assertTrue(executor.contains("OpsMySqlSlowSqlSettings mySqlSlowSqlSettings")),
                () -> assertTrue(executor.contains("executorSettings.defaultMaxEvidenceItems()")),
                () -> assertTrue(executor.contains("mySqlSlowSqlSettings.enabled()")),
                () -> assertFalse(executor.contains("@Value")),
                () -> assertFalse(executor.contains("String elasticsearchUrl")),
                () -> assertFalse(executor.contains("String elasticsearchIndex")),
                () -> assertFalse(executor.contains("String prometheusUrl")),
                () -> assertFalse(executor.contains("boolean mysqlSlowSqlEnabled")),
                () -> assertFalse(executor.contains("private final OpsInvestigationRetryService retryService")),
                () -> assertFalse(executor.contains("private final OpsInvestigationTaskQueue taskQueue")),
                () -> assertTrue(loop.contains("OpsInvestigationRetryService retryService")),
                () -> assertTrue(loop.contains("OpsInvestigationTaskQueue taskQueue")),
                () -> assertTrue(loop.contains("retryService.adjustedRequest(")),
                () -> assertTrue(loop.contains("taskQueue.create(")),
                () -> assertTrue(loop.contains("taskQueue.pollNextPriorityBatch(")),
                () -> assertTrue(loop.contains("taskQueue.queuedSources(")),
                () -> assertTrue(loop.contains("taskQueue.remainingSources(")),
                () -> assertFalse(executor.contains("prioritizedQueue(")),
                () -> assertFalse(loop.contains("prioritizedQueue(")),
                () -> assertFalse(executor.contains("private List<OpsAnalysisResponseDTO.InvestigationTaskDTO> pollNextPriorityBatch(")),
                () -> assertFalse(loop.contains("private List<OpsAnalysisResponseDTO.InvestigationTaskDTO> pollNextPriorityBatch(")),
                () -> assertFalse(executor.contains("shouldRetry(")),
                () -> assertFalse(loop.contains("shouldRetry(")),
                () -> assertFalse(executor.contains("adjustedRequestForRetry(")),
                () -> assertFalse(loop.contains("adjustedRequestForRetry(")),
                () -> assertFalse(executor.contains("markSkippedSources(")),
                () -> assertFalse(loop.contains("markSkippedSources(")),
                () -> assertFalse(executor.contains("skippedReason(")),
                () -> assertFalse(loop.contains("skippedReason(")),
                () -> assertFalse(executor.contains("DataSourceStatusDTO.builder()")),
                () -> assertFalse(loop.contains("DataSourceStatusDTO.builder()")),
                () -> assertFalse(executor.contains("request.getRangeMinutes() * 4")),
                () -> assertFalse(loop.contains("request.getRangeMinutes() * 4")),
                () -> assertFalse(executor.contains("request.getRangeMinutes() + 15")),
                () -> assertFalse(loop.contains("request.getRangeMinutes() + 15")),
                () -> assertFalse(executor.contains("Math.min(Math.max")),
                () -> assertFalse(loop.contains("rangeMinutes * 4")),
                () -> assertFalse(executor.contains("orElse(99)")),
                () -> assertFalse(loop.contains("orElse(99)")),
                () -> assertTrue(retryAcl.contains("InvestigationRetryPolicy POLICY")),
                () -> assertTrue(retryAcl.contains("copyRetryRequest(")),
                () -> assertFalse(retryAcl.contains("@Service")),
                () -> assertTrue(retryPolicy.contains("rangeMinutes * 4")),
                () -> assertTrue(retryPolicy.contains("rangeMinutes + 15")),
                () -> assertTrue(retryPolicy.contains("240")),
                () -> assertTrue(retryPolicy.contains("SOURCE_PROM")),
                () -> assertFalse(retryPolicy.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(retryPolicy.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(retryPolicy.contains("org.springframework")),
                () -> assertTrue(queue.contains("orElse(99)")),
                () -> assertTrue(queue.contains("pollNextPriorityBatch(")),
                () -> assertTrue(queue.contains("queuedSources(")),
                () -> assertTrue(queue.contains("remainingSources(")),
                () -> assertFalse(queue.contains("@Service")),
                () -> assertTrue(projector.contains("DataSourceStatusDTO.builder()")),
                () -> assertTrue(projector.contains("OpsEsLogSettings esLogSettings")),
                () -> assertTrue(projector.contains("OpsPrometheusSettings prometheusSettings")),
                () -> assertTrue(projector.contains("effectiveEsSettings.endpoint()")),
                () -> assertTrue(projector.contains("effectivePrometheusSettings.baseUrl()")),
                () -> assertFalse(projector.contains("String elasticsearchUrl")),
                () -> assertFalse(projector.contains("String elasticsearchIndex")),
                () -> assertFalse(projector.contains("String prometheusUrl")),
                () -> assertTrue(projector.contains("mysql.slow_log/performance_schema")),
                () -> assertTrue(projector.contains("主 Agent 判断本轮没有足够信息增益")),
                () -> assertFalse(projector.contains("@Service")),
                () -> assertFalse(projector.contains("@Value")),
                () -> assertTrue(executorSettings.contains("public record OpsInvestigationExecutorSettings(")),
                () -> assertTrue(executorSettings.contains("public static OpsInvestigationExecutorSettings defaults()")),
                () -> assertFalse(executorSettings.contains("org.springframework")),
                () -> assertFalse(executorSettings.contains("@Value")),
                () -> assertTrue(executionConfiguration.contains("@Configuration")),
                () -> assertTrue(executionConfiguration.contains("OpsInvestigationExecutorSettings opsInvestigationExecutorSettings(")),
                () -> assertTrue(executionConfiguration.contains("${orbisops.multi-agent.main-reflection-llm-enabled:true}")),
                () -> assertTrue(executionConfiguration.contains("${orbisops.multi-agent.parallel-execution-enabled:true}")),
                () -> assertTrue(executionConfiguration.contains("${orbisops.multi-agent.max-task-executions:8}")),
                () -> assertTrue(executionConfiguration.contains("${orbisops.multi-agent.max-adjustments:3}")),
                () -> assertTrue(executionConfiguration.contains("${orbisops.multi-agent.max-evidence-items:12}")));
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
