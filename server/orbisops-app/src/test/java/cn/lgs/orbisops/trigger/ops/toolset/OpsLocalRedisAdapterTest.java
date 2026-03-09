package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalRedisApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsLocalRedisAdapterTest {

    @Test
    void readToolsMustDelegateAndProjectStableResults() {
        LocalRedisApplicationService service = mock(LocalRedisApplicationService.class);
        when(service.info()).thenReturn("redis_version:7");
        when(service.get("app:key")).thenReturn("value");
        when(service.ttl("app:key")).thenReturn(60L);
        when(service.type("app:key")).thenReturn("string");
        OpsLocalRedisAdapter adapter = new OpsLocalRedisAdapter(service, settings());

        assertEquals("redis_version:7", adapter.execute(
                "redis_info", new OpsLocalToolArguments(Map.of())).get("info"));
        assertEquals("value", adapter.execute(
                "redis_get", new OpsLocalToolArguments(Map.of("key", "app:key"))).get("value"));
        assertEquals(60L, adapter.execute(
                "redis_ttl", new OpsLocalToolArguments(Map.of("key", "app:key"))).get("ttl"));
        assertEquals("string", adapter.execute(
                "redis_type", new OpsLocalToolArguments(Map.of("key", "app:key"))).get("type"));
    }

    @Test
    void scanMustUseConfiguredMaxRowsAndPreservePatternProjection() {
        LocalRedisApplicationService service = mock(LocalRedisApplicationService.class);
        when(service.scan("app:*", 99, 37)).thenReturn(List.of("app:1", "app:2"));
        OpsLocalRedisAdapter adapter = new OpsLocalRedisAdapter(service, settings());

        Map<String, Object> result = adapter.execute(
                "redis_scan",
                new OpsLocalToolArguments(Map.of(
                        "pattern", "app:*",
                        "limit", 99)));

        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals("app:*", result.get("pattern"));
        assertEquals(List.of("app:1", "app:2"), result.get("keys"));
        verify(service).scan(eq("app:*"), eq(99), eq(37));
    }

    @Test
    void unsupportedMemoryAndDryRunToolsMustNotInventProof() {
        LocalRedisApplicationService service = mock(LocalRedisApplicationService.class);
        OpsLocalRedisAdapter adapter = new OpsLocalRedisAdapter(service, settings());

        Map<String, Object> memory = adapter.execute(
                "redis_memory_usage",
                new OpsLocalToolArguments(Map.of()));
        Map<String, Object> dryRun = adapter.execute(
                "redis_dry_run_expire",
                new OpsLocalToolArguments(Map.of()));

        assertEquals("NOT_SUPPORTED", memory.get("status"));
        assertEquals(false, memory.get("trustedProof"));
        assertEquals("NOT_SUPPORTED", dryRun.get("status"));
        assertEquals(false, dryRun.get("trustedProof"));
    }

    private OpsLocalAdapterSettings settings() {
        return new OpsLocalAdapterSettings(
                "http://prom", "http://es", "logs", "logs",
                "./logs", "./", 4, 37, 4096);
    }
}
