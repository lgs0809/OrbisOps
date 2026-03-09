package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMemberRepository;
import cn.lgs.orbisops.domain.project.model.ProjectMember;
import cn.lgs.orbisops.domain.project.model.ProjectMemberIdentity;
import cn.lgs.orbisops.domain.project.model.ProjectRole;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectMemberApplicationService {

    private final IProjectMemberRepository repository;

    public ProjectMemberApplicationService(IProjectMemberRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("PROJECT_MEMBER_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    public List<Map<String, Object>> list(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        List<ProjectMember> members = repository.list(id);
        return (members == null ? List.<ProjectMember>of() : members).stream()
                .map(this::rowView)
                .toList();
    }

    public Map<String, Object> grantIfAbsent(String projectId,
                                             String userId,
                                             String username,
                                             String memberRole,
                                             String grantedBy) {
        ProjectMember command = member(
                required(projectId, "PROJECT_ID_REQUIRED"),
                userId,
                username,
                memberRole,
                grantedBy);
        ProjectMember saved = repository.grantIfAbsent(command);
        return grantView(saved == null ? command : saved);
    }

    public List<Map<String, Object>> replace(String projectId,
                                             List<Map<String, Object>> members,
                                             String grantedBy) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        List<Map<String, Object>> inputs = members == null ? List.of() : members;
        LinkedHashSet<String> memberKeys = new LinkedHashSet<>();
        List<ProjectMember> commands = inputs.stream()
                .map(input -> {
                    if (input == null) {
                        throw new IllegalArgumentException("PROJECT_MEMBER_REQUIRED");
                    }
                    ProjectMember member = member(
                            id,
                            text(input.get("userId"), ""),
                            text(input.get("username"), ""),
                            text(input.get("memberRole"), "MEMBER"),
                            grantedBy);
                    if (!memberKeys.add(member.memberKey())) {
                        throw new IllegalArgumentException("项目成员重复：" + member.memberKey());
                    }
                    return member;
                })
                .toList();
        List<ProjectMember> saved = repository.replace(id, commands);
        return (saved == null ? commands : saved).stream()
                .map(this::rowView)
                .toList();
    }

    public boolean member(String projectId, String username, String userId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        ProjectMemberIdentity identity = identity(userId, username);
        return !identity.empty() && repository.findEnabled(id, identity).isPresent();
    }

    public ProjectRole role(String projectId, String username, String userId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        ProjectMemberIdentity identity = identity(userId, username);
        if (identity.empty()) {
            return ProjectRole.NONE;
        }
        return repository.findEnabled(id, identity)
                .map(ProjectMember::role)
                .orElse(ProjectRole.NONE);
    }

    public Set<String> enabledProjectIds(String username, String userId) {
        ProjectMemberIdentity identity = identity(userId, username);
        if (identity.empty()) {
            return Set.of();
        }
        Set<String> projectIds = repository.findEnabledProjectIds(identity);
        return projectIds == null ? Set.of() : Set.copyOf(projectIds);
    }

    private ProjectMember member(String projectId,
                                 String userId,
                                 String username,
                                 String memberRole,
                                 String grantedBy) {
        ProjectMemberIdentity identity = identity(userId, username);
        if (identity.empty()) {
            throw new IllegalArgumentException("项目成员必须提供 userId 或 username");
        }
        String roleText = text(memberRole, "MEMBER");
        ProjectRole role = ProjectRole.parse(roleText);
        if (role == ProjectRole.NONE) {
            throw new IllegalArgumentException("PROJECT_MEMBER_ROLE_INVALID:" + roleText);
        }
        return ProjectMember.enabled(projectId, identity, role, text(grantedBy, ""));
    }

    private ProjectMemberIdentity identity(String userId, String username) {
        return new ProjectMemberIdentity(userId, username);
    }

    private Map<String, Object> rowView(ProjectMember member) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project_id", member.projectId());
        result.put("member_key", member.memberKey());
        result.put("user_id", member.userId());
        result.put("username", member.username());
        result.put("member_role", member.role().name());
        result.put("status", member.status().name());
        result.put("granted_by", member.grantedBy());
        result.put("create_time", member.createdAt());
        result.put("update_time", member.updatedAt());
        return result;
    }

    private Map<String, Object> grantView(ProjectMember member) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", member.projectId());
        result.put("memberKey", member.memberKey());
        result.put("userId", member.userId());
        result.put("username", member.username());
        result.put("memberRole", member.role().name());
        return result;
    }

    private String required(String input, String error) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
