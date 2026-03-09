package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsLocalHostAdaptersTest {

    @Test
    void dockerLogsMustProjectBoundedCommandAndExecutionSettings() {
        LocalHostApplicationService service = mock(LocalHostApplicationService.class);
        when(service.dockerName("app-1")).thenReturn("app-1");
        when(service.run(any(), eq("."), eq("/compose"), eq(false), eq(4), eq(4096)))
                .thenReturn("logs");
        OpsLocalDockerAdapter adapter = new OpsLocalDockerAdapter(service, settings());

        Map<String, Object> result = adapter.execute(
                "docker_logs",
                new OpsLocalToolArguments(Map.of(
                        "container", "app-1",
                        "tail", 999)));

        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals("logs", result.get("output"));
        assertEquals(List.of("docker", "logs", "--tail", "500", "app-1"),
                result.get("command"));
        verify(service).run(
                eq(List.of("docker", "logs", "--tail", "500", "app-1")),
                eq("."), eq("/compose"), eq(false), eq(4), eq(4096));
    }

    @Test
    void composeConfigMustForwardControlledDirectoryAndAllowedRoots() {
        LocalHostApplicationService service = mock(LocalHostApplicationService.class);
        when(service.run(any(), eq("/workspace/app"), eq("/compose"), eq(true), eq(4), eq(4096)))
                .thenReturn("valid");
        OpsLocalDockerAdapter adapter = new OpsLocalDockerAdapter(service, settings());

        Map<String, Object> result = adapter.execute(
                "docker_compose_config",
                new OpsLocalToolArguments(Map.of("composeDir", "/workspace/app")));

        assertEquals(List.of("docker", "compose", "config"), result.get("command"));
        verify(service).run(
                eq(List.of("docker", "compose", "config")),
                eq("/workspace/app"), eq("/compose"), eq(true), eq(4), eq(4096));
    }

    @Test
    void logAdapterMustDelegateTailAndSearchWithConfiguredRootAndLimit() {
        LocalHostApplicationService service = mock(LocalHostApplicationService.class);
        when(service.tail("/logs/app.log", "/logs", 9, 37))
                .thenReturn(List.of("tail-line"));
        when(service.search("/logs/app.log", "/logs", "ERROR", 5, 37))
                .thenReturn(List.of("error-line"));
        OpsLocalLogAdapter adapter = new OpsLocalLogAdapter(service, settings());

        Map<String, Object> tail = adapter.execute(
                "tail_log",
                new OpsLocalToolArguments(Map.of(
                        "file", "/logs/app.log",
                        "limit", 9)));
        Map<String, Object> search = adapter.execute(
                "grep_log",
                new OpsLocalToolArguments(Map.of(
                        "file", "/logs/app.log",
                        "query", "ERROR",
                        "limit", 5)));

        assertEquals(List.of("tail-line"), tail.get("lines"));
        assertEquals(List.of("error-line"), search.get("lines"));
        assertEquals("ERROR", search.get("query"));
        verify(service).tail("/logs/app.log", "/logs", 9, 37);
        verify(service).search("/logs/app.log", "/logs", "ERROR", 5, 37);
    }

    private OpsLocalAdapterSettings settings() {
        return new OpsLocalAdapterSettings(
                "http://prom", "http://es", "logs", "logs",
                "/logs", "/compose", 4, 37, 4096);
    }
}
