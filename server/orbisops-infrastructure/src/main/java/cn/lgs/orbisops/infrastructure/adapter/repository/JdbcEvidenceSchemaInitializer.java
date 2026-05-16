package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcEvidenceSchemaInitializer {

    private final JdbcTemplate jdbc;
    private final boolean toolResultAutoInit;
    private final boolean evidenceAutoInit;
    private final boolean trustedProofAutoInit;

    public JdbcEvidenceSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider,
            @Value("${orbisops.tool-result.auto-init:true}") boolean toolResultAutoInit,
            @Value("${orbisops.evidence.auto-init:true}") boolean evidenceAutoInit,
            @Value("${orbisops.trusted-proof.auto-init:true}") boolean trustedProofAutoInit) {
        this.jdbc = provider.getIfAvailable();
        this.toolResultAutoInit = toolResultAutoInit;
        this.evidenceAutoInit = evidenceAutoInit;
        this.trustedProofAutoInit = trustedProofAutoInit;
    }

    @PostConstruct
    public void initialize() {
        if (jdbc == null) return;
        if (toolResultAutoInit) initializeToolResult();
        if (evidenceAutoInit) initializeEvidence();
        if (trustedProofAutoInit) initializeTrustedProof();
    }

    public void initializeToolResult() {
        requireJdbc().execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_tool_result (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  result_id VARCHAR(100) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  session_id VARCHAR(100) NOT NULL DEFAULT '',
                  run_id VARCHAR(100) NOT NULL DEFAULT '',
                  user_id VARCHAR(128) NOT NULL DEFAULT '',
                  toolset_id VARCHAR(128) NOT NULL DEFAULT '',
                  tool_name VARCHAR(128) NOT NULL DEFAULT '',
                  source VARCHAR(128) NOT NULL DEFAULT '',
                  status VARCHAR(24) NOT NULL DEFAULT 'SUCCEEDED',
                  query_text TEXT NULL,
                  input_hash VARCHAR(64) NOT NULL DEFAULT '',
                  preview_text MEDIUMTEXT NULL,
                  full_output MEDIUMTEXT NULL,
                  full_output_ref VARCHAR(256) NOT NULL DEFAULT '',
                  output_hash VARCHAR(64) NOT NULL DEFAULT '',
                  truncated TINYINT NOT NULL DEFAULT 0,
                  duration_ms BIGINT NOT NULL DEFAULT 0,
                  max_rows INT NOT NULL DEFAULT 0,
                  max_bytes INT NOT NULL DEFAULT 0,
                  max_lines INT NOT NULL DEFAULT 0,
                  max_points INT NOT NULL DEFAULT 0,
                  max_time_range INT NOT NULL DEFAULT 0,
                  created_by VARCHAR(128) NOT NULL DEFAULT '',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_tool_result_id (result_id),
                  KEY idx_tool_result_project (project_id, create_time),
                  KEY idx_tool_result_run (run_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维工具输出结果'
                """);
        requireJdbc().execute("ALTER TABLE ai_ops_tool_result MODIFY COLUMN source VARCHAR(128) NOT NULL DEFAULT ''");
        addColumnIfMissing("ai_ops_tool_result", "status",
                "ALTER TABLE ai_ops_tool_result ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'SUCCEEDED' AFTER source");
        addColumnIfMissing("ai_ops_tool_result", "input_hash",
                "ALTER TABLE ai_ops_tool_result ADD COLUMN input_hash VARCHAR(64) NOT NULL DEFAULT '' AFTER query_text");
        addColumnIfMissing("ai_ops_tool_result", "duration_ms",
                "ALTER TABLE ai_ops_tool_result ADD COLUMN duration_ms BIGINT NOT NULL DEFAULT 0 AFTER truncated");
    }

    public void initializeEvidence() {
        requireJdbc().execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_evidence (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  evidence_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL,
                  run_id VARCHAR(100) NOT NULL,
                  source_type VARCHAR(32) NOT NULL,
                  source_id VARCHAR(128) NOT NULL DEFAULT '',
                  tool_result_id VARCHAR(100) NOT NULL,
                  output_hash VARCHAR(64) NOT NULL,
                  full_output_ref VARCHAR(256) NOT NULL,
                  summary TEXT NULL,
                  verified TINYINT NOT NULL DEFAULT 0,
                  metadata_json MEDIUMTEXT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  created_by VARCHAR(128) NOT NULL DEFAULT '',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_evidence_id (evidence_id),
                  UNIQUE KEY uk_evidence_idempotency (idempotency_key),
                  KEY idx_evidence_run (project_id,run_id,created_at),
                  KEY idx_evidence_result (tool_result_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一运维证据引用'
                """);
    }

    public void initializeTrustedProof() {
        requireJdbc().execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_trusted_proof (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  proof_id VARCHAR(100) NOT NULL,
                  project_id VARCHAR(128) NOT NULL,
                  package_id VARCHAR(100) NOT NULL,
                  package_version INT NOT NULL,
                  package_hash VARCHAR(128) NOT NULL,
                  proof_type VARCHAR(64) NOT NULL,
                  source VARCHAR(64) NOT NULL,
                  external_run_id VARCHAR(120) NULL,
                  command_hash VARCHAR(64) NULL,
                  script_hash VARCHAR(64) NULL,
                  result_status VARCHAR(32) NOT NULL,
                  risk_level VARCHAR(20) NOT NULL,
                  metadata_json MEDIUMTEXT NULL,
                  created_by VARCHAR(128) NOT NULL DEFAULT '',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ops_trusted_proof (proof_id),
                  KEY idx_ops_trusted_proof_pkg (package_id, package_version, package_hash),
                  KEY idx_ops_trusted_proof_project (project_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维 Agent 可信 proof 记录'
                """);
    }

    public boolean available() {
        return jdbc != null;
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        Integer count = requireJdbc().queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        if (count == null || count == 0) requireJdbc().execute(ddl);
    }

    private JdbcTemplate requireJdbc() {
        if (jdbc == null) throw new IllegalStateException("Evidence JDBC 未配置");
        return jdbc;
    }
}
