package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.application.skill.SkillGovernanceAuthorizationPort;
import cn.lgs.orbisops.application.skill.SkillGovernancePermission;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.domain.security.AdminUserStatus;
import org.springframework.stereotype.Component;

@Component
public final class OpsSkillGovernanceAuthorizationAdapter implements SkillGovernanceAuthorizationPort {

    private final AdminUserCatalogPort users;

    public OpsSkillGovernanceAuthorizationAdapter(AdminUserCatalogPort users) {
        this.users = users;
    }

    @Override
    public void require(String actor,
                        SkillGovernancePermission permission,
                        String scope,
                        String projectId,
                        String skillId) {
        AdminUserAccount account = users.findByUsername(text(actor));
        if (account == null
                || account.role() != AdminUserRole.ADMIN
                || !Integer.valueOf(AdminUserStatus.ENABLED).equals(account.status())) {
            throw new SecurityException("SKILL_GOVERNANCE_PERMISSION_DENIED:"
                    + permission.name() + ":" + text(actor));
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
