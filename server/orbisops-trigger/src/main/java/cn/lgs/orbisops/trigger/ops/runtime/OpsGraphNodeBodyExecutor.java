package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionKind;
import cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dispatches a prepared Graph node to Analysis, AgentScope or generic LLM execution. */
final class OpsGraphNodeBodyExecutor {

    private final OpsGraphRuntimeStateManager graphRuntimeStateManager;
    private final OpsAnalysisRuntimeStateManager analysisStateManager;
    private final OpsAnalysisNodeExecutionCoordinator analysisNodeExecutionCoordinator;
    private final OpsAnalysisRoutingPolicy analysisRoutingPolicy;
    private final OpsGraphNodePromptContextPolicy promptContextPolicy;
    private final OpsGraphGenericLlmNodeExecutor genericLlmNodeExecutor;
    private final OpsGraphAgentScopeNodeExecutor agentScopeNodeExecutor;
    private final OpsGraphDirectNodeExecutor directNodeExecutor;
    private final OpsGraphRouterNodeExecutor routerNodeExecutor;
    private final OpsSubWorkflowNodeExecutor subWorkflowNodeExecutor;

    OpsGraphNodeBodyExecutor(
            OpsGraphRuntimeStateManager graphRuntimeStateManager,
            OpsAnalysisRuntimeStateManager analysisStateManager,
            OpsAnalysisNodeExecutionCoordinator analysisNodeExecutionCoordinator,
            OpsAnalysisRoutingPolicy analysisRoutingPolicy,
            OpsGraphNodePromptContextPolicy promptContextPolicy,
            OpsGraphGenericLlmNodeExecutor genericLlmNodeExecutor,
            OpsGraphAgentScopeNodeExecutor agentScopeNodeExecutor,
            OpsGraphDirectNodeExecutor directNodeExecutor,
            OpsGraphRouterNodeExecutor routerNodeExecutor,
            OpsSubWorkflowNodeExecutor subWorkflowNodeExecutor) {
        this.graphRuntimeStateManager = graphRuntimeStateManager;
        this.analysisStateManager = analysisStateManager;
        this.analysisNodeExecutionCoordinator = analysisNodeExecutionCoordinator;
        this.analysisRoutingPolicy = analysisRoutingPolicy;
        this.promptContextPolicy = promptContextPolicy;
        this.genericLlmNodeExecutor = genericLlmNodeExecutor;
        this.agentScopeNodeExecutor = agentScopeNodeExecutor;
        this.directNodeExecutor = directNodeExecutor;
        this.routerNodeExecutor = routerNodeExecutor;
        this.subWorkflowNodeExecutor = subWorkflowNodeExecutor;
    }

    OpsGraphNodeExecutionResult execute(OpsGraphNodeExecutionContext context) {
        if ("START".equals(context.nodeType())) {
            return new OpsGraphNodeExecutionResult(context.input(), null);
        }
        if ("END".equals(context.nodeType())) {
            Map<String, Object> result = new LinkedHashMap<>();
            String packageDecision = context.hooks().analysisNodeHooks().evaluateChangePackage(
                    context.definition(),
                    context.node(),
                    context.request(),
                    context.analysisState() == null ? null : context.analysisState().response(),
                    context.events(),
                    context.eventSink());
            if (StringUtils.hasText(packageDecision)) {
                result.put("changePackageDecision", packageDecision);
                return new OpsGraphNodeExecutionResult(packageDecision, result);
            }
            return new OpsGraphNodeExecutionResult(context.input(), null);
        }
        if ("REVIEW".equals(context.nodeType())
                && graphRuntimeStateManager.completedExplicitSingleSourceGraphInvestigation(
                context.request(), context.state(), analysisRoutingPolicy)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("review_decision", "final_report");
            result.put("selectedReviewRoutes", List.of());
            context.state().value("results").ifPresent(value -> result.put("results", value));
            return new OpsGraphNodeExecutionResult(
                    "用户已明确限定单一调查数据源且真实查询已完成，保留现有证据并进入最终报告。",
                    result);
        }
        if ("ROUTER".equals(context.nodeType())
                && promptContextPolicy.graphReviewDecisionIsFinalReport(context.state())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("review_decision", "final_report");
            result.put("selectedReviewRoutes", List.of());
            return new OpsGraphNodeExecutionResult("final_report", result);
        }
        if (context.analysisState() != null
                && !specializedWorkflow(context.definition())
                && analysisStateManager.supportsNode(context.nodeType())) {
            return executeAnalysisNode(context);
        }
        if ("DIRECT".equals(context.nodeType())) {
            return directNodeExecutor.execute(context);
        }
        if ("ROUTER".equals(context.nodeType())) {
            return routerNodeExecutor.execute(context);
        }
        if ("SUB_WORKFLOW".equals(context.nodeType())) {
            Map<String, Object> result = subWorkflowNodeExecutor.execute(
                    context.definition(),
                    context.node(),
                    context.request(),
                    context.state() == null ? Map.of() : context.state().data());
            return new OpsGraphNodeExecutionResult(messageText(result.get("output")), result);
        }
        if ("AGENTSCOPE".equals(context.nodeType())) {
            return agentScopeNodeExecutor.execute(context);
        }
        return genericLlmNodeExecutor.execute(context);
    }

    private boolean specializedWorkflow(OpsAgentDefinition definition) {
        return definition != null
                && AgentDefinitionKind.SPECIALIZED_WORKFLOW
                == AgentDefinitionKind.parse(definition.getDefinitionKind());
    }

    private OpsGraphNodeExecutionResult executeAnalysisNode(OpsGraphNodeExecutionContext context) {
        OpsLlmTraceContext.Trace trace = new OpsLlmTraceContext.Trace(
                context.events(),
                context.eventSink(),
                "NODE:" + context.node().getNodeId(),
                context.node().getNodeId(),
                context.nodeType(),
                context.node().getAgent(),
                context.routeKey(),
                context.request().getTrustedSkillFrame());
        Map<String, Object> result = OpsNodeDeadlineContext.withTimeout(
                context.analysisState().analysisRequest().getNodeTimeoutSeconds(),
                () -> {
                    Map<String, Object> nodeResult = OpsLlmTraceContext.withTrace(
                            trace,
                            () -> analysisNodeExecutionCoordinator.execute(
                                    context.definition(),
                                    context.node(),
                                    context.request(),
                                    context.analysisState(),
                                    context.state(),
                                    context.events(),
                                    context.eventSink(),
                                    context.hooks().analysisNodeHooks()));
                    OpsNodeDeadlineContext.assertNotExpired();
                    return nodeResult;
                });
        return new OpsGraphNodeExecutionResult(messageText(result.get("output")), result);
    }

    String buildAgentScopeNodeInput(OpsAgentDefinition definition,
                                    OpsWorkflowNode node,
                                    OpsAgentChatRequest request,
                                    String input,
                                    OverAllState state,
                                    OpsGraphNodeExecutionCoordinator.Hooks hooks) {
        return agentScopeNodeExecutor.buildInput(definition, node, request, input, state, hooks);
    }

    Map<String, Object> createAgentScopeObservationResult(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            String output) {
        return agentScopeNodeExecutor.createObservationResult(definition, node, output);
    }

    boolean graphReviewDecisionIsFinalReport(OverAllState state) {
        return promptContextPolicy.graphReviewDecisionIsFinalReport(state);
    }

    private String messageText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String string) {
            return string;
        }
        return String.valueOf(value);
    }
}
