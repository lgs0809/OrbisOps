package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAdminUserRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class OpsChannelIdentityDirectoryAdapter implements ChannelIdentityDirectoryPort {

    private final IAdminUserRepository users;
    private final AuthorizeProjectAccessUseCase projectAccess;

    public OpsChannelIdentityDirectoryAdapter(IAdminUserRepository users,
                                              AuthorizeProjectAccessUseCase projectAccess) {
        if (users == null) throw new IllegalArgumentException("CHANNEL_USER_DIRECTORY_REQUIRED");
        if (projectAccess == null) throw new IllegalArgumentException("CHANNEL_PROJECT_ACCESS_REQUIRED");
        this.users = users;
        this.projectAccess = projectAccess;
    }

    @Override
    public PlatformIdentity resolveActive(String userId, String username) {
        String safeUserId = value(userId);
        String safeUsername = value(username);
        if (safeUserId.isBlank() && safeUsername.isBlank()) {
            throw new IllegalArgumentException("CHANNEL_PLATFORM_USER_REQUIRED");
        }
        if (safeUserId.length() > 128) throw new IllegalArgumentException("CHANNEL_PLATFORM_USER_ID_TOO_LONG");
        if (safeUsername.length() > 128) throw new IllegalArgumentException("CHANNEL_USERNAME_TOO_LONG");
        AdminUserRecord user = !safeUserId.isBlank()
                ? users.queryByUserId(safeUserId)
                : users.queryByUsername(safeUsername);
        if (user == null || !StringUtils.hasText(user.getUserId()) || !StringUtils.hasText(user.getUsername())) {
            throw new IllegalArgumentException("CHANNEL_IDENTITY_PLATFORM_USER_NOT_FOUND");
        }
        if (!safeUserId.isBlank() && !safeUserId.equals(user.getUserId())) {
            throw new IllegalArgumentException("CHANNEL_IDENTITY_PLATFORM_USER_MISMATCH");
        }
        if (!safeUsername.isBlank() && !safeUsername.equals(user.getUsername())) {
            throw new IllegalArgumentException("CHANNEL_IDENTITY_PLATFORM_USER_MISMATCH");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new SecurityException("CHANNEL_IDENTITY_PLATFORM_USER_DISABLED");
        }
        return new PlatformIdentity(user.getUserId(), user.getUsername());
    }

    @Override
    public boolean canAccessProject(String projectId, PlatformIdentity identity) {
        return projectAccess.canAccess(projectId, identity.username(), identity.userId(), false);
    }

    @Override
    public boolean isTrusted(String projectId, ChannelIdentityRecord identity) {
        if (identity == null) return false;
        AdminUserRecord user = users.queryByUserId(identity.platformUserId());
        return user != null
                && Integer.valueOf(1).equals(user.getStatus())
                && identity.platformUserId().equals(user.getUserId())
                && identity.username().equals(user.getUsername())
                && projectAccess.canAccess(projectId, user.getUsername(), user.getUserId(), false);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
