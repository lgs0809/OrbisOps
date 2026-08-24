package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes callback input before the unified execution boundary applies policy. */
final class OpsMcpToolInputSupport {

    static String safeToolCallbackName(String rawValue) {
        String normalized = StringUtils.hasText(rawValue)
                ? rawValue.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_")
                : "project_mcp_tool";
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : "project_mcp_tool";
    }

    Map<String, Object> arguments(String toolInput) {
        return parse(toolInput);
    }

    Map<String, Object> dispatcherArguments(String toolInput) {
        Map<String, Object> parsed = parse(toolInput);
        Object value = parsed.get("arguments");
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Collections.unmodifiableMap(result);
    }

    String requestedToolName(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return "";
        try {
            return text(JSON.parseObject(toolInput).get("toolName"));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    boolean readOnly(List<Map<String, Object>> runtimeTools, String toolName) {
        if (runtimeTools == null || runtimeTools.isEmpty() || !StringUtils.hasText(toolName)) return false;
        return runtimeTools.stream()
                .filter(item -> toolName.equals(text(item.get("toolName"))))
                .findFirst()
                .map(item -> Boolean.TRUE.equals(item.get("readOnly")))
                .orElse(false);
    }

    private Map<String, Object> parse(String toolInput) {
        if (!StringUtils.hasText(toolInput)) return Map.of();
        try {
            Map<String, Object> parsed = JSON.parseObject(toolInput, LinkedHashMap.class);
            return parsed == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("MCP_TOOL_INPUT_MUST_BE_JSON_OBJECT", error);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
