package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Typed result of deciding how one MCP server may be exposed to an Agent. */
public record OpsMcpProgressiveExposure(
        Mode mode,
        List<Map<String, Object>> runtimeTools,
        boolean disclosureEnabled) {

    public OpsMcpProgressiveExposure {
        mode = mode == null ? Mode.LEGACY : mode;
        runtimeTools = runtimeTools == null ? List.of() : List.copyOf(runtimeTools);
    }

    public boolean managed() {
        return Mode.MANAGED.equals(mode);
    }

    public boolean blocked() {
        return Mode.BLOCKED.equals(mode);
    }

    public boolean extensionAvailable() {
        return runtimeTools.stream().anyMatch(item -> item != null
                && "EXTENSION".equalsIgnoreCase(value(item.get("disclosureTier"), "EXTENSION")));
    }

    public List<String> allowedToolNames() {
        return runtimeTools.stream()
                .filter(item -> item != null)
                .map(item -> value(item.get("toolName")))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public static OpsMcpProgressiveExposure legacy() {
        return new OpsMcpProgressiveExposure(Mode.LEGACY, List.of(), false);
    }

    public static OpsMcpProgressiveExposure denied() {
        return new OpsMcpProgressiveExposure(Mode.BLOCKED, List.of(), false);
    }

    public static OpsMcpProgressiveExposure managed(
            List<Map<String, Object>> runtimeTools,
            boolean disclosureEnabled) {
        return new OpsMcpProgressiveExposure(Mode.MANAGED, runtimeTools, disclosureEnabled);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String value(Object value, String fallback) {
        String text = value(value);
        return StringUtils.hasText(text) ? text : value(fallback);
    }

    public enum Mode {
        LEGACY,
        MANAGED,
        BLOCKED
    }
}
