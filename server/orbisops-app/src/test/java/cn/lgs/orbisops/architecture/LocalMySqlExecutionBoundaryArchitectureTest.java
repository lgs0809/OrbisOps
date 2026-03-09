package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMySqlExecutionBoundaryArchitectureTest {

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
    void triggerAdapterMustOnlyRouteAndMapMySqlOperations()
            throws IOException {
        String facade = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalOpsAdapterService.java");
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalMySqlAdapter.java");
        String readHandler = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsMySqlLocalToolExecutionHandler.java");

        assertAll(
                () -> assertTrue(facade.contains("handlerRegistry.execute(")),
                () -> assertTrue(readHandler.contains("adapter.execute(toolName, arguments)")),
                () -> assertFalse(facade.contains("LocalMySqlExecutionTarget")),
                () -> assertFalse(facade.contains("readonlyQuery(")),
                () -> assertFalse(facade.contains("precondition(")),
                () -> assertFalse(facade.contains("updateConfig(")),
                () -> assertTrue(adapter.contains("new LocalMySqlExecutionTarget(")),
                () -> assertTrue(adapter.contains("service.readonlyQuery(")),
                () -> assertTrue(adapter.contains("service.precondition(")),
                () -> assertFalse(adapter.contains("updateConfig(")),
                () -> assertFalse(adapter.contains("JdbcTemplate")),
                () -> assertFalse(adapter.contains("DriverManager")),
                () -> assertFalse(adapter.contains("ResultSet")),
                () -> assertFalse(adapter.contains("PreparedStatement")),
                () -> assertFalse(adapter.contains("ResolveProjectRuntimeResourceQuery")),
                () -> assertFalse(adapter.contains("MYSQL_READONLY_SQL_REQUIRED")),
                () -> assertFalse(adapter.contains("SQL_WRITE_KEYWORDS")),
                () -> assertFalse(adapter.contains("UPDATE `")));
    }

    @Test
    void domainAndApplicationMustOwnPolicyAndUseCases()
            throws IOException {
        String policy = read(DOMAIN + "LocalMySqlPolicy.java");
        String application = read(APPLICATION
                + "LocalMySqlExecutionTarget.java")
                + read(APPLICATION + "LocalMySqlExecutionPort.java")
                + read(APPLICATION + "LocalMySqlApplicationService.java");

        assertAll(
                () -> assertTrue(policy.contains("SQL_WRITE_KEYWORDS")),
                () -> assertTrue(policy.contains("readonlySql(")),
                () -> assertTrue(policy.contains("identifier(")),
                () -> assertTrue(policy.contains("assertResourceBoundary(")),
                () -> assertTrue(policy.contains("sanitizeRows(")),
                () -> assertTrue(application.contains(
                        "executionPort.query(target, sql, parameters)")),
                () -> assertFalse(application.contains("updatePrimary(")),
                () -> assertFalse(application.contains("updateConfig(")),
                () -> assertTrue(application.contains(
                        "policy.readonlySql(sql)")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("java.sql")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void infrastructureMustOwnPrimaryAndProjectJdbcExecution()
            throws IOException {
        String adapter = read(INFRASTRUCTURE
                + "JdbcLocalMySqlExecutionAdapter.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/toolset/"
                + "OpsToolsetApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(adapter.contains(
                        "implements LocalMySqlExecutionPort")),
                () -> assertTrue(adapter.contains("JdbcTemplate")),
                () -> assertTrue(adapter.contains("DriverManager.getConnection(")),
                () -> assertTrue(adapter.contains("PreparedStatement")),
                () -> assertTrue(adapter.contains("ResultSet")),
                () -> assertTrue(adapter.contains(
                        "ResolveProjectRuntimeResourceQuery")),
                () -> assertTrue(adapter.contains("connection.setReadOnly(true)")),
                () -> assertFalse(adapter.contains("OpsLocalOpsAdapterService")),
                () -> assertFalse(adapter.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(configuration.contains(
                        "LocalMySqlExecutionPort executionPort")),
                () -> assertTrue(configuration.contains(
                        "new LocalMySqlApplicationService(executionPort)")));
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
