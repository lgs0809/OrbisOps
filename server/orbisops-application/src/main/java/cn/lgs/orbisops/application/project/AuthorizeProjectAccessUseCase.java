package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.domain.project.model.ProjectRole;
import cn.lgs.orbisops.domain.project.service.ProjectAccessPolicy;

import java.util.List;
import java.util.Set;

public final class AuthorizeProjectAccessUseCase {

    private final ProjectAccessPort port;
    private final ProjectMemberApplicationService memberService;
    private final ProjectAccessPolicy policy;

    public AuthorizeProjectAccessUseCase(ProjectAccessPort port,
                                         ProjectMemberApplicationService memberService) {
        this(port, memberService, new ProjectAccessPolicy());
    }

    AuthorizeProjectAccessUseCase(ProjectAccessPort port,
                                  ProjectMemberApplicationService memberService,
                                  ProjectAccessPolicy policy) {
        if (port == null) throw new IllegalArgumentException("PROJECT_ACCESS_PORT_REQUIRED");
        if (memberService == null) throw new IllegalArgumentException("PROJECT_MEMBER_SERVICE_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("PROJECT_ACCESS_POLICY_REQUIRED");
        this.port = port;
        this.memberService = memberService;
        this.policy = policy;
    }

    public List<ProjectCatalogEntry> catalog(String username, String userId, boolean platformAdmin) {
        List<ProjectCatalogEntry> catalog = safe(port.publicCatalog());
        if (platformAdmin) {
            return catalog;
        }
        String normalizedUsername = value(username);
        String normalizedUserId = value(userId);
        Set<String> memberProjectIds = memberService.enabledProjectIds(
                normalizedUsername, normalizedUserId);
        return catalog.stream()
                .filter(project -> port.owner(
                                project.projectId(), normalizedUsername, normalizedUserId)
                        || memberProjectIds.contains(project.projectId()))
                .toList();
    }

    public boolean canAccess(String projectId, String username, String userId, boolean platformAdmin) {
        String id = projectId(projectId);
        if (!port.exists(id)) {
            return false;
        }
        if (platformAdmin) {
            return true;
        }
        String normalizedUsername = value(username);
        String normalizedUserId = value(userId);
        return port.owner(id, normalizedUsername, normalizedUserId)
                || memberService.member(id, normalizedUsername, normalizedUserId);
    }

    public void requireAccess(String projectId, String username, String userId, boolean platformAdmin) {
        String id = projectId(projectId);
        if (!canAccess(id, username, userId, platformAdmin)) {
            throw new SecurityException("PROJECT_ACCESS_FORBIDDEN:" + id);
        }
    }

    public ProjectRole role(String projectId, String username, String userId, boolean platformAdmin) {
        String id = projectId(projectId);
        if (platformAdmin) {
            return ProjectRole.ADMIN;
        }
        String normalizedUsername = value(username);
        String normalizedUserId = value(userId);
        if (!port.exists(id)) {
            throw new SecurityException("PROJECT_ACCESS_FORBIDDEN:" + id);
        }
        if (port.owner(id, normalizedUsername, normalizedUserId)) {
            return ProjectRole.OWNER;
        }
        ProjectRole role = memberService.role(id, normalizedUsername, normalizedUserId);
        if (role == ProjectRole.NONE) {
            throw new SecurityException("PROJECT_ACCESS_FORBIDDEN:" + id);
        }
        return role;
    }

    public void requireAction(String projectId,
                              String username,
                              String userId,
                              boolean platformAdmin,
                              ProjectAction action) {
        String id = projectId(projectId);
        ProjectRole role = role(id, username, userId, platformAdmin);
        policy.require(action, role, platformAdmin);
    }

    private List<ProjectCatalogEntry> safe(List<ProjectCatalogEntry> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private String projectId(String input) {
        String value = value(input);
        if (value.isBlank()) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        return value;
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }
}
