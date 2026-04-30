package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.*;
import java.util.*;

/** Ranking never grants applicability or authority. RRF ranks and scorer values are not probabilities. */
public final class SkillHybridSelectionPolicy {
    public static final int RECALL_LIMIT=20, RRF_K=60, SELECTED_LIMIT=3;
    private final SkillRoutingBoundaryPolicy boundaries=new SkillRoutingBoundaryPolicy();

    public List<SkillRuntimeSelection.RankedSkill> fuse(List<SkillRuntimeCandidate> lexical,
            List<SkillRuntimeCandidate> semantic,List<SkillRuntimeCandidate> explicit) {
        Map<String,SkillRuntimeCandidate> candidates=new HashMap<>();Map<String,Double> scores=new HashMap<>();
        for(var list:List.of(lexical,semantic)) {
            Set<String> seen=new HashSet<>();int rank=0;
            for(var c:list) if(seen.add(c.skillId()) && rank<RECALL_LIMIT) {
                candidates.put(c.skillId(),c);scores.merge(c.skillId(),1D/(RRF_K+(++rank)),Double::sum);
            }
        }
        Set<String> requested=new HashSet<>();
        for(var c:explicit) { candidates.put(c.skillId(),c);requested.add(c.skillId()); }
        return candidates.values().stream().map(c->new SkillRuntimeSelection.RankedSkill(c,
                scores.getOrDefault(c.skillId(),0D),requested.contains(c.skillId())))
                .sorted(Comparator.comparing(SkillRuntimeSelection.RankedSkill::explicit).reversed()
                        .thenComparing(SkillRuntimeSelection.RankedSkill::score,Comparator.reverseOrder())
                        .thenComparing(r->r.candidate().skillId())).limit(RECALL_LIMIT).toList();
    }

    public SkillRuntimeSelection select(List<SkillRuntimeSelection.RankedSkill> ranked,String query,
            int selectedLimit,int catalogLimit,int activeCount,double suppressionThreshold) {
        return select(ranked,query,selectedLimit,catalogLimit,activeCount,suppressionThreshold,Map.of());
    }

    public SkillRuntimeSelection select(List<SkillRuntimeSelection.RankedSkill> ranked,String query,
            int selectedLimit,int catalogLimit,int activeCount,double suppressionThreshold,
            Map<String,SkillApplicabilityDecision> assessments) {
        var chosen=new ArrayList<SkillRuntimeSelection.RankedSkill>();
        var suppressed=new ArrayList<SkillRuntimeSelection.SuppressedSkill>();
        int limit=Math.min(SELECTED_LIMIT,Math.max(selectedLimit,(int)ranked.stream().filter(SkillRuntimeSelection.RankedSkill::explicit).count()));
        for(var item:ranked) {
            var match=boundaries.matchApplicability(query,item.candidate().routingProfile());
            boolean excluded="WHEN_NOT_TO_USE_MATCHED".equals(match.reasonCode());
            var assessment=assessments.get(item.candidate().skillId());
            boolean applicable=assessment==null?match.matched():assessment.verdict()==SkillApplicabilityDecision.Verdict.MATCH;
            if(excluded || (!item.explicit() && !applicable)) {
                String reason=excluded?"WHEN_NOT_TO_USE_MATCHED":assessment!=null && assessment.verdict()==SkillApplicabilityDecision.Verdict.NO_MATCH
                        ?"SKILL_NOT_APPLICABLE":"SKILL_APPLICABILITY_NEED_INFO";
                suppressed.add(new SkillRuntimeSelection.SuppressedSkill(item,"",reason));
                continue;
            }
            var duplicate=chosen.stream().filter(prior->similarity(prior.candidate().retrievalDescriptor(),item.candidate().retrievalDescriptor())>=suppressionThreshold).findFirst();
            if(!item.explicit() && duplicate.isPresent()) {
                suppressed.add(new SkillRuntimeSelection.SuppressedSkill(item,duplicate.get().candidate().skillId(),"SIMILAR_SKILL_SUPPRESSED"));continue;
            }
            if(chosen.size()<limit) chosen.add(item);
        }
        int catalog=Math.min(RECALL_LIMIT,Math.max(catalogLimit,(int)ranked.stream().filter(SkillRuntimeSelection.RankedSkill::explicit).count()));
        return new SkillRuntimeSelection(ranked.stream().limit(catalog).toList(),chosen,suppressed,activeCount);
    }

    private double similarity(String left,String right) {
        var a=grams(left);var b=grams(right);if(a.isEmpty() || b.isEmpty()) return 0;
        var union=new HashSet<>(a);union.retainAll(b);return 2D*union.size()/(a.size()+b.size());
    }
    private Set<String> grams(String s) {
        int[] points=s.toLowerCase(Locale.ROOT).replaceAll("\\s+","").codePoints().toArray();Set<String> result=new HashSet<>();
        for(int i=0;i+1<points.length;i++) result.add(new String(points,i,2));return result;
    }
}
