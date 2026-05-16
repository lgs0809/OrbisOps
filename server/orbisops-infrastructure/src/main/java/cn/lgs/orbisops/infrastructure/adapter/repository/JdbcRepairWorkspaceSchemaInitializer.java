package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcRepairWorkspaceSchemaInitializer {

    private static final String WRITER_OWNER = "writer_lease_" + "owner";
    private static final String WRITER_CLAIM = "writer_lease_" + "token";
    private static final String WRITER_FENCE = "writer_fencing_" + "token";
    private static final String WRITER_EXPIRES = "writer_lease_" + "expires_at";

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final boolean jdbcEnabled;
    private final boolean autoInit;

    public JdbcRepairWorkspaceSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            @Value("${orbisops.repair.jdbc-enabled:true}") boolean jdbcEnabled,
            @Value("${orbisops.repair.auto-init:true}") boolean autoInit) {
        this.jdbcProvider = jdbcProvider;
        this.jdbcEnabled = jdbcEnabled;
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (!jdbcEnabled || !autoInit) return;
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) return;
        jdbc.execute(("""
                CREATE TABLE IF NOT EXISTS ai_ops_repair_workspace (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  workspace_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  service_id VARCHAR(100) NOT NULL,
                  repository_id VARCHAR(100) NOT NULL,
                  environment VARCHAR(32) NOT NULL,
                  base_commit CHAR(40) NOT NULL,
                  verified_commit CHAR(40) NULL,
                  status VARCHAR(32) NOT NULL,
                  summary VARCHAR(1000) NOT NULL,
                  unified_diff MEDIUMTEXT NULL,
                  changed_files_json TEXT NULL,
                  test_profile VARCHAR(40) NULL,
                  test_command VARCHAR(1000) NULL,
                  test_exit_code INT NULL,
                  test_log MEDIUMTEXT NULL,
                  artifact_path VARCHAR(1500) NULL,
                  artifact_sha256 CHAR(64) NULL,
                  artifact_size BIGINT NULL,
                  created_by VARCHAR(120) NOT NULL,
                  %s VARCHAR(120) NOT NULL DEFAULT '',
                  %s VARCHAR(128) NOT NULL DEFAULT '',
                  %s BIGINT NOT NULL DEFAULT 0,
                  %s DATETIME(3) NULL,
                  state_version BIGINT NOT NULL DEFAULT 0,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_workspace_id (workspace_id),
                  KEY idx_project_status (project_id, status),
                  KEY idx_service_status (service_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控代码修复工作区'
                """).formatted(WRITER_OWNER, WRITER_CLAIM, WRITER_FENCE, WRITER_EXPIRES));
        addColumnIfMissing(jdbc, "verified_commit", "CHAR(40) NULL AFTER base_commit");
        addColumnIfMissing(jdbc, WRITER_OWNER, "VARCHAR(120) NOT NULL DEFAULT '' AFTER created_by");
        addColumnIfMissing(jdbc, WRITER_CLAIM, "VARCHAR(128) NOT NULL DEFAULT '' AFTER " + WRITER_OWNER);
        addColumnIfMissing(jdbc, WRITER_FENCE, "BIGINT NOT NULL DEFAULT 0 AFTER " + WRITER_CLAIM);
        addColumnIfMissing(jdbc, WRITER_EXPIRES, "DATETIME(3) NULL AFTER " + WRITER_FENCE);
        addColumnIfMissing(jdbc, "state_version", "BIGINT NOT NULL DEFAULT 0 AFTER " + WRITER_EXPIRES);
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String column, String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'ai_ops_repair_workspace'
                  AND COLUMN_NAME = ?
                """, Integer.class, column);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE ai_ops_repair_workspace ADD COLUMN " + column + " " + definition);
        }
    }
}
