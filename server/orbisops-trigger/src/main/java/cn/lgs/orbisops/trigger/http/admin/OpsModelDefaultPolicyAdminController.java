package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/model-default-policy")
public class OpsModelDefaultPolicyAdminController {

    private final ModelDefaultPolicyApplicationService policies;

    public OpsModelDefaultPolicyAdminController(ModelDefaultPolicyApplicationService policies) {
        this.policies = policies;
    }

    @GetMapping
    public Response<Map<String, Object>> get(
            @RequestParam(value = "projectId", required = false) String projectId) {
        return success(policies.get(projectId));
    }

    @PutMapping
    public Response<Map<String, Object>> update(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return success(policies.update(
                projectId,
                request == null ? Map.of() : request,
                actor(servletRequest)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private Response<Map<String, Object>> success(Map<String, Object> data) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
