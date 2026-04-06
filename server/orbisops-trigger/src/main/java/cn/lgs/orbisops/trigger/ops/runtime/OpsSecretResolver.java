package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves secret placeholders stored in database-backed runtime configuration.
 */
@Service
public class OpsSecretResolver {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}");
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile("(?i).*(secret|token|password|credential|private|key).*");

    private final Environment environment;

    public OpsSecretResolver(Environment environment) {
        this.environment = environment;
    }

    public String resolve(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        Matcher matcher = ENV_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String fallback = matcher.group(2);
            String replacement = firstText(environment.getProperty(envName), System.getenv(envName), fallback);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public boolean isReference(String value) {
        return StringUtils.hasText(value) && ENV_PATTERN.matcher(value.trim()).matches();
    }

    public Map<String, String> resolveMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, resolve(value)));
        return result;
    }

    public Map<String, String> redactMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, redact(key, value)));
        return result;
    }

    public String redact(String key, String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (StringUtils.hasText(key) && SENSITIVE_KEY_PATTERN.matcher(key).matches()) {
            return "***";
        }
        if (ENV_PATTERN.matcher(value).find()) {
            return value;
        }
        return value.length() <= 8 ? "***" : value.substring(0, 4) + "***" + value.substring(value.length() - 2);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
