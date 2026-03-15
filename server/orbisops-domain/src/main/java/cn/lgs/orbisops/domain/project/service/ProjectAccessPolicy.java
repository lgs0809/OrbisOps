package cn.lgs.orbisops.domain.project.service;

import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.domain.project.model.ProjectRole;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Role/action specification for project-scoped operations. */
public final class ProjectAccessPolicy {

    private static final Set<ProjectRole> MEMBERS = EnumSet.complementOf(EnumSet.of(ProjectRole.NONE));
    private static final Map<ProjectAction, Set<ProjectRole>> ALLOWED = allowed();

    public boolean allowed(ProjectAction action, ProjectRole role, boolean platformAdmin) {
        if (action == null) throw new IllegalArgumentException("PROJECT_ACTION_REQUIRED");
        if (platformAdmin) return true;
        ProjectRole effectiveRole = role == null ? ProjectRole.NONE : role;
        return ALLOWED.getOrDefault(action, Set.of()).contains(effectiveRole);
    }

    public void require(ProjectAction action, ProjectRole role, boolean platformAdmin) {
        if (!allowed(action, role, platformAdmin)) {
            throw new SecurityException("PROJECT_ACTION_FORBIDDEN：action=" + action.name()
                    + " role=" + (role == null ? ProjectRole.NONE.name() : role.name()));
        }
    }

    private static Map<ProjectAction, Set<ProjectRole>> allowed() {
        EnumMap<ProjectAction, Set<ProjectRole>> result = new EnumMap<>(ProjectAction.class);
        result.put(ProjectAction.VIEW, MEMBERS);
        result.put(ProjectAction.PREPARE_CHANGE, MEMBERS);
        result.put(ProjectAction.REVISE_CHANGE, MEMBERS);
        result.put(ProjectAction.REJECT_CHANGE, EnumSet.of(
                ProjectRole.OWNER, ProjectRole.REVIEWER, ProjectRole.APPROVER,
                ProjectRole.MAINTAINER, ProjectRole.OPS_LEAD,
                ProjectRole.ADMIN, ProjectRole.BREAK_GLASS_ADMIN));
        result.put(ProjectAction.APPROVE_CHANGE, EnumSet.of(
                ProjectRole.OWNER, ProjectRole.APPROVER, ProjectRole.MAINTAINER,
                ProjectRole.OPS_LEAD, ProjectRole.ADMIN, ProjectRole.BREAK_GLASS_ADMIN));
        result.put(ProjectAction.LAND_CHANGE, EnumSet.of(
                ProjectRole.OWNER, ProjectRole.OPERATOR, ProjectRole.MAINTAINER,
                ProjectRole.OPS_LEAD, ProjectRole.ADMIN, ProjectRole.BREAK_GLASS_ADMIN));
        result.put(ProjectAction.MANAGE_CAPABILITY, EnumSet.of(
                ProjectRole.OWNER, ProjectRole.MAINTAINER, ProjectRole.OPS_LEAD,
                ProjectRole.ADMIN, ProjectRole.BREAK_GLASS_ADMIN));
        result.put(ProjectAction.ADMIN_POLICY, Set.of());
        return Map.copyOf(result);
    }
}
