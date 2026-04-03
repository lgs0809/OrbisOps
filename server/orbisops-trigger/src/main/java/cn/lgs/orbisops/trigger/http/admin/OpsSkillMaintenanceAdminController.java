package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.skill.SkillMaintenancePort;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops/skill-evolver/maintenance")
public final class OpsSkillMaintenanceAdminController {
    private final SkillMaintenancePort store;
    public OpsSkillMaintenanceAdminController(SkillMaintenancePort store) {this.store=store;}
    @GetMapping public Response<List<Map<String,Object>>> list(@RequestParam(defaultValue="") String projectId) {
        return Response.<List<Map<String,Object>>>builder().code("0000").info("成功").data(store.list(projectId,100)).build();
    }
    @PostMapping("/{id}/keep") public Response<Map<String,Object>> keep(@PathVariable String id,@RequestBody Review request,HttpServletRequest http) {
        Object value=http.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if(!(value instanceof AdminAuthService.AuthPrincipal principal)) throw new SecurityException("AUTH_REQUIRED");
        return Response.<Map<String,Object>>builder().code("0000").info("成功")
            .data(store.acknowledge(request.projectId(),id,principal.username(),request.reason())).build();
    }
    public record Review(String projectId,String reason) { }
}
