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
        when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("{\"decision\":\"PASS\",\"reasons\":[],\"authoringModel\":\"gpt-5.6-terra\"}"));
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
        verifyNoInteractions(results);
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
        verifyNoInteractions(results);
    }
}
