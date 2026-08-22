package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSubAgentDecisionSettingsBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void subAgentDecisionFacadeUsesTypedSwitchesAndPreservesLegacyConstructorBehavior() throws IOException {
        String service = read(OPS + "OpsSubAgentDecisionService.java");
        String settings = read(OPS + "OpsSubAgentDecisionSettings.java");
        String configuration = read(APPLICATION + "OpsSubAgentDecisionConfiguration.java");

        assertAll(
                () -> assertTrue(service.contains("OpsSubAgentDecisionSettings settings")),
                () -> assertTrue(service.contains("legacyConstructorDefaults()")),
                () -> assertTrue(service.contains("settings.decisionLlmEnabled()")),
                () -> assertTrue(service.contains("settings.reflectionLlmEnabled()")),
                () -> assertTrue(service.contains("@Autowired")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("private boolean subAgentLlmEnabled")),
                () -> assertFalse(service.contains("private boolean reflectionLlmEnabled")),
                () -> assertTrue(service.lines().count() <= 150),
                () -> assertTrue(settings.contains("public record OpsSubAgentDecisionSettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.sub-agent-llm-enabled")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.reflection-llm-enabled")));
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
