package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeDeliveryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/repair/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/repair/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedDeliveryModelsPolicyAndRepository() throws IOException {
        String source = readFiles(List.of(
                DOMAIN + "model/CodeDeliveryMode.java",
                DOMAIN + "model/CodeDeliveryCandidate.java",
                DOMAIN + "model/CodeDelivery.java",
                DOMAIN + "model/CodeDeliveryBranch.java",
                DOMAIN + "model/CodeDeliveryPullRequest.java",
                DOMAIN + "model/CodeDeliveryCiSnapshot.java",
                DOMAIN + "model/CodeDeliveryCapabilities.java",
                DOMAIN + "service/CodeDeliveryPolicy.java",
                DOMAIN + "adapter/repository/ICodeDeliveryRepository.java"));

        assertAll(
                () -> assertTrue(source.contains("record CodeDelivery")),
                () -> assertTrue(source.contains("enum CodeDeliveryMode")),
                () -> assertTrue(source.contains("class CodeDeliveryPolicy")),
                () -> assertTrue(source.contains("interface ICodeDeliveryRepository")),
                () -> assertFalse(source.contains("Map<String, Object>")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("HttpClient")),
                () -> assertFalse(source.contains("ProcessBuilder")),
                () -> assertFalse(source.contains("ai_ops_code_delivery")));
    }

    @Test
    void applicationOwnsTypedDeliveryUseCaseAndNarrowPorts() throws IOException {
        String source = readFiles(List.of(
                APPLICATION + "CodeDeliveryApplicationService.java",
                APPLICATION + "CodeDeliveryGitPort.java",
                APPLICATION + "CodeDeliveryProviderPort.java",
                APPLICATION + "CodeDeliverySecretPort.java"));

        assertAll(
                () -> assertTrue(source.contains("class CodeDeliveryApplicationService")),
                () -> assertTrue(source.contains("ICodeDeliveryRepository")),
                () -> assertTrue(source.contains("CodeDeliveryGitPort")),
                () -> assertTrue(source.contains("CodeDeliveryProviderPort")),
                () -> assertTrue(source.contains("Supplier<String> deliveryIdSupplier")),
                () -> assertTrue(source.contains("Clock clock")),
                () -> assertFalse(source.contains("CodeDeliveryIdentityPort")),
                () -> assertFalse(source.contains("OpsCodeDeliveryDTO")),
                () -> assertFalse(source.contains("OpsCodeDeliveryRequestDTO")),
                () -> assertFalse(source.contains("Map<String, Object>")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("HttpClient")),
                () -> assertFalse(source.contains("ProcessBuilder")),
                () -> assertFalse(source.contains("ai_ops_code_delivery")));
    }

    @Test
    void infrastructureExclusivelyOwnsSqlDdlGitAndGithubHttp() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcCodeDeliveryRepository.java");
        String schema = read(INFRASTRUCTURE + "JdbcCodeDeliverySchemaInitializer.java");
        String git = read(INFRASTRUCTURE + "LocalCodeDeliveryGitAdapter.java");
        String github = read(INFRASTRUCTURE + "GithubCodeDeliveryProviderAdapter.java");

        assertAll(
                () -> assertTrue(repository.contains("implements ICodeDeliveryRepository")),
                () -> assertTrue(repository.contains("ai_ops_code_delivery")),
                () -> assertTrue(repository.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_code_delivery")),
                () -> assertTrue(git.contains("implements CodeDeliveryGitPort")),
                () -> assertTrue(git.contains("new ProcessBuilder")),
                () -> assertTrue(github.contains("implements CodeDeliveryProviderPort")),
                () -> assertTrue(github.contains("HttpClient")),
                () -> assertTrue(github.contains("/pulls")),
                () -> assertTrue(github.contains("/actions/runs")),
                () -> assertFalse(repository.contains("OpsCodeDeliveryDTO")),
                () -> assertFalse(git.contains("OpsCodeDeliveryDTO")),
                () -> assertFalse(github.contains("OpsSecretResolver")));
    }

    @Test
    void triggerOnlyMapsDtoAndSecretAcl() throws IOException {
        String mapper = read(TRIGGER + "application/repair/OpsCodeDeliveryMapper.java");
        String secret = read(TRIGGER + "application/repair/OpsCodeDeliverySecretAdapter.java");
        String controller = read(TRIGGER + "http/admin/OpsRepairWorkspaceAdminController.java");
        String combined = mapper + secret + controller;

        assertAll(
                () -> assertTrue(mapper.contains("OpsCodeDeliveryDTO")),
                () -> assertTrue(mapper.contains("CodeDeliveryCandidate")),
                () -> assertTrue(secret.contains("CodeDeliverySecretPort")),
                () -> assertTrue(controller.contains("CodeDeliveryApplicationService")),
                () -> assertFalse(combined.contains("JdbcTemplate")),
                () -> assertFalse(combined.contains("ProcessBuilder")),
                () -> assertFalse(combined.contains("HttpClient")),
                () -> assertFalse(combined.contains("ai_ops_code_delivery")),
                () -> assertFalse(combined.contains("CREATE TABLE")),
                () -> assertFalse(controller.contains("ICodeDeliveryRepository")));
    }

    @Test
    void obsoleteGenericDeliveryChainIsPhysicallyAbsent() {
        Path root = projectRoot();
        assertAll(
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/repair/CodeDeliveryPort.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/repair/OpsCodeDeliveryAdapter.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/repair/OpsCodeDeliveryService.java"))));
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
