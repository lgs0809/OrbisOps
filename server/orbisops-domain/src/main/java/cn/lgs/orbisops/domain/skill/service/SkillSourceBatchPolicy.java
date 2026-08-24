package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.util.*;

/** A complete immutable archive, bounded model pages and an exact review ledger. */
public final class SkillSourceBatchPolicy {
    public static final int PAGE_SIZE=20, ARCHIVE_LIMIT=1000;
    public static final String VERSION="whole-source-batches-v1";
    public List<Map<String,Object>> pages(Map<String,Object> input) {
        var sources=sources(input);var pages=new ArrayList<Map<String,Object>>();
        if(new HashSet<>(sources.stream().map(s->s.get("sourceId")).toList()).size()!=sources.size()) throw invalid();
        for(int start=0;start<sources.size();start+=PAGE_SIZE) {
            var page=new LinkedHashMap<>(input);
            page.put("consolidatedExperiences",List.copyOf(sources.subList(start,Math.min(start+PAGE_SIZE,sources.size()))));
            page.put("sourceBatchPolicyVersion",VERSION);page.put("sourceBatchIndex",pages.size());
            page.put("sourceBatchCount",(sources.size()+PAGE_SIZE-1)/PAGE_SIZE);
            pages.add(Collections.unmodifiableMap(page));
        }
        return List.copyOf(pages);
    }
    public void validateReview(Map<String,Object> page,Map<String,Object> review) {
        if(!Boolean.TRUE.equals(review.get("evidenceSufficient")) || !(review.get("sourceReviews") instanceof List<?> rows)) throw invalid();
        var expected=new LinkedHashMap<String,Object>();sources(page).forEach(s->expected.put((String)s.get("sourceId"),s.get("sourceHash")));
        var seen=new HashSet<String>();
        for(Object item:rows) {
            if(!(item instanceof Map<?,?> row) || !(row.get("sourceId") instanceof String id)
                    || !seen.add(id) || !Objects.equals(expected.get(id),row.get("sourceHash"))) throw invalid();
            for(String field:List.of("conditions","effectiveSteps","acceptance","conflicts","limitations")) {
                if(!(row.get(field) instanceof List<?> values) || values.stream().anyMatch(v->!(v instanceof String s)||s.isBlank())) throw invalid();
                if(Set.of("conditions","effectiveSteps","acceptance").contains(field) && values.isEmpty()) throw invalid();
            }
            if(!(row.get("methodRelation") instanceof String relation)||relation.isBlank()) throw invalid();
        }
        if(!seen.equals(expected.keySet()) || CanonicalJson.stringify(review).length()>32_000) throw invalid();
    }
    public Map<String,Object> finalInput(Map<String,Object> archive,List<Map<String,Object>> reviews) {
        var pages=pages(archive);if(pages.size()!=reviews.size()) throw invalid();
        for(int i=0;i<pages.size();i++) validateReview(pages.get(i),reviews.get(i));
        var result=new LinkedHashMap<>(archive);
        result.put("consolidatedExperiences",sources(archive).stream().map(s->{var row=new LinkedHashMap<>(s);
            row.remove("acceptedTaskEpisode");return Collections.unmodifiableMap(row);}).toList());
        result.put("sourceBatchPolicyVersion",VERSION);result.put("reviewedSourceBatches",reviews);
        if(CanonicalJson.stringify(result).length()>2_000_000) throw new IllegalStateException("SKILL_SOURCE_BATCH_FINAL_BUDGET");
        return Collections.unmodifiableMap(result);
    }
    public List<Map<String,Object>> audit(Map<String,Object> archive,List<Map<String,Object>> reviews) {
        var pages=pages(archive);if(pages.size()!=reviews.size()) throw invalid();
        var audit=new ArrayList<Map<String,Object>>();
        for(int i=0;i<pages.size();i++) {validateReview(pages.get(i),reviews.get(i));
            audit.add(Map.of("batchIndex",i,"inputHash",CanonicalObjectHasher.sha256(pages.get(i)),"reviewHash",CanonicalObjectHasher.sha256(reviews.get(i))));}
        return List.copyOf(audit);
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> sources(Map<String,Object> input) {
        if(!(input.get("consolidatedExperiences") instanceof List<?> list)||list.isEmpty()||list.size()>ARCHIVE_LIMIT
                || list.stream().anyMatch(v->!(v instanceof Map<?,?> m)||!(m.get("sourceId") instanceof String id)||id.isBlank()
                    || !(m.get("sourceHash") instanceof String hash)||hash.isBlank())) throw new IllegalStateException("SKILL_EVOLUTION_SOURCE_SET_INVALID");
        return (List<Map<String,Object>>)(List<?>)list;
    }
    private static IllegalStateException invalid() {return new IllegalStateException("SKILL_SOURCE_BATCH_REVIEW_INVALID");}
}
