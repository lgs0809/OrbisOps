package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillMinimumPublicationChecksTest {
    SkillPatchCandidate candidate(List<Object> extra) {
        List<Object> changes=new ArrayList<>(List.of(Map.of("section","routingProfile","operation","upsert","key","runtime",
                "value",Map.of("category","DATA","subcategory","QUERY_DIAGNOSIS","whenToUse",List.of("定位慢 SQL 和查询延迟"),
                        "whenNotToUse",List.of("生成演示文稿或开发前端页面"),"keywords",List.of("slow sql","explain")))));
        changes.addAll(extra);
        return new SkillPatchCandidate("candidate","a".repeat(64),"run","EVOLVER","project","agent","PROJECT","",
                "CREATE_SKILL",SkillPatchRiskLevel.LOW,0,"","",List.of(Map.of("id","receipt")),changes,List.of(),List.of(),
                SkillPatchCandidateStatus.CANDIDATE,"",null,null);
    }
    @Test void validPackageWithNoGeneratedTestCasesPassesMinimumChecksWithoutRegressionCall() {
        var candidates=mock(SkillPatchCandidateApplicationService.class);when(candidates.getCandidate("candidate")).thenReturn(candidate(List.of()));
        var regression=mock(SkillPatchRegressionEvaluationPort.class);
        var service=new SkillPatchValidationApplicationService(candidates,regression,mock(SkillPatchValidationResultPort.class),new SkillPatchValidationPolicy());
        assertTrue(service.validateMinimumDecision("candidate",List.of()).valid());
        verifyNoInteractions(regression);
    }
    @Test void toolsAbsentFromAcceptedSourcesAndPermissionExpansionAreBlocked() {
        var policy=new SkillMinimumContentPolicy();
        var unsafe=candidate(List.of(Map.of("toolName","write_to_production","executable",true)));
        assertEquals(Set.of("POLICY_UNSUPPORTED_TOOL_DEPENDENCY","POLICY_CAPABILITY_EXPANSION"),new HashSet<>(policy.evaluate(unsafe,Map.of())));
        var observed=candidate(List.of(Map.of("toolName","read_metrics")));
        String episode="{\"receipts\":[{\"toolName\":\"read_metrics\"}]}";
        assertTrue(policy.evaluate(observed,Map.of("consolidatedExperiences",List.of(Map.of("acceptedTaskEpisode",episode)))).isEmpty());
    }
    @Test void privateKeyMaterialCannotEnterPublishedText() {
        String syntheticMarker = "-----BEGIN RSA " + "PRIVATE KEY-----\nSYNTHETIC-TEST";
        assertEquals(List.of("POLICY_SENSITIVE_CONTENT"),new SkillMinimumContentPolicy().evaluate(
                candidate(List.of(Map.of("value",syntheticMarker))),Map.of()));
    }
}
