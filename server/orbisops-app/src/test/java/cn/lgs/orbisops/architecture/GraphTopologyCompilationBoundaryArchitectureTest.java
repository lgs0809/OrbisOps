package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTopologyCompilationBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void assemblerMustRemainAThinStableFacade() throws IOException {
        String assembler = read(RUNTIME + "OpsGraphTopologyAssembler.java");

        assertAll(
                () -> assertTrue(assembler.contains(
                        "private final OpsGraphTopologyCompiler topologyCompiler;")),
                () -> assertTrue(assembler.contains(
                        "private final OpsGraphRouteSelector routeSelector;")),
                () -> assertTrue(assembler.contains("topologyCompiler.compile(")),
                () -> assertTrue(assembler.contains("routeSelector.select(")),
                () -> assertFalse(assembler.contains("addParallelConditionalEdges(")),
                () -> assertFalse(assembler.contains("addConditionalEdges(")),
                () -> assertFalse(assembler.contains("parallelJoins(")),
                () -> assertFalse(assembler.contains("stateRouteConstraint(")),
                () -> assertTrue(assembler.lines().count() < 200));
    }

    @Test
    void compilerMustOwnOnlyGraphApiRegistrationAndJoinDiscovery() throws IOException {
        String compiler = read(RUNTIME + "OpsGraphTopologyCompiler.java");

        assertAll(
                () -> assertTrue(compiler.contains("StateGraph")),
                () -> assertTrue(compiler.contains("addParallelConditionalEdges(")),
                () -> assertTrue(compiler.contains("addConditionalEdges(")),
                () -> assertTrue(compiler.contains("parallelJoins(")),
                () -> assertTrue(compiler.contains("commonAlwaysTargets(")),
                () -> assertTrue(compiler.contains("addImplicitTerminalEdges(")),
                () -> assertFalse(compiler.contains("intentRouteConstraint(")),
                () -> assertFalse(compiler.contains("graphStateExecutedSources(")),
                () -> assertFalse(compiler.contains("stateRouteConstraint(")),
                () -> assertFalse(compiler.contains("@Slf4j")),
                () -> assertTrue(compiler.lines().count() < 280));
    }

    @Test
    void routeSelectorMustOwnRuntimeGatesWithoutCompilingTopology() throws IOException {
        String selector = read(RUNTIME + "OpsGraphRouteSelector.java");

        assertAll(
                () -> assertTrue(selector.contains("routeConstraint(")),
                () -> assertTrue(selector.contains("stateRouteConstraint(")),
                () -> assertTrue(selector.contains("graphStateExecutedSources(")),
                () -> assertTrue(selector.contains("feedbackLoopPolicy.selectRoutes(")),
                () -> assertTrue(selector.contains("conditionEvaluator.matches(")),
                () -> assertFalse(selector.contains("StateGraph")),
                () -> assertFalse(selector.contains("addEdge(")),
                () -> assertFalse(selector.contains("addConditionalEdges(")),
                () -> assertFalse(selector.contains("parallelJoins(")),
                () -> assertTrue(selector.lines().count() < 240));
    }

    @Test
    void topologyBoundariesMustRemainFreeOfDynamicInjection() throws IOException {
        String combined = read(RUNTIME + "OpsGraphTopologyAssembler.java")
                + read(RUNTIME + "OpsGraphTopologyCompiler.java")
                + read(RUNTIME + "OpsGraphRouteSelector.java");

        assertAll(
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Autowired")),
                () -> assertFalse(combined.contains("@Value")));
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
