package cn.lgs.orbisops.config;

import cn.lgs.orbisops.application.security.AdminUserCatalogUseCase;
import cn.lgs.orbisops.application.security.AdminUserCommand;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.domain.security.AdminUserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BootstrapAdminConfigurationTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments(new String[0]);

    @Test
    void disabledBootstrapDoesNotTouchAccountStore() {
        AdminUserCatalogUseCase accounts = mock(AdminUserCatalogUseCase.class);
        BootstrapAdminConfiguration bootstrap = new BootstrapAdminConfiguration(
                accounts, false, "configured-admin-id", "admin", "credential");

        bootstrap.run(NO_ARGS);

        verifyNoInteractions(accounts);
    }

    @Test
    void existingAdministratorIsNeverOverwritten() {
        AdminUserCatalogUseCase accounts = mock(AdminUserCatalogUseCase.class);
        when(accounts.findByUsername("admin")).thenReturn(mock(AdminUserAccount.class));
        BootstrapAdminConfiguration bootstrap = new BootstrapAdminConfiguration(
                accounts, true, "configured-admin-id", " admin ", "credential");

        bootstrap.run(NO_ARGS);

        verify(accounts, never()).create(any());
    }

    @Test
    void enabledBootstrapRequiresExternalCredential() {
        AdminUserCatalogUseCase accounts = mock(AdminUserCatalogUseCase.class);
        BootstrapAdminConfiguration bootstrap = new BootstrapAdminConfiguration(
                accounts, true, "configured-admin-id", "admin", "");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> bootstrap.run(NO_ARGS));

        assertEquals(
                "ORBISOPS_BOOTSTRAP_ADMIN_USERNAME and ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD are required when bootstrap is enabled",
                failure.getMessage());
        verifyNoInteractions(accounts);
    }

    @Test
    void firstRunCreatesOneEnabledAdminThroughNormalAccountUseCase() {
        AdminUserCatalogUseCase accounts = mock(AdminUserCatalogUseCase.class);
        when(accounts.create(any())).thenReturn(true);
        BootstrapAdminConfiguration bootstrap = new BootstrapAdminConfiguration(
                accounts, true, " explicit-admin-id ", " admin ", "credential");

        bootstrap.run(NO_ARGS);

        ArgumentCaptor<AdminUserCommand> command = ArgumentCaptor.forClass(AdminUserCommand.class);
        verify(accounts).create(command.capture());
        assertEquals("explicit-admin-id", command.getValue().userId());
        assertEquals("admin", command.getValue().username());
        assertEquals("credential", command.getValue().credential());
        assertEquals(AdminUserRole.ADMIN.value(), command.getValue().userRole());
        assertEquals(AdminUserStatus.ENABLED, command.getValue().status());
    }

    @Test
    void enabledHeadlessBootstrapGeneratesUserIdWhenOperatorDoesNotProvideOne() {
        AdminUserCatalogUseCase accounts = mock(AdminUserCatalogUseCase.class);
        when(accounts.create(any())).thenReturn(true);
        BootstrapAdminConfiguration bootstrap = new BootstrapAdminConfiguration(
                accounts, true, "", "admin", "credential");

        bootstrap.run(NO_ARGS);

        ArgumentCaptor<AdminUserCommand> command = ArgumentCaptor.forClass(AdminUserCommand.class);
        verify(accounts).create(command.capture());
        assertTrue(command.getValue().userId().startsWith("user-"));
        assertEquals("admin", command.getValue().username());
    }
}
