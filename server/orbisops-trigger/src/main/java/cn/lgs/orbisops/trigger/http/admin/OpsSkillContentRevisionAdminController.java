package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Admin-only read projection; no endpoint can alter revision count, content or review outcomes. */
@RestController
@RequestMapping("/api/v1/admin/ops/skill-evolver/candidates")
public final class OpsSkillContentRevisionAdminController {
    private final SkillPatchCandidateApplicationService candidates;
    private final SkillContentRevisionStorePort revisions;
    public OpsSkillContentRevisionAdminController(SkillPatchCandidateApplicationService candidates,SkillContentRevisionStorePort revisions) {
        this.candidates=candidates;this.revisions=revisions;
    }
    @GetMapping("/{candidateId}/content-revisions")
    public Response<Map<String,Object>> history(@PathVariable String candidateId,@RequestParam String projectId) {
        var candidate=candidates.getCandidate(candidateId);
        if(!projectId.equals(candidate.projectId()))throw new IllegalArgumentException("SKILL_CANDIDATE_PROJECT_MISMATCH");
        return Response.<Map<String,Object>>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
                .data(Map.of("candidateStatus",candidate.status().name(),"maxRounds",2,"revisions",revisions.history(candidateId))).build();
    }
}
