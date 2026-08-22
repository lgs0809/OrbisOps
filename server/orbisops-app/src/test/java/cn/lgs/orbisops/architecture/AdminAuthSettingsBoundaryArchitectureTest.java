package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuthSettingsBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/";

    @Test
    void authenticationPropertiesMustEnterThroughTypedSettingsConfiguration() throws IOException {
        String service = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/security/AdminAuthService.java");
        String settings = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/security/AdminAuthSettings.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/security/AdminAuthConfiguration.java");
        String webConfig = read(TRIGGER
                + "cn/lgs/orbisops/trigger/http/admin/security/AdminWebSecurityConfig.java");

        assertAll(
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("@Resource")),
                () -> assertTrue(service.contains("AdminAuthSettings settings")),
                () -> assertTrue(service.contains("settings.jwtTtlHours()")),
                () -> assertTrue(service.contains("settings.matchesServiceCredential(token)")),
                () -> assertTrue(settings.contains("public record AdminAuthSettings(")),
                () -> assertTrue(settings.contains("hasWeakOrMissingJwtSecret()")),
                () -> assertTrue(configuration.contains("orbisops.admin.auth.jwt-secret")),
                () -> assertTrue(configuration.contains("orbisops.admin.auth.jwt-ttl-hours")),
                () -> assertTrue(configuration.contains("orbisops.admin.auth.service-token")),
                () -> assertTrue(configuration.contains("orbisops.admin.auth.reject-weak-secrets")),
                () -> assertFalse(webConfig.contains("@Value(\"${orbisops.admin.auth.jwt-secret")),
                () -> assertTrue(webConfig.contains("AdminAuthSettings authSettings")),
                () -> assertTrue(webConfig.contains("authSettings.hasWeakOrMissingJwtSecret()")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
