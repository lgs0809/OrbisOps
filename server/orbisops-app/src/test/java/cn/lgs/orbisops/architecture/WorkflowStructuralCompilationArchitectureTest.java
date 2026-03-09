package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowStructuralCompilationArchitectureTest {

    private static final String COMPILATION = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/compilation/";
    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void compilationPipelineMustBeOrderedImmutableAndTyped() throws IOException {
        String pipeline = read(COMPILATION + "AgentWorkflowCompilationPipeline.java");
        String stage = read(COMPILATION + "AgentWorkflowCompilationStage.java");
        String report = read(COMPILATION + "WorkflowCompilationReport.java");
        String failure = read(COMPILATION + "WorkflowCompilationFailure.java");
        String errorCode = read(COMPILATION + "WorkflowCompilationErrorCode.java");

        assertAll(
                () -> assertTrue(stage.contains("String stageId()")),
                () -> assertTrue(stage.contains("int order()")),
                () -> assertTrue(stage.contains("WorkflowCompilationErrorCode failureCode()")),
                () -> assertTrue(pipeline.contains("sorted(Comparator.comparingInt")),
                () -> assertTrue(pipeline.contains("List.copyOf(ordered)")),
                () -> assertTrue(pipeline.contains("WORKFLOW_COMPILATION_STAGE_DUPLICATE")),
                () -> assertTrue(pipeline.contains("WORKFLOW_COMPILATION_STAGE_ORDER_DUPLICATE")),
                () -> assertTrue(pipeline.contains("WorkflowCompilationException")),
                () -> assertTrue(report.contains("List.copyOf(completedStages)")),
                () -> assertTrue(failure.contains("WorkflowCompilationErrorCode code")),
                () -> assertTrue(errorCode.contains("CAPABILITY_REFERENCE_INVALID")),
                () -> assertTrue(errorCode.contains("SECURITY_BOUNDARY_INVALID")));
    }

    @Test
    void requiredStagesMustExistInStableOrder() throws IOException {
        String compiler = read(RUNTIME + "OpsAgentWorkflowStructuralCompiler.java");

        assertAll(
                () -> assertOrdered(compiler,
                        "new SchemaValidationStage()",
                        "new NodeDefinitionCompilationStage(nodeCompilers)",
                        "new RuleExpressionCompilationStage(",
                        "new OpsWorkflowRuleCompilerAdapter()",
                        "new TopologyValidationStage(graphPolicy.domainPolicy())",
                        "new ControlFlowValidationStage(",
                        "new OpsAgentWorkflowControlFlowCompilerAdapter()",
                        "new CapabilityReferenceValidationStage()",
                        "new SecurityBoundaryValidationStage()",
                        "new CompiledPlanAssemblyStage()"),
                () -> assertTrue(compiler.contains("resourceValidator.validate(definition)")),
                () -> assertTrue(compiler.contains("routingMetadataPolicy.validate(")),
                () -> assertTrue(compiler.contains("agentScopePolicy.validate(definition)")));
    }

    @Test
    void nodeCompilerRegistryMustRejectAmbiguityWithoutCentralSwitch() throws IOException {
        String registry = read(COMPILATION + "AgentWorkflowNodeCompilerRegistry.java");
        String structural = read(RUNTIME + "OpsAgentWorkflowStructuralCompiler.java");

        assertAll(
                () -> assertTrue(registry.contains("EnumMap<AgentWorkflowNodeType")),
                () -> assertTrue(registry.contains("Map.copyOf(types)")),
                () -> assertTrue(registry.contains("WORKFLOW_NODE_COMPILER_DUPLICATE")),
                () -> assertTrue(registry.contains("WORKFLOW_NODE_COMPILER_TYPE_DUPLICATE")),
                () -> assertTrue(registry.contains("WORKFLOW_NODE_COMPILER_NOT_FOUND")),
                () -> assertFalse(registry.contains("switch (")),
                () -> assertTrue(structural.contains("new BoundaryNodeCompiler()")),
                () -> assertTrue(structural.contains("new SubWorkflowNodeCompiler()")),
                () -> assertFalse(structural.contains("new ParallelNodeCompiler()")),
                () -> assertFalse(structural.contains("new WaitNodeCompiler()")));
    }

    @Test
    void controlFlowReuseMustCrossBoundedContextsThroughPortAndAcl() throws IOException {
        String stage = read(COMPILATION + "ControlFlowValidationStage.java");
        String port = read(COMPILATION + "AgentWorkflowControlFlowCompiler.java");
        String adapter = read(RUNTIME + "OpsAgentWorkflowControlFlowCompilerAdapter.java");

        assertAll(
                () -> assertTrue(stage.contains("AgentWorkflowControlFlowCompiler compiler")),
                () -> assertFalse(stage.contains("domain.worksession")),
                () -> assertTrue(port.contains("interface AgentWorkflowControlFlowCompiler")),
                () -> assertTrue(adapter.contains("implements AgentWorkflowControlFlowCompiler")),
                () -> assertTrue(adapter.contains("AgentGraphCompiler")),
                () -> assertTrue(adapter.contains("AgentGraphModel")),
                () -> assertTrue(adapter.contains("declaredLoopEdgeIds")));
    }

    @Test
    void compiledOutputMustRemainStructuralAndUnbound() throws IOException {
        String output = read(COMPILATION + "CompiledAgentDefinitionVersion.java");
        String node = read(COMPILATION + "CompiledWorkflowNode.java");
        String context = read(COMPILATION + "AgentWorkflowCompilationContext.java");

        assertAll(
                () -> assertTrue(output.contains("int schemaVersion")),
                () -> assertTrue(output.contains("int definitionVersion")),
                () -> assertTrue(output.contains("String definitionHash")),
                () -> assertTrue(output.contains("List<String> reachableNodeIds")),
                () -> assertTrue(output.contains("List<String> topologicalOrder")),
                () -> assertTrue(node.contains("List<AgentWorkflowResourceReference> resources")),
                () -> assertTrue(context.contains("List.copyOf(compiledNodes.values())")),
                () -> assertFalse(output.contains("ToolCallback")),
                () -> assertFalse(output.contains("McpClient")),
                () -> assertFalse(output.contains("Secret")),
                () -> assertFalse(output.contains("Frozen")),
                () -> assertFalse(output.contains("Replay")));
    }

    @Test
    void validatorMustBeStructuralCompilerFacadeWithCompatibleConstructor() throws IOException {
        String validator = read(RUNTIME + "OpsAgentDefinitionValidator.java");

        assertAll(
                () -> assertTrue(validator.contains(
                        "private final OpsAgentWorkflowStructuralCompiler structuralCompiler;")),
                () -> assertTrue(validator.contains("public OpsAgentDefinitionValidator(")),
                () -> assertTrue(validator.contains("OpsAgentGraphDefinitionPolicy graphDefinitionPolicy")),
                () -> assertTrue(validator.contains("OpsAgentScopeDefinitionPolicy agentScopeDefinitionPolicy")),
                () -> assertTrue(validator.contains("OpsAgentDefinitionResourceValidator resourceValidator")),
                () -> assertTrue(validator.contains("return structuralCompiler.compile(definition);")),
                () -> assertFalse(validator.contains("ObjectProvider")),
                () -> assertFalse(validator.contains("Repository")));
    }

    private void assertOrdered(String source, String... tokens) {
        int offset = -1;
        for (String token : tokens) {
            int next = source.indexOf(token);
            assertTrue(next > offset, "Expected ordered token: " + token);
            offset = next;
        }
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
