package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisRunPersistenceArchitectureTest {

    private static final String DOMAIN_ROOT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/analysis/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcAnalysisRunRepository.java";
    private static final String TRIGGER_OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String TRIGGER_ANALYSIS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/analysis/";

    @Test
    void domainOwnsTypedSnapshotAndRepositoryContract() throws IOException {
        String snapshot = read(DOMAIN_ROOT + "model/AnalysisRunSnapshot.java");
        String repository = read(DOMAIN_ROOT + "adapter/repository/IAnalysisRunRepository.java");

        assertAll(
                () -> assertTrue(snapshot.contains("record AnalysisRunSnapshot")),
                () -> assertTrue(repository.contains("interface IAnalysisRunRepository")),
                () -> assertTrue(repository.contains("Optional<AnalysisRunSnapshot> find(")),
                () -> assertTrue(repository.contains("List<AnalysisRunSnapshot> findRecent(")),
                () -> assertTrue(repository.contains("countActiveByProject(")),
                () -> assertFalse(snapshot.contains("org.springframework")),
                () -> assertFalse(repository.contains("JdbcTemplate")),
                () -> assertFalse(repository.contains("Map<String, Object>")));
    }

    @Test
    void infrastructureOwnsSqlDdlRowMappingAndJdbcConfiguration() throws IOException {
        String repository = read(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(repository.contains("implements IAnalysisRunRepository")),
                () -> assertTrue(repository.contains("JdbcTemplate")),
                () -> assertTrue(repository.contains("INSERT INTO ai_ops_analysis_task")),
                () -> assertTrue(repository.contains("SELECT run_id, project_id, trigger_source")),
                () -> assertTrue(repository.contains("CREATE TABLE IF NOT EXISTS ai_ops_analysis_task")),
                () -> assertTrue(repository.contains("orbisops.runs.jdbc-enabled")),
                () -> assertTrue(repository.contains("orbisops.runs.auto-init")),
                () -> assertTrue(repository.contains("new AnalysisRunSnapshot(")));
    }

    @Test
    void lifecycleProcessManagerDelegatesStorageExecutionAndProtocolsToNarrowOwners() throws IOException {
        String application = read("orbisops-application/src/main/java/"
                + "cn/lgs/orbisops/application/analysis/AsyncAnalysisRunProcessManager.java");
        String service = read(TRIGGER_OPS + "OpsAnalysisRunService.java");
        String store = read(TRIGGER_OPS + "OpsAnalysisRunStore.java");
        String storeAdapter = read(TRIGGER_OPS + "OpsAsyncAnalysisRunStoreAdapter.java");
        String executionAdapter = read(TRIGGER_OPS + "OpsAsyncAnalysisExecutionAdapter.java");
        String settings = read(TRIGGER_OPS + "OpsAnalysisRunSettings.java");
        String protocolMapper = read(TRIGGER_ANALYSIS + "OpsAsyncAnalysisRunProtocolMapper.java");
        String persistenceMapper = read(TRIGGER_ANALYSIS + "OpsAnalysisRunPersistenceMapper.java");
        String settingsConfiguration = read(TRIGGER_ANALYSIS + "OpsAnalysisRunConfiguration.java");
        String assembly = read(TRIGGER_OPS + "OpsAsyncAnalysisRunConfiguration.java");

        assertAll(
                () -> assertTrue(application.contains("class AsyncAnalysisRunProcessManager")),
                () -> assertTrue(application.contains("storePort.save(pending)")),
                () -> assertTrue(application.contains("executionPort.submit(runId")),
                () -> assertTrue(application.contains("cancellationPort.markCanceled(runId)")),
                () -> assertTrue(application.contains("auditPort.succeeded")),
                () -> assertTrue(application.contains("outcomePort.failed")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(application.contains("IAnalysisRunRepository")),
                () -> assertTrue(service.contains("AsyncAnalysisRunProcessManager<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> processManager")),
                () -> assertTrue(service.contains("OpsAsyncAnalysisRunProtocolMapper protocolMapper")),
                () -> assertFalse(service.contains("ThreadPoolExecutor")),
                () -> assertFalse(service.contains("IAnalysisRunRepository")),
                () -> assertFalse(service.contains("OpsAnalysisRunPersistenceMapper")),
                () -> assertFalse(service.contains("AnalysisAuditApplicationService")),
                () -> assertFalse(service.contains("GraphEventApplicationService")),
                () -> assertFalse(service.contains("OpsTelemetryService")),
                () -> assertFalse(service.contains("ConcurrentHashMap")),
                () -> assertFalse(service.contains("System.currentTimeMillis")),
                () -> assertTrue(service.lines().count() <= 80),
                () -> assertTrue(store.contains("IAnalysisRunRepository repository")),
                () -> assertTrue(store.contains("OpsAnalysisRunPersistenceMapper persistenceMapper")),
                () -> assertTrue(store.contains("Map<String, OpsAgentRunRecordDTO> memoryRuns")),
                () -> assertTrue(store.contains("availableRepository.save(persistenceMapper.snapshot(run))")),
                () -> assertTrue(store.contains("ANALYSIS_TASK_STORE_UNAVAILABLE")),
                () -> assertTrue(storeAdapter.contains("implements")),
                () -> assertTrue(storeAdapter.contains("AsyncAnalysisRunStorePort")),
                () -> assertTrue(executionAdapter.contains("ThreadPoolExecutor")),
                () -> assertTrue(executionAdapter.contains("Map<String, Future<?>> futures")),
                () -> assertTrue(protocolMapper.contains("DateTimeFormatter")),
                () -> assertTrue(persistenceMapper.contains("OpsJsonSnapshotCodec")),
                () -> assertTrue(settings.contains("public record OpsAnalysisRunSettings(")),
                () -> assertTrue(settings.contains("public static OpsAnalysisRunSettings defaults()")),
                () -> assertTrue(settingsConfiguration.contains("OpsAnalysisRunSettings opsAnalysisRunSettings(")),
                () -> assertTrue(settingsConfiguration.contains("${orbisops.runs.max-memory-records:200}")),
                () -> assertTrue(settingsConfiguration.contains("${orbisops.runs.allow-in-memory-fallback:false}")),
                () -> assertTrue(settingsConfiguration.contains("${orbisops.runs.reject-when-queue-full:true}")),
                () -> assertTrue(assembly.contains("AsyncAnalysisRunProcessManager<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO>")),
                () -> assertTrue(assembly.contains("new OpsAsyncAnalysisRunStoreAdapter")),
                () -> assertTrue(assembly.contains("new OpsAsyncAnalysisExecutionAdapter")),
                () -> assertTrue(assembly.contains("new OpsAsyncAnalysisOutcomeAdapter")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("OpsJsonSnapshotCodec")),
                () -> assertFalse(service.contains("CREATE TABLE")),
                () -> assertFalse(service.contains("INSERT INTO")));
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
