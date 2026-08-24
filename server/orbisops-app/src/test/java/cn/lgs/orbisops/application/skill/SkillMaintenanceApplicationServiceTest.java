package cn.lgs.orbisops.application.skill;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SkillMaintenanceApplicationServiceTest {
    final SkillMaintenancePort store=mock(SkillMaintenancePort.class);
    final SkillCatalogQueryService catalog=mock(SkillCatalogQueryService.class);
    final SkillManagementUseCase management=mock(SkillManagementUseCase.class);
    final SkillCompressionReviewPort review=mock(SkillCompressionReviewPort.class);
    final SkillTransactionPort tx=new SkillTransactionPort(){public <T>T required(java.util.function.Supplier<T> action){return action.get();}};
    final SkillMaintenanceApplicationService service=new SkillMaintenanceApplicationService(store,catalog,management,tx,review);
    SkillMaintenancePort.Claim prepare(boolean automatic) {
        var s=new SkillMaintenancePort.Subject(1,"project","method",2,"hash","package","Read only.\n\nRead only.",Instant.EPOCH,null,5,0,automatic);
        var claim=new SkillMaintenancePort.Claim("check","lease",1,s);when(store.claim()).thenReturn(Optional.of(claim));
        when(catalog.getProjectSkill("project","method")).thenReturn(Map.of("currentVersion",2,"currentSkillHash","hash","currentPackageHash","package"));
        when(catalog.getSkillVersion("project","method",2,"hash","package","PROJECT")).thenReturn(Map.of("content",s.content()));
        when(catalog.listSkillArtifacts("project","method",2,"hash","package","PROJECT")).thenReturn(List.of(Map.of("path","resources/method.json","content","original")));
        when(review.propose(anyMap(),anyList())).thenReturn(Map.of("changes",List.of(Map.of("path","SKILL.md","content","Read only.","reason","duplicate rule")),"reason","equivalent"));
        return claim;
    }
    @Test void reviewedCompressionPreservesResourcesAndUsesCurrentLeaseAndCatalogCas() {
        var c=prepare(true);var receipt=Map.<String,Object>of("equivalent",true,"safe",true);
        when(review.review(anyMap(),anyList(),eq(Map.of("SKILL.md","Read only.")))).thenReturn(receipt);
        when(management.publishEvolvedProjectSkillOutcome(eq("project"),eq("method"),anyMap(),eq(2),eq("hash"),anyString()))
            .thenReturn(new SkillPublicationOutcome(true,"PUBLISHED","method",3,"new-hash"));
        service.checkNext();
        var order=inOrder(store,management);order.verify(store).claim();order.verify(store).requireCurrent(c);
        order.verify(management).publishEvolvedProjectSkillOutcome(eq("project"),eq("method"),argThat(p->p.get("content").equals("Read only.")&&!p.containsKey("artifacts")&&!p.containsKey("routingProfile")),eq(2),eq("hash"),eq("SYSTEM_SKILL_MAINTENANCE"));
        order.verify(store).finish(eq(c),eq("PENDING_INDEX"),eq("PUBLISHED"),argThat(r->r.containsKey("proposal") && Boolean.TRUE.equals(r.get("safe"))),eq(3));
    }
    @Test void semanticChangeCannotBypassTheNormalSourceQualifiedPatchFlow() {
        var c=prepare(true);var receipt=Map.<String,Object>of("equivalent",false,"safe",true);
        when(review.review(anyMap(),anyList(),anyMap())).thenReturn(receipt);service.checkNext();
        verify(store).finish(eq(c),eq("REVIEW_REQUIRED"),eq("BEHAVIOR_CHANGE_REQUIRES_SOURCE_QUALIFIED_PATCH"),anyMap(),eq(0));verifyNoInteractions(management);
    }
    @Test void transientModelFailureStaysDurableWithoutPublishing() {
        var c=prepare(true);when(review.review(anyMap(),anyList(),anyMap())).thenThrow(new IllegalStateException("timeout"));
        service.checkNext();verify(store).defer(c,"MAINTENANCE_RECHECK_REQUIRED");verifyNoInteractions(management);
    }
    @Test void manualAndChangedBaselinesNeverAutomaticallyPublish() {
        var c=prepare(false);service.checkNext();verify(store).finish(c,"REVIEW_REQUIRED","MANUAL_MAINTENANCE_ONLY",Map.of(),0);
        when(catalog.getProjectSkill("project","method")).thenReturn(Map.of("currentVersion",3));service.checkNext();
        verify(store).finish(c,"SUPERSEDED","BASE_VERSION_CHANGED",Map.of(),0);verifyNoInteractions(review,management);
    }
    @Test void proposalWithoutUsefulChangeStopsWithoutSecondModelCall() {
        var c=prepare(true);when(review.propose(anyMap(),anyList())).thenReturn(Map.of("changes",List.of(),"reason","already minimal"));
        service.checkNext();verify(review,never()).review(anyMap(),anyList(),anyMap());verifyNoInteractions(management);
        verify(store).finish(eq(c),eq("NO_CHANGE"),eq("NO_EQUIVALENT_SIMPLIFICATION"),anyMap(),eq(0));
    }
    @Test void unsafeProposalCannotReachReviewOrPublication() {
        var c=prepare(true);when(review.propose(anyMap(),anyList())).thenReturn(Map.of("changes",List.of(Map.of("path","scripts/new.sh","content","new","reason","new"))));
        service.checkNext();verify(review,never()).review(anyMap(),anyList(),anyMap());verifyNoInteractions(management);
        verify(store).finish(eq(c),eq("REVIEW_REQUIRED"),eq("BEHAVIOR_CHANGE_REQUIRES_SOURCE_QUALIFIED_PATCH"),anyMap(),eq(0));
    }
    @Test void shortEntryWithLongReferencedMethodTriggersScan() {
        var s=new SkillMaintenancePort.Subject(1,"project","method",2,"hash","package","Read resources/method.json",Instant.now(),null,0,0,true);
        when(store.scan(0,100)).thenReturn(List.of(s));
        when(catalog.listSkillArtifacts("project","method",2,"hash","package","PROJECT"))
            .thenReturn(List.of(Map.of("path","resources/method.json","role","RESOURCE","content","a".repeat(2001))));
        assertEquals(0,service.scanBatch(0,String::length,"test-counter",Instant.now()));
        verify(store).enqueue(eq(s),eq("COMPRESS_CHECK"),eq(2001+s.content().length()),eq("test-counter"),eq("BODY_OVER_2000_TOKENS"));
    }
}
