package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import cn.lgs.orbisops.domain.skill.model.SkillMutationMode;
import java.util.List;
import java.util.Map;

/** Scores only the already frozen packages; it never traverses or loads the live catalog. */
final class OpsSkillFrozenSimilarityMatcher {
    private final OpsSkillSimilaritySettings settings;
    private final OpsSkillSimilaritySignatureFactory signatures=new OpsSkillSimilaritySignatureFactory();
    private final OpsSkillSimilarityScorer scorer;
    private final OpsSkillSimilarityMatchProjector projector=new OpsSkillSimilarityMatchProjector();
    private final OpsSkillSimilarityTextMetrics metrics=new OpsSkillSimilarityTextMetrics();
    OpsSkillFrozenSimilarityMatcher(OpsSkillSimilaritySettings settings,OpsSkillSemanticMatcher semantic) {
        this.settings=settings==null?OpsSkillSimilaritySettings.defaults():settings;
        this.scorer=new OpsSkillSimilarityScorer(semantic);
    }
    Map<String,Object> bestMatch(String projectId,Map<String,Object> candidate,List<Map<String,Object>> skills) {
        var context=signatures.candidate(candidate);
        var selected=OpsSkillSimilarityMatchProjector.Match.empty();
        var protectedBest=OpsSkillSimilarityMatchProjector.Match.empty();
        String requested=metrics.text(candidate.get("targetSkillId"));
        for(var skill:cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy.references(skills,projectId)) {
            var governance=new SkillGovernancePolicy().governanceState(skill);
            boolean global="GLOBAL".equals(skill.get("scope"));
            boolean locked=projector.isFrozen(skill) || governance.legacyFrozenClassificationRequired()
                    || governance.mutationMode()==SkillMutationMode.LOCKED || governance.mutationMode()==SkillMutationMode.SEALED;
            // Ordinary platform methods are references for project specialization. Only locked global methods veto a duplicate.
            if(global && !locked) continue;
            boolean protectedSkill=global || "FILE".equals(skill.get("sourceType"))
                    || !governance.autoPublishAllowed() || !Boolean.TRUE.equals(skill.get("autoUpdateEnabled"));
            boolean authorSelected=requested.equals(skill.get("skillId"));
            // A semantic score cannot choose or discard the immutable mutation target.
            // CREATE/atomic operations keep their empty target; PATCH keeps the author's exact frozen identity.
            if(!protectedSkill) {
                if(authorSelected) selected=new OpsSkillSimilarityMatchProjector.Match(skill,0D);
                continue;
            }
            var match=new OpsSkillSimilarityMatchProjector.Match(skill,scorer.score(context,signatures.skill(skill)));
            if(authorSelected || match.score()>=settings.threshold() && match.score()>protectedBest.score()) protectedBest=match;
        }
        if(!protectedBest.skill().isEmpty()) {
            var view=new java.util.LinkedHashMap<>(projector.view(protectedBest,"FROZEN_SIMILARITY_GUARD"));
            view.put("frozen",true);return view;
        }
        if(!requested.isBlank() && selected.skill().isEmpty()) throw new IllegalStateException("SKILL_EVOLUTION_TARGET_NOT_FROZEN");
        return selected.skill().isEmpty()?Map.of():projector.view(selected,"AUTHOR_SELECTED_FROZEN_TARGET");
    }
}
