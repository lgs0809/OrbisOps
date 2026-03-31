package cn.lgs.orbisops.domain.toolset.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Security policy for local and project-scoped MySQL tool execution. */
public final class LocalMySqlPolicy {

    private static final Set<String> SQL_WRITE_KEYWORDS = Set.of(
            "UPDATE", "DELETE", "INSERT", "ALTER", "DROP", "TRUNCATE",
            "CREATE", "MERGE", "CALL", "GRANT", "REVOKE");

    public String readonlySql(Object value) {
        String sql = required(value, "MySQL 工具必须提供 sql");
        String normalized = stripTrailingSemicolon(sql).trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT")
                && !upper.startsWith("SHOW")
                && !upper.startsWith("EXPLAIN")) {
            throw new SecurityException(
                    "MYSQL_READONLY_SQL_REQUIRED：审核前只允许 SELECT/SHOW/EXPLAIN");
        }
        for (String keyword : SQL_WRITE_KEYWORDS) {
            if (upper.matches(".*\\b" + keyword + "\\b.*")) {
                throw new SecurityException(
                        "MYSQL_READONLY_SQL_REQUIRED：SQL 包含写关键字 " + keyword);
            }
        }
        return normalized;
    }

    public String identifier(Object value) {
        String identifier = required(value, "identifier required");
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "非法 MySQL identifier：" + identifier);
        }
        return identifier;
    }

    public void assertResourceBoundary(
            String projectId,
            String resourceId,
            Object databaseArgument) {
        String database = text(databaseArgument);
        if (text(resourceId).isBlank() && !database.isBlank()) {
            throw new SecurityException(
                    "PROJECT_MYSQL_RESOURCE_ID_REQUIRED：业务库只读查询必须通过项目资源 resourceId，不能直接传 database");
        }
        if (!text(resourceId).isBlank() && text(projectId).isBlank()) {
            throw new SecurityException(
                    "PROJECT_MYSQL_PROJECT_ID_REQUIRED：项目业务库只读查询必须提供 projectId");
        }
    }

    public List<Map<String, Object>> sanitizeRows(
            List<Map<String, Object>> rows,
            int maxRows) {
        if (rows == null || rows.isEmpty()) return List.of();
        int limit = Math.max(1, maxRows);
        return rows.stream()
                .limit(limit)
                .map(this::sanitizeRow)
                .toList();
    }

    private Map<String, Object> sanitizeRow(Map<String, Object> row) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (row == null) return safe;
        row.forEach((key, value) -> safe.put(key, sanitize(value)));
        return safe;
    }

    private Object sanitize(Object value) {
        if (!(value instanceof CharSequence)) return value;
        return text(value)
                .replaceAll(
                        "(?i)(password|passwd|pwd|secret|token|access[_-]?key|secret[_-]?key|private[_-]?key|api[_-]?key|credential|authorization|bearer|jwt|session|cookie)\\s*[:=]\\s*[^\\s,;&\"}]+",
                        "$1=***")
                .replaceAll(
                        "(?i)([a-z][a-z0-9+.-]*://[^\\s/@:]+:)([^\\s/@]+)(@)",
                        "$1***$3");
    }

    private String stripTrailingSemicolon(String sql) {
        return sql == null ? "" : sql.replaceAll(";\\s*$", "");
    }

    private String required(Object value, String message) {
        String text = text(value);
        if (text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
