package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable ordered first-match source chain. Runtime configs are never merged. */
public final class OpsMcpRuntimeConfigSourceChain {

    private final List<OpsMcpRuntimeConfigSource> sources;
    private final OpsMcpRuntimeConfigResolutionObserver observer;

    public OpsMcpRuntimeConfigSourceChain(
            List<OpsMcpRuntimeConfigSource> sources,
            OpsMcpRuntimeConfigResolutionObserver observer) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("MCP_CONFIG_SOURCES_REQUIRED");
        }
        this.sources = validate(sources);
        this.observer = observer == null
                ? OpsMcpRuntimeConfigResolutionObserver.noop()
                : observer;
    }

    public OpsMcpRuntimeConfigResolution resolve(OpsMcpRuntimeConfigRequest request) {
        if (request == null) throw new IllegalArgumentException("MCP_CONFIG_REQUEST_REQUIRED");
        List<OpsMcpRuntimeConfigSourceAttempt> attempts = new ArrayList<>();
        for (OpsMcpRuntimeConfigSource source : sources) {
            OpsMcpRuntimeConfigSourceResult result;
            try {
                result = source.resolve(request);
            } catch (RuntimeException error) {
                result = OpsMcpRuntimeConfigSourceResult.unavailable(
                        "SOURCE_FAILURE:" + summary(error));
            }
            if (result == null) {
                result = OpsMcpRuntimeConfigSourceResult.unavailable("SOURCE_RETURNED_NULL");
            }
            attempts.add(new OpsMcpRuntimeConfigSourceAttempt(
                    source.sourceId(), result.outcome(), result.reason()));
            if (result.outcome() == OpsMcpRuntimeConfigSourceResult.Outcome.MATCH) {
                return observed(new OpsMcpRuntimeConfigResolution(
                        request,
                        result.config(),
                        source.sourceId(),
                        result.outcome(),
                        result.reason(),
                        attempts));
            }
            if (result.outcome() == OpsMcpRuntimeConfigSourceResult.Outcome.BLOCKED) {
                return observed(new OpsMcpRuntimeConfigResolution(
                        request,
                        null,
                        source.sourceId(),
                        result.outcome(),
                        result.reason(),
                        attempts));
            }
        }
        OpsMcpRuntimeConfigSourceAttempt terminal = attempts.get(attempts.size() - 1);
        return observed(new OpsMcpRuntimeConfigResolution(
                request,
                null,
                terminal.sourceId(),
                terminal.outcome(),
                terminal.reason(),
                attempts));
    }

    public List<String> sourceOrder() {
        return sources.stream().map(OpsMcpRuntimeConfigSource::sourceId).toList();
    }

    private OpsMcpRuntimeConfigResolution observed(
            OpsMcpRuntimeConfigResolution resolution) {
        observer.observe(resolution);
        return resolution;
    }

    private List<OpsMcpRuntimeConfigSource> validate(
            List<OpsMcpRuntimeConfigSource> candidates) {
        List<OpsMcpRuntimeConfigSource> ordered = candidates.stream()
                .filter(source -> source != null)
                .sorted(Comparator.comparingInt(OpsMcpRuntimeConfigSource::order)
                        .thenComparing(OpsMcpRuntimeConfigSource::sourceId))
                .toList();
        if (ordered.isEmpty()) throw new IllegalArgumentException("MCP_CONFIG_SOURCES_REQUIRED");
        Set<String> ids = new LinkedHashSet<>();
        Set<Integer> orders = new LinkedHashSet<>();
        for (OpsMcpRuntimeConfigSource source : ordered) {
            String id = source.sourceId() == null ? "" : source.sourceId().trim();
            if (id.isBlank()) throw new IllegalArgumentException("MCP_CONFIG_SOURCE_ID_REQUIRED");
            if (!ids.add(id)) throw new IllegalArgumentException("MCP_CONFIG_SOURCE_DUPLICATE:" + id);
            if (!orders.add(source.order())) {
                throw new IllegalArgumentException("MCP_CONFIG_SOURCE_ORDER_DUPLICATE:" + source.order());
            }
        }
        return List.copyOf(ordered);
    }

    private String summary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }
}
