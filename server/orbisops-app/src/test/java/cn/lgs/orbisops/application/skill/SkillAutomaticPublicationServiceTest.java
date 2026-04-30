package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillAutomaticPublicationServiceTest {
    SkillReleasePort releases=mock(SkillReleasePort.class);
    SkillPatchCandidateApplicationService candidates=mock(SkillPatchCandidateApplicationService.class);
    SkillPatchValidationApplicationService validation=mock(SkillPatchValidationApplicationService.class);
    SkillAutomaticPublicationCheckPort checks=mock(SkillAutomaticPublicationCheckPort.class);
    SkillContentReviewPort review=mock(SkillContentReviewPort.class);
    SkillManagementUseCase management=mock(SkillManagementUseCase.class);
    SkillReleasePackageAssembler assembler=mock(SkillReleasePackageAssembler.class);
    AtomicReference<SkillReleaseSnapshot> stored=new AtomicReference<>();
    SkillPatchCandidate candidate=new SkillPatchCandidate("candidate","a".repeat(64),"run","EVOLVER","project","agent","PROJECT","",
            "CREATE_SKILL",SkillPatchRiskLevel.LOW,0,"","",List.of(Map.of("id","receipt")),List.of(),List.of(),List.of(),SkillPatchCandidateStatus.CANDIDATE,"",null,null);
    SkillAutomaticPublicationService service;
    @BeforeEach void setup() {
        when(candidates.getCandidate("candidate")).thenReturn(candidate);
        when(validation.validateMinimumDecision(eq("candidate"),anyList())).thenAnswer(inv -> {
            List<String> failures=inv.getArgument(1);
            return new SkillPatchValidationDecision(failures.isEmpty()?"PASSED":"POLICY_REJECTED",failures.isEmpty(),failures);
        });
        when(checks.requireCurrentSources(candidate)).thenReturn(new SkillPublicationSources(Map.of()));
        when(review.review(candidate,Map.of())).thenReturn(List.of());
        when(assembler.patch(candidate)).thenAnswer(i -> new LinkedHashMap<>());
        when(management.createProjectSkillOutcome(eq("project"),anyMap(),anyString()))
                .thenReturn(new SkillPublicationOutcome(true,"CREATED","evolved-"+"a".repeat(16),1,"hash"));
        when(releases.findByCandidate("candidate")).thenAnswer(i -> Optional.ofNullable(stored.get()));
        doAnswer(i -> {stored.compareAndSet(null,i.getArgument(0));return null;}).when(releases).create(any());
        when(releases.claim(anyString(),any(),any())).thenAnswer(i -> {
            var current=stored.get();SkillReleaseStatus from=i.getArgument(1),to=i.getArgument(2);
            return current.status()==from && stored.compareAndSet(current,current.transitionTo(to));
        });
        when(releases.complete(anyString(),any(),any(),anyString(),anyInt(),anyString(),anyString())).thenAnswer(i -> {
            var current=stored.get();if(current.status()!=i.getArgument(1))return false;
            return stored.compareAndSet(current,new SkillReleaseSnapshot(current.releaseId(),current.candidateId(),current.projectId(),
                    current.agentId(),i.getArgument(6),i.getArgument(2),0,current.baselineVersion(),current.baselineSkillHash(),
                    i.getArgument(3),i.getArgument(4),i.getArgument(5),current.metadata()));
        });
        var tx=new SkillTransactionPort(){public <T>T required(java.util.function.Supplier<T> work){return java.util.Objects.requireNonNull(work.get(),"SKILL_TRANSACTION_RETURNED_NULL");}};
        service=new SkillAutomaticPublicationService(releases,candidates,validation,checks,review,assembler,management,tx,
                mock(SkillReleaseAuditPort.class),new SkillReleaseSettings(true,20,20));
    }
    @Test void noCaseQuotaOrCanaryAndActivationWaitsForReadyIndex() {
        assertEquals(SkillReleaseStatus.PENDING_INDEX,service.start("candidate").releaseStatus());
        service.start("candidate");assertEquals(SkillReleaseStatus.PENDING_INDEX,stored.get().status());
        when(checks.runtimeReady(any())).thenReturn(true);
        assertEquals(SkillReleaseStatus.ACTIVE,service.start("candidate").releaseStatus());
        service.start("candidate");
        verify(management,times(1)).createProjectSkillOutcome(anyString(),anyMap(),anyString());
        verify(review,times(1)).review(any(),anyMap());
        verify(releases,never()).canaryEvidence(any());
        verify(validation,never()).validateDecision(anyString());
        assertEquals(0,stored.get().canaryPercent());
    }
    @Test void contentRejectionRetainsCandidateWithoutPublishing() {
        when(review.review(candidate,Map.of())).thenReturn(List.of("POLICY_CONTENT_REVIEW:scope widened"));
        assertEquals("POLICY_REJECTED",service.start("candidate").statusCode());
        assertNull(stored.get());verifyNoInteractions(management);
    }
    @Test void transientModelFailureDoesNotCreateReleaseAndRetryCanSucceed() {
        when(review.review(candidate,Map.of())).thenThrow(new IllegalStateException("temporary timeout"));
        assertThrows(IllegalStateException.class,() -> service.start("candidate"));assertNull(stored.get());
        doReturn(List.of()).when(review).review(candidate,Map.of());
        assertEquals(SkillReleaseStatus.PENDING_INDEX,service.start("candidate").releaseStatus());
        verify(management,times(1)).createProjectSkillOutcome(anyString(),anyMap(),anyString());
    }
    @Test void sourceRevokedDuringContentReviewNeverPublishes() {
        when(checks.requireCurrentSources(candidate)).thenReturn(new SkillPublicationSources(Map.of())).thenThrow(new IllegalStateException("SOURCE_REVOKED"));
        assertThrows(IllegalStateException.class,() -> service.start("candidate"));
        assertNull(stored.get());verifyNoInteractions(management);
    }
    @Test void historicalRolledBackReleaseCannotBeReenabledByRetry() {
        stored.set(new SkillReleaseSnapshot("release","candidate","project","agent","skill",SkillReleaseStatus.ROLLED_BACK,
                0,0,"","manual correction",1,"hash",Map.of("publicationPolicy",SkillAutomaticPublicationService.POLICY)));
        assertEquals(SkillReleaseStatus.ROLLED_BACK,service.start("candidate").releaseStatus());
        verifyNoInteractions(management,review);
    }

    @Test void knownStaleSourcesRejectBeforePublicationInsteadOfHoldingAPassedCandidateForever() {
        when(checks.requireCurrentSources(candidate)).thenThrow(new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED"));
        assertEquals("POLICY_REJECTED",service.start("candidate").statusCode());
        verify(validation).validateMinimumDecision("candidate",List.of("POLICY_PUBLICATION_BASELINE_STALE:SKILL_EVOLUTION_RELATED_SKILL_CHANGED"));
        assertNull(stored.get());verifyNoInteractions(review,management);
    }

    @Test void readyReleaseWithAStaleBaselineClosesWithoutPublishingOrDiscardingItsAudit() {
        stored.set(new SkillReleaseSnapshot("release","candidate","project","agent","",SkillReleaseStatus.READY,
                0,0,"","MINIMUM_CHECKS_PASSED",0,"",Map.of("publicationPolicy",SkillAutomaticPublicationService.POLICY)));
        when(checks.requireCurrentSources(candidate)).thenThrow(new IllegalStateException("SKILL_ATOMIC_SOURCE_NOT_ACTIVE"));
        service.advance(stored.get());assertEquals(SkillReleaseStatus.ROLLED_BACK,stored.get().status());
        verify(candidates).updateStatus("candidate",SkillPatchCandidateStatus.ROLLED_BACK,
                "POLICY_PUBLICATION_BASELINE_STALE:SKILL_ATOMIC_SOURCE_NOT_ACTIVE");
        verifyNoInteractions(management,review);
        service.start("candidate");assertEquals(SkillReleaseStatus.ROLLED_BACK,stored.get().status());
    }

    @Test void unknownDatabaseFailureAndAlreadyStagedPublicationStayForReconciliation() {
        var ready=new SkillReleaseSnapshot("release","candidate","project","agent","",SkillReleaseStatus.READY,
                0,0,"","MINIMUM_CHECKS_PASSED",0,"",Map.of("publicationPolicy",SkillAutomaticPublicationService.POLICY));
        stored.set(ready);
        when(checks.requireCurrentSources(candidate)).thenThrow(new IllegalStateException("connection unavailable"));
        assertThrows(IllegalStateException.class,()->service.advance(ready));assertEquals(ready,stored.get());
        verify(candidates,never()).updateStatus(anyString(),any(SkillPatchCandidateStatus.class),anyString());
        stored.set(ready.transitionTo(SkillReleaseStatus.PROMOTING).transitionTo(SkillReleaseStatus.PENDING_INDEX));
        when(checks.runtimeReady(any())).thenThrow(new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED"));
        assertThrows(IllegalStateException.class,()->service.advance(stored.get()));
        assertEquals(SkillReleaseStatus.PENDING_INDEX,stored.get().status());verifyNoInteractions(management);
    }

    @Test void retryLosingLeaseDuringRealReviewCannotCreateOrPublishARelease() {
        var guard=mock(Runnable.class);
        doNothing().doThrow(new IllegalStateException("SKILL_PUBLICATION_RETRY_LEASE_LOST")).when(guard).run();
        assertThrows(IllegalStateException.class,()->service.start("candidate",guard));
        verify(review).review(candidate,Map.of());assertNull(stored.get());verifyNoInteractions(management);
    }

    @Test void qualifiedSplitReviewsOnePlanStagesTwoPackagesAndRollsBackAsOneRelease() {
        var input=splitFixture();var atomic=mock(SkillAtomicPublicationPort.class);service.withAtomicPublication(atomic);
        when(checks.requireCurrentSources(candidate)).thenReturn(new SkillPublicationSources(input));
        when(review.review(candidate,input)).thenReturn(List.of());
        when(assembler.patch(any(SkillPatchCandidate.class))).thenAnswer(i->new LinkedHashMap<>());
        when(management.createProjectSkillOutcome(anyString(),anyMap(),anyString())).thenAnswer(i->{
            Map<String,Object> patch=i.getArgument(1);return new SkillPublicationOutcome(true,"CREATED",(String)patch.get("skillId"),1,"hash");
        });
        assertEquals(SkillReleaseStatus.PENDING_INDEX,service.start("candidate").releaseStatus());
        verify(review,times(1)).review(candidate,input);
        verify(management,times(2)).createProjectSkillOutcome(eq("project"),anyMap(),anyString());
        verify(atomic).stage(eq("candidate"),any(),argThat(outcomes->outcomes.size()==2 && outcomes.stream().map(SkillPublicationOutcome::skillId).distinct().count()==2));
        assertTrue(stored.get().metadata().containsKey("atomicPlan"));
        service.start("candidate");assertEquals(SkillReleaseStatus.PENDING_INDEX,stored.get().status());
        when(atomic.active("candidate")).thenReturn(true);
        assertEquals(SkillReleaseStatus.ACTIVE,service.start("candidate").releaseStatus());
        service.rollbackAtomic("project","candidate","admin","SYNTHETIC rollback");
        assertEquals(SkillReleaseStatus.ROLLED_BACK,stored.get().status());
        service.rollbackAtomic("project","candidate","admin","duplicate request");
        verify(atomic,times(1)).rollback("project","candidate","admin","SYNTHETIC rollback");
        verify(checks,never()).runtimeReady(any());
    }

    @Test void splitWithTooFewSourcesCannotInvokeContentReviewOrPublish() {
        var input=new LinkedHashMap<>(splitFixture());input.put("consolidatedExperiences",List.of());
        when(checks.requireCurrentSources(candidate)).thenReturn(new SkillPublicationSources(input));
        service.withAtomicPublication(mock(SkillAtomicPublicationPort.class));
        assertEquals("POLICY_REJECTED",service.start("candidate").statusCode());
        verifyNoInteractions(review,management);assertNull(stored.get());
    }

    private Map<String,Object> splitFixture() {
        var routing=Map.of("section","routingProfile","operation","upsert","key","runtime","value",Map.of("category","OPERATIONS","subcategory","version-check",
                "whenToUse",List.of("read service version"),"whenNotToUse",List.of("delete production resource"),"keywords",List.of("version")));
        var targets=List.of(Map.of("key","first","name","first method","sourceIds",List.of("s1","s2","s3"),"changes",List.of(routing),"artifacts",List.of()),
                Map.of("key","second","name","second method","sourceIds",List.of("s4","s5","s6"),"changes",List.of(routing),"artifacts",List.of()));
        candidate=new SkillPatchCandidate("candidate","a".repeat(64),"run","EVOLVER","project","agent","PROJECT","","SPLIT_SKILL",SkillPatchRiskLevel.LOW,0,"","",
                List.of(Map.of("id","receipt")),List.of(routing,Map.of("section","lifecycleReplacement","operation","upsert","key","runtime","value",Map.of("sourceSkillIds",List.of("old"),"targets",targets))),
                List.of(),List.of(),SkillPatchCandidateStatus.CANDIDATE,"",null,null);
        when(candidates.getCandidate("candidate")).thenReturn(candidate);
        var ref=new LinkedHashMap<String,Object>();ref.put("skillId","old");ref.put("projectId","project");ref.put("scope","PROJECT");ref.put("sourceType","DB");ref.put("currentVersion",1);
        ref.put("content","SYNTHETIC old method");ref.put("relatedArtifacts",List.of());ref.put("currentSkillHash","b".repeat(64));ref.put("currentPackageHash","c".repeat(64));ref.put("catalogFence","d".repeat(64));
        return Map.of("relatedSkills",List.of(ref),"consolidatedExperiences",java.util.stream.IntStream.rangeClosed(1,6).mapToObj(i->Map.of("sourceId","s"+i,"taskEpisodeId","e"+i)).toList());
    }
}
