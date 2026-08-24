package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionRelatedSkillPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillEvolutionRelatedSkillServiceTest {
    ISkillCatalogRepository repository=mock(ISkillCatalogRepository.class);
    SkillFileSourcePort files=mock(SkillFileSourcePort.class);
    SkillCatalogQueryService catalog=mock(SkillCatalogQueryService.class);
    SkillEvolutionRelatedSkillService service=new SkillEvolutionRelatedSkillService(repository,files,catalog);

    SkillCatalogEntry entry(String id,int version,String description) {
        String content="SYNTHETIC_COMPLETE_BODY_"+id+"\n"+"步骤及边界\n".repeat(2000);
        var pkg=SkillPackageManifest.markdown("PROJECT","p",id,"订单排查",description,version,content);
        return new SkillCatalogEntry(1,id,"p","订单排查","PROJECT","",description,content,version,"ENABLED","fixture",null,null,
                "MANUAL","AUTO",true,true,null,"","",null,"a".repeat(64),version,"a".repeat(64),version,
                pkg.packageHash(),pkg.manifestJson(),"{}");
    }
    void ready(List<SkillCatalogEntry> entries) {
        when(repository.available()).thenReturn(true);
        when(repository.findAuthoringMetadata("PROJECT","p")).thenReturn(entries);
        when(repository.findAuthoringMetadata("GLOBAL","")).thenReturn(List.of());
        for(var entry:entries) {
            var full=new SkillCatalogViewMapper().toView(entry,true);
            when(catalog.getProjectSkill("p",entry.skillId())).thenReturn(full);
            when(catalog.listSkillArtifacts("p",entry.skillId(),entry.version(),entry.currentSkillHash(),entry.currentPackageHash(),"PROJECT"))
                    .thenReturn(List.of(Map.of("path","SKILL.md","content",entry.content()),
                            Map.of("path","resources/check.txt","content","完整资源".repeat(3000),"contentHash","b".repeat(64),"encoding","UTF8")));
        }
    }

    @Test void ranksMetadataBeforeReadingFiveWholePackagesAndResourcesWithoutCatalogBodyTraversal() {
        var entries=new ArrayList<SkillCatalogEntry>();for(int i=0;i<12;i++) entries.add(entry("skill-"+String.format("%02d",i),1,"订单排查故障方法"));
        ready(entries);
        var result=service.select("p",Map.of("normalizedUserGoal","订单排查故障方法"));
        assertEquals(5,result.size());assertEquals("skill-00",result.get(0).get("skillId"));
        for(int i=0;i<5;i++) {
            assertEquals(entries.get(i).content(),result.get(i).get("content"));
            var resources=(List<?>)result.get(i).get("relatedArtifacts");
            assertEquals("完整资源".repeat(3000),((Map<?,?>)resources.get(0)).get("content"));
            assertFalse(result.get(i).containsKey("markdown"));
        }
        verify(catalog,times(5)).getProjectSkill(eq("p"),anyString());
        verify(catalog,times(5)).listSkillArtifacts(anyString(),anyString(),anyInt(),anyString(),anyString(),anyString());
        verify(catalog,never()).listProjectSkills(anyString());verify(catalog,never()).listGlobalSkills();
        verify(repository,never()).findAll(anyString(),anyString(),anyBoolean());
        service.requireCurrent("p",result);
        verify(catalog,times(5)).getProjectSkill(eq("p"),anyString());
    }

    @Test void noMetadataOverlapLoadsNoBody() {
        ready(List.of());
        when(files.findAll()).thenReturn(List.of(new SkillFileDefinition("xyz","",Map.of("description","xyz"),"large body","","")));
        assertEquals(List.of(),service.select("p",Map.of("normalizedUserGoal","甲乙丙丁")));
        verifyNoInteractions(catalog);
    }

    Map<String,Object> acceptedBinding(String project,String run,String eventRun,String eventProject,String skill) {
        var payload=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("projectId",eventProject,"runId",eventRun,"skillNames",List.of(skill)));
        var accepted=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of(
                "format","accepted-task-episode-v1","projectId",project,
                "acceptance",Map.of("outcome","SUCCEEDED","projectId",project),
                "evidenceRunScope",List.of(Map.of("runId",run)),
                "trace",List.of(Map.of("eventType","RUNTIME_RESOURCES","status","SUCCEEDED","runId",eventRun,"payloadJson",payload))));
        return Map.of("outcome","SUCCEEDED","acceptedTaskEpisode",accepted,"sourceHash",cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(accepted));
    }

    @Test void actualTaskBindingSurvivesFiveHigherLexicalMatchesWithoutReadingExtraBodies() {
        var entries=new ArrayList<SkillCatalogEntry>();
        for(int i=0;i<8;i++) entries.add(entry("lexical-"+i,1,"订单排查故障方法"));
        entries.add(entry("actually-used",1,"两个独立职责的维护入口，单独检索和回退"));ready(entries);
        var result=service.select("p",Map.of("normalizedUserGoal","订单排查故障方法",
                "consolidatedExperiences",List.of(acceptedBinding("p","run","run","p","actually-used"))));
        assertEquals(5,result.size());assertEquals("actually-used",result.get(0).get("skillId"));
        assertEquals(true,result.get(0).get("relatedTaskBinding"));
        assertEquals(entries.get(8).content(),result.get(0).get("content"));
        verify(catalog,times(5)).getProjectSkill(eq("p"),anyString());
        service.requireCurrent("p",result);
    }

    @Test void untrustedTextTamperedSourceAndForeignRuntimeCannotManufactureBindingPriority() {
        var entries=new ArrayList<SkillCatalogEntry>();
        for(int i=0;i<6;i++) entries.add(entry("lexical-"+i,1,"订单排查故障方法"));
        entries.add(entry("target",1,"无关方法"));ready(entries);
        var valid=acceptedBinding("p","run","run","p","target");
        for(var source:List.of(
                Map.of("outcome","SUCCEEDED","acceptedTaskEpisode",valid.get("acceptedTaskEpisode"),"sourceHash","0".repeat(64)),
                acceptedBinding("other","run","run","other","target"),
                acceptedBinding("p","run","foreign-run","p","target"),
                acceptedBinding("p","run","run","other","target"))) {
            var result=service.select("p",Map.of("normalizedUserGoal","订单排查故障方法",
                    "skillNames",List.of("target"),"consolidatedExperiences",List.of(source)));
            assertEquals(5,result.size());assertFalse(result.stream().anyMatch(s->"target".equals(s.get("skillId"))));
            assertTrue(result.stream().allMatch(s->Boolean.FALSE.equals(s.get("relatedTaskBinding"))));
        }
        verify(catalog,never()).getProjectSkill("p","target");
    }

    @Test void revisionChangeRevokesFrozenReferenceWithoutLoadingNewBody() {
        var first=entry("skill",1,"订单排查");ready(List.of(first));
        var frozen=service.select("p",Map.of("normalizedUserGoal","订单排查"));
        when(repository.findAuthoringMetadata("PROJECT","p")).thenReturn(List.of(entry("skill",2,"订单排查")));
        assertEquals("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",assertThrows(IllegalStateException.class,()->service.requireCurrent("p",frozen)).getMessage());
        verify(catalog,times(1)).getProjectSkill("p","skill");
    }

    @Test void fileScopeAndDatabaseOverrideAreResolvedBeforeFullReads() {
        ready(List.of(entry("skill",1,"订单排查")));
        when(files.findAll()).thenReturn(List.of(
                new SkillFileDefinition("skill","",Map.of("scope","PROJECT","projectId","p","description","订单排查"),"shadowed body","",""),
                new SkillFileDefinition("foreign","",Map.of("scope","PROJECT","projectId","other","description","订单排查"),"foreign body","","")));
        var result=service.select("p",Map.of("normalizedUserGoal","订单排查"));
        assertEquals(1,result.size());assertEquals("DB",result.get(0).get("sourceType"));
        verify(catalog,never()).getProjectSkill(anyString(),eq("foreign"));
    }

    @Test void crossProjectDuplicateAndOversizeFrozenSetsAreRejected() {
        ready(List.of(entry("skill",1,"订单排查")));
        var result=service.select("p",Map.of("normalizedUserGoal","订单排查"));
        assertThrows(IllegalStateException.class,()->SkillEvolutionRelatedSkillPolicy.references(result,"other"));
        assertThrows(IllegalStateException.class,()->SkillEvolutionRelatedSkillPolicy.references(Collections.nCopies(2,result.get(0)),"p"));
        assertThrows(IllegalStateException.class,()->SkillEvolutionRelatedSkillPolicy.references(Collections.nCopies(6,result.get(0)),"p"));
    }
}
