package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.api.dto.AdminUserLoginRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserResponseDTO;
import cn.lgs.orbisops.api.dto.FirstTimeSetupRequestDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** HTTP-facing facade for account catalog and authentication flows. */
@Service
public class AdminUserApplicationService {

    private final AdminUserCatalogUseCase catalogUseCase;
    private final AdminUserAuthenticationUseCase authenticationUseCase;
    private final FirstTimeSetupUseCase firstTimeSetupUseCase;

    public AdminUserApplicationService(
            AdminUserCatalogUseCase catalogUseCase,
            AdminUserAuthenticationUseCase authenticationUseCase,
            FirstTimeSetupUseCase firstTimeSetupUseCase) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("ADMIN_USER_CATALOG_USE_CASE_REQUIRED");
        }
        if (authenticationUseCase == null) {
            throw new IllegalArgumentException("ADMIN_USER_AUTHENTICATION_USE_CASE_REQUIRED");
        }
        if (firstTimeSetupUseCase == null) {
            throw new IllegalArgumentException("FIRST_TIME_SETUP_USE_CASE_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
        this.authenticationUseCase = authenticationUseCase;
        this.firstTimeSetupUseCase = firstTimeSetupUseCase;
    }

    public boolean firstTimeSetupRequired() {
        return firstTimeSetupUseCase.isRequired();
    }

    @Transactional
    public AdminUserResponseDTO setupFirstAdministrator(FirstTimeSetupRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("FIRST_TIME_SETUP_REQUEST_REQUIRED");
        }
        try {
            AdminUserLoginResult result = firstTimeSetupUseCase.setup(request.getUsername(), request.getPassword());
            return toResponse(result.account(), result.token());
        } catch (AdminUserAuthenticationException error) {
            throw new LoginFailedException(error.getMessage());
        }
    }

    public boolean create(AdminUserRequestDTO request) {
        return catalogUseCase.create(toCommand(request));
    }

    public boolean updateById(AdminUserRequestDTO request) {
        return catalogUseCase.updateById(toCommand(request));
    }

    public boolean updateByUserId(AdminUserRequestDTO request) {
        return catalogUseCase.updateByUserId(toCommand(request));
    }

    public boolean deleteById(Long id) {
        return catalogUseCase.deleteById(id);
    }

    public boolean deleteByUserId(String userId) {
        return catalogUseCase.deleteByUserId(userId);
    }

    public AdminUserResponseDTO queryById(Long id) {
        return toResponse(catalogUseCase.findById(id), null);
    }

    public AdminUserResponseDTO queryByUserId(String userId) {
        return toResponse(catalogUseCase.findByUserId(userId), null);
    }

    public AdminUserResponseDTO queryByUsername(String username) {
        return toResponse(catalogUseCase.findByUsername(username), null);
    }

    public List<AdminUserResponseDTO> queryEnabled() {
        return toResponses(catalogUseCase.listEnabled());
    }

    public List<AdminUserResponseDTO> queryByStatus(Integer status) {
        return toResponses(catalogUseCase.listByStatus(status));
    }

    public List<AdminUserResponseDTO> queryAll() {
        return toResponses(catalogUseCase.listAll());
    }

    public List<AdminUserResponseDTO> queryList(AdminUserQueryRequestDTO request) {
        return toResponses(catalogUseCase.query(toQuery(request)));
    }

    public AdminUserResponseDTO login(AdminUserLoginRequestDTO request) {
        try {
            AdminUserLoginResult result = authenticationUseCase.login(toLoginCommand(request));
            return toResponse(result.account(), result.token());
        } catch (AdminUserAuthenticationException error) {
            throw new LoginFailedException(error.getMessage());
        }
    }

    public boolean validateLogin(AdminUserLoginRequestDTO request) {
        try {
            return authenticationUseCase.validateLogin(toLoginCommand(request));
        } catch (AdminUserAuthenticationException error) {
            throw new LoginFailedException(error.getMessage());
        }
    }

    public boolean logout(String authorization) {
        return authenticationUseCase.logout(authorization);
    }

    private AdminUserCommand toCommand(AdminUserRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new AdminUserCommand(
                request.getId(),
                request.getUserId(),
                request.getUsername(),
                request.getPassword(),
                request.getUserRole(),
                request.getStatus());
    }

    private AdminUserCatalogQuery toQuery(AdminUserQueryRequestDTO request) {
        if (request == null) {
            return AdminUserCatalogQuery.all();
        }
        return new AdminUserCatalogQuery(
                request.getUserId(),
                request.getUsername(),
                request.getStatus(),
                request.getPageNum(),
                request.getPageSize());
    }

    private AdminUserLoginCommand toLoginCommand(AdminUserLoginRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new AdminUserLoginCommand(request.getUsername(), request.getPassword());
    }

    private List<AdminUserResponseDTO> toResponses(List<AdminUserAccount> accounts) {
        return accounts == null || accounts.isEmpty()
                ? List.of()
                : accounts.stream().map(account -> toResponse(account, null)).toList();
    }

    private AdminUserResponseDTO toResponse(AdminUserAccount account, String token) {
        if (account == null) {
            return null;
        }
        return AdminUserResponseDTO.builder()
                .id(account.id())
                .userId(account.userId())
                .username(account.username())
                .userRole(account.role() == null ? AdminUserRole.ADMIN.value() : account.role().value())
                .status(account.status())
                .createTime(account.createTime())
                .updateTime(account.updateTime())
                .token(token)
                .build();
    }

    public static class LoginFailedException extends RuntimeException {
        public LoginFailedException(String message) {
            super(message);
        }
    }
}
