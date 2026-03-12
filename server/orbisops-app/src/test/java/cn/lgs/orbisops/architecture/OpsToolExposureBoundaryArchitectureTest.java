package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolExposureBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/tool/";

    @Test
    void toolExposureRulesMustLiveInPureRuntimeToolDomain() throws IOException {
        Path root = projectRoot();
        String facade = read(root, RUNTIME + "OpsToolExecutionPolicy.java");
        String settings = read(root, DOMAIN + "model/ToolExposureSettings.java");
        String policy = read(root, DOMAIN + "service/ToolExposurePolicy.java");
        String projector = read(root, RUNTIME + "OpsToolOperationBoundaryProjector.java");
        String configuration = read(root, APPLICATION + "OpsToolExposureConfiguration.java");

        assertAll(
                () -> assertTrue(facade.contains("ToolExposureSettings settings")),
                () -> assertTrue(facade.contains("ToolExposurePolicy exposurePolicy")),
                () -> assertTrue(facade.contains("OpsToolOperationBoundaryProjector boundaryProjector")),
                () -> assertTrue(facade.contains("exposurePolicy.allows(")),
                () -> assertTrue(facade.contains("boundaryProjector.project(settings)")),
                () -> assertFalse(facade.contains("public OpsToolExecutionPolicy()")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("ToolExposureDecision")),
                () -> assertTrue(facade.lines().count() <= 60),
                () -> assertTrue(settings.contains("public record ToolExposureSettings(")),
                () -> assertFalse(settings.contains("Keyword")),
                () -> assertTrue(policy.contains("boolean allows(")),
                () -> assertTrue(policy.contains("EXPLICIT_CAPABILITIES")),
                () -> assertTrue(policy.contains("\"write\"")),
                () -> assertTrue(policy.contains("\"notification\"")),
                () -> assertFalse(policy.contains("name + \" \" + description")),
                () -> assertFalse(policy.contains("@Service")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(Files.exists(root.resolve(RUNTIME + "OpsToolExposureSettings.java"))),
                () -> assertFalse(Files.exists(root.resolve(RUNTIME + "OpsToolExposurePolicy.java"))),
                () -> assertFalse(Files.exists(root.resolve(DOMAIN + "model/ToolExposureDecision.java"))),
                () -> assertFalse(Files.exists(root.resolve(DOMAIN + "model/ToolExposureReason.java"))),
                () -> assertTrue(projector.contains("toolCapabilityPolicy")),
                () -> assertFalse(projector.contains("@Service")),
                () -> assertTrue(configuration.contains("ToolExposureSettings")),
                () -> assertTrue(configuration.contains("orbisops.agent.action-mode")),
                () -> assertTrue(configuration.contains("orbisops.agent.enforce-read-only-tools")),
                () -> assertFalse(configuration.contains("blocked-action-keywords")),
                () -> assertFalse(configuration.contains("allowed-notification-tool-keywords")));
    }

    private String read(Path root, String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath));
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
