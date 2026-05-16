package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.skill.SkillPatchCandidateApplicationService;
import cn.lgs.orbisops.application.skill.SkillPublicationRetryPort;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Retry a retained candidate through all current checks. AdminWebSecurityConfig protects this namespace. */
@RestController
@RequestMapping("/api/v1/admin/ops/skill-evolver/candidates")
public class OpsSkillPublicationAdminController {
    private final SkillPatchCandidateApplicationService candidates;
    private final SkillPublicationRetryPort retries;
    public OpsSkillPublicationAdminController(SkillPatchCandidateApplicationService candidates,
            SkillPublicationRetryPort retries) { this.candidates=candidates;this.retries=retries; }
    @PostMapping("/{candidateId}/publication/retry")
    public Response<Map<String,Object>> retry(@PathVariable String candidateId,@RequestBody Retry request) {
        var candidate=candidates.getCandidate(candidateId);
        if(request==null || request.projectId()==null || !request.projectId().equals(candidate.projectId()))
            throw new IllegalArgumentException("SKILL_CANDIDATE_PROJECT_MISMATCH");
        return Response.<Map<String,Object>>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
            .data(retries.enqueue(candidate.projectId(),candidateId)).build();
    }
    public record Retry(String projectId) { }
    @GetMapping("/{candidateId}/publication")
    public Response<Map<String,Object>> status(@PathVariable String candidateId,@RequestParam String projectId) {
        var candidate=candidates.getCandidate(candidateId);
        if(!projectId.equals(candidate.projectId())) throw new IllegalArgumentException("SKILL_CANDIDATE_PROJECT_MISMATCH");
        return Response.<Map<String,Object>>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
            .data(retries.status(projectId,candidateId)).build();
    }
}
