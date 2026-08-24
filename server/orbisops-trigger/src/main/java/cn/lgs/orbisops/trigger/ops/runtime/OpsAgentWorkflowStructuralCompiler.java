package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationHooks;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationPipeline;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowCompilationResult;
import cn.lgs.orbisops.domain.agentdefinition.compilation.AgentWorkflowNodeCompilerRegistry;
import cn.lgs.orbisops.domain.agentdefinition.compilation.BoundaryNodeCompiler;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CapabilityReferenceValidationStage;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledPlanAssemblyStage;
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
import cn.lgs.orbisops.domain.agentdefinition.compilation.WorkflowCompilationFailure;
import cn.lgs.orbisops.domain.agentdefinition.compilation.WorkflowCompilationReport;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentDefinitionRoutingMetadataPolicy;

import java.util.List;
import java.util.Map;

/** Trigger ACL and composition root for Definition-time structural compilation. */
public final class OpsAgentWorkflowStructuralCompiler {

    private final OpsAgentGraphDefinitionMapper mapper;
    private final OpsAgentScopeDefinitionPolicy agentScopePolicy;
    private final OpsAgentDefinitionResourceValidator resourceValidator;
    private final AgentDefinitionRoutingMetadataPolicy routingMetadataPolicy;
    private final AgentWorkflowNodeCompilerRegistry nodeCompilers;
    private final AgentWorkflowCompilationPipeline pipeline;

    public OpsAgentWorkflowStructuralCompiler(
            OpsAgentGraphDefinitionPolicy graphPolicy,
            OpsAgentScopeDefinitionPolicy agentScopePolicy,
            OpsAgentDefinitionResourceValidator resourceValidator) {
        if (graphPolicy == null) throw new IllegalArgumentException("AGENT_GRAPH_DEFINITION_POLICY_REQUIRED");
        if (agentScopePolicy == null) throw new IllegalArgumentException("AGENT_SCOPE_DEFINITION_POLICY_REQUIRED");
        if (resourceValidator == null) throw new IllegalArgumentException("AGENT_DEFINITION_RESOURCE_VALIDATOR_REQUIRED");
        this.mapper = new OpsAgentGraphDefinitionMapper();
        this.agentScopePolicy = agentScopePolicy;
        this.resourceValidator = resourceValidator;
        this.routingMetadataPolicy = new AgentDefinitionRoutingMetadataPolicy();
        this.nodeCompilers = standardNodeCompilers();
        this.pipeline = new AgentWorkflowCompilationPipeline(List.of(
                new SchemaValidationStage(),
                new NodeDefinitionCompilationStage(nodeCompilers),
                new RuleExpressionCompilationStage(
                        new OpsWorkflowRuleCompilerAdapter()),
                new TopologyValidationStage(graphPolicy.domainPolicy()),
                new ControlFlowValidationStage(
                        new OpsAgentWorkflowControlFlowCompilerAdapter()),
                new CapabilityReferenceValidationStage(),
                new SecurityBoundaryValidationStage(),
                new CompiledPlanAssemblyStage()));
    }

    public CompiledAgentDefinitionVersion compile(OpsAgentDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("AGENT_DEFINITION_REQUIRED");
        AgentWorkflowDefinition typed;
        try {
            typed = mapper.workflowDefinition(definition);
            if (definition.getNodes() != null) {
                for (OpsWorkflowNode node : definition.getNodes()) {
                    if (node != null) OpsWorkflowNodeOutputContract.compile(node);
                }
            }
        } catch (RuntimeException error) {
            WorkflowCompilationFailure failure = new WorkflowCompilationFailure(
                    WorkflowCompilationErrorCode.SCHEMA_INVALID,
                    SchemaValidationStage.STAGE_ID,
                    definition.getAgentId(),
                    summary(error));
            throw new WorkflowCompilationException(
                    WorkflowCompilationReport.failure(List.of(), failure),
                    error);
        }
        AgentWorkflowCompilationHooks hooks = new AgentWorkflowCompilationHooks() {
            @Override
            public void validateCapabilities(AgentWorkflowDefinition ignored) {
                resourceValidator.validate(definition);
            }

            @Override
            public void validateSecurityBoundary(AgentWorkflowDefinition ignored) {
                routingMetadataPolicy.validate(
                        definition.getDefinitionKind(),
                        definition.getWorkflowInvocationMode(),
                        definition.getWorkflowAutoSelectEnabled(),
                        definition.getWhenToUse(),
                        definition.getWhenNotToUse(),
                        definition.getRoutingKeywords());
                agentScopePolicy.validate(definition);
            }
        };
        AgentWorkflowCompilationResult result = pipeline.compile(typed, hooks);
        return result.output();
    }

    public List<String> stageOrder() {
        return pipeline.stageOrder();
    }

    public Map<AgentWorkflowNodeType, String> nodeCompilerBindings() {
        return nodeCompilers.describe();
    }

    private AgentWorkflowNodeCompilerRegistry standardNodeCompilers() {
        return new AgentWorkflowNodeCompilerRegistry(List.of(
                new BoundaryNodeCompiler(),
                new LlmNodeCompiler(),
                new AgentNodeCompiler(),
                new ToolNodeCompiler(),
                new RagNodeCompiler(),
                new ConditionNodeCompiler(),
                new HumanApprovalNodeCompiler(),
                new SubWorkflowNodeCompiler()));
    }

    private String summary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }
}
