package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminUserBoundaryArchitectureTest {

    private static final String APPLICATION_SECURITY =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/security/";
    private static final String DOMAIN_SECURITY =
            "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/security/";
    private static final String TRIGGER_SECURITY =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/security/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void accountCatalogAndAuthenticationMustRemainSeparatedHexagonalFlows() throws IOException {
        String catalogUseCase = read(APPLICATION_SECURITY + "AdminUserCatalogUseCase.java");
        String authenticationUseCase = read(APPLICATION_SECURITY + "AdminUserAuthenticationUseCase.java");
        String catalogPort = read(APPLICATION_SECURITY + "AdminUserCatalogPort.java");
        String authenticationPort = read(APPLICATION_SECURITY + "AdminUserAuthenticationPort.java");
        String role = read(DOMAIN_SECURITY + "AdminUserRole.java");
        String status = read(DOMAIN_SECURITY + "AdminUserStatus.java");
        String credentialRules = read(DOMAIN_SECURITY + "AdminCredentialRules.java");
        String catalogRepository = read(INFRASTRUCTURE_REPOSITORY + "AdminUserRepository.java");
        String authenticationAdapter = read(TRIGGER_SECURITY + "OpsAdminUserAuthenticationAdapter.java");
        String configuration = read(TRIGGER_SECURITY + "AdminSecurityConfiguration.java");
        String facade = read(TRIGGER_SECURITY + "AdminUserApplicationService.java");

        assertAll(
                () -> assertTrue(role.contains("enum AdminUserRole")),
                () -> assertTrue(status.contains("class AdminUserStatus")),
                () -> assertTrue(credentialRules.contains("record AdminCredentialRules")),
                () -> assertTrue(catalogPort.contains("interface AdminUserCatalogPort")),
                () -> assertTrue(authenticationPort.contains("interface AdminUserAuthenticationPort")),
                () -> assertTrue(catalogUseCase.contains("class AdminUserCatalogUseCase")),
                () -> assertTrue(catalogUseCase.contains("credentialRules.validate")),
                () -> assertTrue(catalogUseCase.contains("authenticationPort.encodeForStorage")),
                () -> assertTrue(authenticationUseCase.contains("class AdminUserAuthenticationUseCase")),
                () -> assertTrue(authenticationUseCase.contains("catalogPort.findByUsername")),
                () -> assertTrue(authenticationUseCase.contains("authenticationPort.issueToken")),
                () -> assertTrue(authenticationUseCase.contains("catalogPort.updateById(migrated)")),
                () -> assertApplicationPure(catalogUseCase),
                () -> assertApplicationPure(authenticationUseCase),
                () -> assertTrue(catalogRepository.contains("implements AdminUserCatalogPort")),
                () -> assertTrue(catalogRepository.contains("AdminUserAccount")),
                () -> assertFalse(catalogRepository.contains("domain.agent")),
                () -> assertFalse(catalogRepository.contains("AdminUserRecord")),
                () -> assertTrue(authenticationAdapter.contains("AdminAuthService")),
                () -> assertFalse(authenticationAdapter.contains("IAdminUserRepository")),
                () -> assertTrue(configuration.contains("AdminCredentialRules adminCredentialRules")),
                () -> assertTrue(configuration.contains("AdminUserCatalogUseCase adminUserCatalogUseCase")),
                () -> assertTrue(configuration.contains("AdminUserAuthenticationUseCase adminUserAuthenticationUseCase")),
                () -> assertFalse(configuration.contains("IAdminUserRepository")),
                () -> assertFalse(configuration.contains("new OpsAdminUserCatalogAdapter")),
                () -> assertTrue(facade.contains("private final AdminUserCatalogUseCase catalogUseCase")),
                () -> assertTrue(facade.contains("private final AdminUserAuthenticationUseCase authenticationUseCase")),
                () -> assertFalse(facade.contains("IAdminUserRepository")),
                () -> assertFalse(facade.contains("AdminUserRecord")),
                () -> assertFalse(facade.contains("AdminAuthService")),
                () -> assertFalse(facade.contains("AdminUserSecuritySettings")),
                () -> assertFalse(facade.contains("BeanUtils")),
                () -> assertFalse(facade.contains("LocalDateTime.now")),
                () -> assertFalse(facade.contains("@Autowired")),
                () -> assertFalse(facade.contains("public AdminUserApplicationService()")));
    }

    private void assertApplicationPure(String source) {
        assertFalse(source.contains("org.springframework"));
        assertFalse(source.contains("IAdminUserRepository"));
        assertFalse(source.contains("AdminUserRecord"));
        assertFalse(source.contains("AdminAuthService"));
        assertFalse(source.contains("DTO"));
        assertFalse(source.contains("BeanUtils"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
