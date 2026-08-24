package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.episode.BackgroundModelRecoveryPort;
import cn.lgs.orbisops.application.episode.BackgroundModelRecoveryService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ops/background-model-retries")
public class OpsBackgroundModelRecoveryController {
    private final BackgroundModelRecoveryService service;
    private final AuthorizeProjectAccessUseCase access;
    public OpsBackgroundModelRecoveryController(BackgroundModelRecoveryService service,AuthorizeProjectAccessUseCase access) {
        this.service=service;this.access=access;
    }
    @PostMapping("/{family}/{taskId}/recover")
    public Response<Boolean> recover(@PathVariable BackgroundModelRecoveryPort.Family family,@PathVariable String taskId,
            @RequestParam String projectId,HttpServletRequest request) {
        if(!(request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE) instanceof AdminAuthService.AuthPrincipal principal)
                || !AdminAuthService.SCOPE_ADMIN.equals(principal.scope()))throw new SecurityException("BACKGROUND_MODEL_ADMIN_REQUIRED");
        access.requireAccess(projectId,principal.username(),principal.userId(),true);
        return Response.<Boolean>builder().code("0000").info("任务保留原身份及等待时间；符合条件时由后台继续")
                .data(service.recover(projectId,family,taskId,principal.username())).build();
    }
}
