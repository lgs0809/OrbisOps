package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionPipelineDecision;
import cn.lgs.orbisops.application.skill.SkillEvolutionPipelineRequest;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionOpportunityInput;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Legacy Map facade over the typed Skill Evolution application process manager. */
@Service
public class OpsSkillEvolutionPipelineService {

    private final SkillEvolutionApplicationService applicationService;

    public OpsSkillEvolutionPipelineService(
            SkillEvolutionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Map<String, Object> process(Map<String, Object> input) {
        Map<String, Object> source = input == null ? Map.of() : input;
        List<SkillEvolutionEvidenceReference> evidenceReferences =
                evidenceReferences(source.get("evidenceRefs"));
        List<String> toolEvidence = stringList(source.get("toolEvidence"));
        List<String> eventSummaries = stringList(source.get("eventSummaries"));
        SkillEvolutionInputSummary summary = new SkillEvolutionInputSummary(
                eventSummaries,
                toolEvidence,
                text(source.get("normalizedUserGoal")),
                text(source.get("finalOutput")),
                bool(source.get("completed")),
                !toolEvidence.isEmpty(),
                bool(source.get("hasMessages")),
                evidenceReferences,
                text(source.get("contextBundleHash")));
        SkillEvolutionPipelineDecision decision = applicationService.decide(
                new SkillEvolutionPipelineRequest(
                        text(source.get("projectId")),
                        text(source.get("agentId")),
                        text(source.get("runId")),
                        text(source.get("sessionId")),
                        text(source.get("triggerReason")),
                        summary,
                        new SkillEvolutionOpportunityInput(
                                text(source.get("triggerReason")),
                                toolEvidence,
                                evidenceReferences,
                                bool(source.get("completed")),
                                bool(source.get("userNegativeFeedback")),
                                bool(source.get("routingCorrected")),
                                bool(source.get("failedThenRecovered")),
                                text(source.get("finalOutput")))));
        Map<String, Object> result = decode(decision.payloadJson());
        result.putIfAbsent("status", decision.status());
        putIfPresent(result, "reasonCode", decision.reasonCode());
        putIfPresent(result, "targetSkillId", decision.targetSkillId());
        putIfPresent(result, "matchedSkillId", decision.matchedSkillId());
        return result;
    }

    private Map<String, Object> decode(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            JSONObject object = JSON.parseObject(json);
            if (object == null) return new LinkedHashMap<>();
            Map<String, Object> result = new LinkedHashMap<>();
            object.forEach(result::put);
            return result;
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private List<SkillEvolutionEvidenceReference> evidenceReferences(Object raw) {
        List<SkillEvolutionEvidenceReference> references = new ArrayList<>();
        for (Object item : list(raw)) {
            if (!(item instanceof Map<?, ?> map)) continue;
            references.add(new SkillEvolutionEvidenceReference(
                    text(map.get("evidenceId")),
                    text(map.get("resultId")),
                    text(map.get("outputHash"))));
        }
        return references;
    }

    private List<String> stringList(Object raw) {
        return list(raw).stream().map(this::text).toList();
    }

    private List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private void putIfPresent(
            Map<String, Object> target,
            String key,
            String value) {
        if (value != null && !value.isBlank()) target.putIfAbsent(key, value);
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value)
                || "true".equalsIgnoreCase(text(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
