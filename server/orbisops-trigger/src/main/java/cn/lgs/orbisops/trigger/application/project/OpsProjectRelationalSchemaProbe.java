package cn.lgs.orbisops.trigger.application.project;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only schema discovery for MySQL and PostgreSQL resources. */
@Component
public class OpsProjectRelationalSchemaProbe implements OpsProjectResourceSchemaProbe {

    @Override
    public boolean supports(String resourceType) {
        return "mysql".equals(resourceType) || "postgresql".equals(resourceType);
    }

    @Override
    public Map<String, Object> scan(String resourceType,
                                    String endpoint,
                                    Map<String, Object> credential) throws SQLException {
        String schemaName = databaseName(endpoint, resourceType);
        List<Map<String, Object>> objects = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(resourceType, endpoint),
                text(credential.get("username"), defaultUsername(resourceType)),
                text(credential.get("password"), ""))) {
            connection.setReadOnly(true);
            if (!StringUtils.hasText(schemaName)) {
                schemaName = "postgresql".equals(resourceType)
                        ? firstSchema(connection)
                        : connection.getCatalog();
            }
            String tableSql = "postgresql".equals(resourceType)
                    ? "SELECT table_name, table_type FROM information_schema.tables WHERE table_schema = ? AND table_type IN ('BASE TABLE','VIEW') ORDER BY table_name LIMIT 50"
                    : "SELECT table_name, COALESCE(table_comment, '') AS table_comment FROM information_schema.tables WHERE table_schema = ? AND table_type IN ('BASE TABLE','VIEW') ORDER BY table_name LIMIT 50";
            try (PreparedStatement statement = connection.prepareStatement(tableSql)) {
                statement.setString(1, schemaName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String tableName = resultSet.getString("table_name");
                        String comment = "postgresql".equals(resourceType)
                                ? resultSet.getString("table_type")
                                : resultSet.getString("table_comment");
                        objects.add(Map.of(
                                "name", tableName,
                                "comment", text(comment, "数据库对象"),
                                "columns", queryColumns(connection, schemaName, tableName),
                                "indexes", queryIndexes(
                                        connection, resourceType, schemaName, tableName)));
                    }
                }
            }
        }
        return Map.of(
                "objects", objects,
                "scannedAt", LocalDateTime.now().toString());
    }

    private List<String> queryColumns(Connection connection,
                                      String schemaName,
                                      String tableName) throws SQLException {
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position LIMIT 16";
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("column_name"));
                }
            }
        }
        return columns;
    }

    private List<String> queryIndexes(Connection connection,
                                      String resourceType,
                                      String schemaName,
                                      String tableName) throws SQLException {
        String sql = "postgresql".equals(resourceType)
                ? "SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = ? ORDER BY indexname LIMIT 8"
                : "SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = ? AND table_name = ? ORDER BY index_name LIMIT 8";
        List<String> indexes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    indexes.add(resultSet.getString(1));
                }
            }
        }
        return indexes;
    }

    private String firstSchema(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT current_schema()")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString(1);
                }
            }
        }
        return "public";
    }

    private String jdbcUrl(String resourceType, String endpoint) {
        if (endpoint.startsWith("jdbc:")) {
            return endpoint;
        }
        URI uri = resourceUri(endpoint, resourceType);
        String database = databaseName(endpoint, resourceType);
        String authority = uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        if ("postgresql".equals(resourceType)) {
            return "jdbc:postgresql://" + authority + "/" + text(database, "postgres");
        }
        return "jdbc:mysql://" + authority + "/" + text(database, "mysql")
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    private URI resourceUri(String endpoint, String defaultScheme) {
        String value = endpoint;
        if (value.startsWith("jdbc:")) {
            value = value.substring("jdbc:".length());
        }
        if (!value.contains("://")) {
            value = defaultScheme + "://" + value;
        }
        return URI.create(value);
    }

    private String databaseName(String endpoint, String resourceType) {
        URI uri = resourceUri(endpoint, resourceType);
        String path = text(uri.getPath(), "");
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "";
        }
        return path.replaceFirst("^/", "").split("/")[0];
    }

    private String defaultUsername(String resourceType) {
        return "postgresql".equals(resourceType) ? "postgres" : "root";
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
