package cn.lgs.orbisops.domain.project.model;

import java.time.LocalDateTime;

public record ProjectMember(
        Long id,
        String projectId,
        String memberKey,
        String userId,
        String username,
        ProjectRole role,
        ProjectMemberStatus status,
        String grantedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ProjectMember {
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        userId = value(userId);
        username = value(username);
        memberKey = value(memberKey);
        if (memberKey.isBlank()) {
            memberKey = !userId.isBlank() ? userId : username;
        }
        if (memberKey.isBlank()) {
            throw new IllegalArgumentException("PROJECT_MEMBER_IDENTITY_REQUIRED");
        }
        role = role == null ? ProjectRole.MEMBER : role;
        status = status == null ? ProjectMemberStatus.ENABLED : status;
        grantedBy = value(grantedBy);
    }

    public static ProjectMember enabled(String projectId,
                                        ProjectMemberIdentity identity,
                                        ProjectRole role,
                                        String grantedBy) {
        if (identity == null || identity.empty()) {
            throw new IllegalArgumentException("PROJECT_MEMBER_IDENTITY_REQUIRED");
        }
        String memberKey = !identity.userId().isBlank()
                ? identity.userId()
                : identity.username();
        return new ProjectMember(
                null,
                projectId,
                memberKey,
                identity.userId(),
                identity.username(),
                role == null ? ProjectRole.MEMBER : role,
                ProjectMemberStatus.ENABLED,
                grantedBy,
                null,
                null);
    }

    public boolean enabled() {
        return status == ProjectMemberStatus.ENABLED;
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
