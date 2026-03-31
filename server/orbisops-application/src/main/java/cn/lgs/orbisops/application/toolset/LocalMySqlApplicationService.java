package cn.lgs.orbisops.application.toolset;

import cn.lgs.orbisops.domain.toolset.service.LocalMySqlPolicy;

import java.util.List;
import java.util.Map;

/** Application service for bounded read-only MySQL diagnostics. */
public class LocalMySqlApplicationService {

    private final LocalMySqlExecutionPort executionPort;
    private final LocalMySqlPolicy policy;

    public LocalMySqlApplicationService(LocalMySqlExecutionPort executionPort) {
        this(executionPort, new LocalMySqlPolicy());
    }

    LocalMySqlApplicationService(
            LocalMySqlExecutionPort executionPort,
            LocalMySqlPolicy policy) {
        if (executionPort == null || policy == null) {
            throw new IllegalArgumentException(
                    "LOCAL_MYSQL_DEPENDENCY_REQUIRED");
        }
        this.executionPort = executionPort;
        this.policy = policy;
    }

    public List<Map<String, Object>> readonlyQuery(
            LocalMySqlExecutionTarget target,
            Object databaseArgument,
            Object sql) {
        assertTarget(target, databaseArgument);
        return query(target, policy.readonlySql(sql), List.of());
    }

    public List<Map<String, Object>> explain(
            LocalMySqlExecutionTarget target,
            Object databaseArgument,
            Object sql) {
        assertTarget(target, databaseArgument);
        String readonly = policy.readonlySql(sql);
        return query(target, "EXPLAIN " + readonly, List.of());
    }

    public List<Map<String, Object>> showTables(
            LocalMySqlExecutionTarget target,
            Object databaseArgument) {
        assertTarget(target, databaseArgument);
        return query(target, "SHOW TABLES", List.of());
    }

    public List<Map<String, Object>> showIndexes(
            LocalMySqlExecutionTarget target,
            Object databaseArgument,
            Object table) {
        assertTarget(target, databaseArgument);
        String identifier = policy.identifier(table);
        return query(
                target,
                "SHOW INDEX FROM `" + identifier + "`",
                List.of());
    }

    public List<Map<String, Object>> showStatus(
            LocalMySqlExecutionTarget target,
            Object databaseArgument) {
        assertTarget(target, databaseArgument);
        return query(target, "SHOW STATUS", List.of());
    }

    public List<Map<String, Object>> precondition(
            LocalMySqlExecutionTarget target,
            Object databaseArgument,
            Map<String, Object> arguments) {
        assertTarget(target, databaseArgument);
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        String tableValue = text(args.get("table"));
        String keyValue = text(args.get("key"));
        if (tableValue.isBlank() || keyValue.isBlank()) {
            return readonlyQuery(
                    target,
                    databaseArgument,
                    args.get("sql"));
        }
        if (target.projectScoped()) {
            throw new SecurityException(
                    "PROJECT_MYSQL_PRECONDITION_TEMPLATE_NOT_ALLOWED：项目业务库只读通道不接受动态模板预检，请提供 SELECT 只读 SQL");
        }
        String table = policy.identifier(tableValue);
        String keyColumn = policy.identifier(
                fallback(args.get("keyColumn"), "config_key"));
        String valueColumn = policy.identifier(
                fallback(args.get("valueColumn"), "config_value"));
        String versionColumn = text(args.get("versionColumn"));
        String select = "SELECT `" + keyColumn
                + "` AS config_key, `" + valueColumn
                + "` AS config_value"
                + (versionColumn.isBlank()
                ? ""
                : ", `" + policy.identifier(versionColumn)
                + "` AS config_version")
                + " FROM `" + table + "` WHERE `" + keyColumn + "` = ?";
        return query(target, select, List.of(keyValue));
    }

    private List<Map<String, Object>> query(
            LocalMySqlExecutionTarget target,
            String sql,
            List<Object> parameters) {
        return policy.sanitizeRows(
                executionPort.query(target, sql, parameters),
                target.maxRows());
    }

    private void assertTarget(
            LocalMySqlExecutionTarget target,
            Object databaseArgument) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "LOCAL_MYSQL_EXECUTION_TARGET_REQUIRED");
        }
        policy.assertResourceBoundary(
                target.projectId(),
                target.resourceId(),
                databaseArgument);
    }

    private String required(Object value, String message) {
        String text = text(value);
        if (text.isBlank()) throw new IllegalArgumentException(message);
        return text;
    }

    private String fallback(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
