package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reuse one explicitly supplied local test schema rather than launching another database. */
final class OrbisOpsExternalMySqlFixture {
    static DriverManagerDataSource dataSource() {
        String url = System.getenv("ORBISOPS_DISCOVERY_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:13362/orbisops_mcp_counting_[a-zA-Z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("AN_EXPLICIT_ISOLATED_ACCEPTANCE_SCHEMA_IS_REQUIRED");
        return new DriverManagerDataSource(url, System.getenv("ORBISOPS_DISCOVERY_TEST_MYSQL_USER"),
                System.getenv("ORBISOPS_DISCOVERY_TEST_MYSQL_PASSWORD"));
    }
    static Path migration(String name) {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("db/migrations/manifest.tsv"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("CANNOT_LOCATE_MIGRATIONS");
        return root.resolve("db/migrations/sql/" + name);
    }
    static void apply(DriverManagerDataSource ds, String name) throws Exception {
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(migration(name)));
        }
    }
    static void createTables(DriverManagerDataSource ds, String... tables) throws Exception {
        String sql = Files.readString(migration("ops-intent-memory-skill-channel.sql"));
        for (String table : tables) {
            int start = sql.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            if (start < 0) throw new IllegalStateException("TABLE_DDL_MISSING:" + table);
            int end = sql.indexOf(';', start);
            new JdbcTemplate(ds).execute(sql.substring(start, end));
        }
    }
}
