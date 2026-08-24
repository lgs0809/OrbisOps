package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import java.util.*;

/** Both recall arms receive the complete authorized metadata set; only their Top20 union is reranked. */
public final class SkillHybridRetrieval {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(SkillHybridRetrieval.class);
    private static final SkillApplicabilityDecision UNVERIFIED_DECISION =
            new SkillApplicabilityDecision(SkillApplicabilityDecision.Verdict.NEED_INFO, List.of(), "");
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
        catch(RuntimeException unavailable) { logFallback("semantic", unavailable); }
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
        } catch(RuntimeException unavailable) { logFallback("rerank", unavailable); }
        Map<String,SkillApplicabilityDecision> assessments=Map.of();
        if(!query.isBlank() && fused.stream().anyMatch(c->!c.explicit())) try {
            var window=fused.stream().map(SkillRuntimeSelection.RankedSkill::candidate).toList();
            assessments=validatedAssessments(query,window,applicability.assess(projectId,query,window));
        } catch(RuntimeException unavailable) { logFallback("applicability", unavailable); }
        return policy.select(fused,query,limit,settings.catalogCandidateLimit(),authorized.size(),settings.similarSuppressionThreshold(),assessments);
    }
    private static void logFallback(String stage, RuntimeException failure) {
        // Only messages emitted by local adapters may become diagnostic codes.
        String reason = switch (Objects.toString(failure.getMessage(), "")) {
            case "SKILL_RETRIEVAL_UNAVAILABLE", "SKILL_RETRIEVAL_INTERRUPTED",
                 "SKILL_RETRIEVAL_NOT_CONFIGURED", "SKILL_RETRIEVAL_INPUT_TOO_LARGE",
                 "SKILL_RETRIEVAL_MODEL_IDENTITY_MISMATCH", "SKILL_APPLICABILITY_MODEL_UNAVAILABLE",
                 "SKILL_APPLICABILITY_INPUT_LIMIT", "SKILL_APPLICABILITY_MODEL_RESPONSE_INVALID",
                 "SKILL_APPLICABILITY_RESPONSE_INVALID" -> failure.getMessage();
            default -> "REDACTED_PROVIDER_ERROR";
        };
        LOG.warn("Skill retrieval fallback stage={} reason={}", stage, reason);
    }
    private Map<String,SkillApplicabilityDecision> validatedAssessments(String query,List<SkillRuntimeCandidate> candidates,
            Map<String,SkillApplicabilityDecision> values) {
        if(values==null || values.isEmpty()) return Map.of();
        var ids=new HashSet<String>();candidates.forEach(c->ids.add(c.skillId()));
        if(!values.keySet().equals(ids)) return Map.of();
        var checked = new HashMap<String, SkillApplicabilityDecision>();
        for (var candidate : candidates) {
            var assessment = values.get(candidate.skillId());
            // The catalog identity applies to the response as a whole. Evidence belongs to each Skill.
            // Keep an unverified candidate out of automatic selection, even if it matched lexically.
            checked.put(candidate.skillId(), hasValidEvidence(query, candidate, assessment)
                    ? assessment : UNVERIFIED_DECISION);
        }
        return Map.copyOf(checked);
    }

    private boolean hasValidEvidence(String query, SkillRuntimeCandidate candidate,
            SkillApplicabilityDecision assessment) {
        if (assessment == null || assessment.verdict() == null || assessment.evidenceQuote().length() > 1000
                || assessment.useCaseIndexes().size() > 32
                || assessment.useCaseIndexes().stream().anyMatch(i -> i < 0 || i >= candidate.routingProfile().useCases().size())) {
            return false;
        }
        if (assessment.verdict() != SkillApplicabilityDecision.Verdict.MATCH) {
            return true;
        }
        return !assessment.useCaseIndexes().isEmpty() && !assessment.evidenceQuote().isBlank()
                && query.contains(assessment.evidenceQuote());
    }
    private Map<String,Double> valid(Map<String,Double> values,Set<String> allowed,boolean complete) {
        if(values==null || values.isEmpty()) return Map.of();
        if((complete && !values.keySet().equals(allowed)) || !allowed.containsAll(values.keySet())
                || values.values().stream().anyMatch(v->v==null || !Double.isFinite(v))) return Map.of();
        return Map.copyOf(values);
    }
}
