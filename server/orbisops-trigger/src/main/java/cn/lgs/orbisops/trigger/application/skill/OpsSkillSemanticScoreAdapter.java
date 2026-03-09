package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillSemanticScorePort;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillSemanticMatcher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public final class OpsSkillSemanticScoreAdapter implements SkillSemanticScorePort {

    private final OpsSkillSemanticMatcher matcher;

    public OpsSkillSemanticScoreAdapter(OpsSkillSemanticMatcher matcher) {
        if (matcher == null) throw new IllegalArgumentException("SKILL_SEMANTIC_MATCHER_REQUIRED");
        this.matcher = matcher;
    }

    @Override
    public Map<String, Double> scores(String query, List<SkillRuntimeCandidate> candidates) {
        List<OpsSkillSemanticMatcher.SkillDocument> documents = candidates == null ? List.of() : candidates.stream()
                .map(candidate -> new OpsSkillSemanticMatcher.SkillDocument(
                        candidate.skillId(), candidate.skillHash(), candidate.retrievalDescriptor()))
                .toList();
        return matcher.scores(query, documents);
    }
}
