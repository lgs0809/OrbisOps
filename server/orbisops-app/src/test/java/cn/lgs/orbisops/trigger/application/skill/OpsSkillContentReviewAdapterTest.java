package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillAuthoringModelClient;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsSkillContentReviewAdapterTest {
    final OpsSkillAuthoringModelClient model=mock(OpsSkillAuthoringModelClient.class);
    final SkillPatchValidationResultPort results=mock(SkillPatchValidationResultPort.class);
    final OpsSkillContentReviewAdapter adapter=new OpsSkillContentReviewAdapter(model,results);
    final SkillPatchCandidate candidate=new SkillPatchCandidate("candidate","a".repeat(64),"run","EVOLVER","project","agent","PROJECT","",
            "CREATE_SKILL",SkillPatchRiskLevel.LOW,0,"","",List.of(),List.of(),List.of(),List.of(),SkillPatchCandidateStatus.CANDIDATE,"",null,null);

    @Test void independentContentReviewDoesNotDemandGeneratedCasesOrCanaryScore() {
        when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("{\"decision\":\"PASS\",\"reasons\":[],\"authoringModel\":\"gpt-6.1-terra\"}"));
        assertTrue(adapter.review(candidate,Map.of()).isEmpty());
        verify(results).upsert(eq("candidate"),eq("CONTENT_REVIEW"),eq("PASSED"),eq(1D),argThat(details->
                candidate.candidateHash().equals(details.get("candidateHash"))));
    }
    @Test void malformedOrAmbiguousVerdictIsDeferredAndNeverRecordedAsPass() {
        for(String raw:List.of("{}","{\"decision\":\"PASS\"}","{\"decision\":\"PASS\",\"reasons\":[\"uncertain\"]}",
                "{\"decision\":\"BLOCK\",\"reasons\":[]}","{\"decision\":\"PASS\",\"reasons\":[false]}")) {
            when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject(raw));
            assertThrows(SkillContentReviewUnavailableException.class,()->adapter.review(candidate,Map.of()));
        }
        verify(results,never()).upsert(anyString(),anyString(),anyString(),anyDouble(),anyMap());
    }
    @Test void unsafeContentIsRetainedAsRejectedProposal() {
        when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("{\"decision\":\"BLOCK\",\"reasons\":[\"unsupported scope\"]}"));
        assertEquals(List.of("POLICY_CONTENT_REVIEW:unsupported scope"),adapter.review(candidate,Map.of()));
        verify(results).upsert(eq("candidate"),eq("CONTENT_REVIEW"),eq("POLICY_REJECTED"),eq(0D),anyMap());
    }
    @Test void temporaryGatewayFailureIsTypedForDurableBackoff() {
        when(model.generate(anyString(),anyString())).thenThrow(new IllegalStateException("gateway timeout"));
        assertEquals("SKILL_CONTENT_REVIEW_UNAVAILABLE",assertThrows(SkillContentReviewUnavailableException.class,
                ()->adapter.review(candidate,Map.of())).getMessage());
        verify(results,never()).upsert(anyString(),anyString(),anyString(),anyDouble(),anyMap());
    }
    Map<String,Object> checkpoint(String verdict,List<String> reasons) {
        return Map.of("policy",SkillAutomaticPublicationService.POLICY,"candidateHash",candidate.candidateHash(),
                "acceptedInputHash",cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(Map.of()),
                "review",Map.of("decision",verdict,"reasons",reasons,"authoringBindingHash","expected-binding",
                        "authoringModel",OpsSkillAuthoringModelClient.MODEL));
    }
    @Test void downstreamRecoveryKeepsTheSameCompletedBlockInsteadOfAskingUntilPass() {
        when(model.bindingHash(anyString(),anyString())).thenReturn("expected-binding");
        when(results.find("candidate","CONTENT_REVIEW")).thenReturn(Optional.of(new SkillPatchValidationResultPort.Checkpoint("POLICY_REJECTED",checkpoint("BLOCK",List.of("missing original limit")))));
        // A second model call could produce a favourable verdict. It must never be sent.
        when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("{\"decision\":\"PASS\",\"reasons\":[]}"));
        for(int attempt=0;attempt<3;attempt++)
            assertEquals(List.of("POLICY_CONTENT_REVIEW:missing original limit"),adapter.review(candidate,Map.of()));
        verify(model,never()).generate(anyString(),anyString());
        verify(results,never()).upsert(anyString(),anyString(),anyString(),anyDouble(),anyMap());
    }
    @Test void contentSourceOrResolvedModelBindingChangesRequireFreshReview() {
        for(String changed:List.of("candidateHash","acceptedInputHash","policy","binding","model")) {
            reset(model,results);
            when(model.bindingHash(anyString(),anyString())).thenReturn("expected-binding");
            var stored=new LinkedHashMap<>(checkpoint("BLOCK",List.of("old limit")));
            if(changed.equals("binding") || changed.equals("model")) {
                var review=new LinkedHashMap<>((Map<String,Object>)stored.get("review"));
                review.put(changed.equals("binding")?"authoringBindingHash":"authoringModel","changed");
                stored.put("review",review);
            } else stored.put(changed,"changed");
            when(results.find("candidate","CONTENT_REVIEW")).thenReturn(Optional.of(new SkillPatchValidationResultPort.Checkpoint("POLICY_REJECTED",stored)));
            when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("{\"decision\":\"PASS\",\"reasons\":[]}"));
            assertTrue(adapter.review(candidate,Map.of()).isEmpty(),changed);
            verify(model).generate(anyString(),anyString());
        }
    }
    @Test void corruptedMatchingCheckpointFailsClosedWithoutRerolling() {
        when(model.bindingHash(anyString(),anyString())).thenReturn("expected-binding");
        when(results.find("candidate","CONTENT_REVIEW")).thenReturn(Optional.of(new SkillPatchValidationResultPort.Checkpoint("PASSED",checkpoint("PASS",List.of("uncertain")))));
        assertThrows(SkillContentReviewUnavailableException.class,()->adapter.review(candidate,Map.of()));
        verify(model,never()).generate(anyString(),anyString());
        verify(results,never()).upsert(anyString(),anyString(),anyString(),anyDouble(),anyMap());
    }

    @Test void aCheckpointWhoseSavedStatusDisagreesWithItsVerdictFailsClosed() {
        when(model.bindingHash(anyString(),anyString())).thenReturn("expected-binding");
        when(results.find("candidate","CONTENT_REVIEW")).thenReturn(Optional.of(
                new SkillPatchValidationResultPort.Checkpoint("PASSED",checkpoint("BLOCK",List.of("original limit")))));
        assertThrows(SkillContentReviewUnavailableException.class,()->adapter.review(candidate,Map.of()));
        verify(model,never()).generate(anyString(),anyString());
    }

    @Test void unstampedLegacyBlockSurvivesRecoveryWithoutRerollingTheSameCandidate() {
        when(model.bindingHash(anyString(),anyString())).thenReturn("expected-binding");
        var old=new LinkedHashMap<>(checkpoint("BLOCK",List.of("retain original condition")));
        old.remove("acceptedInputHash");
        when(results.find("candidate","CONTENT_REVIEW")).thenReturn(Optional.of(
                new SkillPatchValidationResultPort.Checkpoint("POLICY_REJECTED",old)));
        assertEquals(List.of("POLICY_CONTENT_REVIEW:retain original condition"),adapter.review(candidate,Map.of()));
        verify(model,never()).generate(anyString(),anyString());
    }
    @Test void unstampedLegacyPassCannotAuthorizeNewPublicationWithoutFreshReview() {
        when(model.bindingHash(anyString(),anyString())).thenReturn("expected-binding");
        var old=new LinkedHashMap<>(checkpoint("PASS",List.of()));old.remove("acceptedInputHash");
        when(results.find("candidate","CONTENT_REVIEW")).thenReturn(Optional.of(
                new SkillPatchValidationResultPort.Checkpoint("PASSED",old)));
        when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("{\"decision\":\"BLOCK\",\"reasons\":[\"current restriction\"]}"));
        assertEquals(List.of("POLICY_CONTENT_REVIEW:current restriction"),adapter.review(candidate,Map.of()));
        verify(model).generate(anyString(),anyString());
    }

}
