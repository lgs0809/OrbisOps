package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.McpTransportConfigProtectionPort;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Fastjson security protocol adapter for MCP transport configuration. */
public final class OpsMcpTransportConfigProtectionAdapter implements McpTransportConfigProtectionPort {

    private static final String PLACEHOLDER = "******";

    @Override
    public String resolveIncoming(String incomingConfig, String existingConfig) {
        if (!StringUtils.hasText(incomingConfig) || !incomingConfig.contains(PLACEHOLDER)) {
            return incomingConfig;
        }
        return existingConfig == null ? incomingConfig : existingConfig;
    }

    @Override
    public String protectForRead(String rawConfig) {
        if (!StringUtils.hasText(rawConfig)) {
            return rawConfig;
        }
        String value = rawConfig.trim();
        boolean jsonCandidate = (value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("[") && value.endsWith("]"));
        if (jsonCandidate) {
            try {
                Object parsed = JSON.parse(value);
                return JSON.toJSONString(protectJson(parsed));
            } catch (Exception ignored) {
                // Fall through to bounded textual protection for malformed JSON-like input.
            }
        }
        return rawConfig.replaceAll(
                "(?i)(secret|token|password|credential|privateKey|apiKey)([\\\"'\\\\s:=]+)([^,\\\"'\\\\s}]+)",
                "$1$2******");
    }

    private Object protectJson(Object value) {
        if (value instanceof JSONObject object) {
            JSONObject result = new JSONObject(true);
            object.forEach((key, item) -> result.put(
                    key,
                    isProtectedKey(key) ? PLACEHOLDER : protectJson(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::protectJson).toList();
        }
        if (value instanceof Map<?, ?> map) {
            JSONObject result = new JSONObject(true);
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                result.put(name, isProtectedKey(name) ? PLACEHOLDER : protectJson(item));
            });
            return result;
        }
        return value;
    }

    private boolean isProtectedKey(String key) {
        return StringUtils.hasText(key)
                && key.matches("(?i).*(secret|token|password|credential|private|key).*");
    }
}
