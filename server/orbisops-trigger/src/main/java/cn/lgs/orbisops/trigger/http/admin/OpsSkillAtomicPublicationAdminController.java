package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.skill.SkillReleaseApplicationService;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Existing administrator authorization applies; this does not grant production execution permission. */
@RestController
@RequestMapping("/api/v1/admin/ops/skill-evolver/atomic-publications")
public final class OpsSkillAtomicPublicationAdminController {
    private final SkillReleaseApplicationService releases;
    private final cn.lgs.orbisops.application.skill.SkillAtomicPublicationPort publications;
    public OpsSkillAtomicPublicationAdminController(SkillReleaseApplicationService releases,cn.lgs.orbisops.application.skill.SkillAtomicPublicationPort publications) {
        this.releases=releases;this.publications=publications;
    }
    @GetMapping
    public Response<java.util.List<Map<String,Object>>> list(@RequestParam(defaultValue="") String projectId) {
        return Response.<java.util.List<Map<String,Object>>>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
                .data(publications.list(projectId,100)).build();
    }
    @PostMapping("/{candidateId}/rollback")
    public Response<Map<String,Object>> rollback(@PathVariable String candidateId,@RequestBody Rollback request,HttpServletRequest http) {
        Object value=http.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if(!(value instanceof AdminAuthService.AuthPrincipal principal)) throw new SecurityException("AUTH_REQUIRED");
        return Response.<Map<String,Object>>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
                .data(releases.rollbackAtomic(request.projectId(),candidateId,principal.username(),request.reason())).build();
    }
    public record Rollback(String projectId,String reason) { }
}
