package cn.lgs.orbisops.trigger.application.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectResourceSchemaScannerTest {

    @Test
    void unsupportedResourceTypeReturnsUnavailableSchemaWithoutInventingObjects() {
        OpsProjectResourceSchemaScanner scanner =
                new OpsProjectResourceSchemaScanner(List.of());

        Map<String, Object> schema = scanner.scan(
                "custom",
                "local",
                Map.of());

        assertEquals("unavailable", schema.get("source"));
        assertEquals(List.of(), schema.get("objects"));
        assertTrue(String.valueOf(schema.get("message")).contains("不会生成虚构对象"));
        assertTrue(schema.containsKey("scannedAt"));
    }

    @Test
    void normalizesAliasAndRoutesToMatchingProbe() throws Exception {
        OpsProjectResourceSchemaProbe probe = mock(OpsProjectResourceSchemaProbe.class);
        when(probe.supports("postgresql")).thenReturn(true);
        when(probe.scan(eq("postgresql"),
                eq("postgresql://127.0.0.1:5432/app"), anyMap()))
                .thenReturn(Map.of("objects", List.of(Map.of("name", "orders"))));
        OpsProjectResourceSchemaScanner scanner =
                new OpsProjectResourceSchemaScanner(List.of(probe));

        Map<String, Object> schema = scanner.scan("pg", "", Map.of());

        assertEquals("live", schema.get("source"));
        assertEquals(List.of(Map.of("name", "orders")), schema.get("objects"));
        verify(probe).scan(eq("postgresql"),
                eq("postgresql://127.0.0.1:5432/app"), anyMap());
    }

    @Test
    void successfulProbeWithNoObjectsIsStillLive() throws Exception {
        OpsProjectResourceSchemaProbe probe = mock(OpsProjectResourceSchemaProbe.class);
        when(probe.supports("rabbitmq")).thenReturn(true);
        when(probe.scan(eq("rabbitmq"),
                eq("http://127.0.0.1:35672"), anyMap()))
                .thenReturn(Map.of("objects", List.of()));
        OpsProjectResourceSchemaScanner scanner =
                new OpsProjectResourceSchemaScanner(List.of(probe));

        Map<String, Object> schema = scanner.scan(
                "rabbitmq",
                "http://127.0.0.1:35672",
                Map.of());

        assertEquals("live", schema.get("source"));
        assertEquals(List.of(), schema.get("objects"));
        assertTrue(String.valueOf(schema.get("message")).contains("扫描成功"));
    }

    @Test
    void probeFailureUsesUniformUnavailableFallback() throws Exception {
        OpsProjectResourceSchemaProbe probe = mock(OpsProjectResourceSchemaProbe.class);
        when(probe.supports("redis")).thenReturn(true);
        when(probe.scan(eq("redis"), eq("redis://cache.example:6379/0"), anyMap()))
                .thenThrow(new IOException("connection refused"));
        OpsProjectResourceSchemaScanner scanner =
                new OpsProjectResourceSchemaScanner(List.of(probe));

        Map<String, Object> schema = scanner.scan(
                "redis",
                "redis://cache.example:6379/0",
                Map.of());

        assertEquals("unavailable", schema.get("source"));
        assertEquals(List.of(), schema.get("objects"));
        assertTrue(String.valueOf(schema.get("message")).contains("connection refused"));
    }
}
