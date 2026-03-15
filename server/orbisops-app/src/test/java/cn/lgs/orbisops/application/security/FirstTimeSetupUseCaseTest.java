package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminUserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirstTimeSetupUseCaseTest {

    @Test
    void reportsRequiredOnlyForEmptyPlatformUserCatalog() {
        AdminUserCatalogUseCase catalog = mock(AdminUserCatalogUseCase.class);
        AdminUserAuthenticationUseCase authentication = mock(AdminUserAuthenticationUseCase.class);
        FirstTimeSetupUseCase useCase = new FirstTimeSetupUseCase(catalog, authentication);

        when(catalog.listAll()).thenReturn(List.of());
        assertTrue(useCase.isRequired());

        when(catalog.listAll()).thenReturn(List.of(mock(AdminUserAccount.class)));
        assertFalse(useCase.isRequired());
    }

    @Test
    void createsFirstAdministratorAndReturnsExistingLoginResult() {
        AdminUserCatalogUseCase catalog = mock(AdminUserCatalogUseCase.class);
        AdminUserAuthenticationUseCase authentication = mock(AdminUserAuthenticationUseCase.class);
        FirstTimeSetupUseCase useCase = new FirstTimeSetupUseCase(catalog, authentication);
        AdminUserLoginResult loginResult = mock(AdminUserLoginResult.class);

        when(catalog.listAll()).thenReturn(List.of());
        when(catalog.create(any())).thenReturn(true);
        when(authentication.login(any())).thenReturn(loginResult);

        assertEquals(loginResult, useCase.setup("  platform-admin  ", "StrongPass1"));

        ArgumentCaptor<AdminUserCommand> create = ArgumentCaptor.forClass(AdminUserCommand.class);
        verify(catalog).create(create.capture());
        assertEquals("platform-admin", create.getValue().username());
        assertEquals("admin", create.getValue().userRole());
        assertEquals(1, create.getValue().status());
        assertTrue(create.getValue().userId().startsWith("user-"));

        ArgumentCaptor<AdminUserLoginCommand> login = ArgumentCaptor.forClass(AdminUserLoginCommand.class);
        verify(authentication).login(login.capture());
        assertEquals("platform-admin", login.getValue().username());
        assertEquals("StrongPass1", login.getValue().credential());
    }

    @Test
    void refusesSetupAfterAnyPlatformUserExists() {
        AdminUserCatalogUseCase catalog = mock(AdminUserCatalogUseCase.class);
        AdminUserAuthenticationUseCase authentication = mock(AdminUserAuthenticationUseCase.class);
        FirstTimeSetupUseCase useCase = new FirstTimeSetupUseCase(catalog, authentication);

        when(catalog.listAll()).thenReturn(List.of(mock(AdminUserAccount.class)));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> useCase.setup("platform-admin", "StrongPass1"));
        assertEquals(FirstTimeSetupUseCase.ALREADY_COMPLETED, error.getMessage());
    }
}
