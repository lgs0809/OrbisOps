package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetiredSandboxBoundedContextArchitectureTest {

    @Test
    void retiredSandboxBoundedContextMustNotReturnToCurrentWritePath() throws IOException {
        Path root = projectRoot();
        List<String> retiredSourceRoots = List.of(
                "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/sandbox",
                "orbisops-application/src/main/java/cn/lgs/orbisops/application/sandbox",
                "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/sandbox",
                "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/sandbox",
                "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/sandbox");

        for (String retired : retiredSourceRoots) {
            Path path = root.resolve(retired);
            assertFalse(containsJavaSource(path), () -> "Retired Sandbox source returned: " + retired);
        }

        assertFalse(Files.exists(root.resolve(
                "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/http/admin/OpsSandboxAdminController.java")));
        assertFalse(Files.exists(root.resolve(
                "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/toolexecution/dispatch/OpsSandboxToolInvoker.java")));

        String providerType = Files.readString(root.resolve(
                "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/toolset/model/ToolProviderType.java"));
        assertFalse(providerType.contains("SANDBOX"), "Tool Provider published language must not advertise retired SANDBOX");

        String repairPort = Files.readString(root.resolve(
                "orbisops-application/src/main/java/cn/lgs/orbisops/application/repair/RepairWorkspaceExecutionPort.java"));
        assertTrue(repairPort.contains("worktreePath"), "Repair published language must use worktreePath");
        assertFalse(repairPort.contains("sandboxPath"), "Repair published language must not revive sandboxPath");

        for (String profile : List.of("application-dev.yml", "application-prod.yml", "application-test.yml")) {
            String yaml = Files.readString(root.resolve("orbisops-app/src/main/resources").resolve(profile));
            assertFalse(yaml.contains("\n  sandbox:\n"), () -> "Active ops.sandbox configuration returned in " + profile);
        }
    }

    private boolean containsJavaSource(Path path) throws IOException {
        if (!Files.isDirectory(path)) return false;
        try (var files = Files.walk(path)) {
            return files.anyMatch(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".java"));
        }
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
