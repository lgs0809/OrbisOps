package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class OpsMcpToolArgumentPolicyChecker {

    private static final Pattern WRITE_SQL = Pattern.compile(
            "(?is)\\b(DROP|TRUNCATE|DELETE|UPDATE|INSERT|ALTER|CREATE|MERGE|CALL|GRANT|REVOKE)\\b");

    public void assertAllowed(Map<String, Object> argumentPolicy, String argumentsJson, OpsToolCallStage stage) {
        Map<String, Object> policy = argumentPolicy == null ? Map.of() : argumentPolicy;
        Map<String, Object> arguments = parseArguments(argumentsJson);
        requireKeys(policy, arguments);
        forbidKeys(policy, arguments);
        checkAllowedValues(policy, arguments);
        checkForbiddenValues(policy, arguments);
        checkRegex(policy, arguments);
        checkScopes(policy, arguments);
        checkMaxLimit(policy, arguments);
        checkSql(policy, arguments);
        if (OpsToolCallStage.LANDING.equals(stage) && arguments.isEmpty()) {
            throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：LANDING 阶段不允许未知参数结构。");
        }
    }

    private void requireKeys(Map<String, Object> policy, Map<String, Object> arguments) {
        for (String key : strings(policy.get("requiredKeys"))) {
            if (!arguments.containsKey(key)) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：缺少必填参数 " + key);
            }
        }
    }

    private void forbidKeys(Map<String, Object> policy, Map<String, Object> arguments) {
        for (String key : strings(policy.get("forbiddenKeys"))) {
            if (arguments.containsKey(key)) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：禁止参数 " + key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void checkAllowedValues(Map<String, Object> policy, Map<String, Object> arguments) {
        Object values = policy.get("allowedValues");
        if (!(values instanceof Map<?, ?> map)) {
            return;
        }
        map.forEach((key, allowed) -> {
            String name = String.valueOf(key);
            if (arguments.containsKey(name) && !strings(allowed).contains(String.valueOf(arguments.get(name)))) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：参数 " + name + " 不在允许值范围内。");
            }
        });
    }

    private void checkForbiddenValues(Map<String, Object> policy, Map<String, Object> arguments) {
        Object values = policy.get("forbiddenValues");
        if (!(values instanceof Map<?, ?> map)) {
            return;
        }
        map.forEach((key, forbidden) -> {
            String name = String.valueOf(key);
            if (arguments.containsKey(name) && strings(forbidden).contains(String.valueOf(arguments.get(name)))) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：参数 " + name + " 命中禁止值。");
            }
        });
    }

    private void checkRegex(Map<String, Object> policy, Map<String, Object> arguments) {
        Object allow = policy.get("regexAllowlist");
        if (allow instanceof Map<?, ?> map) {
            map.forEach((key, patterns) -> {
                String name = String.valueOf(key);
                if (arguments.containsKey(name) && regexPatterns(patterns).stream()
                        .noneMatch(pattern -> Pattern.compile(pattern).matcher(String.valueOf(arguments.get(name))).find())) {
                    throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：参数 " + name + " 未通过正则 allowlist。");
                }
            });
        }
        Object deny = policy.get("regexDenylist");
        if (deny instanceof Map<?, ?> map) {
            map.forEach((key, patterns) -> {
                String name = String.valueOf(key);
                if (arguments.containsKey(name) && regexPatterns(patterns).stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(String.valueOf(arguments.get(name))).find())) {
                    throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：参数 " + name + " 命中正则 denylist。");
                }
            });
        }
    }

    private List<String> regexPatterns(Object value) {
        return strings(value);
    }

    private void checkScopes(Map<String, Object> policy, Map<String, Object> arguments) {
        checkOneScope(policy, arguments, "allowedNamespaces", "namespace");
        checkOneScope(policy, arguments, "allowedDatabases", "database", "db");
        checkOneScope(policy, arguments, "allowedTables", "table", "tables");
        checkOneScope(policy, arguments, "allowedServices", "service", "serviceName");
        checkOneScope(policy, arguments, "allowedEnvironments", "environment", "env");
        checkOneScope(policy, arguments, "allowedHttpMethods", "method", "httpMethod");
        for (String key : List.of("method", "httpMethod")) {
            if (arguments.containsKey(key) && strings(policy.get("forbiddenHttpMethods")).stream()
                    .map(item -> item.toUpperCase(Locale.ROOT))
                    .anyMatch(item -> item.equals(String.valueOf(arguments.get(key)).toUpperCase(Locale.ROOT)))) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：HTTP method 被禁止。");
            }
        }
    }

    private void checkOneScope(Map<String, Object> policy, Map<String, Object> arguments, String policyKey, String... argKeys) {
        List<String> allowed = strings(policy.get(policyKey));
        if (allowed.isEmpty()) {
            return;
        }
        for (String argKey : argKeys) {
            Object value = arguments.get(argKey);
            if (value == null) {
                continue;
            }
            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (!allowed.contains(String.valueOf(item))) {
                        throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：" + argKey + " 超出允许范围。");
                    }
                }
            } else if (!allowed.contains(String.valueOf(value))) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：" + argKey + " 超出允许范围。");
            }
        }
    }

    private void checkMaxLimit(Map<String, Object> policy, Map<String, Object> arguments) {
        int max = intValue(policy.get("maxLimit"), -1);
        if (max < 0) {
            return;
        }
        for (String key : List.of("limit", "size", "pageSize")) {
            int value = intValue(arguments.get(key), -1);
            if (value > max) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：limit 超出最大值 " + max);
            }
        }
    }

    private void checkSql(Map<String, Object> policy, Map<String, Object> arguments) {
        boolean sqlReadOnlyOnly = bool(policy.get("sqlReadOnlyOnly"), false);
        List<String> forbidden = strings(policy.get("forbiddenSqlKeywords"));
        for (String key : List.of("sql", "query", "statement")) {
            String sql = String.valueOf(arguments.getOrDefault(key, ""));
            if (!StringUtils.hasText(sql)) {
                continue;
            }
            if (sqlReadOnlyOnly && WRITE_SQL.matcher(sql).find()) {
                throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：SQL 只读策略禁止写入类语句。");
            }
            String upper = sql.toUpperCase(Locale.ROOT);
            for (String keyword : forbidden) {
                if (StringUtils.hasText(keyword) && upper.contains(keyword.toUpperCase(Locale.ROOT))) {
                    throw new SecurityException("MCP_TOOL_ARGUMENT_POLICY_VIOLATION：SQL 命中禁止关键字 " + keyword);
                }
            }
        }
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return Map.of();
        }
        try {
            Object parsed = JSON.parse(argumentsJson);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(StringUtils::hasText).toList();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                try {
                    return JSON.parseArray(trimmed).stream().map(String::valueOf).map(String::trim).filter(StringUtils::hasText).toList();
                } catch (Exception ignored) {
                }
            }
            return java.util.Arrays.stream(trimmed.split("[,;\\s]+")).map(String::trim).filter(StringUtils::hasText).toList();
        }
        return List.of();
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
        }
        return fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
