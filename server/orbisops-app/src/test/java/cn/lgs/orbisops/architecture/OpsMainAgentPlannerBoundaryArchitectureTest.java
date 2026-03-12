package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainAgentPlannerBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";
    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/investigation/service/";

    @Test
    void plannerUsesTypedModeSettingsAndDelegatesReplanCollectionPolicy() throws IOException {
        String planner = read(OPS + "OpsMainAgentPlanner.java");
        String settings = read(OPS + "OpsMainAgentPlannerSettings.java");
        String replanPolicy = read(OPS + "OpsMainAgentReplanPolicy.java");
        String domainReplanPolicy = read(DOMAIN + "InvestigationReplanPolicy.java");
        String configuration = read(APPLICATION + "OpsMainAgentPlannerConfiguration.java");

        assertAll(
                () -> assertTrue(planner.contains("OpsMainAgentPlannerSettings settings")),
                () -> assertTrue(planner.contains("OpsMainAgentReplanPolicy replanPolicy")),
                () -> assertTrue(planner.contains("settings.allSourcesMode()")),
                () -> assertTrue(planner.contains("settings.llmEnabled()")),
                () -> assertTrue(planner.contains("replanPolicy.inheritChangeIntent(")),
                () -> assertTrue(planner.contains("replanPolicy.excludeExecutedSources(")),
                () -> assertFalse(planner.contains("@Value")),
                () -> assertFalse(planner.contains("private boolean plannerLlmEnabled")),
                () -> assertFalse(planner.contains("private String plannerMode")),
                () -> assertFalse(planner.contains("private Set<String> executedSources(")),
                () -> assertFalse(planner.contains("private void filterAlreadyExecuted(")),
                () -> assertFalse(planner.contains("private void inheritChangeIntent(")),
                () -> assertTrue(planner.lines().count() <= 260),
                () -> assertTrue(settings.contains("public record OpsMainAgentPlannerSettings(")),
                () -> assertTrue(settings.contains("allSourcesMode()")),
                () -> assertTrue(replanPolicy.contains("InvestigationReplanPolicy domainPolicy")),
                () -> assertTrue(replanPolicy.contains("domainPolicy.excludeExecutedSources(")),
                () -> assertTrue(replanPolicy.contains("domainPolicy.inheritChangeIntent(")),
                () -> assertFalse(replanPolicy.contains("Collectors.toSet")),
                () -> assertFalse(replanPolicy.contains("removeIf(")),
                () -> assertFalse(replanPolicy.contains("@Service")),
                () -> assertTrue(domainReplanPolicy.contains("public final class InvestigationReplanPolicy")),
                () -> assertTrue(domainReplanPolicy.contains("InvestigationPlanningPolicy::normalizeSource")),
                () -> assertTrue(domainReplanPolicy.contains("remaining(plan.tasks(), executed)")),
                () -> assertFalse(domainReplanPolicy.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(domainReplanPolicy.contains("org.springframework")),
                () -> assertFalse(domainReplanPolicy.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.planner-llm-enabled")),
                () -> assertTrue(configuration.contains("orbisops.multi-agent.planner-mode")));
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
