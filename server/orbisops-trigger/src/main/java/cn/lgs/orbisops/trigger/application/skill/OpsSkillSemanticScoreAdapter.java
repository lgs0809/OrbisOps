package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillSemanticScorePort;
import cn.lgs.orbisops.application.skill.SkillRouteIndexPort;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Independent scoped pgvector recall. Never embeds a lexically truncated candidate list. */
@Component
public final class OpsSkillSemanticScoreAdapter implements SkillSemanticScorePort {
    private final OpsSkillRetrievalHttpClient client;
    private final SkillRouteIndexPort index;
    public OpsSkillSemanticScoreAdapter(OpsSkillRetrievalHttpClient client,SkillRouteIndexPort index) {
        this.client=client;this.index=index;
    }
    @Override public Map<String,Double> scores(String query,List<SkillRuntimeCandidate> candidates) {
        if(!client.configured() || query.isBlank() || candidates.isEmpty()) return Map.of();
        // The selected set may contain only GLOBAL refs. The trusted project cannot be inferred
        // from them; the query overload supplies it explicitly for every runtime request.
        throw new IllegalStateException("SKILL_SEMANTIC_PROJECT_CONTEXT_REQUIRED");
    }
    @Override public Map<String,Double> scores(String projectId,String query,List<SkillRuntimeCandidate> candidates) {
        if(!client.configured() || query.isBlank() || candidates.isEmpty()) return Map.of();
        return index.search(projectId,candidates,client.modelIdentity(),client.embed(query,true),20);
    }
}
