package cn.lgs.orbisops.domain.toolexecution.model;

import cn.lgs.orbisops.domain.toolset.model.ToolReference;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Provider-neutral Tool invocation. */
public record ToolInvocation(
        ToolReference reference,
        Map<String, Object> arguments,
        ToolInvocationContext context,
        Duration timeout
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(10);

    public ToolInvocation {
        if (reference == null) throw new IllegalArgumentException("TOOL_INVOCATION_REFERENCE_REQUIRED");
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        if (context == null) throw new IllegalArgumentException("TOOL_INVOCATION_CONTEXT_REQUIRED");
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("TOOL_INVOCATION_TIMEOUT_INVALID");
        }
    }
}
