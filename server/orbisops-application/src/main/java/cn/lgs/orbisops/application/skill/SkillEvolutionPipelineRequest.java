package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionOpportunityInput;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;

public record SkillEvolutionPipelineRequest(
        String projectId,
        String agentId,
        String runId,
        String sessionId,
        String triggerReason,
        SkillEvolutionInputSummary summary,
        SkillEvolutionOpportunityInput opportunity,
        SkillEvolutionJobSnapshot jobClaim) {

    public SkillEvolutionPipelineRequest(String projectId, String agentId, String runId, String sessionId,
            String triggerReason, SkillEvolutionInputSummary summary, SkillEvolutionOpportunityInput opportunity) {
        this(projectId, agentId, runId, sessionId, triggerReason, summary, opportunity, null);
    }

    public SkillEvolutionPipelineRequest(
            String projectId,
            String agentId,
            String runId,
            String sessionId,
            String triggerReason,
            SkillEvolutionInputSummary summary) {
        this(
                projectId,
                agentId,
                runId,
                sessionId,
                triggerReason,
                summary,
                defaultOpportunity(triggerReason, summary));
    }

    public SkillEvolutionPipelineRequest {
        projectId = text(projectId);
        agentId = text(agentId);
        runId = text(runId);
        sessionId = text(sessionId);
        triggerReason = text(triggerReason);
        if (summary == null) {
            throw new IllegalArgumentException(
                    "SKILL_EVOLUTION_INPUT_SUMMARY_REQUIRED");
        }
        opportunity = opportunity == null
                ? defaultOpportunity(triggerReason, summary)
                : opportunity;
    }

    private static SkillEvolutionOpportunityInput defaultOpportunity(
            String triggerReason,
            SkillEvolutionInputSummary summary) {
        if (summary == null) return null;
        return new SkillEvolutionOpportunityInput(
                triggerReason,
                summary.toolEvents(),
                summary.evidenceReferences(),
                summary.hasCompleted(),
                false,
                false,
                false,
                summary.finalReport());
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
