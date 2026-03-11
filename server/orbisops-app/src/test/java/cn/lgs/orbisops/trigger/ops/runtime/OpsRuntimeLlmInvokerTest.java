package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsRuntimeLlmInvokerTest {

    @Test
    void unavailableModelFailsClosedAndEmitsBlockedEvent() {
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        when(availability.isChatAvailable()).thenReturn(false);
        when(availability.unavailableMessage("Agent 模型调用")).thenReturn("model unavailable");
        OpsRuntimeLlmInvoker invoker = new OpsRuntimeLlmInvoker(availability);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        AtomicReference<OpsRuntimeEvent> delivered = new AtomicReference<>();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> invoker.call(
                "system", "user", null, events, delivered::set, System.nanoTime()));

        assertEquals("model unavailable", error.getMessage());
        assertEquals(1, events.size());
        assertEquals("MODEL_CALL_BLOCKED", events.get(0).getEventType());
        assertEquals("BLOCKED", events.get(0).getStatus());
        assertEquals("MODEL_UNAVAILABLE", events.get(0).getPayload().get("reasonCode"));
        assertEquals(events.get(0), delivered.get());
    }

    @Test
    void missingAvailabilityAlsoFailsClosed() {
        OpsRuntimeLlmInvoker invoker = new OpsRuntimeLlmInvoker(null);
        List<OpsRuntimeEvent> events = new ArrayList<>();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> invoker.call(
                "system", "user", null, events, null, System.nanoTime()));

        assertTrue(error.getMessage().contains("模型可用性服务未初始化"));
        assertEquals("MODEL_CALL_BLOCKED", events.get(0).getEventType());
    }

    @Test
    void modelPayloadPreservesRuntimeResourceMetadataAndBoundsPrompts() {
        OpsRuntimeResourceBundle bundle = OpsRuntimeResourceBundle.builder()
                .modelId("model-1")
                .projectId("project-1")
                .agentVersion(7)
                .skillNames(List.of("slow-sql", "log-search"))
                .mcpIds(List.of("mysql-mcp"))
                .mcpServers(List.of())
                .ragEnabled(true)
                .knowledgeBaseId("kb-1")
                .metadata(Map.of("runtimeMode", "GRAPH"))
                .build();
        String longPrompt = "x".repeat(5000);

        Map<String, Object> payload = new OpsRuntimeLlmInvoker(null)
                .modelCallPayload(longPrompt, "user", bundle);

        assertEquals("model-1", payload.get("modelId"));
        assertEquals("project-1", payload.get("projectId"));
        assertEquals(2, payload.get("skillCount"));
        assertEquals(1, ((List<?>) payload.get("mcpIds")).size());
        assertEquals(true, payload.get("ragEnabled"));
        assertEquals("GRAPH", payload.get("runtimeMode"));
        assertEquals(5004, payload.get("promptChars"));
        assertTrue(String.valueOf(payload.get("systemPrompt")).length() <= 4000);
    }

    @Test
    void genericGraphModelCallsRespectBoundedExecutorTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            OpsRuntimeLlmInvoker invoker = new OpsRuntimeLlmInvoker(null, executor, 1);
            long started = System.nanoTime();

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> invoker.boundedModelCall(() -> {
                        try {
                            Thread.sleep(5000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "late";
                    }));

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            assertTrue(error.getMessage().contains("模型调用超过当前总时限"));
            assertTrue(elapsedMs < 2500L, "generic Graph model call must not wait indefinitely");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void legacyRuntimeNoLongerOwnsModelPayloadConstruction() {
        List<String> methodNames = java.util.Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertFalse(methodNames.contains("modelCallPayload"));
    }
}
