package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledCodeArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/repair/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/repair/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedEffectsResultsAndSecurityPolicy() throws IOException {
        String source = readFiles(List.of(
                DOMAIN + "model/ControlledCodeEffect.java",
                DOMAIN + "model/ControlledCodeAction.java",
                DOMAIN + "model/ControlledCodeResult.java",
                DOMAIN + "service/ControlledCodePolicy.java"));

        assertAll(
                () -> assertTrue(source.contains("enum ControlledCodeEffect")),
                () -> assertTrue(source.contains("sealed interface ControlledCodeResult")),
                () -> assertTrue(source.contains("class ControlledCodePolicy")),
                () -> assertTrue(source.contains("requireReadBeforeWrite")),
                () -> assertTrue(source.contains("DANGEROUS_TOKENS")),
                () -> assertFalse(source.contains("Map<String, Object>")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("Files.")),
                () -> assertFalse(source.contains("ProcessBuilder")));
    }

    @Test
    void applicationOwnsTypedCommandsUseCaseAndNarrowPorts() throws IOException {
        String source = readFiles(List.of(
                APPLICATION + "ControlledCodeCommands.java",
                APPLICATION + "ControlledCodeApplicationService.java",
                APPLICATION + "ControlledCodeFilePort.java",
                APPLICATION + "ControlledCodeSourcePort.java",
                APPLICATION + "ControlledCodeAuditPort.java",
                APPLICATION + "ControlledCodeToolResultPort.java",
                APPLICATION + "ControlledCodeProofPort.java"));

        assertAll(
                () -> assertTrue(source.contains("class ControlledCodeApplicationService")),
                () -> assertTrue(source.contains("ControlledCodeFilePort")),
                () -> assertTrue(source.contains("ControlledCodeSourcePort")),
                () -> assertTrue(source.contains("ControlledCodeToolResultPort")),
                () -> assertTrue(source.contains("ControlledCodeProofPort")),
                () -> assertFalse(source.contains("Map<String, Object>")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("Files.")),
                () -> assertFalse(source.contains("ProcessBuilder")),
                () -> assertFalse(source.contains("SourceRepositoryApplicationService")),
                () -> assertFalse(source.contains("OpsToolResultStore")),
                () -> assertFalse(source.contains("OpsTrustedProofService")));
    }

    @Test
    void infrastructureExclusivelyOwnsFilesystemAndProcessExecution() throws IOException {
        String source = read(INFRASTRUCTURE + "LocalControlledCodeFileAdapter.java");

        assertAll(
                () -> assertTrue(source.contains("implements ControlledCodeFilePort")),
                () -> assertTrue(source.contains("Files.readString")),
                () -> assertTrue(source.contains("Files.writeString")),
                () -> assertTrue(source.contains("new ProcessBuilder")),
                () -> assertTrue(source.contains("toRealPath")),
                () -> assertFalse(source.contains("OpsControlledCodeToolService")),
                () -> assertFalse(source.contains("OpsRepairWorkspaceService")),
                () -> assertFalse(source.contains("OpsToolResultStore")),
                () -> assertFalse(source.contains("OpsTrustedProofService")));
    }

    @Test
    void triggerContainsOnlyMapMapperOuterAdaptersAndNarrowFacade() throws IOException {
        String facade = read(TRIGGER + "ops/repair/OpsControlledCodeToolService.java");
        String mapper = read(TRIGGER + "application/repair/OpsControlledCodeMapper.java");
        String source = read(TRIGGER + "application/repair/OpsControlledCodeSourceAdapter.java");
        String audit = read(TRIGGER + "application/repair/OpsControlledCodeAuditAdapter.java");
        String result = read(TRIGGER + "application/repair/OpsControlledCodeToolResultAdapter.java");
        String proof = read(TRIGGER + "application/repair/OpsControlledCodeProofAdapter.java");
        String provider = read(TRIGGER + "ops/repair/OpsRepairToolProvider.java");

        assertAll(
                () -> assertTrue(facade.contains("ControlledCodeApplicationService")),
                () -> assertTrue(facade.contains("OpsControlledCodeMapper")),
                () -> assertTrue(mapper.contains("Map<String, Object>")),
                () -> assertTrue(source.contains("ControlledCodeSourcePort")),
                () -> assertTrue(source.contains("SourceRepositoryApplicationService")),
                () -> assertTrue(audit.contains("ControlledCodeAuditPort")),
                () -> assertTrue(result.contains("ControlledCodeToolResultPort")),
                () -> assertTrue(proof.contains("ControlledCodeProofPort")),
                () -> assertTrue(Files.readAllLines(projectRoot().resolve(
                        TRIGGER + "ops/repair/OpsControlledCodeToolService.java")).size() < 120),
                () -> assertFalse(facade.contains("Files.")),
                () -> assertFalse(facade.contains("Path")),
                () -> assertFalse(facade.contains("ProcessBuilder")),
                () -> assertFalse(facade.contains("MessageDigest")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("OpsToolResultStore")),
                () -> assertFalse(facade.contains("OpsTrustedProofService")),
                () -> assertFalse(provider.contains("OpsRepairWorkspaceService")),
                () -> assertFalse(provider.contains("OpsControlledCodeToolService")));
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
