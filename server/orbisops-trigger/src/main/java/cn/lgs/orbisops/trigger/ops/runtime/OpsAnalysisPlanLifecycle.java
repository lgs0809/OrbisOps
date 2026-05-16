package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Owns initial plan resolution and planner-backed replan transitions. */
final class OpsAnalysisPlanLifecycle {

    private final OpsMainAgentPlanner planner;

    OpsAnalysisPlanLifecycle(OpsMainAgentPlanner planner) {
        this.planner = planner;
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO ensurePlan(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            AtomicReference<OpsAnalysisResponseDTO.OpsInvestigationPlanDTO> planRef) {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planRef.get();
        if (plan != null) return plan;
        plan = response.getInvestigationPlan();
        if (plan == null) {
            plan = planner.plan(request, questionContext);
            response.setInvestigationPlan(plan);
        }
        planRef.set(plan);
        return plan;
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO replan(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext questionContext,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO currentPlan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            int round,
            int maxMainRounds) {
        return planner.replan(
                request,
                questionContext,
                currentPlan,
                results,
                round,
                maxMainRounds);
    }
}
