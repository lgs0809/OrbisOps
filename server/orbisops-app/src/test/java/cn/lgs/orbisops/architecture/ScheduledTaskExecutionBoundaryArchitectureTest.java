package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledTaskExecutionBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/schedule/";
    private static final String TRIGGER_JOB =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/job/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void scheduledTaskExecutionMustRemainAHexagonalApplicationProcessManager() throws IOException {
        String useCase = read(APPLICATION + "ScheduledTaskExecutionUseCase.java");
        String catalogPort = read(APPLICATION + "ScheduledTaskExecutionCatalogPort.java");
        String executorPort = read(APPLICATION + "ScheduledTaskExecutionExecutorPort.java");
        String analysisPort = read(APPLICATION + "ScheduledTaskAnalysisPort.java");
        String facade = read(TRIGGER_JOB + "AgentTaskExecutionService.java");
        String catalogRepository = read(INFRASTRUCTURE_REPOSITORY + "TaskExecutionRepository.java");
        String executorAdapter = read(TRIGGER_JOB + "OpsScheduledTaskExecutionExecutorAdapter.java");
        String analysisAdapter = read(TRIGGER_JOB + "OpsScheduledTaskAnalysisAdapter.java");
        String configuration = read(TRIGGER_JOB + "ScheduledTaskExecutionConfiguration.java");

        assertAll(
                () -> assertTrue(catalogPort.contains("interface ScheduledTaskExecutionCatalogPort")),
                () -> assertTrue(executorPort.contains("interface ScheduledTaskExecutionExecutorPort")),
                () -> assertTrue(analysisPort.contains("interface ScheduledTaskAnalysisPort")),
                () -> assertTrue(useCase.contains("class ScheduledTaskExecutionUseCase")),
                () -> assertTrue(useCase.contains("Long executionId = catalogPort.create(command)")),
                () -> assertTrue(useCase.contains("executorPort.execute")),
                () -> assertTrue(useCase.contains("catalogPort.updateInput")),
                () -> assertTrue(useCase.contains("catalogPort.markSucceeded")),
                () -> assertTrue(useCase.contains("catalogPort.markFailed")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("ITaskExecutionRepository")),
                () -> assertFalse(useCase.contains("ThreadPoolExecutor")),
                () -> assertFalse(useCase.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(useCase.contains("fastjson")),
                () -> assertTrue(catalogRepository.contains("implements ScheduledTaskExecutionCatalogPort, TaskExecutionCatalogPort")),
                () -> assertFalse(catalogRepository.contains("domain.agent")),
                () -> assertFalse(catalogRepository.contains("TaskExecutionRecord")),
                () -> assertFalse(catalogRepository.contains("ThreadPoolExecutor")),
                () -> assertTrue(executorAdapter.contains("ThreadPoolExecutor")),
                () -> assertFalse(executorAdapter.contains("ITaskExecutionRepository")),
                () -> assertTrue(analysisAdapter.contains("OpsAnalysisApplicationService")),
                () -> assertTrue(analysisAdapter.contains("OpsTaskScheduleRuntimeConfigurationCodec")),
                () -> assertTrue(analysisAdapter.contains("com.alibaba.fastjson.JSON")),
                () -> assertFalse(analysisAdapter.contains("ITaskExecutionRepository")),
                () -> assertTrue(configuration.contains("ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO>")),
                () -> assertTrue(configuration.contains("ScheduledTaskExecutionCatalogPort catalogPort")),
                () -> assertFalse(configuration.contains("OpsScheduledTaskExecutionCatalogAdapter")),
                () -> assertFalse(configuration.contains("ITaskExecutionRepository")),
                () -> assertTrue(configuration.contains("new OpsScheduledTaskExecutionExecutorAdapter")),
                () -> assertTrue(configuration.contains("new OpsScheduledTaskAnalysisAdapter")),
                () -> assertTrue(facade.contains("private final ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> executionUseCase")),
                () -> assertFalse(facade.contains("ITaskExecutionRepository")),
                () -> assertFalse(facade.contains("OpsAnalysisApplicationService")),
                () -> assertFalse(facade.contains("ThreadPoolExecutor")),
                () -> assertFalse(facade.contains("com.alibaba.fastjson")),
                () -> assertFalse(facade.contains("OpsJsonSnapshotCodec")),
                () -> assertFalse(facade.contains("@Resource")),
                () -> assertTrue(facade.lines().count() <= 65));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
