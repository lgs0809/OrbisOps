package cn.lgs.orbisops.trigger.ops.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Observable MCP runtime config source selection, including legacy fallback usage. */
@Component
public final class OpsMcpRuntimeConfigResolutionTelemetry
        implements OpsMcpRuntimeConfigResolutionObserver {

    public static final String METER_NAME = "orbisops.mcp.runtime.config.resolution";

    private final MeterRegistry registry;
    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

    @Autowired
    public OpsMcpRuntimeConfigResolutionTelemetry(
            ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider == null ? null : registryProvider.getIfAvailable());
    }

    OpsMcpRuntimeConfigResolutionTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void observe(OpsMcpRuntimeConfigResolution resolution) {
        if (resolution == null) return;
        String source = normalized(resolution.selectedSourceId(), "NONE");
        String outcome = resolution.terminalOutcome().name();
        String fallback = Boolean.toString(resolution.fallback());
        counts.computeIfAbsent(key(source, outcome), ignored -> new LongAdder()).increment();
        if (registry != null) {
            Counter.builder(METER_NAME)
                    .tag("source", source)
                    .tag("outcome", outcome)
                    .tag("fallback", fallback)
                    .register(registry)
                    .increment();
        }
    }

    public long count(String sourceId, OpsMcpRuntimeConfigSourceResult.Outcome outcome) {
        if (outcome == null) return 0L;
        LongAdder value = counts.get(key(normalized(sourceId, "NONE"), outcome.name()));
        return value == null ? 0L : value.sum();
    }

    public long legacyFallbackCount() {
        return count(
                OpsLegacyMcpRuntimeConfigSource.SOURCE_ID,
                OpsMcpRuntimeConfigSourceResult.Outcome.MATCH);
    }

    private String key(String sourceId, String outcome) {
        return sourceId + "|" + outcome;
    }

    private String normalized(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }
}
