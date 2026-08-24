package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reuse frozen author input on transport retries; reread changed baselines before authoring a new proposal. */
final class SkillEvolutionProposalResolver {
    private final SkillEvolutionProposalPort proposals;
    private final SkillEvolutionSimilarityPort similarity;
    SkillEvolutionProposalResolver(SkillEvolutionProposalPort proposals,SkillEvolutionSimilarityPort similarity) {
        this.proposals=proposals;this.similarity=similarity;
    }
    SkillEvolutionProposalSnapshot resolve(String projectId,SkillEvolutionJobSnapshot claim,String sourceHash,String clusterKey,Map<String,Object> input) {
        try {
            var existing=proposals.existing(claim,sourceHash);
            if(existing.isPresent()) return existing.orElseThrow();
        } catch(IllegalStateException stale) {
            if(!"SKILL_EVOLUTION_RELATED_SKILL_CHANGED".equals(stale.getMessage())
                    || !proposals.supersedeChangedBaseline(claim,sourceHash)) throw stale;
        }
        var prepared=new LinkedHashMap<>(input);
        prepared.put("relatedSkills",similarity.relatedSkills(projectId,input).skills());
        return proposals.freeze(claim,sourceHash,clusterKey,prepared);
    }
}
