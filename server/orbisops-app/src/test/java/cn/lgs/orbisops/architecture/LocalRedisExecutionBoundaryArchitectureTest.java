package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRedisExecutionBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/toolset/";
    private static final String DOMAIN =
            "orbisops-domain/src/main/java/"
                    + "cn/lgs/orbisops/domain/toolset/service/";
    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/"
                    + "cn/lgs/orbisops/infrastructure/adapter/toolset/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/";

    @Test
    void triggerMustOnlyRouteAndPresentRedisOperations()
            throws IOException {
        String facade = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalOpsAdapterService.java");
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalRedisAdapter.java");
        String readHandler = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsRedisLocalToolExecutionHandler.java");

        assertAll(
                () -> assertTrue(facade.contains("handlerRegistry.execute(")),
                () -> assertTrue(readHandler.contains("adapter.execute(toolName, arguments)")),
                () -> assertFalse(facade.contains("private final LocalRedisApplicationService")),
                () -> assertFalse(facade.contains(".scan(")),
                () -> assertFalse(facade.contains(".expire(")),
                () -> assertFalse(facade.contains(".set(")),
                () -> assertTrue(adapter.contains("service.info()")),
                () -> assertTrue(adapter.contains("service.scan(")),
                () -> assertFalse(adapter.contains("service.expire(")),
                () -> assertFalse(adapter.contains("service.set(")),
                () -> assertFalse(adapter.contains("StringRedisTemplate")),
                () -> assertFalse(adapter.contains("ScanOptions")),
                () -> assertFalse(adapter.contains("opsForValue()")),
                () -> assertFalse(adapter.contains("opsForList()")),
                () -> assertFalse(adapter.contains("REDIS_SCAN_PATTERN_NOT_ALLOWED")),
                () -> assertFalse(adapter.contains("REDIS_KEY_NOT_ALLOWED")));
    }

    @Test
    void domainAndApplicationMustOwnPolicyAndUseCases()
            throws IOException {
        String policy = read(DOMAIN + "LocalRedisPolicy.java");
        String application = read(APPLICATION
                + "LocalRedisExecutionPort.java")
                + read(APPLICATION + "LocalRedisApplicationService.java");

        assertAll(
                () -> assertTrue(policy.contains("scanPattern(")),
                () -> assertTrue(policy.contains(
                        "REDIS_SCAN_PATTERN_NOT_ALLOWED")),
                () -> assertTrue(policy.contains("REDIS_KEY_NOT_ALLOWED")),
                () -> assertTrue(application.contains("executionPort.scan(")),
                () -> assertFalse(application.contains("executionPort.expire(")),
                () -> assertFalse(application.contains("executionPort.set(")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("StringRedisTemplate")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("StringRedisTemplate")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void infrastructureMustOwnRedisClientAndOptionalAvailability()
            throws IOException {
        String adapter = read(INFRASTRUCTURE
                + "RedisLocalRedisExecutionAdapter.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/toolset/"
                + "OpsToolsetApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(adapter.contains(
                        "implements LocalRedisExecutionPort")),
                () -> assertTrue(adapter.contains("StringRedisTemplate")),
                () -> assertTrue(adapter.contains("ObjectProvider")),
                () -> assertTrue(adapter.contains("requiredTemplate()")),
                () -> assertTrue(adapter.contains("opsForValue()")),
                () -> assertTrue(adapter.contains("ScanOptions")),
                () -> assertTrue(adapter.contains("Cursor<String>")),
                () -> assertFalse(adapter.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(Files.exists(projectRoot().resolve(INFRASTRUCTURE
                        + "UnavailableLocalRedisExecutionAdapter.java"))),
                () -> assertTrue(configuration.contains(
                        "LocalRedisExecutionPort executionPort")),
                () -> assertTrue(configuration.contains(
                        "new LocalRedisApplicationService(executionPort)")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(
                current.resolve("orbisops-application"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(
                parent.resolve("orbisops-application"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(
                nested.resolve("orbisops-application"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
