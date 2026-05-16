package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class JdbcSkillLifecycleSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.skill-lifecycle.auto-init:true}")
    private boolean autoInit;

    public JdbcSkillLifecycleSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbcTemplate = provider.getIfAvailable();
    }

    @PostConstruct
    public void initialize() {
        if (!autoInit || jdbcTemplate == null) return;
        try {
            createProposalTable();
            createDecisionTable();
            createLineageTable();
        } catch (DataAccessException error) {
            log.warn("Skill Lifecycle 表初始化失败，生命周期治理降级：{}", error.getMessage());
        }
    }

    private void createProposalTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_lifecycle_proposal (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  proposal_id VARCHAR(128) NOT NULL,
                  operation VARCHAR(48) NOT NULL,
                  sources_json MEDIUMTEXT NULL,
                  target_skill_ids_json MEDIUMTEXT NULL,
                  reason TEXT NOT NULL,
                  evidence_ids_json MEDIUMTEXT NULL,
                  behavior_evaluation_id VARCHAR(128) NOT NULL DEFAULT '',
                  behavior_replay_passed TINYINT NOT NULL DEFAULT 0,
                  proposed_at TIMESTAMP(6) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_lifecycle_proposal_id (proposal_id),
                  KEY idx_skill_lifecycle_operation (operation, proposed_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill生命周期提案'
                """);
    }

    private void createDecisionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_lifecycle_decision (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  proposal_id VARCHAR(128) NOT NULL,
                  approved TINYINT NOT NULL,
                  target_retention_state VARCHAR(48) NULL,
                  reason_codes_json MEDIUMTEXT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_lifecycle_decision_proposal (proposal_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill生命周期决策'
                """);
    }

    private void createLineageTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_lineage_edge (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  proposal_id VARCHAR(128) NOT NULL,
                  source_reference VARCHAR(256) NOT NULL,
                  target_skill_id VARCHAR(128) NOT NULL,
                  relation_type VARCHAR(48) NOT NULL,
                  created_at TIMESTAMP(6) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_lineage_edge
                    (proposal_id, source_reference, target_skill_id, relation_type),
                  KEY idx_skill_lineage_source (source_reference, created_at),
                  KEY idx_skill_lineage_target (target_skill_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill派生关系'
                """);
    }
}
