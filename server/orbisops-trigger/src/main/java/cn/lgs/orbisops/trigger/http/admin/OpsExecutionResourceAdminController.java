package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.api.dto.OpsExecutionResourceRequestDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.execution.OpsExecutionResourceAdapter;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ops/execution-resources")
public class OpsExecutionResourceAdminController {

    private final OpsExecutionResourceAdapter resources;

    public OpsExecutionResourceAdminController(OpsExecutionResourceAdapter resources) {
        this.resources = resources;
    }

    @GetMapping("/capabilities")
    public Response<Map<String, Object>> capabilities() {
        return success(resources.capabilities());
    }

    @GetMapping
    public Response<List<OpsExecutionResourceDTO>> list(
            @RequestParam(value = "projectId", required = false) String projectId) {
        return handle("查询项目执行资源失败", () -> resources.list(projectId));
    }

    @PostMapping
    public Response<OpsExecutionResourceDTO> upsert(
            @RequestBody OpsExecutionResourceRequestDTO request,
            HttpServletRequest servletRequest) {
        return handle("保存项目执行资源失败", () -> resources.upsert(request, actor(servletRequest)));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal.username();
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private <T> Response<T> handle(String message, Supplier<T> action) {
        try {
            return success(action.get());
        } catch (Exception e) {
            log.warn("{}：{}", message, e.getMessage());
            return Response.<T>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(message + "：" + e.getMessage())
                    .data(null)
                    .build();
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
