package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalMySqlApplicationService;
import cn.lgs.orbisops.application.toolset.LocalMySqlExecutionTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsLocalMySqlAdapterTest {

    @Test
    void readonlyQueryMustBuildTypedTargetAndPreserveResourceProjection() {
        LocalMySqlApplicationService service = mock(LocalMySqlApplicationService.class);
        when(service.readonlyQuery(any(), eq("ignored-db"), eq("select 1")))
                .thenReturn(List.of(Map.of("1", 1)));
        OpsLocalMySqlAdapter adapter = new OpsLocalMySqlAdapter(service, settings());

        Map<String, Object> result = adapter.execute(
                "mysql_query_readonly",
                new OpsLocalToolArguments(Map.of(
                        "projectId", "project-1",
                        "resourceId", "mysql-prod",
                        "database", "ignored-db",
                        "sql", "select 1",
                        "username", "attacker",
                        "password", "secret")));

        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals("mysql-prod", result.get("resourceId"));
        assertEquals(List.of(Map.of("1", 1)), result.get("rows"));
        ArgumentCaptor<LocalMySqlExecutionTarget> target =
                ArgumentCaptor.forClass(LocalMySqlExecutionTarget.class);
        verify(service).readonlyQuery(target.capture(), eq("ignored-db"), eq("select 1"));
        assertEquals(new LocalMySqlExecutionTarget(
                "project-1", "mysql-prod", 37, 4), target.getValue());
    }

    @Test
    void preconditionMustForwardCompleteImmutableArgumentMap() {
        LocalMySqlApplicationService service = mock(LocalMySqlApplicationService.class);
        when(service.precondition(any(), isNull(), any()))
                .thenReturn(List.of(Map.of("config_value", "on")));
        OpsLocalMySqlAdapter adapter = new OpsLocalMySqlAdapter(service, settings());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("table", "ops_config");
        input.put("key", "switch");
        input.put("versionColumn", "version");
        input.put("optional", null);

        Map<String, Object> result = adapter.execute(
                "mysql_config_precondition_check",
                new OpsLocalToolArguments(input));

        assertEquals("SUCCEEDED", result.get("status"));
        ArgumentCaptor<Map<String, Object>> arguments = ArgumentCaptor.forClass(Map.class);
        verify(service).precondition(any(), isNull(), arguments.capture());
        assertEquals("ops_config", arguments.getValue().get("table"));
        assertEquals("version", arguments.getValue().get("versionColumn"));
        assertFalse(arguments.getValue().containsKey("database"));
        assertEquals(null, arguments.getValue().get("optional"));
    }

    @Test
    void dryRunToolsMustRemainExplicitlyUnsupportedWithoutCallingApplicationService() {
        LocalMySqlApplicationService service = mock(LocalMySqlApplicationService.class);
        OpsLocalMySqlAdapter adapter = new OpsLocalMySqlAdapter(service, settings());

        Map<String, Object> result = adapter.execute(
                "mysql_sql_dry_run",
                new OpsLocalToolArguments(Map.of()));

        assertEquals("NOT_SUPPORTED", result.get("status"));
        assertEquals(false, result.get("trustedProof"));
        verify(service, never()).readonlyQuery(any(), any(), any());
        verify(service, never()).precondition(any(), any(), any());
    }

    private OpsLocalAdapterSettings settings() {
        return new OpsLocalAdapterSettings(
                "http://prom", "http://es", "logs", "logs",
                "./logs", "./", 4, 37, 4096);
    }
}
