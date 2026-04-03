package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillRerankPort;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.skill.service.SkillRouteProjectionIdentity;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** Compatibility class name; routing uses the dedicated Qwen scorer, never a chat model. */
@Component
public final class OpsSkillLlmRerankAdapter implements SkillRerankPort {
    private final OpsSkillRetrievalHttpClient client;
    public OpsSkillLlmRerankAdapter(OpsSkillRetrievalHttpClient client) { this.client=client; }
    @Override public Map<String,Double> scores(String query,List<SkillRuntimeCandidate> candidates) {
        if(!client.configured() || query==null || query.isBlank() || candidates==null || candidates.isEmpty()) return Map.of();
        if(candidates.size()>20 || candidates.stream().anyMatch(c->!c.activeAtUse()))
            throw new IllegalArgumentException("SKILL_RERANK_CANDIDATES_INVALID");
        return client.rerank(query,candidates.stream().map(c->Map.of("id",c.skillId(),"text",SkillRouteProjectionIdentity.rerankDocument(c))).toList());
    }
}
