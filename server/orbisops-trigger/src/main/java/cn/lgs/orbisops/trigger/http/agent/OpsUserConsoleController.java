package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.ops.OpsUserConsoleApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
public class OpsUserConsoleController {

    private final OpsUserConsoleApplicationService userConsoleApplicationService;

    public OpsUserConsoleController(OpsUserConsoleApplicationService userConsoleApplicationService) {
        this.userConsoleApplicationService = userConsoleApplicationService;
    }

    @GetMapping("/dashboard/overview")
    public Response<Map<String, Object>> dashboard(HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        return handle("查询个人工作台失败",
                () -> userConsoleApplicationService.dashboardOverview(principal.username(), principal.userId()));
    }

    @GetMapping("/my-executions")
    public Response<List<Map<String, Object>>> myExecutions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        return handle("查询我的执行失败",
                () -> userConsoleApplicationService.myExecutions(
                        principal.username(), principal.userId(), status, Optional.ofNullable(limit).orElse(100)));
    }

    @GetMapping("/my-executions/{packageId}")
    public Response<Map<String, Object>> myExecutionDetail(
            @PathVariable("packageId") String packageId,
            HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        return handle("查询我的执行详情失败",
                () -> userConsoleApplicationService.myExecutions(principal.username(), principal.userId(), null, 200)
                        .stream()
                        .filter(item -> packageId.equals(item.get("packageId")))
                        .findFirst()
                        .orElse(null));
    }

    @GetMapping("/my-audits")
    public Response<List<Map<String, Object>>> myAudits(
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        return handle("查询我的审计失败",
                () -> userConsoleApplicationService.myAudits(
                        principal.username(), principal.userId(), Optional.ofNullable(limit).orElse(100)));
    }

    @GetMapping("/my-audits/{auditId}")
    public Response<Map<String, Object>> myAuditDetail(
            @PathVariable("auditId") String auditId,
            HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        return handle("查询我的审计详情失败",
                () -> userConsoleApplicationService.myAudits(principal.username(), principal.userId(), 200)
                        .stream()
                        .filter(item -> auditId.equals(String.valueOf(item.get("id"))))
                        .findFirst()
                        .orElse(null));
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal authPrincipal) {
            return authPrincipal;
        }
        throw new IllegalStateException("未获取到已认证用户");
    }

    private <T> Response<T> handle(String message, Supplier<T> action) {
        try {
            return success(action.get());
        } catch (SecurityException e) {
            throw e;
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
