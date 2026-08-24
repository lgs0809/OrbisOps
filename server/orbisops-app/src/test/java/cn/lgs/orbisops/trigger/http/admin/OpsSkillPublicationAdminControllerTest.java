package cn.lgs.orbisops.trigger.http.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OpsSkillPublicationAdminControllerTest {
    @Test void wrongProjectCannotStartRetainedCandidate() {
        var candidates=mock(SkillPatchCandidateApplicationService.class);var releases=mock(SkillPublicationRetryPort.class);
        when(candidates.getCandidate("candidate")).thenReturn(candidate());
        var controller=new OpsSkillPublicationAdminController(candidates,releases);
        assertThrows(IllegalArgumentException.class,()->controller.retry("candidate",new OpsSkillPublicationAdminController.Retry("other")));
        verifyNoInteractions(releases);
    }
    @Test void retainedCandidateGoesThroughExistingReleaseChecks() {
        var candidates=mock(SkillPatchCandidateApplicationService.class);var releases=mock(SkillPublicationRetryPort.class);
        when(candidates.getCandidate("candidate")).thenReturn(candidate());
        when(releases.enqueue("project","candidate")).thenReturn(Map.of("status","PENDING"));
        var result=new OpsSkillPublicationAdminController(candidates,releases).retry("candidate",new OpsSkillPublicationAdminController.Retry("project"));
        assertEquals("PENDING",result.getData().get("status"));verify(releases).enqueue("project","candidate");
        verify(candidates,never()).updateStatus(anyString(),any(SkillPatchCandidateStatus.class),anyString());
    }
    private SkillPatchCandidate candidate() {return new SkillPatchCandidate("candidate","a".repeat(64),"run","EVOLVER",
        "project","agent","PROJECT","","CREATE_SKILL",SkillPatchRiskLevel.LOW,0,"","",List.of(),List.of(),List.of(),List.of(),
        SkillPatchCandidateStatus.CANDIDATE,"",null,null);}
}
