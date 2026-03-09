package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisAuditArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/audit/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/audit/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedAnalysisAuditRecordPolicyAndRepositoryPort() throws IOException {
        String source = readFiles(List.of(
                DOMAIN + "model/AnalysisAuditRecord.java",
                DOMAIN + "service/AnalysisAuditPolicy.java",
                DOMAIN + "adapter/repository/IAnalysisAuditRepository.java"));

        assertAll(
                () -> assertTrue(source.contains("record AnalysisAuditRecord")),
                () -> assertTrue(source.contains("class AnalysisAuditPolicy")),
                () -> assertTrue(source.contains("interface IAnalysisAuditRepository")),
                () -> assertFalse(source.contains("OpsAuditRecordDTO")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("ai_ops_agent_audit")));
    }

    @Test
    void applicationOwnsBoundedHistoryAndPersistentFallbackSelection() throws IOException {
        String source = read(APPLICATION + "AnalysisAuditApplicationService.java");

        assertAll(
                () -> assertTrue(source.contains("IAnalysisAuditRepository")),
                () -> assertTrue(source.contains("Deque<AnalysisAuditRecord>")),
                () -> assertTrue(source.contains("repository.upsert")),
                () -> assertTrue(source.contains("repository.list")),
                () -> assertTrue(source.contains("recent.addFirst")),
                () -> assertFalse(source.contains("OpsAuditRecordDTO")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("ai_ops_agent_audit")));
    }

    @Test
    void infrastructureExclusivelyOwnsSqlJsonRowMappingAndDdl() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcAnalysisAuditRepository.java");
        String schema = read(INFRASTRUCTURE + "JdbcAnalysisAuditSchemaInitializer.java");

        assertAll(
                () -> assertTrue(repository.contains("implements IAnalysisAuditRepository")),
                () -> assertTrue(repository.contains("ai_ops_agent_audit")),
                () -> assertTrue(repository.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(repository.contains("parseStringList")),
                () -> assertTrue(repository.contains("parseStringMap")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_agent_audit")),
                () -> assertTrue(schema.contains("@PostConstruct")));
    }

    @Test
    void triggerOnlyMapsDtoAndCoordinatesTypedApplication() throws IOException {
        String mapper = read(TRIGGER + "application/audit/OpsAnalysisAuditMapper.java");
        String analysisRun = read(TRIGGER + "ops/OpsAnalysisRunService.java");
        String auditAdapter = read(TRIGGER + "ops/OpsAsyncAnalysisAuditAdapter.java");
        String controller = read(TRIGGER + "http/admin/OpsAuditAdminController.java");

        assertAll(
                () -> assertTrue(mapper.contains("AnalysisAuditRecord")),
                () -> assertTrue(mapper.contains("OpsAuditRecordDTO")),
                () -> assertTrue(auditAdapter.contains("AnalysisAuditApplicationService")),
                () -> assertTrue(auditAdapter.contains("mapper.success(")),
                () -> assertTrue(auditAdapter.contains("mapper.failure(")),
                () -> assertFalse(analysisRun.contains("AnalysisAuditApplicationService")),
                () -> assertTrue(controller.contains("AnalysisAuditApplicationService")),
                () -> assertFalse(analysisRun.contains("OpsAuditService")),
                () -> assertFalse(controller.contains("RuntimeAuditQueryService")),
                () -> assertFalse(mapper.contains("JdbcTemplate")),
                () -> assertFalse(mapper.contains("ai_ops_agent_audit")));
    }

    @Test
    void obsoleteGenericAndTriggerServiceChainIsPhysicallyAbsent() {
        Path root = projectRoot();
        assertAll(
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/audit/RuntimeAuditPort.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/audit/RuntimeAuditQueryService.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/audit/OpsRuntimeAuditAdapter.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/OpsAuditService.java"))));
    }

    private String readFiles(List<String> paths) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String path : paths) result.append(read(path)).append('\n');
        return result.toString();
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
