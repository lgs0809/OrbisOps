package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Verified source metadata plus its whole frozen task. Never loaded by ordinary runtime retrieval. */
public record SkillExperienceConsolidationSample(
        String observationId,
        String runId,
        String sessionId,
        String observationType,
        String outcome,
        String taskTemplateHash,
        String trajectoryHash,
        String summary,
        double qualityScore,
        List<SkillExperienceEvidenceReference> evidenceReferences,
        String sourceId,
        String sourceHash,
        String episodeJson,
        String taskEpisodeId,
        String conditionKey) {

    public SkillExperienceConsolidationSample(String observationId,String runId,String sessionId,String observationType,
            String outcome,String taskTemplateHash,String trajectoryHash,String summary,double qualityScore,
            List<SkillExperienceEvidenceReference> evidenceReferences) {
        this(observationId,runId,sessionId,observationType,outcome,taskTemplateHash,trajectoryHash,summary,qualityScore,
                evidenceReferences,"","","","","");
    }

    public SkillExperienceConsolidationSample {
        evidenceReferences = evidenceReferences == null
                ? List.of()
                : List.copyOf(evidenceReferences);
        sourceId=sourceId==null?"":sourceId; sourceHash=sourceHash==null?"":sourceHash;
        episodeJson=episodeJson==null?"":episodeJson; taskEpisodeId=taskEpisodeId==null?"":taskEpisodeId;
        conditionKey=conditionKey==null?"":conditionKey;
    }

    public boolean hardCase() {
        return !"SUCCEEDED".equalsIgnoreCase(outcome)
                || List.of(
                        "USER_NEGATIVE_FEEDBACK",
                        "ROUTING_CORRECTION",
                        "FAILED_THEN_RECOVERED_PATTERN")
                .contains(observationType);
    }
}
