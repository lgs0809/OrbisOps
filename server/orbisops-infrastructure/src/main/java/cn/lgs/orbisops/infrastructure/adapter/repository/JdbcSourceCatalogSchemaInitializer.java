package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcSourceCatalogSchemaInitializer {

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final boolean enabled;
    private final boolean jdbcEnabled;
    private final boolean autoInit;

    public JdbcSourceCatalogSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            @Value("${orbisops.source-repository.enabled:false}") boolean enabled,
            @Value("${orbisops.source-repository.jdbc-enabled:true}") boolean jdbcEnabled,
            @Value("${orbisops.source-repository.auto-init:true}") boolean autoInit) {
        this.jdbcProvider = jdbcProvider;
        this.enabled = enabled;
        this.jdbcEnabled = jdbcEnabled;
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void initialize() {
        if (!enabled || !jdbcEnabled || !autoInit) return;
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) throw new IllegalStateException("代码仓库配置必须使用 MySQL 持久化");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_source_repository (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  repository_id VARCHAR(100) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  name VARCHAR(200) NOT NULL,
                  local_path VARCHAR(1000) NOT NULL,
                  access_mode VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
                  code_mcp_id VARCHAR(100) NOT NULL DEFAULT '',
                  logical_root VARCHAR(100) NOT NULL DEFAULT '',
                  default_revision VARCHAR(200) NOT NULL,
                  default_commit_sha CHAR(40) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  created_by VARCHAR(120) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_repository_id (repository_id),
                  KEY idx_project_status (project_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目只读代码仓库'
                """);
        addRepositoryColumnIfMissing(jdbc, "access_mode", "VARCHAR(16) NOT NULL DEFAULT 'LOCAL' AFTER local_path");
        addRepositoryColumnIfMissing(jdbc, "code_mcp_id", "VARCHAR(100) NOT NULL DEFAULT '' AFTER access_mode");
        addRepositoryColumnIfMissing(jdbc, "logical_root", "VARCHAR(100) NOT NULL DEFAULT '' AFTER code_mcp_id");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_deployment_revision (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  deployment_id VARCHAR(260) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  repository_id VARCHAR(100) NOT NULL,
                  environment VARCHAR(32) NOT NULL,
                  service_name VARCHAR(100) NOT NULL,
                  commit_sha CHAR(40) NOT NULL,
                  image_ref VARCHAR(500) NULL,
                  recorded_by VARCHAR(120) NOT NULL,
                  deployed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_deployment_id (deployment_id),
                  KEY idx_project_env (project_id, environment),
                  KEY idx_repository_commit (repository_id, commit_sha)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产部署与Git Commit映射'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_project_service (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  service_id VARCHAR(100) NOT NULL,
                  project_id VARCHAR(80) NOT NULL,
                  name VARCHAR(128) NOT NULL,
                  repository_id VARCHAR(100) NOT NULL,
                  module_path VARCHAR(512) NOT NULL,
                  build_profile VARCHAR(40) NOT NULL,
                  artifact_path VARCHAR(512) NULL,
                  deployment_resource_id VARCHAR(128) NULL,
                  health_url VARCHAR(1000) NULL,
                  smoke_urls_json TEXT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'READY',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_project_service (project_id, service_id),
                  KEY idx_service_repository (project_id, repository_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目运维服务目录'
                """);
    }

    private void addRepositoryColumnIfMissing(JdbcTemplate jdbc, String column, String definition) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'ai_ops_source_repository'
                  AND COLUMN_NAME = ?
                """, Integer.class, column);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE ai_ops_source_repository ADD COLUMN " + column + " " + definition);
        }
    }
}
