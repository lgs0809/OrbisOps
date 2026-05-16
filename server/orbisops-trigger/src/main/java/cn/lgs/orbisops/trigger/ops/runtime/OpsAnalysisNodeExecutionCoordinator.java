package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Dispatches the Analysis node protocol to bounded node executors. */
final class OpsAnalysisNodeExecutionCoordinator {

    private final OpsAnalysisPlanningNodeExecutor planningExecutor;
    private final OpsAnalysisInvestigationNodeExecutor investigationExecutor;
    private final OpsAnalysisReviewNodeExecutor reviewExecutor;
    private final OpsAnalysisTerminalNodeExecutor terminalExecutor;
    private final OpsAnalysisNodeLifecycle lifecycle;
    private final OpsAnalysisRoutingPolicy routingPolicy;

    OpsAnalysisNodeExecutionCoordinator(
            OpsAnalysisPlanningNodeExecutor planningExecutor,
            OpsAnalysisInvestigationNodeExecutor investigationExecutor,
            OpsAnalysisReviewNodeExecutor reviewExecutor,
            OpsAnalysisTerminalNodeExecutor terminalExecutor,
            OpsAnalysisNodeLifecycle lifecycle,
            OpsAnalysisRoutingPolicy routingPolicy) {
        this.planningExecutor = planningExecutor;
        this.investigationExecutor = investigationExecutor;
        this.reviewExecutor = reviewExecutor;
        this.terminalExecutor = terminalExecutor;
        this.lifecycle = lifecycle;
        this.routingPolicy = routingPolicy;
    }

    Map<String, Object> execute(OpsAgentDefinition definition,
                                OpsWorkflowNode node,
                                OpsAgentChatRequest runtimeRequest,
                                OpsAnalysisRuntimeStateManager.State analysisState,
                                OverAllState graphState,
                                List<OpsRuntimeEvent> events,
                                Consumer<OpsRuntimeEvent> eventSink,
                                Hooks hooks) {
        String nodeType = hooks.executionNodeType(node);
        OpsAnalysisNodeExecutionContext context = new OpsAnalysisNodeExecutionContext(
                definition,
                node,
                runtimeRequest,
                analysisState,
                graphState,
                events,
                eventSink,
                hooks,
                nodeType,
                lifecycle.now(),
                System.currentTimeMillis(),
                new LinkedHashMap<>());
        lifecycle.publishStarted(context);
        try {
            lifecycle.assertNotCanceled(context);
            switch (nodeType) {
                case "START", "END" -> planningExecutor.executeStructural(context);
                case "PLAN" -> planningExecutor.executePlan(context);
                case "AGENT" -> executeAgent(context);
                case "ROUTER" -> planningExecutor.executeRouter(context);
                case "EXECUTE" -> planningExecutor.executePlanTasks(context);
                case "SUB_AGENT", "RAG", "ELASTICSEARCH", "ES", "PROMETHEUS", "MYSQL_SLOW_SQL" ->
                        investigationExecutor.execute(context, nodeType, false);
                case "REVIEW", "REFLECT" -> reviewExecutor.execute(context);
                case "REPORT" -> terminalExecutor.executeReport(context);
                case "NOTIFY" -> terminalExecutor.executeNotify(context);
                default -> terminalExecutor.executeUnsupported(context);
            }
            return context.output();
        } catch (RuntimeException error) {
            lifecycle.recordFailure(context, error);
            throw error;
        }
    }

    private void executeAgent(OpsAnalysisNodeExecutionContext context) {
        String role = routingPolicy.agentRole(context.node());
        if ("main_planner".equals(role)) {
            planningExecutor.executePlan(context);
            return;
        }
        if ("data_agent".equals(role)) {
            investigationExecutor.execute(context, "AGENT", true);
            return;
        }
        String summary = "通用 Agent 节点未声明运维角色，跳过专用分析："
                + context.node().getNodeId();
        lifecycle.record(context, "SKIPPED", summary);
        context.output().put("response", context.response());
        context.output().put("output", summary);
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filterGraphReplanTasks(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults,
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state) {
        return reviewExecutor.filterGraphReplanTasks(plan, existingResults, definition, request, state);
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filterGraphReplanTasks(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults,
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAgentChatRequest runtimeRequest,
            OverAllState state) {
        return reviewExecutor.filterGraphReplanTasks(
                plan, existingResults, definition, request, runtimeRequest, state);
    }

    interface Hooks {
        String executionNodeType(OpsWorkflowNode node);

        String evaluateChangePackage(OpsAgentDefinition definition,
                                     OpsWorkflowNode node,
                                     OpsAgentChatRequest request,
                                     OpsAnalysisResponseDTO response,
                                     List<OpsRuntimeEvent> events,
                                     Consumer<OpsRuntimeEvent> eventSink);
    }
}
