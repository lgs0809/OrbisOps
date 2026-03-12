package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentLlmSettingsBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void llmClientUsesImmutableSettingsAndConfigurationOwnsProperties() throws IOException {
        String client = read(OPS + "OpsAgentLlmClient.java");
        String settings = read(OPS + "OpsAgentLlmSettings.java");
        String configuration = read(APPLICATION + "OpsAgentLlmConfiguration.java");

        assertAll(
                () -> assertTrue(client.contains("OpsAgentLlmSettings settings")),
                () -> assertTrue(client.contains("@Autowired")),
                () -> assertTrue(client.contains("OpsAgentLlmSettings.defaults()")),
                () -> assertFalse(client.contains("@Value")),
                () -> assertFalse(client.contains("private boolean enabled")),
                () -> assertFalse(client.contains("private int maxOutputChars")),
                () -> assertFalse(client.contains("private int modelCallTimeoutSeconds")),
                () -> assertFalse(client.contains("private boolean failOnLlmDegradation")),
                () -> assertFalse(client.contains("ReflectionTestUtils")),
                () -> assertTrue(client.lines().count() <= 220),
                () -> assertTrue(settings.contains("public record OpsAgentLlmSettings(")),
                () -> assertTrue(settings.contains("policySettings()")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(configuration.contains("@Bean")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.enabled")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.model-call-timeout-seconds")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.json-response-format-enabled")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.json-skill-context-retry-max-chars")));
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
