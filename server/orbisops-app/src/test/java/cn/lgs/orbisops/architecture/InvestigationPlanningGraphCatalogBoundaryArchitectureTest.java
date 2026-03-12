package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationPlanningGraphCatalogBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void plannerDelegatesGraphProtocolAndSubAgentCatalogProjection() throws IOException {
        String planner = read(OPS + "OpsMainAgentPlanner.java");
        String graph = read(OPS + "OpsMainAgentGraphRoutingService.java");
        String catalog = read(OPS + "OpsMainAgentSubAgentCatalog.java");

        assertAll(
                () -> assertTrue(planner.contains("OpsMainAgentGraphRoutingService graphRoutingService")),
                () -> assertTrue(planner.contains("OpsMainAgentSubAgentCatalog subAgentCatalog")),
                () -> assertTrue(planner.contains("graphRoutingService.scoped(")),
                () -> assertTrue(planner.contains("graphRoutingService.emptyPlan(")),
                () -> assertTrue(planner.contains("graphRoutingService.filterPlan(")),
                () -> assertTrue(planner.contains("graphRoutingService.routeSources(")),
                () -> assertTrue(planner.contains("subAgentCatalog.replace(")),
                () -> assertTrue(planner.contains("subAgentCatalog.availableSources()")),
                () -> assertTrue(planner.contains("subAgentCatalog.capabilityCatalog()")),
                () -> assertFalse(planner.contains("JSON.parseObject(")),
                () -> assertFalse(planner.contains("JSONArray")),
                () -> assertFalse(planner.contains("JSONObject")),
                () -> assertFalse(planner.contains("graphRouteSources(")),
                () -> assertFalse(planner.contains("graphChoiceSource(")),
                () -> assertFalse(planner.contains("filterPlanToGraphChoices(")),
                () -> assertFalse(planner.contains("Map<String, OpsSubAgent>")),
                () -> assertFalse(planner.contains("availableSourceCatalog(")),
                () -> assertTrue(planner.lines().count() <= 280),
                () -> assertTrue(graph.contains("JSON.parseObject(")),
                () -> assertTrue(graph.contains("Set<String> routeSources(")),
                () -> assertTrue(graph.contains("filterPlan(")),
                () -> assertTrue(graph.contains("choiceSource(")),
                () -> assertTrue(graph.contains("condition.startsWith(\"needs:\")")),
                () -> assertTrue(graph.contains("replan_required")),
                () -> assertFalse(graph.contains("@Service")),
                () -> assertFalse(graph.contains("@Value")),
                () -> assertFalse(graph.contains("org.springframework")),
                () -> assertTrue(graph.lines().count() <= 200),
                () -> assertTrue(catalog.contains("Map<String, OpsSubAgent>")),
                () -> assertTrue(catalog.contains("void replace(List<OpsSubAgent>")),
                () -> assertTrue(catalog.contains("Set<String> availableSources()")),
                () -> assertTrue(catalog.contains("String capabilityCatalog()")),
                () -> assertFalse(catalog.contains("@Service")),
                () -> assertFalse(catalog.contains("@Value")),
                () -> assertFalse(catalog.contains("org.springframework")),
                () -> assertTrue(catalog.lines().count() <= 100));
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
