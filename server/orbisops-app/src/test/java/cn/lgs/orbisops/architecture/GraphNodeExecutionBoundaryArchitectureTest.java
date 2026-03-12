package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphNodeExecutionBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void coordinatorMustRemainAThinLifecycleAndBodyFacade() throws IOException {
        String coordinator = read(RUNTIME + "OpsGraphNodeExecutionCoordinator.java");

        assertAll(
                () -> assertTrue(coordinator.contains("private final OpsGraphNodeLifecycle lifecycle;")),
                () -> assertTrue(coordinator.contains("private final OpsGraphNodeBodyExecutor bodyExecutor;")),
                () -> assertFalse(coordinator.contains("OpsRuntimeResourceAssembler")),
                () -> assertFalse(coordinator.contains("OpsNodeRagService")),
                () -> assertFalse(coordinator.contains("OpsRuntimeLlmInvoker")),
                () -> assertFalse(coordinator.contains("OpsAgentScopeExecutionCoordinator")),
                () -> assertFalse(coordinator.contains("OpsTelemetryService")),
                () -> assertFalse(coordinator.contains("GraphEventApplicationService")),
                () -> assertFalse(coordinator.contains("executeGenericLlmNode(")),
                () -> assertFalse(coordinator.contains("executeSingleAgentScopeNode(")),
                () -> assertTrue(coordinator.lines().count() < 100));
    }

    @Test
    void graphNodeBoundariesMustStayBoundedAndFreeOfDynamicInjection() throws IOException {
        String context = read(RUNTIME + "OpsGraphNodeExecutionContext.java");
        String result = read(RUNTIME + "OpsGraphNodeExecutionResult.java");
        String promptPolicy = read(RUNTIME + "OpsGraphNodePromptContextPolicy.java");
        String genericLlm = read(RUNTIME + "OpsGraphGenericLlmNodeExecutor.java");
        String agentScope = read(RUNTIME + "OpsGraphAgentScopeNodeExecutor.java");
        String body = read(RUNTIME + "OpsGraphNodeBodyExecutor.java");
        String lifecycle = read(RUNTIME + "OpsGraphNodeLifecycle.java");
        String reporter = read(RUNTIME + "OpsGraphNodeLifecycleReporter.java");
        String combined = context + result + promptPolicy + genericLlm
                + agentScope + body + lifecycle + reporter;

        assertAll(
                () -> assertTrue(context.lines().count() < 50),
                () -> assertTrue(result.lines().count() < 20),
                () -> assertTrue(promptPolicy.lines().count() < 120),
                () -> assertTrue(genericLlm.lines().count() < 90),
                () -> assertTrue(genericLlm.contains("bundle.setTools(java.util.List.of())")),
                () -> assertTrue(agentScope.lines().count() < 300),
                () -> assertTrue(body.lines().count() < 180),
                () -> assertTrue(body.contains("analysisNodeHooks().evaluateChangePackage(")),
                () -> assertTrue(body.contains("&& !specializedWorkflow(context.definition())")),
                () -> assertTrue(lifecycle.lines().count() < 160),
                () -> assertTrue(reporter.lines().count() < 140),
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Value")),
                () -> assertFalse(combined.contains("@Autowired")));
    }

    @Test
    void explicitStructuralAndDeterministicExecutorsMustDispatchBeforeGenericLlmFallback() throws IOException {
        String body = read(RUNTIME + "OpsGraphNodeBodyExecutor.java");
        String direct = read(RUNTIME + "OpsGraphDirectNodeExecutor.java");
        String router = read(RUNTIME + "OpsGraphRouterNodeExecutor.java");
        String analysisPlanning = read(RUNTIME + "OpsAnalysisPlanningNodeExecutor.java");
        String configuration = read(RUNTIME + "OpsWorkSessionRuntimeConfiguration.java");
        int directDispatch = body.indexOf("if (\"DIRECT\".equals(context.nodeType()))");
        int routerDispatch = body.indexOf("if (\"ROUTER\".equals(context.nodeType()))");
        int subWorkflowDispatch = body.indexOf("if (\"SUB_WORKFLOW\".equals(context.nodeType()))");
        int genericFallback = body.indexOf("return genericLlmNodeExecutor.execute(context);");

        assertAll(
                () -> assertTrue(directDispatch >= 0 && directDispatch < genericFallback),
                () -> assertTrue(routerDispatch >= 0 && routerDispatch < genericFallback),
                () -> assertTrue(subWorkflowDispatch >= 0 && subWorkflowDispatch < genericFallback),
                () -> assertTrue(body.contains("directNodeExecutor.execute(context)")),
                () -> assertTrue(body.contains("routerNodeExecutor.execute(context)")),
                () -> assertTrue(body.contains("subWorkflowNodeExecutor.execute(")),
                () -> assertTrue(direct.contains("resourceAssembler.assembleNode(")),
                () -> assertTrue(direct.contains("resolved.tool().call(resolved.input())")),
                () -> assertFalse(direct.contains("RuntimeLlm")),
                () -> assertFalse(router.contains("RuntimeLlm")),
                () -> assertFalse(router.contains("ChatModel")),
                () -> assertFalse(analysisPlanning.contains("planLoopCoordinator.ensurePlan(")),
                () -> assertTrue(configuration.contains("new OpsSubWorkflowNodeExecutor(")),
                () -> assertTrue(configuration.contains("ObjectProvider<OpsChatApplicationService>")));
    }


    @Test
    void compositionRootMustOwnTheOnlyDirectCoordinatorConstruction() throws IOException {
        String assembly = read(RUNTIME + "OpsGraphNodeExecutionAssembly.java");
        String configuration = read(RUNTIME + "OpsWorkSessionRuntimeConfiguration.java");

        assertAll(
                () -> assertTrue(assembly.contains("new OpsGraphNodePromptContextPolicy(")),
                () -> assertTrue(assembly.contains("new OpsGraphGenericLlmNodeExecutor(")),
                () -> assertTrue(assembly.contains("new OpsGraphAgentScopeNodeExecutor(")),
                () -> assertTrue(assembly.contains("new OpsGraphDirectNodeExecutor(")),
                () -> assertTrue(assembly.contains("new OpsGraphRouterNodeExecutor(")),
                () -> assertTrue(assembly.contains("new OpsGraphNodeBodyExecutor(")),
                () -> assertTrue(assembly.contains("new OpsGraphNodeLifecycleReporter(")),
                () -> assertTrue(assembly.contains("new OpsGraphNodeLifecycle(")),
                () -> assertTrue(assembly.contains("new OpsGraphNodeExecutionCoordinator(")),
                () -> assertTrue(configuration.contains("OpsGraphNodeExecutionAssembly.create(")),
                () -> assertFalse(configuration.contains("new OpsGraphNodeExecutionCoordinator(")),
                () -> assertTrue(assembly.lines().count() < 90));
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
