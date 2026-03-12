package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphFeedbackLoopBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void definitionPolicyMustOwnLoopDeclarationsBoundsAndExitMappings() throws IOException {
        String policy = read(RUNTIME + "OpsGraphLoopDefinitionPolicy.java");

        assertAll(
                () -> assertTrue(policy.contains("LOOP_EXIT_CONDITION_PREFIX")),
                () -> assertTrue(policy.contains("Optional<OpsLoopPolicy> forEdge(")),
                () -> assertTrue(policy.contains("effectiveMaxRounds(")),
                () -> assertTrue(policy.contains("addExitMappings(")),
                () -> assertTrue(policy.contains("exhaustedExitCondition(")),
                () -> assertTrue(policy.contains("feedbackLoopPolicies(")),
                () -> assertTrue(policy.contains("graphEdgeKey(")),
                () -> assertFalse(policy.contains("OverAllState")),
                () -> assertFalse(policy.contains("LOOP_ROUNDS_KEY")),
                () -> assertFalse(policy.contains("ObjectProvider")),
                () -> assertFalse(policy.contains("@Value")),
                () -> assertTrue(policy.lines().count() < 270));
    }

    @Test
    void roundStateMustOwnOnlyGraphStateCountersAndLegacyCompatibility() throws IOException {
        String state = read(RUNTIME + "OpsGraphLoopRoundState.java");

        assertAll(
                () -> assertTrue(state.contains("LOOP_ROUNDS_KEY")),
                () -> assertTrue(state.contains("void increment(")),
                () -> assertTrue(state.contains("int legacyReviewRound(")),
                () -> assertTrue(state.contains("state.updateState(")),
                () -> assertFalse(state.contains("OpsLoopPolicy")),
                () -> assertFalse(state.contains("OpsGraphEdge")),
                () -> assertFalse(state.contains("OpsAgentDefinition")),
                () -> assertFalse(state.contains("ObjectProvider")),
                () -> assertTrue(state.lines().count() < 90));
    }

    @Test
    void coordinatorMustOwnAvailabilityExhaustionSelectionAndAuditMetadata() throws IOException {
        String policy = read(RUNTIME + "OpsGraphFeedbackLoopPolicy.java");

        assertAll(
                () -> assertTrue(policy.contains("OpsGraphLoopDefinitionPolicy")),
                () -> assertTrue(policy.contains("OpsGraphLoopRoundState")),
                () -> assertTrue(policy.contains("hasAvailableEdgeForSource(")),
                () -> assertTrue(policy.contains("primaryRoundStatus(")),
                () -> assertTrue(policy.contains("allExhausted(")),
                () -> assertTrue(policy.contains("Selection selectRoutes(")),
                () -> assertTrue(policy.contains("incrementedLoopIds")),
                () -> assertTrue(policy.contains("edgeMetadata(")),
                () -> assertFalse(policy.contains("LOOP_EXIT_CONDITION_PREFIX")),
                () -> assertFalse(policy.contains("state.updateState(")),
                () -> assertFalse(policy.contains("ObjectProvider")),
                () -> assertTrue(policy.lines().count() < 240));
    }

    @Test
    void topologyBoundariesMustDelegateLoopSemanticsAndContainNoLoopStateImplementation()
            throws IOException {
        String assembler = read(RUNTIME + "OpsGraphTopologyAssembler.java");
        String routeSelector = read(RUNTIME + "OpsGraphRouteSelector.java");
        String combined = assembler + routeSelector;

        assertAll(
                () -> assertTrue(assembler.contains(
                        "private final OpsGraphFeedbackLoopPolicy feedbackLoopPolicy;")),
                () -> assertTrue(assembler.contains(
                        "private final OpsGraphRouteSelector routeSelector;")),
                () -> assertTrue(assembler.contains(
                        "private final OpsGraphTopologyCompiler topologyCompiler;")),
                () -> assertTrue(assembler.contains("feedbackLoopPolicy.recursionLimit(")),
                () -> assertTrue(routeSelector.contains("feedbackLoopPolicy.selectRoutes(")),
                () -> assertTrue(assembler.contains("feedbackLoopPolicy.edgeMetadata(")),
                () -> assertEquals(1, occurrences(
                        assembler, "OpsGraphTopologyAssembler(")),
                () -> assertFalse(combined.contains("LOOP_EXIT_CONDITION_PREFIX")),
                () -> assertFalse(combined.contains("loopRoundMap(")),
                () -> assertFalse(combined.contains("incrementLoopRound(")),
                () -> assertFalse(combined.contains("loopExitTarget(")),
                () -> assertFalse(combined.contains("feedbackLoopPolicies(")),
                () -> assertFalse(combined.contains("legacyReviewRound(")),
                () -> assertFalse(combined.contains("decisionLoopRounds")),
                () -> assertTrue(assembler.lines().count() < 200),
                () -> assertTrue(routeSelector.lines().count() < 240));
    }

    @Test
    void compositionRootAndTestsMustUseTheFourDependencyAssemblerGraph() throws IOException {
        String configuration = read(RUNTIME + "OpsWorkSessionRuntimeConfiguration.java");
        String testFactory = read("orbisops-app/src/test/java/"
                + "cn/lgs/orbisops/trigger/ops/runtime/"
                + "OpsGraphTopologyAssemblerTestFactory.java");

        assertAll(
                () -> assertTrue(configuration.contains(
                        "new OpsGraphFeedbackLoopPolicy(")),
                () -> assertTrue(configuration.contains(
                        "new OpsGraphTopologyAssembler(")),
                () -> assertTrue(testFactory.contains(
                        "new OpsGraphFeedbackLoopPolicy(")),
                () -> assertTrue(testFactory.contains(
                        "new OpsGraphTopologyAssembler(")));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
