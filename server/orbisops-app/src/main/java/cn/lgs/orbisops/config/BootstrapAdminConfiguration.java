package cn.lgs.orbisops.config;

import cn.lgs.orbisops.application.security.AdminUserCatalogUseCase;
import cn.lgs.orbisops.application.security.AdminUserCommand;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.domain.security.AdminUserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Optional first-run administrator bootstrap.
 *
 * <p>Disabled by default. No credential is embedded in the application or repository. Operators
 * must explicitly provide the bootstrap username and password through external configuration.</p>
 */
@Configuration
public class BootstrapAdminConfiguration implements ApplicationRunner {

    private final AdminUserCatalogUseCase catalogUseCase;
    private final boolean enabled;
    private final String userId;
    private final String username;
    private final String password;

    public BootstrapAdminConfiguration(
            AdminUserCatalogUseCase catalogUseCase,
            @Value("${orbisops.bootstrap.admin.enabled:false}") boolean enabled,
            @Value("${orbisops.bootstrap.admin.user-id:}") String userId,
            @Value("${orbisops.bootstrap.admin.username:}") String username,
            @Value("${orbisops.bootstrap.admin.password:}") String password) {
        this.catalogUseCase = catalogUseCase;
        this.enabled = enabled;
        this.userId = userId;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "ORBISOPS_BOOTSTRAP_ADMIN_USERNAME and ORBISOPS_BOOTSTRAP_ADMIN_PASSWORD are required when bootstrap is enabled");
        }
        if (catalogUseCase.findByUsername(username.trim()) != null) {
            return;
        }
        boolean created = catalogUseCase.create(new AdminUserCommand(
                null,
                StringUtils.hasText(userId) ? userId.trim() : "user-" + UUID.randomUUID(),
                username.trim(),
                password,
                AdminUserRole.ADMIN.value(),
                AdminUserStatus.ENABLED));
        if (!created) {
            throw new IllegalStateException("Failed to create bootstrap administrator");
        }
    }
}
