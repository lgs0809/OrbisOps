package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillApplicabilityTest {
    final SkillRuntimeSelectionSettings settings=new SkillRuntimeSelectionSettings(20,3,3,.9,.99,.42);
    final SkillRuntimeCandidate skill=new SkillRuntimeCandidate("pool","p","PROJECT","Redis 连接池","借用排查",1,"h","package","m",Map.of(),"SKILL.md","ACTIVE","AUTO",10,
            new SkillRoutingProfile("GENERAL","","连接池等待",List.of("Redis 连接池耗尽"),List.of("删除生产数据"),List.of("Redis")));
    final String paraphrase="缓存客户端借不到空闲会话，工作线程全在等资源";
    SkillApplicabilityDecision match(String query){return new SkillApplicabilityDecision(SkillApplicabilityDecision.Verdict.MATCH,List.of(0),query);}
    SkillHybridRetrieval pipeline(SkillApplicabilityPort port){return new SkillHybridRetrieval((q,c)->Map.of("pool",1D),(q,c)->Map.of(),settings,port);}
    @Test void semanticAssessmentCanSelectAParaphraseWithoutSharedSurfaceWords() {
        var decision=pipeline((p,q,c)->{assertEquals("p",p);assertEquals(List.of(skill),c);return Map.of("pool",match(q));})
                .select("p",paraphrase,List.of(skill),Set.of(),3);
        assertEquals("pool",decision.selected().get(0).candidate().skillId());
    }
    @Test void noMatchAndNeedInfoRejectEvenWhenLexicalAndVectorRanksAreHigh() {
        for(var verdict:List.of(SkillApplicabilityDecision.Verdict.NO_MATCH,SkillApplicabilityDecision.Verdict.NEED_INFO)) {
            var result=pipeline((p,q,c)->Map.of("pool",new SkillApplicabilityDecision(verdict,List.of(),"")))
                    .select("p","Redis 连接池耗尽",List.of(skill),Set.of(),3);
            assertTrue(result.selected().isEmpty());
        }
    }
    @Test void invalidIdentityOrInventedGroundsCannotPromoteAResult() {
        var invalid=List.of(Map.of("foreign",match(paraphrase)),
                Map.of("pool",new SkillApplicabilityDecision(SkillApplicabilityDecision.Verdict.MATCH,List.of(9),paraphrase)),
                Map.of("pool",match("用户没有提供的环境事实")),
                Map.of("pool",new SkillApplicabilityDecision(SkillApplicabilityDecision.Verdict.MATCH,List.of(),paraphrase)));
        for(var values:invalid) assertTrue(pipeline((p,q,c)->values).select("p",paraphrase,List.of(skill),Set.of(),3).selected().isEmpty());
    }
    @Test void unavailableAssessmentKeepsConservativeFallbackAndExplicitBindings() {
        var unavailable=pipeline((p,q,c)->{throw new IllegalStateException("MODEL_UNAVAILABLE");});
        assertTrue(unavailable.select("p",paraphrase,List.of(skill),Set.of(),3).selected().isEmpty());
        assertEquals(1,unavailable.select("p","Redis 连接池耗尽",List.of(skill),Set.of(),3).selected().size());
        assertEquals(1,unavailable.select("p",paraphrase,List.of(skill),Set.of("pool"),3).selected().size());
        assertTrue(pipeline((p,q,c)->Map.of("pool",match(q))).select("p","删除生产数据",List.of(skill),Set.of(),3).selected().isEmpty());
    }
    @Test void bothOrdinaryAndFrozenQueriesUseAssessmentAndRecheckAuthorizationAfterIt() {
        var catalog=mock(SkillCatalogPort.class);
        var view=new SkillRuntimeSelectionViewMapper(new SkillBoundReferenceMapper()).result(new SkillRuntimeSelection(
                List.of(new SkillRuntimeSelection.RankedSkill(skill,1,false)),List.of(),List.of(),1)).catalogRefs().get(0);
        var entry=new SkillCatalogSnapshot(skill,view);
        when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of(entry));
        var port=mock(SkillApplicabilityPort.class);
        when(port.assess(eq("p"),eq(paraphrase),anyList())).thenReturn(Map.of("pool",match(paraphrase)));
        var query=new SelectRuntimeSkillsQuery(catalog,(q,c)->Map.of("pool",1D),(q,c)->Map.of(),settings,(p,c)->c,port);
        assertEquals(1,query.select(new SelectRuntimeSkillsQuery.Request("p","a",List.of(),paraphrase,3)).selectedCount());
        assertEquals(1,query.selectFrozen(new SelectRuntimeSkillsQuery.FrozenRequest("p",List.of(view),paraphrase,3)).selectedCount());
        verify(port,times(2)).assess(eq("p"),eq(paraphrase),anyList());
        when(port.assess(anyString(),anyString(),anyList())).thenAnswer(invocation->{
            when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of());return Map.of("pool",match(paraphrase));
        });
        assertEquals(0,query.select(new SelectRuntimeSkillsQuery.Request("p","a",List.of(),paraphrase,3)).selectedCount());
    }
}
