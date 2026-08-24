package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationContext;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationHooks;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationPipeline;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationResult;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowNodeCompilerRegistry;
import cn.lgs.orbisops.domain.agentdefinition.compilation.BoundaryNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CapabilityReferenceValidationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledPlanAssemblyStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowEdge;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledWorkflowNode;
import cn.lgs.orbisops.domain.agentdefinition.compilation.ConditionNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.ControlFlowValidationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.HumanApprovalNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.LlmNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.NodeDefinitionCompilationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.RagNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.RuleExpressionCompilationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.SchemaValidationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.SecurityBoundaryValidationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.SubWorkflowNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.ToolNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.TopologyValidationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.WorkflowCompilationErrorCode;
import cn.lgs.orbisops.domain.agentdefinition.compilation.WorkflowCompilationException;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRuleExpression;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentGraphDefinitionPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentWorkflowControlFlowCompilerAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkflowCompilationPipelineTest {

    @Test
    void humanApprovalMustPublishWithoutAnArtificialModelAgent() {
        AgentWorkflowCompilationResult result = pipeline().compile(
                definition(List.of(node("approval", "HUMAN_APPROVAL", "")), List.of()),
                AgentWorkflowCompilationHooks.noop());
        assertTrue(result.report().successful());
        assertEquals(expectedStages(), result.report().completedStages());
        assertEquals("human-approval-node", result.output().nodes().get(0).compilerId());
    }

    @Test
    void validDefinitionMustCompileThroughAllStagesInOrder() {
        AtomicInteger capabilityChecks = new AtomicInteger();
        AtomicInteger securityChecks = new AtomicInteger();
        AgentWorkflowCompilationPipeline pipeline = pipeline();
        AgentWorkflowDefinition definition = definition(
                List.of(node("worker", "CHAT", "agent")),
                List.of());

        AgentWorkflowCompilationResult result = pipeline.compile(
                definition,
                hooks(capabilityChecks, securityChecks));

        assertTrue(result.report().successful());
        assertEquals(expectedStages(), result.report().completedStages());
        assertEquals(expectedStages(), result.output().completedStages());
        assertEquals("worker", result.output().startNodeId());
        assertEquals(List.of("worker"), result.output().reachableNodeIds());
        assertEquals(List.of("worker"), result.output().terminalNodeIds());
        assertEquals("llm-node", result.output().nodes().get(0).compilerId());
        assertEquals(1, capabilityChecks.get());
        assertEquals(1, securityChecks.get());
        assertThrows(UnsupportedOperationException.class,
                () -> result.output().nodes().add(result.output().nodes().get(0)));
    }

    @Test
    void missingCompilerMustReturnTypedFailureAndStopLaterStages() {
        AgentWorkflowCompilationPipeline pipeline = pipeline();
        AgentWorkflowDefinition definition = definition(
                List.of(node("parallel", "PARALLEL", "agent")),
                List.of());

        WorkflowCompilationException error = assertThrows(
                WorkflowCompilationException.class,
                () -> pipeline.compile(definition, AgentWorkflowCompilationHooks.noop()));

        assertEquals(WorkflowCompilationErrorCode.NODE_COMPILER_NOT_FOUND,
                error.report().failures().get(0).code());
        assertEquals(NodeDefinitionCompilationStage.STAGE_ID,
                error.report().failures().get(0).stageId());
        assertEquals("parallel", error.report().failures().get(0).subjectId());
        assertEquals(List.of(SchemaValidationStage.STAGE_ID),
                error.report().completedStages());
    }

    @Test
    void oversizedRuleMustFailAtRuleStageBeforeTopologyAndHooks() {
        AtomicInteger capabilityChecks = new AtomicInteger();
        AtomicInteger securityChecks = new AtomicInteger();
        AgentGraphDefinition.Edge edge = new AgentGraphDefinition.Edge(
                "edge-1", "route", "one", "two", "expression",
                "x".repeat(4097), false, false, 10, Map.of(), "");
        AgentWorkflowDefinition definition = definition(
                List.of(node("one", "CHAT", "one-agent"), node("two", "CHAT", "two-agent")),
                List.of(edge));

        WorkflowCompilationException error = assertThrows(
                WorkflowCompilationException.class,
                () -> pipeline().compile(definition, hooks(capabilityChecks, securityChecks)));

        assertEquals(WorkflowCompilationErrorCode.RULE_INVALID,
                error.report().failures().get(0).code());
        assertEquals(RuleExpressionCompilationStage.STAGE_ID,
                error.report().failures().get(0).stageId());
        assertEquals(0, capabilityChecks.get());
        assertEquals(0, securityChecks.get());
    }

    @Test
    void capabilityAndSecurityFailuresMustKeepDistinctTypedCodes() {
        AgentWorkflowDefinition definition = definition(
                List.of(node("worker", "CHAT", "agent")),
                List.of());
        WorkflowCompilationException capabilityError = assertThrows(
                WorkflowCompilationException.class,
                () -> pipeline().compile(definition, new AgentWorkflowCompilationHooks() {
                    @Override
                    public void validateCapabilities(AgentWorkflowDefinition ignored) {
                        throw new IllegalStateException("missing capability");
                    }

                    @Override
                    public void validateSecurityBoundary(AgentWorkflowDefinition ignored) {
                    }
                }));
        assertEquals(WorkflowCompilationErrorCode.CAPABILITY_REFERENCE_INVALID,
                capabilityError.report().failures().get(0).code());

        WorkflowCompilationException securityError = assertThrows(
                WorkflowCompilationException.class,
                () -> pipeline().compile(definition, new AgentWorkflowCompilationHooks() {
                    @Override
                    public void validateCapabilities(AgentWorkflowDefinition ignored) {
                    }

                    @Override
                    public void validateSecurityBoundary(AgentWorkflowDefinition ignored) {
                        throw new IllegalStateException("unsafe boundary");
                    }
                }));
        assertEquals(WorkflowCompilationErrorCode.SECURITY_BOUNDARY_INVALID,
                securityError.report().failures().get(0).code());
    }

    @Test
    void declaredLoopFeedbackEdgeMustBeExcludedFromTopologicalCycle() {
        AgentGraphDefinition graph = new AgentGraphDefinition(
                "loop-agent", "GRAPH", "start",
                List.of(
                        node("start", "START", "start"),
                        node("worker", "AGENT", "worker"),
                        node("end", "END", "end")),
                List.of(),
                List.of(new AgentGraphDefinition.Loop(
                        "loop-1", List.of("start", "worker"),
                        List.of("back"), 3, "to-end")));
        AgentWorkflowCompilationContext context = new AgentWorkflowCompilationContext(
                new AgentWorkflowDefinition(1, 0, "", graph),
                AgentWorkflowCompilationHooks.noop());
        context.addCompiledNode(compiledNode("start", AgentWorkflowNodeType.START, "START"));
        context.addCompiledNode(compiledNode("worker", AgentWorkflowNodeType.AGENT, "AGENT"));
        context.addCompiledNode(compiledNode("end", AgentWorkflowNodeType.END, "END"));
        context.setCompiledEdges(List.of(
                compiledEdge("to-worker", "start", "worker", false),
                compiledEdge("back", "worker", "start", false),
                compiledEdge("to-end", "worker", "end", false)));

        new ControlFlowValidationStage(
                new OpsAgentWorkflowControlFlowCompilerAdapter()).compile(context);

        assertEquals(List.of("start", "worker", "end"), context.topologicalOrder());
        assertEquals(List.of("end"), context.terminalNodeIds());
    }

    @Test
    void duplicateStageOrderMustFailClosedAtConstruction() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new AgentWorkflowCompilationPipeline(List.of(
                        new SchemaValidationStage(),
                        new SchemaValidationStage())));
        assertTrue(error.getMessage().startsWith("WORKFLOW_COMPILATION_STAGE_DUPLICATE")
                || error.getMessage().startsWith("WORKFLOW_COMPILATION_STAGE_ORDER_DUPLICATE"));
    }

    private AgentWorkflowCompilationPipeline pipeline() {
        AgentWorkflowNodeCompilerRegistry compilers = new AgentWorkflowNodeCompilerRegistry(List.of(
                new BoundaryNodeCompiler(),
                new LlmNodeCompiler(),
                new AgentNodeCompiler(),
                new ToolNodeCompiler(),
                new RagNodeCompiler(),
                new ConditionNodeCompiler(),
                new HumanApprovalNodeCompiler(),
                new SubWorkflowNodeCompiler()));
        return new AgentWorkflowCompilationPipeline(List.of(
                new CompiledPlanAssemblyStage(),
                new SecurityBoundaryValidationStage(),
                new ControlFlowValidationStage(
                        new OpsAgentWorkflowControlFlowCompilerAdapter()),
                new RuleExpressionCompilationStage(
                        new cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowRuleCompilerAdapter()),
                new SchemaValidationStage(),
                new CapabilityReferenceValidationStage(),
                new TopologyValidationStage(new AgentGraphDefinitionPolicy()),
                new NodeDefinitionCompilationStage(compilers)));
    }

    @Test
    void publishedStartNodeMustRejectInvalidRealCallBudgets() {
        for (Object invalid : List.of(0, -1, 1.5, "12", 1001, Double.NaN)) {
            var start = new cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition(
                    "start", AgentWorkflowNodeType.START, "START", "", "", "", "", List.of(),
                    Map.of("maxRealToolCalls", invalid));
            assertThrows(IllegalArgumentException.class, () -> new BoundaryNodeCompiler().compile(start, null));
        }
        var valid = new cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition(
                "start", AgentWorkflowNodeType.START, "START", "", "", "", "", List.of(), Map.of("maxRealToolCalls", 12));
        assertEquals(12, new BoundaryNodeCompiler().compile(valid, null).config().get("maxRealToolCalls"));
    }

    private AgentWorkflowCompilationHooks hooks(
            AtomicInteger capabilityChecks,
            AtomicInteger securityChecks) {
        return new AgentWorkflowCompilationHooks() {
            @Override
            public void validateCapabilities(AgentWorkflowDefinition definition) {
                capabilityChecks.incrementAndGet();
            }

            @Override
            public void validateSecurityBoundary(AgentWorkflowDefinition definition) {
                securityChecks.incrementAndGet();
            }
        };
    }

    private AgentWorkflowDefinition definition(
            List<AgentGraphDefinition.Node> nodes,
            List<AgentGraphDefinition.Edge> edges) {
        return new AgentWorkflowDefinition(
                1, 0, "",
                new AgentGraphDefinition(
                        "agent-1", "CHAT", "", nodes, edges, List.of()));
    }

    private AgentGraphDefinition.Node node(String id, String type, String agent) {
        return new AgentGraphDefinition.Node(
                id, type, "AUTO", agent, false, false,
                List.of(), 0, Map.of());
    }

    private CompiledWorkflowNode compiledNode(
            String id,
            AgentWorkflowNodeType type,
            String publishedType) {
        return new CompiledWorkflowNode(
                id, type, publishedType, "test-compiler", List.of(), Map.of());
    }

    private CompiledWorkflowEdge compiledEdge(
            String edgeId,
            String source,
            String target,
            boolean feedback) {
        AgentWorkflowRouteMode mode = AgentWorkflowRouteMode.ALWAYS;
        return new CompiledWorkflowEdge(
                edgeId, source, target, mode,
                new AgentWorkflowRuleExpression(mode, "always"),
                false, feedback, 0, Map.of());
    }

    private List<String> expectedStages() {
        return List.of(
                SchemaValidationStage.STAGE_ID,
                NodeDefinitionCompilationStage.STAGE_ID,
                RuleExpressionCompilationStage.STAGE_ID,
                TopologyValidationStage.STAGE_ID,
                ControlFlowValidationStage.STAGE_ID,
                CapabilityReferenceValidationStage.STAGE_ID,
                SecurityBoundaryValidationStage.STAGE_ID,
                CompiledPlanAssemblyStage.STAGE_ID);
    }
}
