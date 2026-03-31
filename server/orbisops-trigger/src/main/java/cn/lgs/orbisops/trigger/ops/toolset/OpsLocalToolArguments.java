package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Framework-neutral input accessor for local tool protocol arguments. */
public final class OpsLocalToolArguments {

    private final Map<String, Object> values;

    public OpsLocalToolArguments(Map<String, Object> values) {
        this.values = values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public Object raw(String key) {
        return values.get(key);
    }

    public String text(String key) {
        return text(values.get(key));
    }

    public String text(String key, String fallback) {
        String value = text(key);
        return value.isBlank() ? fallback : value;
    }

    public String required(String key, String message) {
        String value = text(key);
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    public int boundedInt(String key, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(text(key));
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
