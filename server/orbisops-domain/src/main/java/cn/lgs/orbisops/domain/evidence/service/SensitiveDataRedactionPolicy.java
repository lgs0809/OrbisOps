package cn.lgs.orbisops.domain.evidence.service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Recursive redaction policy for persisted tool input, output, evidence and audit projections. */
public final class SensitiveDataRedactionPolicy {

    private static final String MASK = "***";
    private static final String DEPTH_LIMIT = "<redacted:depth-limit>";
    private static final String CYCLE = "<redacted:cycle>";
    private static final int MAX_DEPTH = 16;
    private static final int MAX_COLLECTION_ITEMS = 2_000;
    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "accesstoken",
            "refreshtoken", "accesskey", "secretkey", "privatekey", "apikey",
            "credential", "credentials", "authorization", "proxyauthorization",
            "bearer", "jwt", "cookie", "setcookie", "clientsecret");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|access[_-]?token|refresh[_-]?token|"
                    + "access[_-]?key|secret[_-]?key|private[_-]?key|api[_-]?key|credential|"
                    + "authorization|proxy[_-]?authorization|jwt|cookie|set-cookie|client[_-]?secret)"
                    + "(\\s*[:=]\\s*)"
                    + "(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;&}\\]\\r\\n]+)");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)([\"'](?:password|passwd|pwd|secret|token|access[_-]?token|refresh[_-]?token|"
                    + "access[_-]?key|secret[_-]?key|private[_-]?key|api[_-]?key|credential|"
                    + "authorization|proxy[_-]?authorization|jwt|cookie|set-cookie|client[_-]?secret)"
                    + "[\"']\\s*:\\s*)"
                    + "(?:[\"'][^\"']*[\"']|[^,}\\]]+)");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/]+=*");
    private static final Pattern BASIC = Pattern.compile(
            "(?i)\\bBasic\\s+[A-Za-z0-9+/=]{8,}");
    private static final Pattern JWT = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"
                    + "(?![A-Za-z0-9_-])");
    private static final Pattern URI_USER_INFO = Pattern.compile(
            "(?i)\\b([a-z][a-z0-9+.-]*://)([^\\s/@:]+):([^\\s/@]+)@");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&;](?:password|passwd|pwd|secret|token|api[_-]?key)=)[^&;\\s]+");

    public Object redact(Object source) {
        return redact(source, 0, new IdentityHashMap<>());
    }

    public Map<String, Object> redactMap(Map<?, ?> source) {
        Object redacted = redact(source);
        if (!(redacted instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return Collections.unmodifiableMap(result);
    }

    public String redactText(String source) {
        String value = source == null ? "" : source;
        value = JSON_SECRET.matcher(value).replaceAll("$1\"***\"");
        value = URI_USER_INFO.matcher(value).replaceAll("$1***:***@");
        value = QUERY_SECRET.matcher(value).replaceAll("$1" + MASK);
        value = KEY_VALUE.matcher(value).replaceAll(match -> match.group(1) + match.group(2) + MASK);
        value = BEARER.matcher(value).replaceAll("Bearer " + MASK);
        value = BASIC.matcher(value).replaceAll("Basic " + MASK);
        return JWT.matcher(value).replaceAll("***.***.***");
    }

    public boolean sensitive(String key) {
        String normalized = key == null
                ? ""
                : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (SECRET_KEYS.contains(normalized)) return true;
        return normalized.endsWith("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("token")
                || normalized.endsWith("privatekey")
                || normalized.endsWith("apikey");
    }

    private Object redact(
            Object source,
            int depth,
            IdentityHashMap<Object, Boolean> visiting) {
        if (source == null) return null;
        if (source instanceof CharSequence text) return redactText(text.toString());
        if (source instanceof Number || source instanceof Boolean || source instanceof Enum<?>) {
            return source;
        }
        if (depth >= MAX_DEPTH) return DEPTH_LIMIT;
        if (visiting.put(source, Boolean.TRUE) != null) return CYCLE;
        try {
            if (source instanceof Map<?, ?> map) return redactMap(map, depth, visiting);
            if (source instanceof Iterable<?> iterable) return redactIterable(iterable, depth, visiting);
            if (source.getClass().isArray()) return redactArray(source, depth, visiting);
            return redactText(String.valueOf(source));
        } finally {
            visiting.remove(source);
        }
    }

    private Map<String, Object> redactMap(
            Map<?, ?> source,
            int depth,
            IdentityHashMap<Object, Boolean> visiting) {
        if (source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (count++ >= MAX_COLLECTION_ITEMS) {
                result.put("__redaction__", "<redacted:item-limit size=" + source.size() + ">");
                break;
            }
            String name = String.valueOf(entry.getKey());
            result.put(
                    name,
                    sensitive(name)
                            ? MASK
                            : redact(entry.getValue(), depth + 1, visiting));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Object> redactIterable(
            Iterable<?> source,
            int depth,
            IdentityHashMap<Object, Boolean> visiting) {
        List<Object> result = new ArrayList<>();
        int count = 0;
        for (Object value : source) {
            if (count++ >= MAX_COLLECTION_ITEMS) {
                result.add("<redacted:item-limit>");
                break;
            }
            result.add(redact(value, depth + 1, visiting));
        }
        return Collections.unmodifiableList(result);
    }

    private List<Object> redactArray(
            Object source,
            int depth,
            IdentityHashMap<Object, Boolean> visiting) {
        int length = Array.getLength(source);
        List<Object> result = new ArrayList<>(Math.min(length, MAX_COLLECTION_ITEMS) + 1);
        for (int index = 0; index < length && index < MAX_COLLECTION_ITEMS; index++) {
            result.add(redact(Array.get(source, index), depth + 1, visiting));
        }
        if (length > MAX_COLLECTION_ITEMS) {
            result.add("<redacted:item-limit size=" + length + ">");
        }
        return Collections.unmodifiableList(result);
    }
}
