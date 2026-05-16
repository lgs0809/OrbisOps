package cn.lgs.orbisops.trigger.ops.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMcpRuntimeConfigSourceChainTest {

    @Test
    void chainMustSortSourcesAndStopAtFirstMatchWithoutMerging() {
        AtomicInteger laterCalls = new AtomicInteger();
        OpsMcpServerConfig matched = OpsMcpServerConfig.builder().name("project").build();
        OpsMcpRuntimeConfigSourceChain chain = new OpsMcpRuntimeConfigSourceChain(
                List.of(
                        source("SOURCE", 300, request -> {
                            laterCalls.incrementAndGet();
                            return OpsMcpRuntimeConfigSourceResult.match(
                                    OpsMcpServerConfig.builder().name("source").build());
                        }),
                        source("PROJECT", 100, request -> OpsMcpRuntimeConfigSourceResult.match(matched)),
                        source("LEGACY", 200, request -> {
                            laterCalls.incrementAndGet();
                            return OpsMcpRuntimeConfigSourceResult.match(
                                    OpsMcpServerConfig.builder().name("legacy").build());
                        })),
                OpsMcpRuntimeConfigResolutionObserver.noop());

        OpsMcpRuntimeConfigResolution result = chain.resolve(
                new OpsMcpRuntimeConfigRequest("project-1", "mcp-1"));

        assertTrue(result.matched());
        assertEquals("PROJECT", result.selectedSourceId());
        assertEquals(matched, result.config());
        assertEquals(0, laterCalls.get());
        assertEquals(List.of("PROJECT", "LEGACY", "SOURCE"), chain.sourceOrder());
        assertFalse(result.fallback());
    }

    @Test
    void blockedSourceMustTerminateBeforeLowerPriorityFallback() {
        AtomicInteger sourceCalls = new AtomicInteger();
        OpsMcpRuntimeConfigSourceChain chain = new OpsMcpRuntimeConfigSourceChain(
                List.of(
                        source("PROJECT", 100, request ->
                                OpsMcpRuntimeConfigSourceResult.miss("NOT_BOUND")),
                        source("LEGACY", 200, request ->
                                OpsMcpRuntimeConfigSourceResult.blocked("NOT_AUTHORIZED")),
                        source("SOURCE", 300, request -> {
                            sourceCalls.incrementAndGet();
                            return OpsMcpRuntimeConfigSourceResult.match(
                                    OpsMcpServerConfig.builder().name("source").build());
                        })),
                OpsMcpRuntimeConfigResolutionObserver.noop());

        OpsMcpRuntimeConfigResolution result = chain.resolve(
                new OpsMcpRuntimeConfigRequest("project-1", "mcp-1"));

        assertTrue(result.blocked());
        assertEquals("LEGACY", result.selectedSourceId());
        assertEquals("NOT_AUTHORIZED", result.terminalReason());
        assertEquals(0, sourceCalls.get());
        assertEquals(2, result.attempts().size());
    }

    @Test
    void missesAndUnavailableSourcesMustProduceAuditableFallbackReason() {
        AtomicReference<OpsMcpRuntimeConfigResolution> observed = new AtomicReference<>();
        AtomicInteger observations = new AtomicInteger();
        OpsMcpRuntimeConfigSourceChain chain = new OpsMcpRuntimeConfigSourceChain(
                List.of(
                        source("PROJECT", 100, request ->
                                OpsMcpRuntimeConfigSourceResult.miss("NOT_BOUND")),
                        source("LEGACY", 200, request -> {
                            throw new IllegalStateException("db down");
                        }),
                        source("SOURCE", 300, request ->
                                OpsMcpRuntimeConfigSourceResult.match(
                                        OpsMcpServerConfig.builder().name("source").build()))),
                resolution -> {
                    observations.incrementAndGet();
                    observed.set(resolution);
                });

        OpsMcpRuntimeConfigResolution result = chain.resolve(
                new OpsMcpRuntimeConfigRequest("project-1", "mcp-1"));

        assertTrue(result.matched());
        assertTrue(result.fallback());
        assertTrue(result.fallbackReason().contains("PROJECT:MISS:NOT_BOUND"));
        assertTrue(result.fallbackReason().contains("LEGACY:UNAVAILABLE:SOURCE_FAILURE:db down"));
        assertEquals(1, observations.get());
        assertEquals(result, observed.get());
        assertThrows(UnsupportedOperationException.class,
                () -> result.attempts().add(result.attempts().get(0)));
    }

    @Test
    void duplicateSourceIdAndOrderMustFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new OpsMcpRuntimeConfigSourceChain(
                List.of(
                        source("PROJECT", 100, request -> OpsMcpRuntimeConfigSourceResult.miss("x")),
                        source("PROJECT", 200, request -> OpsMcpRuntimeConfigSourceResult.miss("y"))),
                null));
        assertThrows(IllegalArgumentException.class, () -> new OpsMcpRuntimeConfigSourceChain(
                List.of(
                        source("PROJECT", 100, request -> OpsMcpRuntimeConfigSourceResult.miss("x")),
                        source("LEGACY", 100, request -> OpsMcpRuntimeConfigSourceResult.miss("y"))),
                null));
    }

    @Test
    void telemetryMustCountLegacyRuntimeMatches() {
        OpsMcpRuntimeConfigResolutionTelemetry telemetry =
                new OpsMcpRuntimeConfigResolutionTelemetry((MeterRegistry) null);
        OpsMcpRuntimeConfigSourceChain chain = new OpsMcpRuntimeConfigSourceChain(
                List.of(
                        source(OpsProjectMcpRuntimeConfigSource.SOURCE_ID, 100, request ->
                                OpsMcpRuntimeConfigSourceResult.miss("NOT_BOUND")),
                        source(OpsLegacyMcpRuntimeConfigSource.SOURCE_ID, 200, request ->
                                OpsMcpRuntimeConfigSourceResult.match(
                                        OpsMcpServerConfig.builder().name("legacy").build()))),
                telemetry);

        chain.resolve(new OpsMcpRuntimeConfigRequest("project-1", "legacy-1"));

        assertEquals(1L, telemetry.legacyFallbackCount());
        assertEquals(1L, telemetry.count(
                OpsLegacyMcpRuntimeConfigSource.SOURCE_ID,
                OpsMcpRuntimeConfigSourceResult.Outcome.MATCH));
    }

    private OpsMcpRuntimeConfigSource source(
            String id,
            int order,
            Resolver resolver) {
        return new OpsMcpRuntimeConfigSource() {
            @Override
            public String sourceId() {
                return id;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public OpsMcpRuntimeConfigSourceResult resolve(
                    OpsMcpRuntimeConfigRequest request) {
                return resolver.resolve(request);
            }
        };
    }

    @FunctionalInterface
    private interface Resolver {
        OpsMcpRuntimeConfigSourceResult resolve(OpsMcpRuntimeConfigRequest request);
    }
}
