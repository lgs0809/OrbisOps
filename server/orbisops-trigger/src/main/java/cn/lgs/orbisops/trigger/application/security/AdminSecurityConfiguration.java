package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.application.security.AdminUserAuthenticationPort;
import cn.lgs.orbisops.application.security.AdminUserAuthenticationUseCase;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.application.security.AdminUserCatalogUseCase;
import cn.lgs.orbisops.application.security.FirstTimeSetupUseCase;
import cn.lgs.orbisops.domain.security.AdminCredentialRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Spring property and Application assembly boundary for account security. */
@Configuration
public class AdminSecurityConfiguration {

    @Bean
    public AdminUserSecuritySettings adminUserSecuritySettings(Environment environment) {
        return new AdminUserSecuritySettings(
                environment.getProperty("orbisops.admin.auth.password-min-length", Integer.class, 8),
                environment.getProperty("orbisops.admin.auth.allow-prehashed-passwords", Boolean.class, false));
    }

    @Bean
    public AdminCredentialRules adminCredentialRules(AdminUserSecuritySettings settings) {
        return new AdminCredentialRules(settings.minimumLength(), settings.prehashedAllowed());
    }

    @Bean
    public AdminUserAuthenticationPort adminUserAuthenticationPort(AdminAuthService authenticationService) {
        return new OpsAdminUserAuthenticationAdapter(authenticationService);
    }

    @Bean
    public AdminUserCatalogUseCase adminUserCatalogUseCase(
            AdminUserCatalogPort catalogPort,
            AdminUserAuthenticationPort authenticationPort,
            AdminCredentialRules credentialRules) {
        return new AdminUserCatalogUseCase(catalogPort, authenticationPort, credentialRules);
    }

    @Bean
    public AdminUserAuthenticationUseCase adminUserAuthenticationUseCase(
            AdminUserCatalogPort catalogPort,
            AdminUserAuthenticationPort authenticationPort) {
        return new AdminUserAuthenticationUseCase(catalogPort, authenticationPort);
    }

    @Bean
    public FirstTimeSetupUseCase firstTimeSetupUseCase(
            AdminUserCatalogUseCase catalogUseCase,
            AdminUserAuthenticationUseCase authenticationUseCase) {
        return new FirstTimeSetupUseCase(catalogUseCase, authenticationUseCase);
    }
}
