package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsFinalReportSettingsBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void finalReportServiceUsesTypedSettingsAndKeepsCompatibilityConstructor() throws IOException {
        String service = read(OPS + "OpsFinalReportService.java");
        String settings = read(OPS + "OpsFinalReportSettings.java");
        String configuration = read(APPLICATION + "OpsFinalReportConfiguration.java");

        assertAll(
                () -> assertTrue(service.contains("OpsFinalReportSettings settings")),
                () -> assertTrue(service.contains("public OpsFinalReportService(OpsAgentLlmClient llmClient)")),
                () -> assertTrue(service.contains("@Autowired")),
                () -> assertTrue(service.contains("settings.llmEnabled()")),
                () -> assertTrue(service.contains("settings.maxChars()")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("private boolean finalReportLlmEnabled")),
                () -> assertFalse(service.contains("private int finalReportMaxChars")),
                () -> assertTrue(service.lines().count() <= 170),
                () -> assertTrue(settings.contains("public record OpsFinalReportSettings(")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.final-report-llm-enabled")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.final-report-max-chars")));
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
