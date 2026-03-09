package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentInvestigationWindowBoundaryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/runtime/investigation/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void monotonicQueryWindowRulesMustBelongToRuntimeInvestigationDomain() throws IOException {
        String model = read(DOMAIN + "model/InvestigationWindow.java");
        String policy = read(DOMAIN + "service/InvestigationWindowPolicy.java");
        String adapter = read(TRIGGER + "OpsSubAgentRequestPolicy.java");

        assertAll(
                () -> assertTrue(model.contains("public record InvestigationWindow(")),
                () -> assertTrue(policy.contains("MAX_RETRY_RANGE_MINUTES = 240")),
                () -> assertTrue(policy.contains("Math.max(safe.rangeMinutes(), candidate)")),
                () -> assertTrue(policy.contains("PROMETHEUS_WINDOW_RANK")),
                () -> assertTrue(policy.contains("expandPrometheusWindow(")),
                () -> assertFalse(policy.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertTrue(adapter.contains("InvestigationWindowPolicy windowPolicy")),
                () -> assertTrue(adapter.contains("windowPolicy.applyLogDecision(")),
                () -> assertTrue(adapter.contains("windowPolicy.expandRange(")),
                () -> assertTrue(adapter.contains("windowPolicy.expandPrometheusWindow(")),
                () -> assertFalse(adapter.contains("MAX_RETRY_RANGE_MINUTES")),
                () -> assertFalse(adapter.contains("promWindowRank(")),
                () -> assertFalse(adapter.contains("nextPromWindow(")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
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
