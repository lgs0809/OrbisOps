package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairWorkspaceArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/repair/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/repair/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedWorkspaceLifecycleAndRepositoryPort() throws IOException {
        String source = readFiles(List.of(
                DOMAIN + "model/RepairWorkspaceCandidate.java",
                DOMAIN + "model/RepairWorkspace.java",
                DOMAIN + "model/RepairWorkspaceStatus.java",
                DOMAIN + "model/RepairWriterLease.java",
                DOMAIN + "model/RepairDiffSnapshot.java",
                DOMAIN + "model/RepairCommitResult.java",
                DOMAIN + "model/RepairVerificationResult.java",
                DOMAIN + "model/RepairArtifactValidation.java",
                DOMAIN + "model/RepairCleanupResult.java",
                DOMAIN + "service/RepairWorkspacePolicy.java",
                DOMAIN + "adapter/repository/IRepairWorkspaceRepository.java"));

        assertAll(
                () -> assertTrue(source.contains("record RepairWorkspace")),
                () -> assertTrue(source.contains("ACTIVE")),
                () -> assertTrue(source.contains("COMMITTED")),
                () -> assertTrue(source.contains("class RepairWorkspacePolicy")),
                () -> assertTrue(source.contains("interface IRepairWorkspaceRepository")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("ProcessBuilder")),
                () -> assertFalse(source.contains("ai_ops_repair_workspace")));
    }

    @Test
    void applicationOwnsTypedWorkspaceUseCaseTransactionsExecutionAndAudit() throws IOException {
        String source = readFiles(List.of(
                APPLICATION + "RepairWorkspaceApplicationService.java",
                APPLICATION + "RepairWorkspaceExecutionPort.java",
                APPLICATION + "RepairSourceCatalogPort.java",
                APPLICATION + "RepairTransactionPort.java",
                APPLICATION + "RepairAuditEvent.java",
                APPLICATION + "RepairAuditPort.java",
                APPLICATION + "RepairExecutionCommand.java",
                APPLICATION + "RepairWorktreeCommand.java"));

        assertAll(
                () -> assertTrue(source.contains("class RepairWorkspaceApplicationService")),
                () -> assertTrue(source.contains("RepairWorkspaceExecutionPort")),
                () -> assertTrue(source.contains("RepairTransactionPort")),
                () -> assertTrue(source.contains("record RepairAuditEvent")),
                () -> assertFalse(source.contains("OpsRepairWorkspaceDTO")),
                () -> assertFalse(source.contains("OpsRepairWorkspaceRequestDTO")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("ProcessBuilder")),
                () -> assertFalse(source.contains("ai_ops_repair_workspace")));
    }

    @Test
    void infrastructureExclusivelyOwnsWorkspaceSqlDdlJsonAndGitProcess() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcRepairWorkspaceRepository.java");
        String schema = read(INFRASTRUCTURE + "JdbcRepairWorkspaceSchemaInitializer.java");
        String execution = read(INFRASTRUCTURE + "LocalRepairWorkspaceExecutionAdapter.java");

        assertAll(
                () -> assertTrue(repository.contains("implements IRepairWorkspaceRepository")),
                () -> assertTrue(repository.contains("ai_ops_repair_workspace")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(repository.contains("state_version=state_version+1")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_repair_workspace")),
                () -> assertTrue(execution.contains("implements RepairWorkspaceExecutionPort")),
                () -> assertTrue(execution.contains("new ProcessBuilder")),
                () -> assertTrue(execution.contains("\"git\", \"-C\"")),
                () -> assertFalse(execution.contains("OpsRepairWorkspaceDTO")),
                () -> assertFalse(execution.contains("JdbcTemplate")));
    }

    @Test
    void triggerContainsOnlyCompatibilityDtoAclForWorkspace() throws IOException {
        String facade = read(TRIGGER + "ops/repair/OpsRepairWorkspaceService.java");
        String mapper = read(TRIGGER + "application/repair/OpsRepairWorkspaceMapper.java");
        String controller = read(TRIGGER + "http/admin/OpsRepairWorkspaceAdminController.java");
        String cleanup = read(TRIGGER + "application/changepackage/OpsChangePackageCleanupAdapter.java");

        assertAll(
                () -> assertTrue(facade.contains("RepairWorkspaceApplicationService")),
                () -> assertTrue(mapper.contains("OpsRepairWorkspaceDTO")),
                () -> assertTrue(controller.contains("RepairWorkspaceApplicationService")),
                () -> assertTrue(cleanup.contains("RepairWorkspaceApplicationService")),
                () -> assertFalse(facade.contains("JdbcTemplate")),
                () -> assertFalse(facade.contains("ProcessBuilder")),
                () -> assertFalse(facade.contains("@PostConstruct")),
                () -> assertFalse(facade.contains("ai_ops_repair_workspace")),
                () -> assertFalse(facade.contains("CREATE TABLE")),
                () -> assertFalse(controller.contains("JdbcTemplate")),
                () -> assertFalse(controller.contains("IRepairWorkspaceRepository")));
    }

    @Test
    void obsoleteWorkspaceReverseProxyChainIsPhysicallyAbsent() {
        Path root = projectRoot();
        assertAll(
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/repair/RepairWorkspacePort.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/repair/OpsRepairWorkspaceAdapter.java"))));
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
