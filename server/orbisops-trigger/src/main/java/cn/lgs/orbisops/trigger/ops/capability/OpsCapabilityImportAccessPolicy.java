package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;

/** Project authorization and actor identity boundary for capability import. */
final class OpsCapabilityImportAccessPolicy {

    private final AuthorizeProjectAccessUseCase projectAccessUseCase;

    OpsCapabilityImportAccessPolicy(
            AuthorizeProjectAccessUseCase projectAccessUseCase) {
        this.projectAccessUseCase = projectAccessUseCase;
    }

    String requireProjectId(String projectId) {
        return OpsCapabilityImportValues.require(
                projectId,
                "CAPABILITY_IMPORT_PROJECT_REQUIRED");
    }

    void assertCanManage(
            String projectId,
            AdminAuthService.AuthPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("CAPABILITY_IMPORT_AUTH_REQUIRED");
        }
        assertCanManage(
                projectId,
                principal.username(),
                principal.userId(),
                AdminAuthService.SCOPE_ADMIN.equals(principal.scope()));
    }

    void assertCanManage(
            String projectId,
            String username,
            String userId,
            boolean platformAdmin) {
        projectAccessUseCase.requireAction(
                projectId,
                username,
                userId,
                platformAdmin,
                ProjectAction.MANAGE_CAPABILITY);
    }

    String actor(AdminAuthService.AuthPrincipal principal) {
        return principal == null
                ? ""
                : actor(principal.username(), principal.userId());
    }

    String actor(String username, String userId) {
        return OpsCapabilityImportValues.firstText(userId, username);
    }
}
