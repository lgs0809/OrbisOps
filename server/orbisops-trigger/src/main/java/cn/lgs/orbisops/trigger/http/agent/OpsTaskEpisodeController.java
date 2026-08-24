package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.episode.TaskEpisodeApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class OpsTaskEpisodeController {
    private final TaskEpisodeApplicationService service;
    private final AuthorizeProjectAccessUseCase access;
    public OpsTaskEpisodeController(TaskEpisodeApplicationService service, AuthorizeProjectAccessUseCase access) {
        this.service = service; this.access = access;
    }
    @GetMapping({"/api/v1/user/ops/task-episodes", "/api/v1/admin/ops/task-episodes"})
    public Response<Map<String, Object>> view(@RequestParam String projectId, @RequestParam String sessionId,
            @RequestParam(defaultValue = "100") int limit, HttpServletRequest request) {
        var principal = authorize(projectId, request);
        return success(service.view(projectId, sessionId, principal.userId(), AdminAuthService.SCOPE_ADMIN.equals(principal.scope()), limit));
    }
    @PostMapping("/api/v1/admin/ops/task-episodes/sweep")
    public Response<Integer> sweep(@RequestParam String projectId, HttpServletRequest request) {
        var principal = authorize(projectId, request);
        if (!AdminAuthService.SCOPE_ADMIN.equals(principal.scope())) throw new SecurityException("EPISODE_ADMIN_REQUIRED");
        return success(service.sweep(projectId, "MANUAL"));
    }
    private AdminAuthService.AuthPrincipal authorize(String project, HttpServletRequest request) {
        if (!(request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE) instanceof AdminAuthService.AuthPrincipal principal))
            throw new SecurityException("EPISODE_AUTH_REQUIRED");
        access.requireAccess(project, principal.username(), principal.userId(), AdminAuthService.SCOPE_ADMIN.equals(principal.scope()));
        return principal;
    }
    private <T> Response<T> success(T value) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo()).data(value).build();
    }
}
