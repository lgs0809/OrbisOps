package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionToolRuntimeProfileArchitectureTest {

    @Test
    void productionProfileMustDisableExternalLocalAndGenericLandingSurfaces() throws IOException {
        String prod = read("orbisops-app/src/main/resources/application-prod.yml");
        String settings = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/toolset/OpsExternalLocalProviderSettings.java");
        String readiness = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/toolset/OpsToolRuntimeReadiness.java");
        String environment = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/ops/OpsCapabilityReadinessEnvironmentAdapter.java");
        String jdbcReadiness = read("orbisops-infrastructure/src/main/java/"
                + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcChangePackageReadinessAdapter.java");

        assertAll(
                () -> assertTrue(prod.contains("mode: PRODUCTION")),
                () -> assertTrue(prod.contains("prometheus-enabled: false")),
                () -> assertTrue(prod.contains("elasticsearch-enabled: false")),
                () -> assertTrue(prod.contains("mysql-enabled: false")),
                () -> assertTrue(prod.contains("redis-enabled: false")),
                () -> assertTrue(prod.contains("docker-enabled: false")),
                () -> assertTrue(prod.contains("forbid-arbitrary-sql: true")),
                () -> assertTrue(prod.contains("forbid-arbitrary-shell: true")),
                () -> assertTrue(prod.contains("forbid-wildcard-rollout: true")),
                () -> assertTrue(settings.contains("externalResourcesRequireMcp")),
                () -> assertTrue(settings.contains("ARBITRARY_SQL_TOOLSETS")),
                () -> assertTrue(settings.contains("ARBITRARY_COMMAND_TOOLSETS")),
                () -> assertFalse(readiness.contains("LEGACY_LOCAL_LANDING")),
                () -> assertTrue(environment.contains("toolRuntimeProfile")),
                () -> assertFalse(jdbcReadiness.contains("legacyMysqlLandingEnabled")),
                () -> assertFalse(jdbcReadiness.contains("ai_ops_mysql_landing_receipt")));
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
