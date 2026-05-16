package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningPlan;
import cn.lgs.orbisops.domain.investigation.service.InvestigationReplanPolicy;

import java.util.List;

/** Trigger adapter over the pure Investigation replan domain policy. */
final class OpsMainAgentReplanPolicy {

    private final InvestigationReplanPolicy domainPolicy =
            new InvestigationReplanPolicy();

    void inheritChangeIntent(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO target,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO source) {
        if (target == null || source == null) return;
        OpsMainAgentDeterministicPlanningService mapping =
                new OpsMainAgentDeterministicPlanningService();
        apply(target, mapping.toDto(domainPolicy.inheritChangeIntent(
                mapping.toDomain(target),
                mapping.toDomain(source))));
    }

    void excludeExecutedSources(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            OpsMainAgentDeterministicPlanningService planning) {
        if (plan == null) return;
        OpsMainAgentDeterministicPlanningService mapping = planning == null
                ? new OpsMainAgentDeterministicPlanningService()
                : planning;
        InvestigationPlanningPlan filtered = domainPolicy.excludeExecutedSources(
                mapping.toDomain(plan),
                mapping.observations(results));
        apply(plan, mapping.toDto(filtered));
    }

    private void apply(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO target,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO source) {
        if (target == null || source == null) return;
        target.setIntent(source.getIntent());
        target.setReason(source.getReason());
        target.setChangeRequested(source.getChangeRequested());
        target.setChangeIntent(source.getChangeIntent());
        target.setTasks(source.getTasks());
        target.setConditionalTasks(source.getConditionalTasks());
        target.setSkippedTasks(source.getSkippedTasks());
    }
}
