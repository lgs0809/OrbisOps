package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Coordinates the bounded main-agent plan, review and replan lifecycle. */
final class OpsAnalysisPlanLoopCoordinator {

    private final OpsAnalysisPlanLifecycle planLifecycle;
    private final OpsAnalysisImmediateFollowUpCoordinator immediateFollowUpCoordinator;
    private final OpsAnalysisReplanTaskRunner replanTaskRunner;
    private final OpsInvestigationExecutor investigationExecutor;
    private final OpsAnalysisRuntimeStateManager stateManager;

    OpsAnalysisPlanLoopCoordinator(
            OpsMainAgentPlanner planner,
            OpsInvestigationExecutor investigationExecutor,
            OpsAnalysisRuntimeStateManager stateManager,
            OpsAnalysisRoutingPolicy routingPolicy,
            Executor subAgentExecutor) {
        this.planLifecycle = new OpsAnalysisPlanLifecycle(planner);
        this.immediateFollowUpCoordinator =
                new OpsAnalysisImmediateFollowUpCoordinator(
                        investigationExecutor,
                        stateManager,
                        routingPolicy);
        this.replanTaskRunner = new OpsAnalysisReplanTaskRunner(
                investigationExecutor,
                stateManager,
                routingPolicy,
                subAgentExecutor);
        this.investigationExecutor = investigationExecutor;
        this.stateManager = stateManager;
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO ensurePlan(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            AtomicReference<OpsAnalysisResponseDTO.OpsInvestigationPlanDTO> planRef) {
        return planLifecycle.ensurePlan(
                request, response, questionContext, planRef);
    }

    void triggerImmediateFollowUps(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            List<String> runtimeNotes,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults,
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult,
            Set<String> immediateFollowUpSources,
            AtomicReference<List<OpsAnalysisResponseDTO.InvestigationResultDTO>> resultRef) {
        immediateFollowUpCoordinator.trigger(
                definition,
                request,
                response,
                questionContext,
                steps,
                runtimeNotes,
                plan,
                initialResults,
                latestResult,
                immediateFollowUpSources,
                resultRef);
    }

    MainLoopOutcome runMainPlanExecuteLoop(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            AtomicReference<OpsAnalysisResponseDTO.OpsInvestigationPlanDTO> planRef,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO initialPlan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults,
            int maxMainRounds,
            boolean initialFollowUpsAlreadyProcessed) {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO currentPlan = initialPlan;
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results =
                new ArrayList<>(Optional.ofNullable(initialResults).orElse(List.of()));
        int previousSize = results.size();
        int executedRounds = 1;

        stateManager.appendExecutionNotes(response, List.of(
                "主 Agent 进入 Plan-and-Execute 循环，agent="
                        + definition.getAgentId()
                        + "，maxRounds="
                        + maxMainRounds
                        + "。"));
        if (initialFollowUpsAlreadyProcessed) {
            stateManager.appendExecutionNotes(response, List.of(
                    "初始子 Agent 已启用即时复盘，主 Agent 不再重复处理首轮结果，直接进入后续重规划判断。"));
        } else {
            OpsInvestigationExecutor.FollowUpOutcome firstOutcome =
                    investigationExecutor.executeGraphFollowUps(
                            request,
                            response,
                            currentPlan,
                            questionContext,
                            results);
            results = new ArrayList<>(
                    Optional.ofNullable(firstOutcome.results()).orElse(List.of()));
            response.setInvestigationResults(results);
            stateManager.appendExecutionNotes(
                    response,
                    prefixRoundNotes(1, firstOutcome.executionNotes()));
            stateManager.recordFollowUpSteps(
                    steps, request, response, previousSize, results);
        }

        for (int round = 2; round <= maxMainRounds; round++) {
            stateManager.assertNotCanceled(request);
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO nextPlan =
                    planLifecycle.replan(
                            request,
                            questionContext,
                            currentPlan,
                            results,
                            round,
                            maxMainRounds);
            if (nextPlan == null
                    || nextPlan.getTasks() == null
                    || nextPlan.getTasks().isEmpty()) {
                stateManager.appendExecutionNotes(response, List.of(
                        "第 "
                                + round
                                + " 轮主 Agent 重规划：当前证据不需要追加新数据源，停止循环。"));
                break;
            }
            currentPlan = nextPlan;
            planRef.set(currentPlan);
            response.setInvestigationPlan(currentPlan);
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> newResults =
                    replanTaskRunner.execute(
                            definition,
                            request,
                            response,
                            questionContext,
                            steps,
                            round,
                            currentPlan,
                            results);
            if (newResults.isEmpty()) {
                stateManager.appendExecutionNotes(response, List.of(
                        "第 "
                                + round
                                + " 轮主 Agent 重规划：候选数据源已执行或不可用，停止循环。"));
                break;
            }
            results = new ArrayList<>(results);
            results.addAll(newResults);
            response.setInvestigationResults(results);
            stateManager.appendExecutionNotes(response, List.of(
                    "第 "
                            + round
                            + " 轮主 Agent 重规划执行数据源："
                            + newResults.stream()
                            .map(OpsAnalysisResponseDTO.InvestigationResultDTO::getSource)
                            .filter(StringUtils::hasText)
                            .collect(Collectors.joining(","))
                            + "。"));
            executedRounds = round;
        }
        if (maxMainRounds <= 1) {
            stateManager.appendExecutionNotes(response, List.of(
                    "主 Agent maxRounds=1，仅执行初始计划和一次复盘，不进入重规划轮次。"));
        }
        return new MainLoopOutcome(results, executedRounds);
    }

    void waitForInitialSubAgents(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults,
            OpsAgentRunRequestDTO request) {
        replanTaskRunner.waitForInitialSubAgents(plan, initialResults, request);
    }

    private List<String> prefixRoundNotes(int round, List<String> notes) {
        return Optional.ofNullable(notes).orElse(List.of()).stream()
                .map(note -> "第 " + round + " 轮主 Agent 复盘：" + note)
                .toList();
    }

    record MainLoopOutcome(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            int executedRounds) {
    }
}
