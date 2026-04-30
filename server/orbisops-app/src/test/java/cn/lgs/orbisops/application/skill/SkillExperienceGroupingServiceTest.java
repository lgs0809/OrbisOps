package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillExperienceGroupingServiceTest {
    private final SkillExperienceGroupingStore store=mock(SkillExperienceGroupingStore.class);
    private final SkillExperienceGroupingModelPort model=mock(SkillExperienceGroupingModelPort.class);
    private final SkillExperienceGroupingIndexPort index=mock(SkillExperienceGroupingIndexPort.class);
    private final SkillExperienceEmbeddingPort embedding=mock(SkillExperienceEmbeddingPort.class);
    private final SkillExperiencePort experience=mock(SkillExperiencePort.class);
    private final SkillExperienceGroupingService service=new SkillExperienceGroupingService(store,model,index,embedding,experience);
    private final SkillEvolutionJobSnapshot claim=new SkillEvolutionJobSnapshot(1,"job","run","session","project","agent","BACKGROUND",
            SkillEvolutionJobStatus.RUNNING,0,null,"",null,null,"source","lease",1,Long.MAX_VALUE);
    private final Method method=new Method("验证版本",List.of("只读访问"),List.of("查询实际版本"),List.of("与目标比对"),List.of("资源查询"));
    private final Fact fact=new Fact("source","source-hash","episode",1,method);
    private final Group group=new Group("eg-group","project",1,"group-hash",method,List.of(fact));
    private final SkillEvolutionPipelineRequest request=new SkillEvolutionPipelineRequest("project","agent","run","session","BACKGROUND",
            new SkillEvolutionInputSummary(List.of(),List.of(),"验证版本","已验收",true,true,true,List.of(),"context","{\"accepted\":true}","source-hash"),null,claim);
    private final SkillExperienceRecordResult observed=new SkillExperienceRecordResult(
            new SkillExperienceObservation("episode","observation","legacy","project","agent","run","session","DIAGNOSTIC",null,"",List.of(),"","SUCCESS",List.of(),"已验收",1,1),null,false);

    private void cachedFact() {
        when(store.fact(claim,"source-hash")).thenReturn(Optional.of(fact));
        when(embedding.modelIdentity()).thenReturn("fixture-embedding");
        when(embedding.embed(anyString(),anyBoolean())).thenReturn(new float[]{1});
    }
    @Test void indexFailureResumesCommittedGroupWithoutReextractingOrDoubleCommitting() {
        cachedFact();
        when(store.assigned(claim,"source-hash")).thenReturn(Optional.empty(),Optional.of(group));
        var decision=new Decision("CREATE","","new method","{}");
        when(model.decide(fact,List.of())).thenReturn(decision);
        when(store.commit(claim,"source-hash",observed,decision,List.of())).thenReturn(group);
        doThrow(new IllegalStateException("POSTGRES_UNAVAILABLE")).doNothing().when(index).put(eq(group),eq("fixture-embedding"),any());
        assertEquals("SKILL_GROUPING_DEFERRED",assertThrows(SkillGroupingDeferredException.class,()->service.group(request,observed)).getMessage());
        verifyNoInteractions(experience);
        var counts=new SkillExperienceClusterSnapshot(1,1,1,"ACCUMULATING",1);
        when(experience.cluster("project","agent","eg-group")).thenReturn(counts);
        var recovered=service.group(request,observed);
        assertEquals("eg-group",recovered.observation().clusterKey());assertEquals(counts,recovered.cluster());
        verify(model,never()).extract(anyString());verify(store,times(1)).commit(any(),anyString(),any(),any(),anyList());
        verify(index,times(2)).put(eq(group),eq("fixture-embedding"),any());
    }
    @Test void unavailableRecallNeverFallsBackToCreatingAnotherGroup() {
        cachedFact();when(index.search(anyString(),anyString(),anyString(),any())).thenThrow(new IllegalStateException("POSTGRES_UNAVAILABLE"));
        assertThrows(SkillGroupingDeferredException.class,()->service.group(request,observed));
        verifyNoInteractions(model);verify(store,never()).commit(any(),anyString(),any(),any(),anyList());
    }
    @Test void reviewPersistsPrivateFactAndDefersWithoutPublishingOrWaitingForUser() {
        cachedFact();var decision=new Decision("REVIEW","","insufficient evidence","{}");when(model.decide(fact,List.of())).thenReturn(decision);
        assertThrows(SkillGroupingDeferredException.class,()->service.group(request,observed));
        verify(store).defer(claim,"source-hash",decision);verify(store,never()).commit(any(),anyString(),any(),any(),anyList());
        verifyNoInteractions(experience);
    }
    @Test void modelCannotAppendToUnrecalledGroup() {
        cachedFact();when(model.decide(fact,List.of())).thenReturn(new Decision("APPEND","unseen","model guessed id","{}"));
        var error=assertThrows(SkillGroupingDeferredException.class,()->service.group(request,observed));
        assertEquals("SKILL_GROUPING_UNKNOWN_GROUP",error.getCause().getMessage());
        verify(store,never()).commit(any(),anyString(),any(),any(),anyList());
    }
    @Test void recalledGroupFromAnotherProjectCannotReachModel() {
        cachedFact();when(index.search(anyString(),anyString(),anyString(),any())).thenReturn(List.of(group.reference()));
        when(store.current("project","eg-group")).thenReturn(Optional.of(new Group("eg-group","other-project",1,"hash",method,List.of(fact))));
        assertEquals("SKILL_GROUPING_PROJECT_MISMATCH",assertThrows(SkillGroupingDeferredException.class,()->service.group(request,observed)).getCause().getMessage());
        verifyNoInteractions(model);verify(index,never()).put(any(),anyString(),any());
    }
    @Test void revokedSourceDoesNotBecomeAnIndefinitelyRetriedNetworkFailure() {
        when(store.fact(claim,"source-hash")).thenThrow(new IllegalStateException("SKILL_EVOLUTION_SOURCE_REVOKED"));
        assertEquals("SKILL_EVOLUTION_SOURCE_REVOKED",assertThrows(IllegalStateException.class,()->service.group(request,observed)).getMessage());
        verifyNoInteractions(model,index,embedding,experience);
    }
    @Test void committedGroupReusesIndividualFactsInsteadOfEncodingTheGrowingAggregate() {
        cachedFact();when(store.assigned(claim,"source-hash")).thenReturn(Optional.of(group));
        when(index.factEmbedding("project",fact,"fixture-embedding")).thenReturn(Optional.of(new float[]{1}));
        service.group(request,observed);
        verify(embedding,never()).embed(anyString(),anyBoolean());
        verify(index).put(eq(group),eq("fixture-embedding"),org.mockito.AdditionalMatchers.aryEq(new float[]{1}));
    }
    @Test void centroidsAreNormalizedAndInvalidInputsFailClosed() {
        float[] mean=SkillExperienceVectorProjection.centroid(List.of(new float[]{1,0},new float[]{0,1}));
        assertEquals(Math.sqrt(.5),mean[0],1e-6);assertEquals(mean[0],mean[1]);
        assertArrayEquals(new float[]{1,0},SkillExperienceVectorProjection.centroid(List.of(new float[]{1,0},new float[]{-1,0})));
        assertThrows(IllegalArgumentException.class,()->SkillExperienceVectorProjection.centroid(List.of(new float[]{0,0})));
        assertThrows(IllegalArgumentException.class,()->SkillExperienceVectorProjection.centroid(List.of(new float[]{1},new float[]{1,0})));
    }
    @Test void backgroundDiagnosticsRetainCauseTypesWithoutRemotePayloadOrSecrets() {
        var error=new SkillGroupingDeferredException(new IllegalStateException("SKILL_GROUPING_METHOD_INVALID",
                new java.net.SocketTimeoutException("secret-token and remote response must never be recorded")));
        assertEquals("SKILL_GROUPING_DEFERRED: IllegalStateException[SKILL_GROUPING_METHOD_INVALID] -> SocketTimeoutException",error.diagnostic());
        assertFalse(error.diagnostic().contains("secret-token"));
    }
}
