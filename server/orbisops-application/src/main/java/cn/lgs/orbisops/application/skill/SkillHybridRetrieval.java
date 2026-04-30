package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import java.util.*;

/** Both recall arms receive the complete authorized metadata set; only their Top20 union is reranked. */
public final class SkillHybridRetrieval {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(SkillHybridRetrieval.class);
    private final SkillSemanticScorePort semantic;
    private final SkillRerankPort reranker;
    private final SkillRuntimeSelectionSettings settings;
    private final SkillApplicabilityPort applicability;
    private final SkillRuntimeRecallPolicy lexical=new SkillRuntimeRecallPolicy();
    private final SkillHybridSelectionPolicy policy=new SkillHybridSelectionPolicy();
    public SkillHybridRetrieval(SkillSemanticScorePort semantic,SkillRerankPort reranker,SkillRuntimeSelectionSettings settings) {
        this(semantic,reranker,settings,SkillApplicabilityPort.UNAVAILABLE);
    }
    public SkillHybridRetrieval(SkillSemanticScorePort semantic,SkillRerankPort reranker,SkillRuntimeSelectionSettings settings,SkillApplicabilityPort applicability) {
        this.semantic=semantic;this.reranker=reranker;this.settings=settings;this.applicability=Objects.requireNonNull(applicability);
    }
    public SkillRuntimeSelection select(String projectId,String query,List<SkillRuntimeCandidate> authorized,Set<String> requested,int limit) {
        Set<String> available=new HashSet<>();authorized.forEach(c->available.add(c.skillId()));
        if(!available.containsAll(requested)) throw new IllegalStateException("REQUESTED_SKILL_NOT_ACTIVE");
        var exact=authorized.stream().filter(c->requested.contains(c.skillId())).toList();
        var words=lexical.recall(query,authorized,Set.of(),20);
        Map<String,Double> vectorScores=Map.of();
        if(!query.isBlank() && !authorized.isEmpty()) try { vectorScores=valid(semantic.scores(projectId,query,authorized),available,false); }
        catch(RuntimeException unavailable) { /* Optional recall failure retains the lexical arm. */ }
        var scores=vectorScores;
        var vectors=authorized.stream().filter(c->scores.containsKey(c.skillId()))
                .sorted(Comparator.<SkillRuntimeCandidate,Double>comparing(c->scores.get(c.skillId())).reversed().thenComparing(SkillRuntimeCandidate::skillId))
                .limit(20).toList();
        var fused=policy.fuse(words,vectors,exact);
        if(settings.llmRerankEnabled() && !query.isBlank() && !fused.isEmpty()) try {
            var documents=fused.stream().map(SkillRuntimeSelection.RankedSkill::candidate).toList();
            Set<String> ids=new HashSet<>();documents.forEach(c->ids.add(c.skillId()));
            var reranked=valid(reranker.scores(query,documents),ids,true);
            if(!reranked.isEmpty()) fused=fused.stream().sorted(Comparator.comparing(SkillRuntimeSelection.RankedSkill::explicit).reversed()
                    .thenComparing(r->reranked.get(r.candidate().skillId()),Comparator.reverseOrder())
                    .thenComparing(r->r.candidate().skillId())).toList();
        } catch(RuntimeException unavailable) { /* One attempt only; retain authorized RRF order. */ }
        Map<String,SkillApplicabilityDecision> assessments=Map.of();
        if(!query.isBlank() && fused.stream().anyMatch(c->!c.explicit())) try {
            var window=fused.stream().map(SkillRuntimeSelection.RankedSkill::candidate).toList();
            assessments=validatedAssessments(query,window,applicability.assess(projectId,query,window));
        } catch(RuntimeException unavailable) {
            // Preserve the fallback cause without logging query contents, provider payloads or credentials.
            String reason=unavailable.getMessage();
            LOG.warn("Skill applicability fallback: type={}, reason={}", unavailable.getClass().getSimpleName(),
                    reason!=null && reason.matches("[A-Z][A-Z0-9_]{1,100}")?reason:"REDACTED_PROVIDER_ERROR");
        }
        return policy.select(fused,query,limit,settings.catalogCandidateLimit(),authorized.size(),settings.similarSuppressionThreshold(),assessments);
    }
    private Map<String,SkillApplicabilityDecision> validatedAssessments(String query,List<SkillRuntimeCandidate> candidates,
            Map<String,SkillApplicabilityDecision> values) {
        if(values==null || values.isEmpty()) return Map.of();
        var ids=new HashSet<String>();candidates.forEach(c->ids.add(c.skillId()));
        if(!values.keySet().equals(ids)) return Map.of();
        for(var c:candidates) {
            var a=values.get(c.skillId());
            if(a==null || a.verdict()==null || a.evidenceQuote().length()>1000
                    || a.useCaseIndexes().size()>32 || a.useCaseIndexes().stream().anyMatch(i->i==null || i<0 || i>=c.routingProfile().useCases().size())) return Map.of();
            if(a.verdict()==SkillApplicabilityDecision.Verdict.MATCH && (a.useCaseIndexes().isEmpty()
                    || a.evidenceQuote().isBlank() || !query.contains(a.evidenceQuote()))) return Map.of();
        }
        return Map.copyOf(values);
    }
    private Map<String,Double> valid(Map<String,Double> values,Set<String> allowed,boolean complete) {
        if(values==null || values.isEmpty()) return Map.of();
        if((complete && !values.keySet().equals(allowed)) || !allowed.containsAll(values.keySet())
                || values.values().stream().anyMatch(v->v==null || !Double.isFinite(v))) return Map.of();
        return Map.copyOf(values);
    }
}
