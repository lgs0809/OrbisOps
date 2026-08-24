package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillAtomicPublicationPlanTest {
    @Test void splitIdentitiesAreDeterministicAndEachBranchKeepsItsOwnMethod() {
        var candidate=candidate("SPLIT_SKILL",replacement(false));
        var first=SkillAtomicPublicationPlan.from(candidate,input());
        assertEquals(first,SkillAtomicPublicationPlan.from(candidate,input()));
        assertEquals(2,first.targets().stream().map(SkillAtomicPublicationPlan.Target::skillId).distinct().count());
        assertTrue(first.targets().get(0).skillId().matches("evolved-[0-9a-f]{24}"));
        var branch=first.branch(candidate,first.targets().get(0));
        assertEquals("CREATE_SKILL",branch.patchType());assertEquals("",branch.targetSkillId());
        assertEquals(first.targets().get(0).changes(),branch.changes());
    }
    @Test void independentEpisodesAndAllSixCoveredSourcesAreRequired() {
        var input=new LinkedHashMap<>(input());
        input.put("consolidatedExperiences",List.of(sample(1),sample(2),sample(3)));
        assertThrows(IllegalArgumentException.class,()->SkillAtomicPublicationPlan.from(candidate("SPLIT_SKILL",replacement(false)),input));
        var samples=new ArrayList<>(samples());samples.set(5,Map.of("sourceId","s6","taskEpisodeId","e1"));
        input.put("consolidatedExperiences",samples);
        assertThrows(IllegalArgumentException.class,()->SkillAtomicPublicationPlan.from(candidate("SPLIT_SKILL",replacement(false)),input));
        var plan=new LinkedHashMap<>(replacement(false));plan.put("targets",List.of(target("one",List.of("s1","s2","s3")),target("two",List.of("s1","s2","s3"))));
        assertThrows(IllegalArgumentException.class,()->SkillAtomicPublicationPlan.from(candidate("SPLIT_SKILL",plan),input()));
    }
    @Test void unknownSourceUnqualifiedBranchAndDuplicateTargetCannotPass() {
        for(var invalid:List.of(
                Map.of("sourceSkillIds",List.of("foreign"),"targets",replacement(false).get("targets")),
                Map.of("sourceSkillIds",List.of("old-a"),"targets",List.of(target("one",List.of("s1","s2")),target("two",List.of("s3","s4","s5","s6")))),
                Map.of("sourceSkillIds",List.of("old-a"),"targets",List.of(target("one",List.of("s1","s2","s3")),target("one",List.of("s4","s5","s6")))),
                Map.of("sourceSkillIds",List.of("old-a"),"targets",List.of(target("one",List.of("s1","s2","unknown")),target("two",List.of("s4","s5","s6"))))))
            assertThrows(IllegalArgumentException.class,()->SkillAtomicPublicationPlan.from(candidate("SPLIT_SKILL",invalid),input()));
    }
    @Test void mergeRequiresThreeSourcesForEachOriginalAndCoverageByMergedMethod() {
        assertEquals(1,SkillAtomicPublicationPlan.from(candidate("MERGE_SKILLS",replacement(true)),input()).targets().size());
        var plan=new LinkedHashMap<>(replacement(true));plan.put("sourceGroups",List.of(Map.of("skillId","old-a","sourceIds",List.of("s1","s2","s3"))));
        assertThrows(IllegalArgumentException.class,()->SkillAtomicPublicationPlan.from(candidate("MERGE_SKILLS",plan),input()));
        plan.put("sourceGroups",List.of(Map.of("skillId","old-a","sourceIds",List.of("s1","s2","s3")),Map.of("skillId","old-b","sourceIds",List.of("s4","s5"))));
        assertThrows(IllegalArgumentException.class,()->SkillAtomicPublicationPlan.from(candidate("MERGE_SKILLS",plan),input()));
    }
    @Test void historicSourcesCannotBeBorrowedFromAnUnrelatedMethodToSatisfySplitOrMerge() {
        var input=new LinkedHashMap<>(input());
        input.put("sourcePortfolioPolicyVersion","published-method-source-portfolio-v1");
        input.put("primarySourceIds",List.of("s1","s2","s3"));
        input.put("relatedSkillSourceGroups",List.of(Map.of("skillId","old-a","sourceIds",List.of()),
                Map.of("skillId","old-b","sourceIds",List.of("s4","s5","s6"))));
        assertEquals("SKILL_ATOMIC_SOURCE_METHOD_MISMATCH",assertThrows(IllegalArgumentException.class,
                ()->SkillAtomicPublicationPlan.from(candidate("SPLIT_SKILL",replacement(false)),input)).getMessage());
        assertEquals(1,SkillAtomicPublicationPlan.from(candidate("MERGE_SKILLS",replacement(true)),input).targets().size());
        input.put("relatedSkillSourceGroups",List.of(Map.of("skillId","old-a","sourceIds",List.of("s4","s5","s6")),
                Map.of("skillId","old-b","sourceIds",List.of())));
        assertEquals("SKILL_ATOMIC_SOURCE_METHOD_MISMATCH",assertThrows(IllegalArgumentException.class,
                ()->SkillAtomicPublicationPlan.from(candidate("MERGE_SKILLS",replacement(true)),input)).getMessage());
    }
    private static SkillPatchCandidate candidate(String type,Map<String,?> replacement) {
        return new SkillPatchCandidate("candidate","a".repeat(64),"run","EVOLVER","p","agent","PROJECT","",type,SkillPatchRiskLevel.LOW,0,"","",
                List.of(Map.of("id","receipt")),List.of(Map.of("section","lifecycleReplacement","operation","upsert","key","runtime","value",replacement)),
                List.of(),List.of(),SkillPatchCandidateStatus.CANDIDATE,"",null,null);
    }
    private static Map<String,Object> input() {return Map.of("relatedSkills",List.of(reference("old-a"),reference("old-b")),"consolidatedExperiences",samples());}
    private static List<Map<String,Object>> samples() {return java.util.stream.IntStream.rangeClosed(1,6).mapToObj(SkillAtomicPublicationPlanTest::sample).toList();}
    private static Map<String,Object> sample(int i) {return Map.of("sourceId","s"+i,"taskEpisodeId","e"+i);}
    private static Map<String,Object> reference(String id) {
        var m=new LinkedHashMap<String,Object>();m.put("skillId",id);m.put("scope","PROJECT");m.put("projectId","p");m.put("sourceType","DB");m.put("currentVersion",1);
        m.put("content","synthetic method");m.put("relatedArtifacts",List.of());m.put("catalogFence","a".repeat(64));m.put("currentSkillHash","b".repeat(64));m.put("currentPackageHash","c".repeat(64));return m;
    }
    private static Map<String,Object> target(String key,List<String> ids) {
        return Map.of("key",key,"name",key,"sourceIds",ids,"changes",List.of(Map.of("section","diagnosticRecipe","operation","upsert","key","runtime","value",Map.of("step","read"))),"artifacts",List.of());
    }
    private static Map<String,Object> replacement(boolean merge) {
        if(!merge) return Map.of("sourceSkillIds",List.of("old-a"),"targets",List.of(target("one",List.of("s1","s2","s3")),target("two",List.of("s4","s5","s6"))));
        return Map.of("sourceSkillIds",List.of("old-a","old-b"),"targets",List.of(target("merged",List.of("s1","s2","s3","s4","s5","s6"))),
                "sourceGroups",List.of(Map.of("skillId","old-a","sourceIds",List.of("s1","s2","s3")),Map.of("skillId","old-b","sourceIds",List.of("s4","s5","s6"))));
    }
}
