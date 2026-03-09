package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphEdgePromptContextBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void promptContextMustRemainAStableTextFacade() throws IOException {
        String facade = read(RUNTIME + "OpsGraphEdgePromptContext.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsGraphEdgeCatalog.incomingEdges(")),
                () -> assertTrue(facade.contains("OpsGraphEdgeCatalog.outgoingEdges(")),
                () -> assertTrue(facade.contains("OpsGraphEdgeChoiceRenderer.edgeChoiceJson(")),
                () -> assertTrue(facade.contains("OpsGraphRouterPromptContract.routerChoiceContext(")),
                () -> assertTrue(facade.contains("OpsGraphRouterPromptContract.downstreamRouterContract(")),
                () -> assertTrue(facade.contains("compactIncomingHandoff(")),
                () -> assertFalse(facade.contains("JSONObject")),
                () -> assertFalse(facade.contains("JSONArray")),
                () -> assertFalse(facade.contains("routerRequiredOutput(")),
                () -> assertFalse(facade.contains("routeOutputHint(")),
                () -> assertFalse(facade.contains("Collectors.toMap(")),
                () -> assertTrue(facade.lines().count() < 190));
    }

    @Test
    void edgeCatalogMustOwnOnlyDeterministicGraphQueries() throws IOException {
        String catalog = read(RUNTIME + "OpsGraphEdgeCatalog.java");

        assertAll(
                () -> assertTrue(catalog.contains("incomingEdges(")),
                () -> assertTrue(catalog.contains("outgoingEdges(")),
                () -> assertTrue(catalog.contains("selectedEdges(")),
                () -> assertTrue(catalog.contains("nodeById(")),
                () -> assertFalse(catalog.contains("JSONObject")),
                () -> assertFalse(catalog.contains("JSON.toJSONString(")),
                () -> assertFalse(catalog.contains("routeOutputHint(")),
                () -> assertFalse(catalog.contains("configText(")),
                () -> assertTrue(catalog.lines().count() < 80));
    }

    @Test
    void choiceRendererMustOwnChoiceSchemaAndRouteOutputHints() throws IOException {
        String renderer = read(RUNTIME + "OpsGraphEdgeChoiceRenderer.java");

        assertAll(
                () -> assertTrue(renderer.contains("edgeChoiceJson(")),
                () -> assertTrue(renderer.contains("edgeChoices(")),
                () -> assertTrue(renderer.contains("routerRequiredOutput(")),
                () -> assertTrue(renderer.contains("routeKey(")),
                () -> assertTrue(renderer.contains("routeOutputHint(")),
                () -> assertTrue(renderer.contains("choiceDescription(")),
                () -> assertFalse(renderer.contains("routerChoiceContext(")),
                () -> assertFalse(renderer.contains("downstreamRouterContract(")),
                () -> assertFalse(renderer.contains("incomingEdges(")),
                () -> assertTrue(renderer.lines().count() < 230));
    }

    @Test
    void routerContractMustOwnRouterDiscoveryAndContractDocuments() throws IOException {
        String router = read(RUNTIME + "OpsGraphRouterPromptContract.java");

        assertAll(
                () -> assertTrue(router.contains("routerChoiceContext(")),
                () -> assertTrue(router.contains("downstreamRouterContract(")),
                () -> assertTrue(router.contains("selectionContract")),
                () -> assertTrue(router.contains("requiredOutput")),
                () -> assertTrue(router.contains("MAX_ROUTING_CHOICES_CHARS")),
                () -> assertFalse(router.contains("compactIncomingHandoff(")),
                () -> assertFalse(router.contains("renderEdges(")),
                () -> assertFalse(router.contains("routeOutputHint(")),
                () -> assertTrue(router.lines().count() < 190));
    }

    @Test
    void promptRenderingBoundariesMustRemainFreeOfDynamicInjection() throws IOException {
        String combined = read(RUNTIME + "OpsGraphEdgePromptContext.java")
                + read(RUNTIME + "OpsGraphEdgeCatalog.java")
                + read(RUNTIME + "OpsGraphEdgeChoiceRenderer.java")
                + read(RUNTIME + "OpsGraphRouterPromptContract.java");

        assertAll(
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Autowired")),
                () -> assertFalse(combined.contains("@Value")),
                () -> assertFalse(combined.contains("JdbcTemplate")),
                () -> assertFalse(combined.contains("ChatClient")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
