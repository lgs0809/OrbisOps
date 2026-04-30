package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillHybridRetrievalTest {
    private final SkillRuntimeSelectionSettings settings=new SkillRuntimeSelectionSettings(20,3,3,0.9,0.99,0.42,20,0.12,0.65,true,20,0.35);
    @ParameterizedTest @ValueSource(ints={100,1000,10000})
    void independentRecallCapacityWithSyntheticCatalog(int count) {
        var catalog=new ArrayList<SkillRuntimeCandidate>();
        for(int i=0;i<count;i++) catalog.add(candidate("s-"+String.format("%05d",i),"p","PROJECT","training signal "+i));
        String last=catalog.get(count-1).skillId();var semanticCalls=new AtomicInteger();var rerankCalls=new AtomicInteger();
        SkillSemanticScorePort semantic=(q,allowed)->{ semanticCalls.incrementAndGet();assertEquals(count,allowed.size());return Map.of(last,10D); };
        SkillRerankPort reranker=(q,window)->{rerankCalls.incrementAndGet();assertEquals(20,window.size());assertTrue(window.stream().anyMatch(c->c.skillId().equals(last)));return Map.of();};
        long start=System.nanoTime();var result=new SkillHybridRetrieval(semantic,reranker,settings).select("p","unknown query",catalog,Set.of(),3);
        assertEquals(1,semanticCalls.get());assertEquals(1,rerankCalls.get());assertEquals(20,result.catalog().size());assertTrue(result.selected().isEmpty());
        assertTrue(result.catalog().stream().anyMatch(r->r.candidate().skillId().equals(last)));
        System.out.println("OPS07_SYNTHETIC_CAPACITY count="+count+" elapsedMs="+(System.nanoTime()-start)/1_000_000+" modelQuality=NOT_TESTED");
    }
    @Test void rrfUsesRanksAndRejectsInvalidScorerAsAWhole() {
        var a=candidate("a","p","PROJECT","inspect logs");var b=candidate("b","p","PROJECT","inspect metrics");
        var fused=new SkillHybridSelectionPolicy().fuse(List.of(a,b),List.of(b),List.of());
        assertEquals("b",fused.get(0).candidate().skillId());assertEquals(1D/62+1D/61,fused.get(0).score(),1e-10);
        var calls=new AtomicInteger();var actual=new SkillHybridRetrieval((q,c)->Map.of("b",10D),(q,c)->{ calls.incrementAndGet();return Map.of("invented",999D);},settings)
                .select("p","inspect metrics",List.of(a,b),Set.of(),3);
        assertEquals(1,calls.get());assertEquals("b",actual.catalog().get(0).candidate().skillId());
    }
    @Test void currentAuthorizationFiltersBothArmsAndRechecksAfterRerank() {
        var catalog=mock(SkillCatalogPort.class);var a=candidate("a","p","PROJECT","inspect logs");var b=candidate("b","","GLOBAL","inspect logs");
        when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of(snapshot(a)),List.of());
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of(snapshot(b))); // GLOBAL is ungranted.
        var query=new SelectRuntimeSkillsQuery(catalog,(q,allowed)->{assertEquals(List.of(a),allowed);return Map.of("a",1D);},settings);
        assertThrows(SecurityException.class,()->query.select(new SelectRuntimeSkillsQuery.Request("p","agent",List.of("a"),"inspect logs",3)));
    }
    @Test void frozenReferencesRequireTrustedScopeAndRespectRevocationWithoutRebindingVersion() {
        var catalog=mock(SkillCatalogPort.class);var old=candidate("a","p","PROJECT","inspect logs");
        when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of(snapshot(old)));
        var query=new SelectRuntimeSkillsQuery(catalog,(q,c)->Map.of(),settings);
        var ref=snapshot(old).view();
        assertEquals(1,query.selectFrozen(new SelectRuntimeSkillsQuery.FrozenRequest("p",List.of(ref),"inspect logs",3)).selectedCount());
        when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of());
        assertEquals(0,query.selectFrozen(new SelectRuntimeSkillsQuery.FrozenRequest("p",List.of(ref),"inspect logs",3)).selectedCount());
        assertThrows(SecurityException.class,()->query.selectFrozen(new SelectRuntimeSkillsQuery.FrozenRequest("another-project",List.of(ref),"inspect logs",3)));
    }
    @Test void explicitNamesCannotBuyMoreThanThreeSlotsOrOverrideNegativeBoundary() {
        var c=candidate("a","p","PROJECT","inspect logs");
        var result=new SkillHybridRetrieval((q,a)->Map.of(),(q,a)->Map.of(),settings).select("p","delete production",List.of(c),Set.of("a"),3);
        assertTrue(result.selected().isEmpty());
        var query=new SelectRuntimeSkillsQuery(mock(SkillCatalogPort.class),(q,a)->Map.of(),settings);
        assertThrows(IllegalArgumentException.class,()->query.select(new SelectRuntimeSkillsQuery.Request("p","agent",List.of("a","b","c","d"),"inspect logs",20)));
    }
    @Test void aSharedTopicKeywordCannotAuthorizeAnUnrelatedMethodEvenWhenRankedFirst() {
        var c=new SkillRuntimeCandidate("static-audit","p","PROJECT","静态页面配置审计","核对资源配置",1,
                "h","p","m",Map.of(),"SKILL.md","ACTIVE","AUTO",10,
                new SkillRoutingProfile("GENERAL","","配置审计",List.of("静态页面 配置审计"),List.of(),List.of("静态页面")));
        var pipeline=new SkillHybridRetrieval((q,a)->Map.of(c.skillId(),99D),(q,a)->Map.of(c.skillId(),99D),settings);
        var unrelated=pipeline.select("p","只调整静态页面字体颜色，不调查任何服务器。",List.of(c),Set.of(),3);
        assertEquals(1,unrelated.catalog().size());
        assertTrue(unrelated.selected().isEmpty());
        assertEquals("SKILL_APPLICABILITY_NEED_INFO",unrelated.suppressed().get(0).reasonCode());
        assertEquals(1,pipeline.select("p","请进行静态页面配置审计",List.of(c),Set.of(),3).selected().size());
        assertEquals(1,pipeline.select("p","检查这个对象",List.of(c),Set.of(c.skillId()),3).selected().size());
    }
    private static SkillCatalogSnapshot snapshot(SkillRuntimeCandidate c) {
        var view=new SkillRuntimeSelectionViewMapper(new SkillBoundReferenceMapper()).result(new SkillRuntimeSelection(List.of(new SkillRuntimeSelection.RankedSkill(c,1,false)),List.of(),List.of(),1)).catalogRefs().get(0);
        return new SkillCatalogSnapshot(c,view);
    }
    private static SkillRuntimeCandidate candidate(String id,String project,String scope,String use) {
        return new SkillRuntimeCandidate(id,project,scope,id,use,1,"h-"+id,"p-"+id,"m-"+id,Map.of(),"SKILL.md","ACTIVE","AUTO",10,
                new SkillRoutingProfile("GENERAL","",use,List.of(use),List.of("delete production"),List.of(id)));
    }
}
