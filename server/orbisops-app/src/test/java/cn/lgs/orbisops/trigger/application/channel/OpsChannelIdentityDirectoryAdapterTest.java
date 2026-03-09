package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAdminUserRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelIdentityDirectoryAdapterTest {

    private IAdminUserRepository users;
    private AuthorizeProjectAccessUseCase projectAccess;
    private OpsChannelIdentityDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        users = mock(IAdminUserRepository.class);
        projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        adapter = new OpsChannelIdentityDirectoryAdapter(users, projectAccess);
    }

    @Test
    void resolveActiveRejectsUsernameThatDoesNotMatchAuthoritativeUserStore() {
        when(users.queryByUserId("user-1")).thenReturn(user("user-1", "alice", 1));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> adapter.resolveActive("user-1", "mallory"));

        assertEquals("CHANNEL_IDENTITY_PLATFORM_USER_MISMATCH", failure.getMessage());
    }

    @Test
    void resolveActiveRejectsDisabledPlatformUser() {
        when(users.queryByUserId("user-1")).thenReturn(user("user-1", "alice", 0));

        SecurityException failure = assertThrows(SecurityException.class,
                () -> adapter.resolveActive("user-1", "alice"));

        assertEquals("CHANNEL_IDENTITY_PLATFORM_USER_DISABLED", failure.getMessage());
        verify(projectAccess, never()).canAccess(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void trustedIdentityRequiresEnabledMatchingUserAndCurrentProjectMembership() {
        ChannelIdentityRecord identity = new ChannelIdentityRecord(
                "mapping-1", "channel-1", "project-1", "sender-1", "user-1", "alice",
                ChannelStatus.ACTIVE, 1, "admin", null, null);
        when(users.queryByUserId("user-1"))
                .thenReturn(user("user-1", "alice", 0), user("user-1", "alice", 1), user("user-1", "alice", 1));
        when(projectAccess.canAccess("project-1", "alice", "user-1", false))
                .thenReturn(false, true);

        assertFalse(adapter.isTrusted("project-1", identity));
        assertFalse(adapter.isTrusted("project-1", identity));
        assertTrue(adapter.isTrusted("project-1", identity));
    }

    @Test
    void activeResolutionReturnsAuthoritativeIdentity() {
        when(users.queryByUsername("alice")).thenReturn(user("user-1", "alice", 1));

        ChannelIdentityDirectoryPort.PlatformIdentity identity = adapter.resolveActive("", "alice");

        assertEquals("user-1", identity.userId());
        assertEquals("alice", identity.username());
    }

    private AdminUserRecord user(String userId, String username, int status) {
        return AdminUserRecord.builder().userId(userId).username(username).status(status).build();
    }
}
