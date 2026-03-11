package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
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

    private AdminUserCatalogPort users;
    private AuthorizeProjectAccessUseCase projectAccess;
    private OpsChannelIdentityDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        users = mock(AdminUserCatalogPort.class);
        projectAccess = mock(AuthorizeProjectAccessUseCase.class);
        adapter = new OpsChannelIdentityDirectoryAdapter(users, projectAccess);
    }

    @Test
    void resolveActiveRejectsUsernameThatDoesNotMatchAuthoritativeUserStore() {
        when(users.findByUserId("user-1")).thenReturn(user("user-1", "alice", 1));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> adapter.resolveActive("user-1", "mallory"));

        assertEquals("CHANNEL_IDENTITY_PLATFORM_USER_MISMATCH", failure.getMessage());
    }

    @Test
    void resolveActiveRejectsDisabledPlatformUser() {
        when(users.findByUserId("user-1")).thenReturn(user("user-1", "alice", 0));

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
        when(users.findByUserId("user-1"))
                .thenReturn(user("user-1", "alice", 0), user("user-1", "alice", 1), user("user-1", "alice", 1));
        when(projectAccess.canAccess("project-1", "alice", "user-1", false))
                .thenReturn(false, true);

        assertFalse(adapter.isTrusted("project-1", identity));
        assertFalse(adapter.isTrusted("project-1", identity));
        assertTrue(adapter.isTrusted("project-1", identity));
    }

    @Test
    void activeResolutionReturnsAuthoritativeIdentity() {
        when(users.findByUsername("alice")).thenReturn(user("user-1", "alice", 1));

        ChannelIdentityDirectoryPort.PlatformIdentity identity = adapter.resolveActive("", "alice");

        assertEquals("user-1", identity.userId());
        assertEquals("alice", identity.username());
    }

    private AdminUserAccount user(String userId, String username, int status) {
        return new AdminUserAccount(null, userId, username, null, AdminUserRole.USER, status, null, null);
    }
}
