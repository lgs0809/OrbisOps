package cn.lgs.orbisops.trigger.ops.change;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** FastJSON-backed compatibility reader for legacy ChangePackage structured fields. */
final class OpsPreApprovalStructuredValueReader {

    Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String text && hasText(text) && text.trim().startsWith("{")) {
            return JSON.parseObject(text, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        return Map.of();
    }

    List<Map<String, Object>> operations(Object value) {
        Object parsed = parseMaybeJson(value);
        if (parsed instanceof Iterable<?> iterable) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    map.forEach((key, nested) -> data.put(String.valueOf(key), nested));
                    result.add(data);
                }
            }
            return result;
        }
        if (parsed instanceof Map<?, ?> map) {
            return operations(firstNonNull(
                    map.get("steps"),
                    map.get("operations"),
                    map.get("mcpSteps")));
        }
        return List.of();
    }

    List<String> stringList(Object value) {
        Object parsed = parseMaybeJson(value);
        if (parsed instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            iterable.forEach(item -> result.add(text(item, "")));
            return result;
        }
        return List.of();
    }

    String firstString(Object value) {
        Object parsed = parseMaybeJson(value);
        if (parsed instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = text(item, "");
                if (hasText(text)) return text;
            }
        }
        return text(value, "");
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private Object parseMaybeJson(Object value) {
        if (value instanceof String text && hasText(text)) {
            if (text.trim().startsWith("[")) return JSON.parseArray(text);
            if (text.trim().startsWith("{")) return object(text);
        }
        return value;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return hasText(normalized) ? normalized : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
