package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Infrastructure-only expand migration for Skill diagnosis and optimization state. */
@Slf4j
@Component
public final class JdbcSkillOptimizationSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.skill-optimization.auto-init:true}")
    private boolean autoInit;

    public JdbcSkillOptimizationSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbcTemplate = provider.getIfAvailable();
    }

    @PostConstruct
    public void initialize() {
        if (!autoInit || jdbcTemplate == null) return;
        try {
            createDiagnosisTable();
            createMemoryTable();
            createRunTable();
        } catch (DataAccessException error) {
            log.warn("Skill Optimization 表初始化失败，优化闭环降级：{}", error.getMessage());
        }
    }

    private void createDiagnosisTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_defect_diagnosis (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  diagnosis_id VARCHAR(128) NOT NULL,
                  skill_id VARCHAR(128) NOT NULL,
                  skill_version BIGINT NOT NULL,
                  defect_layer VARCHAR(48) NOT NULL,
                  symptom TEXT NOT NULL,
                  root_cause TEXT NOT NULL,
                  supporting_trajectory_ids_json MEDIUMTEXT NULL,
                  counterexample_ids_json MEDIUMTEXT NULL,
                  confidence DECIMAL(8,6) NOT NULL,
                  suggested_direction TEXT NOT NULL,
                  diagnosed_at TIMESTAMP(6) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_diagnosis_id (diagnosis_id),
                  KEY idx_skill_diagnosis_version (skill_id, skill_version, diagnosed_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill缺陷诊断'
                """);
    }

    private void createMemoryTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_optimization_memory (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  memory_id VARCHAR(128) NOT NULL,
                  skill_id VARCHAR(128) NOT NULL,
                  skill_version BIGINT NOT NULL,
                  memory_type VARCHAR(64) NOT NULL,
                  summary TEXT NOT NULL,
                  evidence_ids_json MEDIUMTEXT NULL,
                  model_compatibility VARCHAR(512) NOT NULL DEFAULT '',
                  environment_compatibility VARCHAR(512) NOT NULL DEFAULT '',
                  effective TINYINT NOT NULL DEFAULT 0,
                  recorded_at TIMESTAMP(6) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_optimization_memory_id (memory_id),
                  KEY idx_skill_optimization_memory (skill_id, recorded_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill优化专用记忆'
                """);
    }

    private void createRunTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_optimization_run (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  optimization_run_id VARCHAR(128) NOT NULL,
                  skill_id VARCHAR(128) NOT NULL,
                  base_version BIGINT NOT NULL,
                  base_skill_hash VARCHAR(128) NOT NULL,
                  status VARCHAR(48) NOT NULL,
                  max_rounds INT NOT NULL,
                  rounds_json MEDIUMTEXT NULL,
                  created_at TIMESTAMP(6) NOT NULL,
                  updated_at TIMESTAMP(6) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_optimization_run_id (optimization_run_id),
                  KEY idx_skill_optimization_run (skill_id, status, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill优化运行状态'
                """);
    }
}
