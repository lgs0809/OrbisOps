package cn.lgs.orbisops.domain.toolset.service;

/** Security policy for local Redis diagnostic and approved landing operations. */
public final class LocalRedisPolicy {

    public String scanPattern(Object value) {
        String pattern = required(
                value,
                "REDIS_SCAN_PATTERN_REQUIRED：Redis scan 必须提供受控 namespace/pattern");
        boolean namespaceWildcard = pattern.matches(
                "[A-Za-z0-9][A-Za-z0-9_.:-]{2,}\\*");
        if ("*".equals(pattern)
                || pattern.length() < 3
                || (!pattern.contains(":")
                && !pattern.contains("/")
                && !namespaceWildcard)) {
            throw new SecurityException(
                    "REDIS_SCAN_PATTERN_NOT_ALLOWED：Redis scan 禁止全库 *，必须限制 namespace/pattern");
        }
        return pattern;
    }

    public String controlledKey(Object value) {
        String key = required(
                value,
                "REDIS_KEY_REQUIRED：Redis 写操作必须提供受控 key");
        if (key.length() < 3
                || (!key.contains(":") && !key.contains("/"))) {
            throw new SecurityException(
                    "REDIS_KEY_NOT_ALLOWED：Redis landing key 必须限制 namespace");
        }
        return key;
    }

    public String requiredReadKey(Object value, String message) {
        return required(value, message);
    }

    public int limit(Object value, int maxRows, int fallback) {
        int maximum = Math.max(1, maxRows);
        try {
            int parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(text(value));
            return Math.max(1, Math.min(maximum, parsed));
        } catch (RuntimeException ignored) {
            return Math.max(1, Math.min(maximum, fallback));
        }
    }

    public long ttlSeconds(Object value) {
        try {
            long parsed = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(text(value));
            return Math.max(1L, Math.min(86_400L, parsed));
        } catch (RuntimeException ignored) {
            return 300L;
        }
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
