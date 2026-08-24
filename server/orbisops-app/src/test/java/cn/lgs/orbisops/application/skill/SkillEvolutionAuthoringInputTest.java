package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionOpportunityPolicy;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic application fixture. Captures complete author input; it is not a model quality evaluation. */
class SkillEvolutionAuthoringInputTest {
    @Test void wholeAcceptedTasksEnterExactlyOneAuthorCallAndMutableHintTextNeverEnters() { exercise(""); }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"SKILL_EVOLUTION_INSUFFICIENT_NEW_SOURCES", "SKILL_EVOLUTION_INSUFFICIENT_NEW_CONDITIONS"})
    void insufficientNewEvidenceKeepsExperienceWithoutRetryingAuthorOrStartingRelease(String reason) { exercise(reason); }
    @Test void unrelatedExceptionWithoutMessageKeepsItsOriginalCause() { exercise("NULL_FAILURE"); }
    void exercise(String sourceFailure) {
        var samples=new ArrayList<SkillExperienceConsolidationSample>();
        for(int i=1;i<=3;i++) {
            String raw=CanonicalJson.stringify(Map.of("format","accepted-task-episode-v1","projectId","p",
                    "runId","run-"+i,"sessionId","s-"+i,"sourceId","a-"+i,"episodeId","e-"+i,
                    "goal","SYNTHETIC task goal","messages",List.of("ORIGINAL_TASK_TURN_"+i,"CORRECTED_TASK_TURN_"+i)));
            samples.add(new SkillExperienceConsolidationSample("o-"+i,"run-"+i,"s-"+i,"SUCCESSFUL_DIAGNOSTIC_PATTERN","SUCCEEDED",
                    "template","trajectory","short summary",1,List.of(),"a-"+i,CanonicalObjectHasher.sha256Text(raw),raw,"e-"+i,"condition-"+(i%2)));
        }
        var current=samples.get(2);var signals=mock(SkillEvolutionSignalApplicationService.class);
        var experience=mock(SkillExperienceApplicationService.class);var candidates=mock(SkillPatchCandidateApplicationService.class);
        var release=mock(SkillReleaseApplicationService.class);var similarity=mock(SkillEvolutionSimilarityPort.class);
        when(similarity.relatedSkills(eq("p"),anyMap())).thenReturn(new SkillEvolutionRelatedSkills("p",List.of()));
        var observed=new SkillExperienceObservation("old-audit-id","o-3","cluster","p","agent","run-3","s-3",
                "SUCCESSFUL_DIAGNOSTIC_PATTERN",new SkillExperienceTaskTemplate("investigate","slow response","SUCCESS",List.of("LOGS"),"SUCCEEDED"),
                "template",List.of("QUERY_LOGS"),"trajectory","SUCCEEDED",List.of(),"SYNTHETIC verified source",3,1,
                new VerifiedTaskOutcome("p","run-3","e-3",1,"a-3","condition-1"));
        when(experience.recordObservation(any())).thenReturn(new SkillExperienceRecordResult(observed,new SkillExperienceClusterSnapshot(3,3,3,"ACCUMULATING",3),true));
        when(experience.clusterEvidence("p","agent","cluster")).thenReturn(new SkillExperienceClusterEvidence(3,3,0,3,List.of("SUCCESS"),2));
        when(experience.consolidationSamples(eq("p"),eq("agent"),eq("cluster"),anyInt(),eq("run-3"))).thenReturn(samples);
        when(signals.record(any())).thenReturn(new SkillEvolutionSignalSnapshot("signal","idem","p","agent","run-3","s-3","SUCCESS","{}","PENDING",Instant.now()));
        var hint=new SkillEvolutionHintSnapshot("hint","signal","p","run-3","SUCCESS",
                CanonicalJson.stringify(Map.of("evolutionSourceHash",current.sourceHash(),"content","MUTABLE_UNTRUSTED_HINT_MUST_NOT_ENTER")),"PENDING",Instant.now());
        when(signals.createHint(any())).thenReturn(hint);when(signals.pendingHints(eq("p"),anyInt())).thenReturn(List.of(hint));
        var requests=new ArrayList<Map<String,Object>>();
        SkillEvolutionAuthoringPort author=input->{requests.add(input);return SkillEvolutionAuthoredCandidate.from(sourceFailure.isBlank()
                ?Map.of("patchType","NO_CHANGE","authoringSource","SYNTHETIC_TEST")
                :Map.of("patchType","UPDATE_DIAGNOSTIC_RECIPE","changes",List.of(Map.of("section","procedure","value","SYNTHETIC refined evidence handling")),"authoringSource","SYNTHETIC_TEST"));};
        var failure=new IllegalStateException("NULL_FAILURE".equals(sourceFailure)?null:sourceFailure);
        if(!sourceFailure.isBlank()) when(candidates.createEvolution(anyMap(),nullable(SkillEvolutionJobSnapshot.class),anyString())).thenThrow(failure);
        var service=new SkillEvolutionApplicationService(signals,experience,candidates,release,author,similarity,
                mock(SkillEvolutionPipelineAuditPort.class),new SkillEvolutionPayloadCodec(),new SkillEvolutionOpportunityPolicy(),new SkillEvolutionPromotionSettings(3,3),new SkillEvolutionCandidateSetSelector(),SkillCandidateTournamentSettings.legacy(),
                new SkillEvolutionProposalPort() {
                    private SkillEvolutionProposalSnapshot stored;
                    public Optional<SkillEvolutionProposalSnapshot> existing(SkillEvolutionJobSnapshot claim,String sourceHash) {return Optional.ofNullable(stored);}
                    public SkillEvolutionProposalSnapshot freeze(SkillEvolutionJobSnapshot claim,String sourceHash,String cluster,Map<String,Object> input) {
                        if(stored!=null) return stored;
                        String raw=CanonicalJson.stringify(input);stored=new SkillEvolutionProposalSnapshot("synthetic-plan",CanonicalObjectHasher.sha256Text(raw),raw,"");return stored;
                    }
                    public SkillEvolutionProposalSnapshot authored(SkillEvolutionJobSnapshot claim,SkillEvolutionProposalSnapshot plan,Map<String,Object> result) {
                        stored=new SkillEvolutionProposalSnapshot(plan.planId(),plan.planHash(),plan.inputJson(),CanonicalJson.stringify(result));return stored;
                    }
                });
        var summary=new SkillEvolutionInputSummary(List.of("完成"),List.of("actual tool receipt reference"),"排查接口错误",
                "因为日志证据显示同类错误，先查询日志然后验证根因。".repeat(4),true,true,true,
                List.of(new SkillEvolutionEvidenceReference("e","result","hash")),"bundle",current.episodeJson(),current.sourceHash());
        if("NULL_FAILURE".equals(sourceFailure)) {
            assertSame(failure,assertThrows(IllegalStateException.class,()->service.decide(new SkillEvolutionPipelineRequest("p","agent","run-3","s-3","RUN_COMPLETED",summary))));
            verifyNoInteractions(release);return;
        }
        var decision=service.decide(new SkillEvolutionPipelineRequest("p","agent","run-3","s-3","RUN_COMPLETED",summary));
        assertTrue(decision.skipped());
        assertTrue(service.decide(new SkillEvolutionPipelineRequest("p","agent","run-3","s-3","RUN_COMPLETED",summary)).skipped());
        assertEquals(1,requests.size(),"A retry reuses the retained author result");
        var request=requests.get(0);assertEquals(1,request.get("candidateBudget"));
        String serialized=CanonicalJson.stringify(request);
        for(int i=1;i<=3;i++){assertTrue(serialized.contains("ORIGINAL_TASK_TURN_"+i));assertTrue(serialized.contains("CORRECTED_TASK_TURN_"+i));}
        assertFalse(serialized.contains("MUTABLE_UNTRUSTED_HINT_MUST_NOT_ENTER"));
        assertEquals(3,((List<?>)request.get("consolidatedExperiences")).size());
        assertFalse(request.containsKey("acceptedTaskEpisode"),"The current whole source is sent once, inside the source set");
        if(sourceFailure.isBlank()) verifyNoInteractions(candidates,release);
        else {
            assertEquals("SKIP_INSUFFICIENT_NEW_SOURCE_DIVERSITY",decision.reasonCode());
            verify(candidates,times(2)).createEvolution(anyMap(),nullable(SkillEvolutionJobSnapshot.class),anyString());
            verifyNoInteractions(release);
            verify(experience,never()).markPromoted(anyString(),anyString(),anyString(),anyString());
            verify(signals,never()).markHintsConsumed(anyList(),anyString());
        }
        verify(similarity,times(1)).relatedSkills(eq("p"),anyMap());
        verify(similarity,times(2)).validateRelatedSkills("p",List.of());
        verify(similarity,never()).bestMatch(anyString(),anyMap());
    }
}
