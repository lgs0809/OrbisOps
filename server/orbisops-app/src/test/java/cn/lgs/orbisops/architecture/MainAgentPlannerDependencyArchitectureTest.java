package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainAgentPlannerDependencyArchitectureTest {

    private static final String PLANNER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsMainAgentPlanner.java";

    @Test
    void subAgentCatalogMustBePopulatedDuringConstruction() throws IOException {
        String planner = read(PLANNER);

        assertAll(
                () -> assertTrue(planner.contains("List<OpsSubAgent> subAgents")),
                () -> assertTrue(planner.contains("this.subAgentCatalog.replace(subAgents)")),
                () -> assertTrue(planner.contains("this(llmClient, settings, List.of())")),
                () -> assertTrue(planner.contains("@Autowired")),
                () -> assertFalse(planner.contains("@Autowired(required = false)")),
                () -> assertFalse(planner.contains("void setSubAgents(")),
                () -> assertFalse(planner.contains("subAgentCatalog.replace(subAgents);\n    }\n\n    public OpsAnalysis")));
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
