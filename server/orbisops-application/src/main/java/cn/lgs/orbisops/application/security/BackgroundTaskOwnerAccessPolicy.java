package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.application.project.BackgroundProjectAccessPort;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.domain.security.AdminUserStatus;

/** Background work inherits the current authorization of its original owner, never the maintenance operator. */
public final class BackgroundTaskOwnerAccessPolicy implements BackgroundTaskOwnerAccessPort {
    private final BackgroundProjectAccessPort projects;
    private final AdminUserCatalogPort accounts;
    public BackgroundTaskOwnerAccessPolicy(BackgroundProjectAccessPort projects,AdminUserCatalogPort accounts) {
        this.projects=projects;this.accounts=accounts;
    }
    public void requireAccess(String projectId,String ownerId) {
        var owner=accounts.findByUserId(ownerId);
        if(owner==null || !Integer.valueOf(AdminUserStatus.ENABLED).equals(owner.status()))
            throw new SecurityException("BACKGROUND_MODEL_OWNER_UNAVAILABLE");
        if(projectId==null || projectId.isBlank())return; // Personal memory still requires the retained source owner identity.
        projects.requireAccess(projectId,owner.username(),owner.userId(),owner.role()==AdminUserRole.ADMIN);
    }
}
