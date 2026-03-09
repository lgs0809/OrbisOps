package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;
import cn.lgs.orbisops.application.toolset.LocalHostCommandPort;
import cn.lgs.orbisops.application.toolset.LocalMySqlApplicationService;
import cn.lgs.orbisops.application.toolset.LocalMySqlExecutionPort;
import cn.lgs.orbisops.application.toolset.LocalMySqlExecutionTarget;
import cn.lgs.orbisops.application.toolset.LocalRedisApplicationService;
import cn.lgs.orbisops.application.toolset.LocalRedisExecutionPort;
import cn.lgs.orbisops.infrastructure.adapter.toolset.FileLocalLogAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsLocalOpsAdapterServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mysqlReadOnlyToolRejectsWriteSqlBeforeInfrastructureExecution() {
        LocalMySqlExecutionPort executionPort = mock(LocalMySqlExecutionPort.class);
        OpsLocalOpsAdapterService service = service(executionPort);

        SecurityException ex = assertThrows(SecurityException.class, () -> service.execute(
                "LOCAL_MYSQL",
                "mysql_query_readonly",
                Map.of("sql", "update orders set status='PAID' where id=1")));

        assertTrue(ex.getMessage().contains("MYSQL_READONLY_SQL_REQUIRED"));
        verifyNoInteractions(executionPort);
    }

    @Test
    void mysqlReadOnlyToolAllowsSelectAndUsesTypedExecutionPort() {
        LocalMySqlExecutionPort executionPort = mock(LocalMySqlExecutionPort.class);
        when(executionPort.query(
                any(LocalMySqlExecutionTarget.class),
                eq("select * from orders limit 1"),
                eq(List.of())))
                .thenReturn(List.of(Map.of("id", 1)));
        OpsLocalOpsAdapterService service = service(executionPort);

        Map<String, Object> result = service.execute(
                "LOCAL_MYSQL",
                "mysql_query_readonly",
                Map.of("sql", "select * from orders limit 1"));

        assertEquals("SUCCEEDED", result.get("status"));
        verify(executionPort).query(
                any(LocalMySqlExecutionTarget.class),
                eq("select * from orders limit 1"),
                eq(List.of()));
    }

    @Test
    void projectMysqlRejectsDatabaseWithoutConfiguredResourceId() {
        LocalMySqlExecutionPort executionPort = mock(LocalMySqlExecutionPort.class);
        OpsLocalOpsAdapterService service = service(executionPort);

        SecurityException ex = assertThrows(SecurityException.class, () -> service.execute(
                "LOCAL_MYSQL",
                "mysql_query_readonly",
                Map.of("database", "demo_db", "sql", "select 1")));

        assertTrue(ex.getMessage().contains("PROJECT_MYSQL_RESOURCE_ID_REQUIRED"));
        verifyNoInteractions(executionPort);
    }

    @Test
    void projectMysqlRequestCredentialsAreNotPassedToExecutionPort() {
        LocalMySqlExecutionPort executionPort = mock(LocalMySqlExecutionPort.class);
        when(executionPort.query(
                any(LocalMySqlExecutionTarget.class),
                eq("select 1"),
                eq(List.of())))
                .thenReturn(List.of(Map.of("1", 1)));
        OpsLocalOpsAdapterService service = service(executionPort);

        service.execute(
                "LOCAL_MYSQL",
                "mysql_query_readonly",
                Map.of(
                        "projectId", "demo-project",
                        "resourceId", "demo-project-mysql-prod",
                        "url", "jdbc:mysql://attacker/db",
                        "username", "attacker",
                        "password", "ignored",
                        "sql", "select 1"));

        verify(executionPort).query(
                eq(new LocalMySqlExecutionTarget(
                        "demo-project",
                        "demo-project-mysql-prod",
                        200,
                        1)),
                eq("select 1"),
                eq(List.of()));
    }

    @Test
    void projectMysqlRejectsWriteSqlBeforeResourceExecution() {
        LocalMySqlExecutionPort executionPort = mock(LocalMySqlExecutionPort.class);
        OpsLocalOpsAdapterService service = service(executionPort);

        SecurityException ex = assertThrows(SecurityException.class, () -> service.execute(
                "LOCAL_MYSQL",
                "mysql_query_readonly",
                Map.of(
                        "projectId", "demo-project",
                        "resourceId", "demo-project-mysql-prod",
                        "sql", "delete from sku where id = 1")));

        assertTrue(ex.getMessage().contains("MYSQL_READONLY_SQL_REQUIRED"));
        verifyNoInteractions(executionPort);
    }

    @Test
    void localLogMasksSecretsAndStaysInsideAllowedRoot() throws Exception {
        Path logFile = tempDir.resolve("app.log");
        Files.writeString(logFile, """
                INFO started
                ERROR DB_PASSWORD=abc123 token:hello
                """);
        OpsLocalOpsAdapterService service = service(mock(LocalMySqlExecutionPort.class));

        Map<String, Object> result = service.execute(
                "LOCAL_LOG",
                "grep_log",
                Map.of("file", logFile.toString(), "query", "ERROR", "limit", 5));

        String line = String.valueOf(((List<?>) result.get("lines")).get(0));
        assertTrue(line.contains("DB_PASSWORD=***"));
        assertTrue(line.contains("token=***"));
    }

    @Test
    void localLogRejectsPathOutsideAllowedRoot() {
        OpsLocalOpsAdapterService service = service(mock(LocalMySqlExecutionPort.class));

        SecurityException ex = assertThrows(SecurityException.class, () -> service.execute(
                "LOCAL_LOG",
                "tail_log",
                Map.of("file", tempDir.resolveSibling("outside.log").toString())));

        assertTrue(ex.getMessage().contains("LOCAL_LOG_PATH_NOT_ALLOWED"));
    }

    @Test
    void elasticsearchRequiresWhitelistedIndexAndTimeRange() {
        OpsLocalOpsAdapterService service = service(mock(LocalMySqlExecutionPort.class));

        IllegalArgumentException noRange = assertThrows(IllegalArgumentException.class, () -> service.execute(
                "LOCAL_ELASTICSEARCH",
                "elk_search",
                Map.of("index", "app-logs", "query", "level:ERROR")));
        assertTrue(noRange.getMessage().contains("ELK_TIME_RANGE_REQUIRED"));

        SecurityException badIndex = assertThrows(SecurityException.class, () -> service.execute(
                "LOCAL_ELASTICSEARCH",
                "elk_search",
                Map.of(
                        "index", "other-logs",
                        "query", "level:ERROR",
                        "rangeMinutes", 10)));
        assertTrue(badIndex.getMessage().contains("ELK_INDEX_NOT_ALLOWED"));
    }

    @Test
    void redisScanRejectsUnboundedWildcardPattern() {
        LocalRedisExecutionPort redisPort = mock(LocalRedisExecutionPort.class);
        OpsLocalOpsAdapterService service = service(
                mock(LocalMySqlExecutionPort.class),
                redisPort);

        SecurityException ex = assertThrows(SecurityException.class, () -> service.execute(
                "LOCAL_REDIS",
                "redis_scan",
                Map.of("pattern", "*")));

        assertTrue(ex.getMessage().contains("REDIS_SCAN_PATTERN_NOT_ALLOWED"));
    }

    @Test
    void prometheusRejectsInvalidStepBeforeHttpCall() {
        OpsLocalOpsAdapterService service = service(mock(LocalMySqlExecutionPort.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.execute(
                "LOCAL_PROMETHEUS",
                "prometheus_range_query",
                Map.of("query", "up", "rangeMinutes", 10, "step", "0s")));

        assertTrue(ex.getMessage().contains("PROMETHEUS_STEP_INVALID"));
    }

    @Test
    void unknownLocalAdapterFailsClosed() {
        OpsLocalOpsAdapterService service = service(mock(LocalMySqlExecutionPort.class));

        SecurityException ex = assertThrows(SecurityException.class, () -> service.execute(
                "LOCAL_PRODUCTION_MUTATOR",
                "prod_write",
                Map.of()));

        assertTrue(ex.getMessage().contains("LOCAL_ADAPTER_NOT_IMPLEMENTED"));
    }

    private OpsLocalOpsAdapterService service(
            LocalMySqlExecutionPort executionPort) {
        return service(
                executionPort,
                mock(LocalRedisExecutionPort.class));
    }

    private OpsLocalOpsAdapterService service(
            LocalMySqlExecutionPort executionPort,
            LocalRedisExecutionPort redisPort) {
        LocalHostCommandPort commandPort = mock(LocalHostCommandPort.class);
        return new OpsLocalOpsAdapterService(
                new LocalHostApplicationService(
                        commandPort,
                        new FileLocalLogAdapter()),
                new LocalMySqlApplicationService(executionPort),
                new LocalRedisApplicationService(redisPort),
                new OpsLocalAdapterSettings(
                        "http://127.0.0.1:9090",
                        "http://127.0.0.1:9200",
                        "app-logs",
                        "app-logs",
                        tempDir.toString(),
                        "./",
                        1,
                        200,
                        1024));
    }
}
