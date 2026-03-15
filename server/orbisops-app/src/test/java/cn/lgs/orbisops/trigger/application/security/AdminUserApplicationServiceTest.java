package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.api.dto.AdminUserLoginRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserResponseDTO;
import cn.lgs.orbisops.application.security.AdminUserAuthenticationException;
import cn.lgs.orbisops.application.security.AdminUserAuthenticationUseCase;
import cn.lgs.orbisops.application.security.AdminUserCatalogQuery;
import cn.lgs.orbisops.application.security.AdminUserCatalogUseCase;
import cn.lgs.orbisops.application.security.AdminUserCommand;
import cn.lgs.orbisops.application.security.AdminUserLoginCommand;
import cn.lgs.orbisops.application.security.AdminUserLoginResult;
import cn.lgs.orbisops.application.security.FirstTimeSetupUseCase;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserApplicationServiceTest {

    @Test
    void createProjectsRequestToTypedCommand() {
        AdminUserCatalogUseCase catalog = mock(AdminUserCatalogUseCase.class);
        AdminUserAuthenticationUseCase authentication = mock(AdminUserAuthenticationUseCase.class);
        AdminUserApplicationService service = new AdminUserApplicationService(
                catalog, authentication, new FirstTimeSetupUseCase(catalog, authentication));
        when(catalog.create(any())).thenReturn(true);
        AdminUserRequestDTO request = AdminUserRequestDTO.builder()
                .id(7L)
                .userId("u-10001")
                .username("ops-admin")
                .password("StrongPass1")
                .userRole("ADMIN")
                .status(1)
                .build();

        assertTrue(service.create(request));

        ArgumentCaptor<AdminUserCommand> command = ArgumentCaptor.forClass(AdminUserCommand.class);
        verify(catalog).create(command.capture());
        assertEquals(7L, command.getValue().id());
        assertEquals("u-10001", command.getValue().userId());
        assertEquals("StrongPass1", command.getValue().credential());
        assertEquals("ADMIN", command.getValue().userRole());
    }

    @Test
    void queryProjectsFiltersAndTypedAccountResponse() {
        AdminUserCatalogUseCase catalog = mock(AdminUserCatalogUseCase.class);
        AdminUserAuthenticationUseCase authentication = mock(AdminUserAuthenticationUseCase.class);
        AdminUserApplicationService service = new AdminUserApplicationService(
                catalog, authentication, new FirstTimeSetupUseCase(catalog, authentication));
        AdminUserQueryRequestDTO request = AdminUserQueryRequestDTO.builder()
                .userId("u-10001")
                .username("ops")
                .status(1)
                .pageNum(2)
                .pageSize(20)
                .build();
        when(catalog.query(any())).thenReturn(List.of(account()));

        List<AdminUserResponseDTO> result = service.queryList(request);

        ArgumentCaptor<AdminUserCatalogQuery> query = ArgumentCaptor.forClass(AdminUserCatalogQuery.class);
        verify(catalog).query(query.capture());
        assertEquals("u-10001", query.getValue().userId());
        assertEquals(2, query.getValue().pageNum());
        assertEquals(20, query.getValue().pageSize());
        assertEquals(1, result.size());
        assertEquals("admin", result.get(0).getUserRole());
        assertEquals(null, result.get(0).getToken());
    }

    @Test
    void loginProjectsCommandTokenAndCompatibilityException() {
        AdminUserCatalogUseCase catalog = mock(AdminUserCatalogUseCase.class);
        AdminUserAuthenticationUseCase authentication = mock(AdminUserAuthenticationUseCase.class);
        AdminUserApplicationService service = new AdminUserApplicationService(
                catalog, authentication, new FirstTimeSetupUseCase(catalog, authentication));
        AdminUserLoginRequestDTO request = AdminUserLoginRequestDTO.builder()
                .username("ops-admin")
                .password("StrongPass1")
                .build();
        when(authentication.login(any())).thenReturn(new AdminUserLoginResult(account(), "jwt-token"));

        AdminUserResponseDTO response = service.login(request);

        ArgumentCaptor<AdminUserLoginCommand> command = ArgumentCaptor.forClass(AdminUserLoginCommand.class);
        verify(authentication).login(command.capture());
        assertEquals("ops-admin", command.getValue().username());
        assertEquals("StrongPass1", command.getValue().credential());
        assertEquals("jwt-token", response.getToken());

        when(authentication.validateLogin(any()))
                .thenThrow(new AdminUserAuthenticationException("用户已被锁定"));
        AdminUserApplicationService.LoginFailedException error = assertThrows(
                AdminUserApplicationService.LoginFailedException.class,
                () -> service.validateLogin(request));
        assertEquals("用户已被锁定", error.getMessage());
    }

    private AdminUserAccount account() {
        return new AdminUserAccount(
                7L,
                "u-10001",
                "ops-admin",
                "encoded-value",
                AdminUserRole.ADMIN,
                1,
                LocalDateTime.of(2026, 7, 30, 7, 0),
                LocalDateTime.of(2026, 7, 30, 7, 30));
    }
}
