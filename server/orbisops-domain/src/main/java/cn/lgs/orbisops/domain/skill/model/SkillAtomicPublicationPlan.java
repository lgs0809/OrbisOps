package cn.lgs.orbisops.domain.skill.model;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy;
import java.util.*;

/** One authored replacement, with server-derived target identities and qualified branch sources. */
public record SkillAtomicPublicationPlan(String operation, String projectId, List<Source> sources, List<Target> targets) {
    public record Source(String skillId, int version, String skillHash, String packageHash, String catalogFence) { }
    public record Target(String key, String skillId, String name, Set<String> sourceIds,
                         List<Object> changes, List<Object> artifacts) {
        public Target {
            // Set.copyOf iteration changes between JVMs; persisted plans must survive a restart byte-for-byte.
            sourceIds=Collections.unmodifiableSet(new TreeSet<>(sourceIds));
            changes=List.copyOf(changes);artifacts=List.copyOf(artifacts);
        }
    }

    public static boolean supports(String type) { return Set.of("SPLIT_SKILL", "MERGE_SKILLS").contains(type); }

    public static SkillAtomicPublicationPlan from(SkillPatchCandidate candidate, Map<String,Object> input) {
        if (!supports(candidate.patchType()) || !candidate.targetSkillId().isBlank()) fail("SHAPE");
        var replacements=candidate.changes().stream().filter(x -> x instanceof Map<?,?> m
                && "lifecycleReplacement".equals(m.get("section"))).toList();
        if (replacements.size()!=1) fail("PLAN_REQUIRED");
        var change=object(replacements.get(0));
        if (!"upsert".equals(change.get("operation")) || !"runtime".equals(change.get("key"))) fail("SHAPE");
        var plan=object(change.get("value"));
        var ids=strings(plan.get("sourceSkillIds"));
        var references=SkillEvolutionRelatedSkillPolicy.references(input.get("relatedSkills"),candidate.projectId());
        var sources=new ArrayList<Source>();
        for (String id:ids.stream().sorted().toList()) {
            var matched=references.stream().filter(s -> id.equals(s.get("skillId")) && "PROJECT".equals(s.get("scope"))
                    && "DB".equals(s.get("sourceType"))).toList();
            if (matched.size()!=1) fail("SOURCE_OUTSIDE_FROZEN_SET");
            var source=matched.get(0);
            sources.add(new Source(id,((Number)source.get("currentVersion")).intValue(),text(source.get("currentSkillHash")),
                    text(source.get("currentPackageHash")),text(source.get("catalogFence"))));
        }
        var accepted=new LinkedHashMap<String,String>();
        for (Object item:list(input.get("consolidatedExperiences"))) {
            var sample=object(item);String id=text(sample.get("sourceId")),episode=text(sample.get("taskEpisodeId"));
            if (id.isBlank() || episode.isBlank() || accepted.put(id,episode)!=null) fail("SOURCE_IDENTITY");
        }
        if (accepted.size()<6 || new HashSet<>(accepted.values()).size()!=accepted.size()) fail("SIX_INDEPENDENT_SOURCES_REQUIRED");
        var targets=new ArrayList<Target>();var keys=new HashSet<String>();var used=new HashSet<String>();
        for (Object item:list(plan.get("targets"))) {
            var target=object(item);String key=text(target.get("key")),name=text(target.get("name"));
            if (!key.matches("[a-z][a-z0-9_-]{0,63}") || !keys.add(key) || name.isBlank() || name.length()>128) fail("TARGET_IDENTITY");
            var qualified=strings(target.get("sourceIds"));
            requireSources(qualified,accepted);used.addAll(qualified);
            var changes=list(target.get("changes"));var artifacts=list(target.get("artifacts"));
            if (changes.isEmpty() || changes.stream().anyMatch(x -> "lifecycleReplacement".equals(object(x).get("section")))) fail("NESTED_PLAN");
            String id="evolved-"+CanonicalObjectHasher.sha256(Map.of("candidateHash",candidate.candidateHash(),"branch",key)).substring(0,24);
            targets.add(new Target(key,id,name,Set.copyOf(qualified),changes,artifacts));
        }
        if (used.size()<6) fail("SIX_INDEPENDENT_SOURCES_REQUIRED");
        // Published historic sources belong to the method that actually used them.
        // Current sources can support a new boundary, subject to the existing content review.
        var qualifiedByMethod=new HashMap<String,Set<String>>();
        if(input.containsKey("sourcePortfolioPolicyVersion")) {
            var primary=strings(input.get("primarySourceIds"));
            for(Object item:list(input.get("relatedSkillSourceGroups"))) {
                var group=object(item);var qualified=new HashSet<>(strings(group.get("sourceIds")));qualified.addAll(primary);
                if(qualifiedByMethod.put(text(group.get("skillId")),qualified)!=null) fail("SOURCE_IDENTITY");
            }
        }
        if ("SPLIT_SKILL".equals(candidate.patchType())) {
            if (sources.size()!=1 || targets.size()<2 || targets.size()>5) fail("SPLIT_SHAPE");
            if(input.containsKey("sourcePortfolioPolicyVersion") && !qualifiedByMethod.getOrDefault(sources.get(0).skillId(),Set.of()).containsAll(used))
                fail("SOURCE_METHOD_MISMATCH");
        } else {
            if (sources.size()<2 || sources.size()>5 || targets.size()!=1) fail("MERGE_SHAPE");
            var groups=new HashSet<String>();
            for (Object item:list(plan.get("sourceGroups"))) {
                var group=object(item);String id=text(group.get("skillId"));
                if (!ids.contains(id) || !groups.add(id)) fail("MERGE_SOURCE_GROUP");
                var qualified=strings(group.get("sourceIds"));requireSources(qualified,accepted);
                if(input.containsKey("sourcePortfolioPolicyVersion") && !qualifiedByMethod.getOrDefault(id,Set.of()).containsAll(qualified))
                    fail("SOURCE_METHOD_MISMATCH");
                if (!used.containsAll(qualified)) fail("MERGE_SOURCE_COVERAGE");
            }
            if (!groups.equals(ids)) fail("MERGE_SOURCE_GROUP");
        }
        return new SkillAtomicPublicationPlan(candidate.patchType(),candidate.projectId(),List.copyOf(sources),List.copyOf(targets));
    }

    public SkillPatchCandidate branch(SkillPatchCandidate parent,Target target) {
        return new SkillPatchCandidate(parent.candidateId(),parent.candidateHash(),parent.sourceRunId(),parent.sourceType(),
                projectId,parent.agentId(),"PROJECT","","CREATE_SKILL",parent.riskLevel(),0,"",parent.contextBundleHash(),
                parent.evidenceRefs(),target.changes(),target.artifacts(),List.of(),parent.status(),parent.reasonCode(),parent.createdAt(),parent.updatedAt());
    }
    private static void requireSources(Set<String> ids,Map<String,String> accepted) {
        if (ids.size()<3 || !accepted.keySet().containsAll(ids)) fail("THREE_BRANCH_SOURCES_REQUIRED");
    }
    private static Set<String> strings(Object value) {
        var result=new LinkedHashSet<String>();
        for (Object item:list(value)) if (!(item instanceof String id) || id.isBlank() || !result.add(id)) fail("DUPLICATE_OR_INVALID_ID");
        return result;
    }
    private static List<Object> list(Object value) {
        if (!(value instanceof List<?>)) fail("ARRAY_REQUIRED");
        return new ArrayList<>((List<?>)value);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?>)) fail("OBJECT_REQUIRED");
        return (Map<String,Object>)value;
    }
    private static String text(Object value) { return value instanceof String s?s:""; }
    private static void fail(String reason) { throw new IllegalArgumentException("SKILL_ATOMIC_"+reason); }
}
