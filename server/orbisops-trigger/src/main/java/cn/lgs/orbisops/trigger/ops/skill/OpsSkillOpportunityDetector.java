package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionOpportunityInput;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionOpportunityPolicy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Legacy Map facade over the domain Skill Evolution opportunity policy. */
@Service
public class OpsSkillOpportunityDetector {

    private final SkillEvolutionOpportunityPolicy policy;

    public OpsSkillOpportunityDetector() {
        this(new SkillEvolutionOpportunityPolicy());
    }

    OpsSkillOpportunityDetector(SkillEvolutionOpportunityPolicy policy) {
        this.policy = policy == null
                ? new SkillEvolutionOpportunityPolicy()
                : policy;
    }

    public String detect(Map<String, Object> input) {
        Map<String, Object> source = input == null ? Map.of() : input;
        return policy.detect(new SkillEvolutionOpportunityInput(
                text(source.get("triggerReason")),
                stringList(source.get("toolEvidence")),
                evidenceReferences(source.get("evidenceRefs")),
                bool(source.get("completed")),
                bool(source.get("userNegativeFeedback")),
                bool(source.get("routingCorrected")),
                bool(source.get("failedThenRecovered")),
                text(source.get("finalOutput"))));
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

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value)
                || "true".equalsIgnoreCase(text(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
