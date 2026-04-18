package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.worksession.OpsWorkflowApprovalWebApplicationService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/agent/chat", "/api/v1/user/chat"})
public class OpsWorkflowApprovalController {

    private final OpsWorkflowApprovalWebApplicationService approvals;
    private final AuthorizeProjectAccessUseCase projectAccess;

    public OpsWorkflowApprovalController(OpsWorkflowApprovalWebApplicationService approvals,
                                         AuthorizeProjectAccessUseCase projectAccess) {
        if (approvals == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_WEB_SERVICE_REQUIRED");
        if (projectAccess == null) throw new IllegalArgumentException("PROJECT_ACCESS_SERVICE_REQUIRED");
        this.approvals = approvals;
        this.projectAccess = projectAccess;
    }

    @GetMapping("/runs/{runId}/workflow-approval")
    public Response<Map<String, Object>> approval(@PathVariable("runId") String runId,
                                                   @RequestParam("projectId") String projectId,
                                                   HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        assertProjectAccess(projectId, principal);
        Map<String, Object> view;
        if (userScoped(principal)) {
            view = approvals.viewForActor(runId, projectId, actor(principal));
        } else if (principal.serviceToken()) {
            view = approvals.view(runId, projectId);
        } else {
            view = approvals.viewForProjectActor(runId, projectId, actor(principal));
        }
        return success(view);
    }

    @PostMapping("/runs/{runId}/workflow-approval/decision")
    public Response<Map<String, Object>> decide(@PathVariable("runId") String runId,
                                                 @RequestParam("projectId") String projectId,
                                                 @RequestBody Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        assertProjectAccess(projectId, principal);
        return success(approvals.decide(
                runId,
                projectId,
                actor(principal),
                required(request == null ? null : request.get("decision"), "审批决定不能为空"),
                required(request == null ? null : request.get("approvalId"), "必须指定已查看的审批记录")));
    }

    @PostMapping("/runs/{runId}/workflow-approval/resume")
    public Response<Map<String, Object>> retryResume(@PathVariable("runId") String runId,
                                                      @RequestParam("projectId") String projectId,
                                                      @RequestBody Map<String, Object> request,
                                                      HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        assertProjectAccess(projectId, principal);
        return success(approvals.retryResume(runId, projectId, actor(principal),
                required(request == null ? null : request.get("approvalId"), "必须指定已查看的审批记录")));
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal;
        throw new SecurityException("未获取到已认证用户");
    }

    private String actor(AdminAuthService.AuthPrincipal principal) {
        if (principal == null || principal.serviceToken()) {
            throw new SecurityException("WORKFLOW_APPROVAL_HUMAN_ACTOR_REQUIRED");
        }
        return StringUtils.hasText(principal.userId()) ? principal.userId() : principal.username();
    }

    private boolean userScoped(AdminAuthService.AuthPrincipal principal) {
        return principal != null
                && !principal.serviceToken()
                && AdminAuthService.SCOPE_USER.equals(principal.scope());
    }

    private void assertProjectAccess(String projectId, AdminAuthService.AuthPrincipal principal) {
        projectAccess.requireAccess(
                required(projectId, "projectId 不能为空"),
                principal == null ? "" : principal.username(),
                principal == null ? "" : principal.userId(),
                !userScoped(principal));
    }

    private String required(Object value, String message) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
