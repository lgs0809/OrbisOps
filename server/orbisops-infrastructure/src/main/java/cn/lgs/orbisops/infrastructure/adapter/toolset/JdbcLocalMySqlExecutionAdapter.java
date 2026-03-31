package cn.lgs.orbisops.infrastructure.adapter.toolset;

import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeResource;
import cn.lgs.orbisops.application.project.ResolveProjectRuntimeResourceQuery;
import cn.lgs.orbisops.application.toolset.LocalMySqlExecutionPort;
import cn.lgs.orbisops.application.toolset.LocalMySqlExecutionTarget;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** JDBC execution adapter for primary and project-scoped MySQL resources. */
@Repository
public class JdbcLocalMySqlExecutionAdapter
        implements LocalMySqlExecutionPort {

    private static final Set<String> READY_STATUSES = Set.of(
            "ENABLED", "CONNECTED", "READY", "ACTIVE");

    private final JdbcTemplate jdbcTemplate;
    private final ResolveProjectRuntimeResourceQuery runtimeResourceQuery;

    public JdbcLocalMySqlExecutionAdapter(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ResolveProjectRuntimeResourceQuery runtimeResourceQuery) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.runtimeResourceQuery = runtimeResourceQuery;
    }

    @Override
    public List<Map<String, Object>> query(
            LocalMySqlExecutionTarget target,
            String sql,
            List<Object> parameters) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "LOCAL_MYSQL_EXECUTION_TARGET_REQUIRED");
        }
        return target.projectScoped()
                ? queryProject(target, sql, parameters)
                : queryPrimary(target, sql, parameters);
    }

    private List<Map<String, Object>> queryPrimary(
            LocalMySqlExecutionTarget target,
            String sql,
            List<Object> parameters) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("MySQL JdbcTemplate 未初始化");
        }
        List<Object> safeParameters = parameters == null
                ? List.of()
                : parameters;
        List<Map<String, Object>> rows = jdbcTemplate.execute(
                (ConnectionCallback<List<Map<String, Object>>>) connection ->
                        executeQuery(
                                connection,
                                sql,
                                safeParameters,
                                target.maxRows(),
                                target.timeoutSeconds()));
        return rows == null ? List.of() : rows;
    }

    private List<Map<String, Object>> queryProject(
            LocalMySqlExecutionTarget target,
            String sql,
            List<Object> parameters) {
        String projectId = required(
                target.projectId(),
                "PROJECT_MYSQL_PROJECT_ID_REQUIRED：项目业务库只读查询必须提供 projectId");
        String resourceId = required(
                target.resourceId(),
                "PROJECT_MYSQL_RESOURCE_ID_REQUIRED：项目业务库只读查询必须提供 resourceId");
        ProjectWorkspaceRuntimeResource resource = runtimeResourceQuery.resolve(
                        projectId,
                        resourceId)
                .orElseThrow(() -> new SecurityException(
                        "PROJECT_MYSQL_RESOURCE_NOT_FOUND：项目未配置该 MySQL 资源"));
        assertReadyMySqlResource(resource);
        Map<String, Object> credential = resource.credential() == null
                ? Map.of()
                : resource.credential();
        String username = required(
                credential.get("username"),
                "PROJECT_MYSQL_USERNAME_REQUIRED：MySQL 资源缺少 username");
        String password = text(credential.get("password"));
        String jdbcUrl = mysqlJdbcUrl(resource.endpoint());
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                username,
                password)) {
            connection.setReadOnly(true);
            return executeQuery(
                    connection,
                    sql,
                    parameters == null ? List.of() : parameters,
                    target.maxRows(),
                    target.timeoutSeconds());
        } catch (Exception error) {
            throw new IllegalStateException(
                    "PROJECT_MYSQL_READONLY_FAILED：" + errorMessage(error),
                    error);
        }
    }

    private List<Map<String, Object>> executeQuery(
            Connection connection,
            String sql,
            List<Object> parameters,
            int maxRows,
            int timeoutSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(Math.max(1, timeoutSeconds));
            statement.setMaxRows(Math.max(1, maxRows));
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSetRows(resultSet, maxRows);
            }
        }
    }

    private List<Map<String, Object>> resultSetRows(
            ResultSet resultSet,
            int maxRows) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columns = metaData.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next() && rows.size() < Math.max(1, maxRows)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= columns; index++) {
                row.put(
                        metaData.getColumnLabel(index),
                        resultSet.getObject(index));
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private void assertReadyMySqlResource(
            ProjectWorkspaceRuntimeResource resource) {
        if (!"mysql".equalsIgnoreCase(text(resource.type()))) {
            throw new SecurityException(
                    "PROJECT_MYSQL_RESOURCE_TYPE_INVALID：resourceId 不是 MySQL 类型资源");
        }
        String status = fallback(
                resource.status(),
                "ENABLED").toUpperCase(Locale.ROOT);
        if (!READY_STATUSES.contains(status)) {
            throw new SecurityException(
                    "PROJECT_MYSQL_RESOURCE_NOT_READY：MySQL 资源未处于可用状态");
        }
    }

    private String mysqlJdbcUrl(String endpoint) {
        String raw = required(
                endpoint,
                "PROJECT_MYSQL_ENDPOINT_REQUIRED：MySQL 资源缺少 endpoint");
        URI uri = URI.create(raw);
        if (!"mysql".equalsIgnoreCase(text(uri.getScheme()))) {
            throw new SecurityException(
                    "PROJECT_MYSQL_ENDPOINT_INVALID：endpoint 必须使用 mysql://host:port/database");
        }
        String host = required(
                uri.getHost(),
                "PROJECT_MYSQL_ENDPOINT_INVALID：endpoint 缺少 host");
        int port = uri.getPort() > 0 ? uri.getPort() : 3306;
        String database = text(uri.getPath()).replaceFirst("^/", "");
        if (!database.matches("[A-Za-z0-9_$-]+")) {
            throw new SecurityException(
                    "PROJECT_MYSQL_DATABASE_INVALID：endpoint database 非法");
        }
        String query = text(uri.getRawQuery());
        String suffix = query.isBlank()
                ? "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
                : "?" + query;
        return "jdbc:mysql://" + host + ":" + port + "/" + database + suffix;
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

    private String errorMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
