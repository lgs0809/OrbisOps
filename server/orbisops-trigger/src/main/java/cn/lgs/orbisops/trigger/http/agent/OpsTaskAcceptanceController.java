package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.episode.TaskAcceptanceApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.domain.skill.model.TaskAcceptanceRequest;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/user/ops/task-acceptance","/api/v1/admin/ops/task-acceptance"})
public class OpsTaskAcceptanceController {
    private final TaskAcceptanceApplicationService service;
    private final AuthorizeProjectAccessUseCase access;
    public OpsTaskAcceptanceController(TaskAcceptanceApplicationService service,AuthorizeProjectAccessUseCase access) {
        this.service=service;this.access=access;
    }
    @GetMapping("/{episodeId}") public Response<Map<String,Object>> inspect(@PathVariable String episodeId,@RequestParam String projectId,HttpServletRequest request) {
        var p=authorize(projectId,request,false);
        return ok(service.inspect(projectId,episodeId,p.userId(),admin(p)));
    }
    @PostMapping("/{episodeId}") public Response<Map<String,Object>> verify(@PathVariable String episodeId,@RequestParam String projectId,
            @RequestBody TaskAcceptanceRequest input,HttpServletRequest request) {
        var p=authorize(projectId,request,true);
        return ok(service.verify(projectId,episodeId,input,p.userId(),admin(p)));
    }
    @PostMapping("/{episodeId}/draft") public Response<Map<String,Object>> draft(@PathVariable String episodeId,@RequestParam String projectId,
            @RequestBody TaskAcceptanceApplicationService.NaturalRequest input,HttpServletRequest request) {
        var p=authorize(projectId,request,true);
        return ok(service.draft(projectId,episodeId,input,p.userId(),admin(p)));
    }
    private AdminAuthService.AuthPrincipal authorize(String project,HttpServletRequest request,boolean write) {
        if (!(request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE) instanceof AdminAuthService.AuthPrincipal p))
            throw new SecurityException("TASK_ACCEPTANCE_AUTH_REQUIRED");
        if (write) access.requireAction(project,p.username(),p.userId(),admin(p),ProjectAction.APPROVE_CHANGE);
        else access.requireAccess(project,p.username(),p.userId(),admin(p));
        return p;
    }
    private boolean admin(AdminAuthService.AuthPrincipal p) { return AdminAuthService.SCOPE_ADMIN.equals(p.scope()); }
    private <T> Response<T> ok(T value) { return Response.<T>builder().code("0000").info("成功").data(value).build(); }
}
