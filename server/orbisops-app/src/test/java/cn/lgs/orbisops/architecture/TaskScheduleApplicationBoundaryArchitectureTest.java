package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskScheduleApplicationBoundaryArchitectureTest {

    private static final String APPLICATION_SCHEDULE =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/schedule/";
    private static final String TRIGGER_CONFIG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/config/";
    private static final String TRIGGER_JOB =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/job/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void scheduledAgentTasksMustRemainHexagonalApplicationProcessManagers() throws IOException {
        String catalogUseCase = read(APPLICATION_SCHEDULE + "TaskScheduleCatalogUseCase.java");
        String executionUseCase = read(APPLICATION_SCHEDULE + "TaskScheduleExecutionUseCase.java");
        String catalogPort = read(APPLICATION_SCHEDULE + "TaskScheduleCatalogPort.java");
        String executionPort = read(APPLICATION_SCHEDULE + "TaskScheduleExecutionPort.java");
        String codec = read(TRIGGER_CONFIG + "OpsTaskScheduleRuntimeConfigurationCodec.java");
        String catalogRepository = read(INFRASTRUCTURE_REPOSITORY + "TaskScheduleRepository.java");
        String persistenceCodec = read(INFRASTRUCTURE_REPOSITORY + "TaskSchedulePersistenceCodec.java");
        String auditAdapter = read(TRIGGER_CONFIG + "OpsTaskScheduleAuditAdapter.java");
        String agentAdapter = read(TRIGGER_CONFIG + "OpsTaskScheduleAgentSnapshotAdapter.java");
        String cronAdapter = read(TRIGGER_CONFIG + "OpsTaskScheduleCronValidationAdapter.java");
        String executionAdapter = read(TRIGGER_CONFIG + "OpsTaskScheduleExecutionAdapter.java");
        String executionRepository = read(INFRASTRUCTURE_REPOSITORY + "TaskExecutionRepository.java");
        String schedulerAdapter = read(TRIGGER_JOB + "AgentTaskJob.java");
        String configuration = read(TRIGGER_CONFIG + "TaskScheduleConfiguration.java");
        String facade = read(TRIGGER_CONFIG + "TaskScheduleApplicationService.java");

        assertAll(
                () -> assertTrue(catalogPort.contains("interface TaskScheduleCatalogPort")),
                () -> assertTrue(executionPort.contains("interface TaskScheduleExecutionPort")),
                () -> assertTrue(catalogUseCase.contains("class TaskScheduleCatalogUseCase")),
                () -> assertTrue(catalogUseCase.contains("agentSnapshotPort.resolve")),
                () -> assertTrue(catalogUseCase.contains("cronValidationPort.validate")),
                () -> assertTrue(catalogUseCase.contains("if (created)")),
                () -> assertTrue(catalogUseCase.contains("if (updated)")),
                () -> assertTrue(catalogUseCase.contains("if (deleted)")),
                () -> assertTrue(executionUseCase.contains("executionPort.submit(schedule, \"MANUAL\")")),
                () -> assertTrue(executionUseCase.contains("executionCatalogPort.list(scheduleId, safeLimit)")),
                () -> assertApplicationPure(catalogUseCase),
                () -> assertApplicationPure(executionUseCase),
                () -> assertTrue(codec.contains("com.alibaba.fastjson.JSONObject")),
                () -> assertFalse(codec.contains("Repository")),
                () -> assertFalse(codec.contains("AgentTaskExecutionService")),
                () -> assertTrue(catalogRepository.contains("implements TaskScheduleCatalogPort")),
                () -> assertTrue(catalogRepository.contains("TaskScheduleDefinition")),
                () -> assertFalse(catalogRepository.contains("domain.agent")),
                () -> assertFalse(catalogRepository.contains("TaskScheduleRecord")),
                () -> assertTrue(persistenceCodec.contains("com.fasterxml.jackson.databind.ObjectMapper")),
                () -> assertFalse(persistenceCodec.contains("com.alibaba.fastjson")),
                () -> assertFalse(persistenceCodec.contains("domain.agent")),
                () -> assertFalse(catalogRepository.contains("OpsConfigAuditService")),
                () -> assertFalse(catalogRepository.contains("org.springframework.scheduling.support.CronExpression")),
                () -> assertTrue(auditAdapter.contains("OpsConfigAuditService")),
                () -> assertFalse(auditAdapter.contains("ITaskScheduleRepository")),
                () -> assertTrue(agentAdapter.contains("OpsAgentDefinitionQueryGateway")),
                () -> assertFalse(agentAdapter.contains("ITaskScheduleRepository")),
                () -> assertTrue(cronAdapter.contains("CronExpression")),
                () -> assertFalse(cronAdapter.contains("ITaskScheduleRepository")),
                () -> assertTrue(executionAdapter.contains("AgentTaskExecutionService")),
                () -> assertTrue(executionAdapter.contains("ScheduledTaskExecutionCommand")),
                () -> assertFalse(executionAdapter.contains("TaskScheduleRecord")),
                () -> assertFalse(executionAdapter.contains("ITaskExecutionRepository")),
                () -> assertTrue(executionRepository.contains("implements ScheduledTaskExecutionCatalogPort, TaskExecutionCatalogPort")),
                () -> assertFalse(executionRepository.contains("domain.agent")),
                () -> assertFalse(executionRepository.contains("TaskExecutionRecord")),
                () -> assertTrue(schedulerAdapter.contains("ScheduledTaskRegistryPort")),
                () -> assertTrue(schedulerAdapter.contains("TaskScheduleExecutionUseCase")),
                () -> assertTrue(schedulerAdapter.contains("runScheduled(schedule.id())")),
                () -> assertFalse(schedulerAdapter.contains("domain.agent")),
                () -> assertFalse(schedulerAdapter.contains("taskParam")),
                () -> assertTrue(configuration.contains("TaskScheduleCatalogUseCase taskScheduleCatalogUseCase")),
                () -> assertFalse(configuration.contains("OpsTaskScheduleCatalogAdapter")),
                () -> assertFalse(configuration.contains("ITaskScheduleRepository")),
                () -> assertFalse(configuration.contains("OpsTaskExecutionCatalogAdapter")),
                () -> assertFalse(configuration.contains("ITaskExecutionRepository")),
                () -> assertTrue(configuration.contains("TaskScheduleExecutionUseCase taskScheduleExecutionUseCase")),
                () -> assertTrue(facade.contains("private final TaskScheduleCatalogUseCase catalogUseCase")),
                () -> assertTrue(facade.contains("private final TaskScheduleExecutionUseCase executionUseCase")),
                () -> assertFalse(facade.contains("ITaskScheduleRepository")),
                () -> assertFalse(facade.contains("ITaskExecutionRepository")),
                () -> assertFalse(facade.contains("AgentTaskExecutionService")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("OpsAgentDefinitionQueryGateway")),
                () -> assertFalse(facade.contains("com.alibaba.fastjson")),
                () -> assertFalse(facade.contains("org.springframework.scheduling.support.CronExpression")),
                () -> assertFalse(facade.contains("TaskScheduleRecord")),
                () -> assertFalse(facade.contains("TaskExecutionRecord")),
                () -> assertFalse(facade.contains("LocalDateTime.now")));
    }

    private void assertApplicationPure(String source) {
        assertFalse(source.contains("org.springframework"));
        assertFalse(source.contains("com.alibaba.fastjson"));
        assertFalse(source.contains("Repository"));
        assertFalse(source.contains("DTO"));
        assertFalse(source.contains("TaskScheduleRecord"));
        assertFalse(source.contains("TaskExecutionRecord"));
        assertFalse(source.contains("AgentTaskExecutionService"));
        assertFalse(source.contains("OpsConfigAuditService"));
        assertFalse(source.contains("OpsAgentDefinition"));
        assertFalse(source.contains("CronExpression"));
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
