package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OpsChangePackagePermissionService {

    private final AuthorizeProjectAccessUseCase projectAccess;
    private final OpsConfigAuditService auditService;

    public OpsChangePackagePermissionService(AuthorizeProjectAccessUseCase projectAccess,
                                             ObjectProvider<OpsConfigAuditService> auditServiceProvider) {
        this.projectAccess = projectAccess;
        this.auditService = auditServiceProvider.getIfAvailable();
    }

    public void assertCanViewPackage(String projectId, AdminAuthService.AuthPrincipal principal) {
        assertProjectMember(projectId, principal, "view");
    }

    public void assertCanPreparePackage(String projectId, AdminAuthService.AuthPrincipal principal) {
        assertProjectMember(projectId, principal, "prepare");
    }

    public void assertCanRevisePackage(String projectId, AdminAuthService.AuthPrincipal principal) {
        assertProjectMember(projectId, principal, "revise");
    }

    public void assertCanRejectPackage(String projectId, AdminAuthService.AuthPrincipal principal) {
        assertAction(projectId, principal, ProjectAction.REJECT_CHANGE, "reject");
    }

    public void assertCanApprovePackage(String projectId, AdminAuthService.AuthPrincipal principal) {
        assertAction(projectId, principal, ProjectAction.APPROVE_CHANGE, "approve");
    }

    public void assertCanLandPackage(String projectId, AdminAuthService.AuthPrincipal principal) {
        assertAction(projectId, principal, ProjectAction.LAND_CHANGE, "land");
    }

    public void assertCanAdminPolicy(String projectId, AdminAuthService.AuthPrincipal principal) {
        assertAction(projectId, principal, ProjectAction.ADMIN_POLICY, "admin-policy");
    }

    /**
     * Read-only capability projection for UI/read-model consumers. Unlike the assert* methods this method must not
     * write denial audit records: rendering a list must never manufacture security incidents merely because an actor
     * does not own a particular action.
     */
    public Map<String, Boolean> capabilities(String projectId, AdminAuthService.AuthPrincipal principal) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(projectId) || principal == null) {
            result.put("canView", false);
            result.put("canPrepare", false);
            result.put("canRevise", false);
            result.put("canSubmitReview", false);
            result.put("canApprove", false);
            result.put("canReject", false);
            result.put("canLand", false);
            result.put("canCleanup", false);
            return result;
        }
        String normalizedProjectId = projectId.trim();
        boolean member = allowsProjectMember(normalizedProjectId, principal);
        boolean canApprove = allowsAction(normalizedProjectId, principal, ProjectAction.APPROVE_CHANGE);
        boolean canReject = allowsAction(normalizedProjectId, principal, ProjectAction.REJECT_CHANGE);
        boolean canLand = allowsAction(normalizedProjectId, principal, ProjectAction.LAND_CHANGE);
        result.put("canView", member);
        result.put("canPrepare", member);
        result.put("canRevise", member);
        result.put("canSubmitReview", member);
        result.put("canApprove", canApprove);
        result.put("canReject", canReject);
        result.put("canLand", canLand);
        result.put("canCleanup", canLand);
        return result;
    }

    private boolean allowsProjectMember(String projectId, AdminAuthService.AuthPrincipal principal) {
        try {
            projectAccess.requireAccess(projectId, principal.username(), principal.userId(), isAdmin(principal));
            return true;
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private boolean allowsAction(String projectId,
                                 AdminAuthService.AuthPrincipal principal,
                                 ProjectAction action) {
        try {
            projectAccess.requireAction(projectId, principal.username(), principal.userId(), isAdmin(principal), action);
            return true;
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private void assertProjectMember(String projectId, AdminAuthService.AuthPrincipal principal, String action) {
        String normalizedProjectId = requireProject(projectId);
        boolean platformAdmin = isAdmin(principal);
        if (!platformAdmin && principal == null) {
            deny(normalizedProjectId, null, action, "未认证用户不能访问处置方案");
        }
        try {
            projectAccess.requireAccess(
                    normalizedProjectId,
                    principal == null ? "" : principal.username(),
                    principal == null ? "" : principal.userId(),
                    platformAdmin);
        } catch (RuntimeException error) {
            deny(normalizedProjectId, principal, action, error.getMessage());
        }
    }

    private void assertAction(String projectId,
                              AdminAuthService.AuthPrincipal principal,
                              ProjectAction projectAction,
                              String auditAction) {
        String normalizedProjectId = requireProject(projectId);
        boolean platformAdmin = isAdmin(principal);
        if (!platformAdmin && principal == null) {
            deny(normalizedProjectId, null, auditAction, "未认证用户不能执行项目操作");
        }
        try {
            projectAccess.requireAction(
                    normalizedProjectId,
                    principal == null ? "" : principal.username(),
                    principal == null ? "" : principal.userId(),
                    platformAdmin,
                    projectAction);
        } catch (RuntimeException error) {
            deny(normalizedProjectId, principal, auditAction, error.getMessage());
        }
    }

    private boolean isAdmin(AdminAuthService.AuthPrincipal principal) {
        return principal != null && AdminAuthService.SCOPE_ADMIN.equals(principal.scope());
    }

    private String requireProject(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("ChangePackage 权限判断必须提供 projectId");
        }
        return projectId.trim();
    }

    private void deny(String projectId, AdminAuthService.AuthPrincipal principal, String action, String reason) {
        if (auditService != null) {
            auditService.recordRuntimeEvent(projectId,
                    "",
                    principal == null ? "" : principal.userId(),
                    "change-package-permission",
                    action,
                    "",
                    "MEDIUM",
                    "DENIED",
                    Map.of("actor", principal == null ? "" : principal.username(), "reason", reason == null ? "" : reason));
        }
        throw new SecurityException("ChangePackage 权限不足：" + action + "，" + (reason == null ? "" : reason));
    }
}
