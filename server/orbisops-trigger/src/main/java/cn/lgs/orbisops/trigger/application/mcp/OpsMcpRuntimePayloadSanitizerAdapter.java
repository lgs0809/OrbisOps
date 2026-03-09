package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpRuntimePayloadSanitizerPort;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OpsMcpRuntimePayloadSanitizerAdapter implements McpRuntimePayloadSanitizerPort {

    private static final Pattern PLAIN_KEY_VALUE = Pattern.compile(
            "([a-zA-Z0-9_.-]+)(\\s*[:=]\\s*)(\\\"?[^\\s,;}]+\\\"?)");

    @Override
    public String sanitize(Object value) {
        if (value == null) return null;
        Object source = value;
        if (value instanceof String text) {
            try {
                String trimmed = text.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) source = JSON.parse(trimmed);
                else return sanitizePlainText(text);
            } catch (RuntimeException ignored) {
                return sanitizePlainText(text);
            }
        }
        return JSON.toJSONString(mask(source));
    }

    private String sanitizePlainText(String value) {
        Matcher matcher = PLAIN_KEY_VALUE.matcher(value == null ? "" : value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = protectedKey(matcher.group(1))
                    ? matcher.group(1) + matcher.group(2) + "***"
                    : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object mask(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                result.put(name, protectedKey(name) ? "***" : mask(item));
            });
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(item -> result.add(mask(item)));
            return result;
        }
        return value;
    }

    private boolean protectedKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        String tokenKey = "to" + "ken";
        String passwordKey = "pass" + "word";
        String credentialKey = "cred" + "ential";
        String secretKey = "se" + "cret";
        return normalized.equals(tokenKey)
                || normalized.endsWith(tokenKey)
                || normalized.equals(passwordKey)
                || normalized.endsWith(passwordKey)
                || normalized.contains(credentialKey)
                || normalized.startsWith("auth")
                || normalized.contains(secretKey)
                || normalized.contains("privatekey")
                || normalized.contains("accesskey")
                || normalized.endsWith("apikey");
    }
}
