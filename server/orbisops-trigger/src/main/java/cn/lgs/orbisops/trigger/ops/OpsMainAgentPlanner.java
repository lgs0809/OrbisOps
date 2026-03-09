package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.investigation.service.InvestigationPlanningPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Main operations agent planner and fallback coordinator. */
@Slf4j
@Service
public class OpsMainAgentPlanner {

    public static final String SOURCE_RAG = InvestigationPlanningPolicy.SOURCE_RAG;
    public static final String SOURCE_ES = InvestigationPlanningPolicy.SOURCE_ES;
    public static final String SOURCE_PROM = InvestigationPlanningPolicy.SOURCE_PROM;
    public static final String SOURCE_MYSQL_SLOW_SQL = InvestigationPlanningPolicy.SOURCE_MYSQL_SLOW_SQL;

    private final OpsAgentLlmClient llmClient;
    private final OpsMainAgentDeterministicPlanningService deterministicPlanningService;
    private final OpsMainAgentPlanningProtocolService planningProtocolService;
    private final OpsMainAgentGraphRoutingService graphRoutingService;
    private final OpsMainAgentSubAgentCatalog subAgentCatalog;
    private final OpsMainAgentReplanPolicy replanPolicy;
    private final OpsMainAgentPlannerSettings settings;

    public OpsMainAgentPlanner(OpsAgentLlmClient llmClient) {
        this(
                llmClient,
                OpsMainAgentPlannerSettings.defaults(),
                List.of());
    }

    public OpsMainAgentPlanner(
            OpsAgentLlmClient llmClient,
            OpsMainAgentPlannerSettings settings) {
        this(llmClient, settings, List.of());
    }

    @Autowired
    public OpsMainAgentPlanner(
            OpsAgentLlmClient llmClient,
            OpsMainAgentPlannerSettings settings,
            List<OpsSubAgent> subAgents) {
        this.llmClient = llmClient;
        this.deterministicPlanningService = new OpsMainAgentDeterministicPlanningService();
        this.planningProtocolService = new OpsMainAgentPlanningProtocolService(
                llmClient,
                deterministicPlanningService);
        this.graphRoutingService = new OpsMainAgentGraphRoutingService(deterministicPlanningService);
        this.subAgentCatalog = new OpsMainAgentSubAgentCatalog();
        this.subAgentCatalog.replace(subAgents);
        this.replanPolicy = new OpsMainAgentReplanPolicy();
        this.settings = settings == null ? OpsMainAgentPlannerSettings.defaults() : settings;
    }

    public OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context) {
        return plan(request, context, "", List.of());
    }

    public OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            String graphRoutingChoices) {
        return plan(request, context, graphRoutingChoices, List.of());
    }

    public OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            String graphRoutingChoices,
            List<String> skillNames) {
        boolean graphScoped = graphRoutingService.scoped(graphRoutingChoices);
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO fallbackPlan = graphScoped
                ? graphRoutingService.emptyPlan(
                        "Graph 画布已提供路由候选；模型未给出合法选择时不使用内置数据源兜底。")
                : deterministicPlanningService.initial(
                        request,
                        context,
                        subAgentCatalog.availableSources());
        if (!graphScoped && settings.allSourcesMode()) {
            return deterministicPlanningService.allSources(
                    context,
                    subAgentCatalog.availableSources());
        }
        if (!settings.llmEnabled()) {
            return fallbackPlan;
        }

        OpsMainAgentPlanningProtocolService.Attempt attempt;
        try {
            attempt = planningProtocolService.plan(
                    new OpsMainAgentPlanningProtocolService.PlanInput(
                            request,
                            context,
                            fallbackPlan,
                            graphScoped,
                            graphRoutingChoices,
                            subAgentCatalog.capabilityCatalog(),
                            subAgentCatalog.availableSources(),
                            skillNames));
            if (!attempt.valid()) {
                return fallbackPlan;
            }
        } catch (OpsLlmDegradationException error) {
            log.warn("主 Agent planner LLM 失败，返回当前模式兜底计划：{}", error.getMessage());
            return fallbackPlan;
        }
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO llmPlan = attempt.plan();
        if (graphScoped) {
            llmPlan = graphRoutingService.filterPlan(llmPlan, graphRoutingChoices);
        }
        if (llmPlan == null || llmPlan.getTasks() == null || llmPlan.getTasks().isEmpty()) {
            if (graphScoped) {
                return graphRoutingService.emptyPlan(
                        "Graph 画布路由下，主 Agent 判断无需进入任何条件分支。");
            }
            rejectInvalidPlan();
            return fallbackPlan;
        }
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO guardrailFallback = graphScoped
                ? graphRoutingService.filterPlan(
                        deterministicPlanningService.initial(
                                request,
                                context,
                                subAgentCatalog.availableSources()),
                        graphRoutingChoices)
                : fallbackPlan;
        llmPlan = deterministicPlanningService.guardrails(llmPlan, guardrailFallback, context);
        llmPlan.setReason("LLM主Agent：" + llmPlan.getReason());
        return llmPlan;
    }

    public OpsAnalysisResponseDTO.OpsInvestigationPlanDTO replan(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO previousPlan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            int round,
            int maxRounds) {
        return replan(request, context, previousPlan, results, round, maxRounds, "", List.of());
    }

    public OpsAnalysisResponseDTO.OpsInvestigationPlanDTO replan(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO previousPlan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            int round,
            int maxRounds,
            String graphRoutingChoices) {
        return replan(
                request,
                context,
                previousPlan,
                results,
                round,
                maxRounds,
                graphRoutingChoices,
                List.of());
    }

    public OpsAnalysisResponseDTO.OpsInvestigationPlanDTO replan(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO previousPlan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            int round,
            int maxRounds,
            String graphRoutingChoices,
            List<String> skillNames) {
        boolean graphScoped = graphRoutingService.scoped(graphRoutingChoices);
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO fallbackPlan = graphScoped
                ? graphRoutingService.emptyPlan(
                        "Graph 画布已提供补查回边；模型未给出合法选择时不使用内置 replan 兜底。")
                : deterministicPlanningService.replan(
                        context,
                        results,
                        round,
                        maxRounds,
                        subAgentCatalog.availableSources());
        replanPolicy.inheritChangeIntent(fallbackPlan, previousPlan);
        if (!graphScoped && settings.allSourcesMode()) {
            return deterministicPlanningService.allSourcesReplanStop(previousPlan);
        }
        if (!settings.llmEnabled()) {
            return fallbackPlan;
        }

        OpsMainAgentPlanningProtocolService.Attempt attempt;
        try {
            attempt = planningProtocolService.replan(
                    new OpsMainAgentPlanningProtocolService.ReplanInput(
                            request,
                            context,
                            previousPlan,
                            results,
                            fallbackPlan,
                            round,
                            maxRounds,
                            graphScoped,
                            graphRoutingChoices,
                            graphRoutingService.routeSources(graphRoutingChoices),
                            subAgentCatalog.availableSources(),
                            skillNames));
            if (!attempt.valid()) {
                return fallbackPlan;
            }
        } catch (OpsLlmDegradationException error) {
            log.warn("主 Agent replanner LLM 失败，使用规则重规划：{}", error.getMessage());
            return fallbackPlan;
        }
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO llmPlan = attempt.plan();
        replanPolicy.inheritChangeIntent(llmPlan, previousPlan);
        if (llmPlan == null) {
            llmClient.rejectDegradation("ops-main-agent-replanner", "replan 未生成有效计划");
            return fallbackPlan;
        }
        if (graphScoped) {
            llmPlan = graphRoutingService.filterPlan(llmPlan, graphRoutingChoices);
        } else {
            replanPolicy.excludeExecutedSources(llmPlan, results, deterministicPlanningService);
        }
        if (llmPlan.getTasks() == null || llmPlan.getTasks().isEmpty()) {
            llmPlan.setReason("LLM主Agent：" + Optional.ofNullable(llmPlan.getReason())
                    .orElse("判断无需追加新数据源。"));
            return llmPlan;
        }
        llmPlan.setReason("LLM主Agent：" + llmPlan.getReason());
        return llmPlan;
    }

    private void rejectInvalidPlan() {
        try {
            llmClient.rejectDegradation("ops-main-agent-planner", "planner 未生成有效 tasks");
        } catch (OpsLlmDegradationException error) {
            log.warn("主 Agent planner 计划无效，使用规则计划：{}", error.getMessage());
        }
    }
}
