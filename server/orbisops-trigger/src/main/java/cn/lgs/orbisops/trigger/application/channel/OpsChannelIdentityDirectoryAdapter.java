package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class OpsChannelIdentityDirectoryAdapter implements ChannelIdentityDirectoryPort {

    private final AdminUserCatalogPort users;
    private final AuthorizeProjectAccessUseCase projectAccess;

    public OpsChannelIdentityDirectoryAdapter(AdminUserCatalogPort users,
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
        AdminUserAccount user = !safeUserId.isBlank()
                ? users.findByUserId(safeUserId)
                : users.findByUsername(safeUsername);
        if (user == null || !StringUtils.hasText(user.userId()) || !StringUtils.hasText(user.username())) {
            throw new IllegalArgumentException("CHANNEL_IDENTITY_PLATFORM_USER_NOT_FOUND");
        }
        if (!safeUserId.isBlank() && !safeUserId.equals(user.userId())) {
            throw new IllegalArgumentException("CHANNEL_IDENTITY_PLATFORM_USER_MISMATCH");
        }
        if (!safeUsername.isBlank() && !safeUsername.equals(user.username())) {
            throw new IllegalArgumentException("CHANNEL_IDENTITY_PLATFORM_USER_MISMATCH");
        }
        if (!Integer.valueOf(1).equals(user.status())) {
            throw new SecurityException("CHANNEL_IDENTITY_PLATFORM_USER_DISABLED");
        }
        return new PlatformIdentity(user.userId(), user.username());
    }

    @Override
    public boolean canAccessProject(String projectId, PlatformIdentity identity) {
        return projectAccess.canAccess(projectId, identity.username(), identity.userId(), false);
    }

    @Override
    public boolean isTrusted(String projectId, ChannelIdentityRecord identity) {
        if (identity == null) return false;
        AdminUserAccount user = users.findByUserId(identity.platformUserId());
        return user != null
                && Integer.valueOf(1).equals(user.status())
                && identity.platformUserId().equals(user.userId())
                && identity.username().equals(user.username())
                && projectAccess.canAccess(projectId, user.username(), user.userId(), false);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
