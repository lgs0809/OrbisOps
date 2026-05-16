package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed extraction helpers for project MCP descriptor maps. */
final class OpsProjectMcpConfigValues {

    private OpsProjectMcpConfigValues() {
    }

    static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    static List<String> stringList(Object value, List<String> fallback) {
        if (value instanceof List<?> raw) {
            List<String> result = raw.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .filter(item -> !item.startsWith("{\"$ref\""))
                    .toList();
            return result.isEmpty() ? fallback : result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text);
        }
        return fallback;
    }

    static String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
