package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.domain.security.AdminPhoneNumber;
import cn.lgs.orbisops.domain.security.AdminUserStatus;

import java.util.UUID;

/** Owns the one-time transition from an empty account catalog to the first administrator. */
public final class FirstTimeSetupUseCase {

    public static final String ALREADY_COMPLETED = "FIRST_TIME_SETUP_ALREADY_COMPLETED";

    private final AdminUserCatalogUseCase catalogUseCase;
    private final AdminUserAuthenticationUseCase authenticationUseCase;

    public FirstTimeSetupUseCase(
            AdminUserCatalogUseCase catalogUseCase,
            AdminUserAuthenticationUseCase authenticationUseCase) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("ADMIN_USER_CATALOG_USE_CASE_REQUIRED");
        }
        if (authenticationUseCase == null) {
            throw new IllegalArgumentException("ADMIN_USER_AUTHENTICATION_USE_CASE_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
        this.authenticationUseCase = authenticationUseCase;
    }

    public boolean isRequired() {
        return catalogUseCase.listAll().isEmpty();
    }

    public synchronized AdminUserLoginResult setup(String username, String password) {
        return setup(username, password, null);
    }

    public synchronized AdminUserLoginResult setup(String username, String password, String phone) {
        if (!isRequired()) {
            throw new IllegalStateException(ALREADY_COMPLETED);
        }
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedPhone = AdminPhoneNumber.optional(phone);
        String userId = "user-" + UUID.randomUUID();
        boolean created = catalogUseCase.create(new AdminUserCommand(
                null,
                userId,
                normalizedUsername,
                password,
                AdminUserRole.ADMIN.value(),
                AdminUserStatus.ENABLED,
                normalizedPhone));
        if (!created) {
            throw new IllegalStateException("FIRST_TIME_SETUP_CREATE_FAILED");
        }
        return authenticationUseCase.login(new AdminUserLoginCommand(normalizedUsername, password, normalizedPhone));
    }
}
