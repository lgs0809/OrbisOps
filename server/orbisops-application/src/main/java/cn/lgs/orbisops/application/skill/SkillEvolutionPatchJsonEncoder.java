package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;

import java.util.LinkedHashMap;
import java.util.Map;

/** Application-owned persisted payload contract for Skill Evolution patches and validation. */
public final class SkillEvolutionPatchJsonEncoder {

    public String encodePatch(String contentJson, SkillEvolutionInputSummary summary) {
        if (summary == null) throw new IllegalArgumentException("SKILL_EVOLUTION_INPUT_SUMMARY_REQUIRED");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", value(contentJson));
        payload.put("summary", summary(summary));
        return CanonicalJson.stringifyPreservingOrder(payload);
    }

    public String encodeCandidateValidation(String pipelineJson) {
        String json = value(pipelineJson);
        return json.isBlank() ? "{}" : json;
    }

    public String encodeSkipValidation(String decision, String reason) {
        String effectiveDecision = value(decision);
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", !effectiveDecision.startsWith("SKIP_UNSAFE"));
        validation.put("reason", value(reason));
        return CanonicalJson.stringifyPreservingOrder(validation);
    }

    private Map<String, Object> summary(SkillEvolutionInputSummary summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventSummaries", summary.eventSummaries());
        data.put("toolEvents", summary.toolEvents());
        data.put("normalizedUserGoal", summary.normalizedUserGoal());
        data.put("finalReport", summary.finalReport());
        data.put("hasCompleted", summary.hasCompleted());
        data.put("hasToolEvidence", summary.hasToolEvidence());
        data.put("hasMessages", summary.hasMessages());
        data.put("evolutionSourceHash", summary.sourceHash());
        return data;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
