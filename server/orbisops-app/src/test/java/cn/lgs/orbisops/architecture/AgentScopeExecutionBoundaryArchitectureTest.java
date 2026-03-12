package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeExecutionBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void executorMustRemainAnExecutionOrchestratorWithBoundedTransportRecovery() throws IOException {
        String executor = read(RUNTIME + "OpsAgentScopeExecutor.java");

        assertAll(
                () -> assertTrue(executor.contains(
                        "private final OpsAgentScopeConfigPolicy configPolicy;")),
                () -> assertTrue(executor.contains(
                        "private final OpsAgentScopeContextPreparer contextPreparer;")),
                () -> assertTrue(executor.contains(
                        "private final OpsAgentScopePipelineFactory pipelineFactory;")),
                () -> assertTrue(executor.contains(
                        "private final OpsAgentScopeOutputReader outputReader;")),
                () -> assertFalse(executor.contains("ReactAgent.builder(")),
                () -> assertFalse(executor.contains("ParallelAgent.builder(")),
                () -> assertFalse(executor.contains("SequentialAgent.builder(")),
                () -> assertFalse(executor.contains("enhancePrompt(")),
                () -> assertFalse(executor.contains("AssistantMessage")),
                () -> assertFalse(executor.contains("selectBuiltInMicrokernelRoles(")),
                () -> assertFalse(executor.contains("messageDiagnostics(")),
                () -> assertTrue(executor.contains("OpsAgentScopeOutcomeEnvelope outcomeEnvelope")),
                () -> assertTrue(executor.contains("REACT_TRANSPORT_RETRY")),
                () -> assertTrue(executor.contains("WORKFLOW_OUTCOME")),
                () -> assertTrue(executor.lines().count() < 400));
    }

    @Test
    void configPolicyMustOwnProjectionAndToolRoundBoundsWithoutIntentRouting() throws IOException {
        String config = read(RUNTIME + "OpsAgentScopeConfigPolicy.java");

        assertAll(
                () -> assertFalse(config.contains("selectBuiltInMicrokernelRoles(")),
                () -> assertTrue(config.contains("configFromNode(")),
                () -> assertTrue(config.contains("defaultConfig(")),
                () -> assertTrue(config.contains("maxToolRounds(")),
                () -> assertFalse(config.contains("OpsIntentDecision")),
                () -> assertFalse(config.contains("OpsRuntimeIntentService")),
                () -> assertFalse(config.contains("ReactAgent")),
                () -> assertFalse(config.contains("OpsNodeRagService")),
                () -> assertFalse(config.contains("OverAllState")),
                () -> assertTrue(config.lines().count() < 250));
    }

    @Test
    void contextPreparerMustOwnResourcesAndMemoryWithoutImplicitRagRetrieval() throws IOException {
        String context = read(RUNTIME + "OpsAgentScopeContextPreparer.java");

        assertAll(
                () -> assertTrue(context.contains("OpsRuntimeResourceAssembler")),
                () -> assertTrue(context.contains("OpsNodeRagService")),
                () -> assertFalse(context.contains("enhancePrompt(")),
                () -> assertTrue(context.contains("hasPreparedMemoryContext(")),
                () -> assertFalse(context.contains("ReactAgent")),
                () -> assertFalse(context.contains("ParallelAgent")),
                () -> assertFalse(context.contains("AssistantMessage")),
                () -> assertTrue(context.lines().count() < 100));
    }

    @Test
    void pipelineFactoryMustOwnSdkAgentsFlowsAndToolLoopBudget() throws IOException {
        String pipeline = read(RUNTIME + "OpsAgentScopePipelineFactory.java");
        String flow = read(RUNTIME + "OpsAgentScopeFlowPolicy.java");

        assertAll(
                () -> assertTrue(pipeline.contains("ReactAgent.builder(")),
                () -> assertTrue(pipeline.contains("ParallelAgent.builder(")),
                () -> assertTrue(pipeline.contains("SequentialAgent.builder(")),
                () -> assertTrue(pipeline.contains("ToolLoopCoordinator")),
                () -> assertTrue(pipeline.contains("CompileConfig")),
                () -> assertTrue(pipeline.contains("prepare(")),
                () -> assertTrue(pipeline.contains("buildFlow(")),
                () -> assertFalse(pipeline.contains("OpsNodeRagService")),
                () -> assertFalse(pipeline.contains("AssistantMessage")),
                () -> assertTrue(pipeline.lines().count() < 220),
                () -> assertTrue(flow.contains("maxConcurrency(")),
                () -> assertTrue(flow.contains("outputKey(")),
                () -> assertTrue(flow.contains("recursionLimit(")),
                () -> assertTrue(flow.lines().count() < 70));
    }

    @Test
    void outputReaderMustOwnMessageExtractionAndDiagnosticsOnly() throws IOException {
        String output = read(RUNTIME + "OpsAgentScopeOutputReader.java");

        assertAll(
                () -> assertTrue(output.contains("stateOutput(")),
                () -> assertTrue(output.contains("isMeaningfulText(")),
                () -> assertTrue(output.contains("stateDiagnostics(")),
                () -> assertTrue(output.contains("AssistantMessage")),
                () -> assertTrue(output.contains("ChatResponse")),
                () -> assertFalse(output.contains("ReactAgent")),
                () -> assertFalse(output.contains("OpsNodeRagService")),
                () -> assertFalse(output.contains("OpsRuntimeIntentService")),
                () -> assertTrue(output.lines().count() < 200));
    }

    @Test
    void agentScopeBoundariesMustRemainFreeOfDynamicInjection() throws IOException {
        String combined = read(RUNTIME + "OpsAgentScopeExecutor.java")
                + read(RUNTIME + "OpsAgentScopeConfigPolicy.java")
                + read(RUNTIME + "OpsAgentScopeFlowPolicy.java")
                + read(RUNTIME + "OpsAgentScopeContextPreparer.java")
                + read(RUNTIME + "OpsAgentScopePipelineFactory.java")
                + read(RUNTIME + "OpsAgentScopeOutputReader.java");

        assertAll(
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Autowired")),
                () -> assertFalse(combined.contains("@Value")),
                () -> assertFalse(combined.contains("JdbcTemplate")));
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
