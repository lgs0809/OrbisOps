package cn.lgs.orbisops.infrastructure.adapter.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Infrastructure-only compatibility initializer for Skill catalog and immutable Package tables. */
@Slf4j
@Component
public final class JdbcSkillCatalogSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.skill-catalog.auto-init:true}")
    private boolean autoInit;

    public JdbcSkillCatalogSchemaInitializer(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @PostConstruct
    public void initialize() {
        if (!autoInit || jdbcTemplate == null) return;
        try {
            createCatalogTable();
            ensureCatalogColumns();
            backfillLegacyGovernance();
            createVersionTable();
            ensureVersionColumns();
            createArtifactTable();
            ensureArtifactColumns();
        } catch (DataAccessException error) {
            log.warn("Skill Catalog 表初始化失败，DB Skill 能力降级：{}", error.getMessage());
        }
    }

    private void createCatalogTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                  skill_id VARCHAR(128) NOT NULL COMMENT 'Skill ID',
                  project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
                  skill_name VARCHAR(160) NOT NULL COMMENT 'Skill名称',
                  scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
                  source_global_skill_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '来源通用Skill',
                  description TEXT NULL COMMENT '说明',
                  content MEDIUMTEXT NULL COMMENT 'Skill正文',
                  version INT NOT NULL DEFAULT 1 COMMENT '版本',
                  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
                  origin VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/EVOLVED/IMPORTED',
                  update_mode VARCHAR(32) NOT NULL DEFAULT 'AUTO' COMMENT '兼容投影',
                  lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'DRAFT/ACTIVE/PAUSED/DEPRECATED/RETIRED',
                  mutation_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL_ONLY' COMMENT 'AUTO/MANUAL_ONLY/LOCKED/SEALED',
                  execution_mode VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/SHADOW_ONLY/QUARANTINED/DISABLED',
                  binding_mode VARCHAR(32) NOT NULL DEFAULT 'FLOATING' COMMENT 'FLOATING/PINNED',
                  lock_type VARCHAR(48) NOT NULL DEFAULT 'NONE' COMMENT '治理锁类型',
                  lock_reason VARCHAR(512) NOT NULL DEFAULT '' COMMENT '治理锁原因',
                  lock_actor VARCHAR(128) NOT NULL DEFAULT '' COMMENT '治理锁操作人',
                  lock_approval_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '治理审批ID',
                  lock_at TIMESTAMP NULL DEFAULT NULL COMMENT '治理锁时间',
                  legacy_frozen_classification_required TINYINT NOT NULL DEFAULT 0 COMMENT '旧FROZEN待分类',
                  auto_update_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许自动更新',
                  auto_merge_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许相似合并',
                  last_evolved_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近自动进化时间',
                  frozen_reason VARCHAR(512) NOT NULL DEFAULT '' COMMENT '冻结原因',
                  frozen_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '冻结人',
                  frozen_at TIMESTAMP NULL DEFAULT NULL COMMENT '冻结时间',
                  skill_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Skill规范化Hash',
                  current_version INT NOT NULL DEFAULT 1 COMMENT '当前指针版本',
                  current_skill_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT '当前指针Hash',
                  version_seq INT NOT NULL DEFAULT 1 COMMENT '版本序列',
                  current_package_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT '当前Skill Package Hash',
                  package_manifest_json MEDIUMTEXT NULL COMMENT '当前Skill Package Manifest',
                  artifact_hashes_json MEDIUMTEXT NULL COMMENT '当前Artifact Hash集合',
                  create_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_scope_project_skill (scope, project_id, skill_id),
                  KEY idx_project_status (project_id, status),
                  KEY idx_scope_status (scope, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Skill目录表'
                """);
    }

    private void ensureCatalogColumns() {
        ensureColumn("ai_ops_skill", "origin",
                "ALTER TABLE ai_ops_skill ADD COLUMN origin VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/EVOLVED/IMPORTED'");
        ensureColumn("ai_ops_skill", "update_mode",
                "ALTER TABLE ai_ops_skill ADD COLUMN update_mode VARCHAR(32) NOT NULL DEFAULT 'AUTO' COMMENT '兼容投影'");
        ensureColumn("ai_ops_skill", "lifecycle_status",
                "ALTER TABLE ai_ops_skill ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'DRAFT/ACTIVE/PAUSED/DEPRECATED/RETIRED'");
        ensureColumn("ai_ops_skill", "mutation_mode",
                "ALTER TABLE ai_ops_skill ADD COLUMN mutation_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL_ONLY' COMMENT 'AUTO/MANUAL_ONLY/LOCKED/SEALED'");
        ensureColumn("ai_ops_skill", "execution_mode",
                "ALTER TABLE ai_ops_skill ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/SHADOW_ONLY/QUARANTINED/DISABLED'");
        ensureColumn("ai_ops_skill", "binding_mode",
                "ALTER TABLE ai_ops_skill ADD COLUMN binding_mode VARCHAR(32) NOT NULL DEFAULT 'FLOATING' COMMENT 'FLOATING/PINNED'");
        ensureColumn("ai_ops_skill", "lock_type",
                "ALTER TABLE ai_ops_skill ADD COLUMN lock_type VARCHAR(48) NOT NULL DEFAULT 'NONE' COMMENT '治理锁类型'");
        ensureColumn("ai_ops_skill", "lock_reason",
                "ALTER TABLE ai_ops_skill ADD COLUMN lock_reason VARCHAR(512) NOT NULL DEFAULT '' COMMENT '治理锁原因'");
        ensureColumn("ai_ops_skill", "lock_actor",
                "ALTER TABLE ai_ops_skill ADD COLUMN lock_actor VARCHAR(128) NOT NULL DEFAULT '' COMMENT '治理锁操作人'");
        ensureColumn("ai_ops_skill", "lock_approval_id",
                "ALTER TABLE ai_ops_skill ADD COLUMN lock_approval_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '治理审批ID'");
        ensureColumn("ai_ops_skill", "lock_at",
                "ALTER TABLE ai_ops_skill ADD COLUMN lock_at TIMESTAMP NULL DEFAULT NULL COMMENT '治理锁时间'");
        ensureColumn("ai_ops_skill", "legacy_frozen_classification_required",
                "ALTER TABLE ai_ops_skill ADD COLUMN legacy_frozen_classification_required TINYINT NOT NULL DEFAULT 0 COMMENT '旧FROZEN待分类'");
        ensureColumn("ai_ops_skill", "auto_update_enabled",
                "ALTER TABLE ai_ops_skill ADD COLUMN auto_update_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许自动更新'");
        ensureColumn("ai_ops_skill", "auto_merge_enabled",
                "ALTER TABLE ai_ops_skill ADD COLUMN auto_merge_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许相似合并'");
        ensureColumn("ai_ops_skill", "last_evolved_at",
                "ALTER TABLE ai_ops_skill ADD COLUMN last_evolved_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近自动进化时间'");
        ensureColumn("ai_ops_skill", "frozen_reason",
                "ALTER TABLE ai_ops_skill ADD COLUMN frozen_reason VARCHAR(512) NOT NULL DEFAULT '' COMMENT '冻结原因'");
        ensureColumn("ai_ops_skill", "frozen_by",
                "ALTER TABLE ai_ops_skill ADD COLUMN frozen_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '冻结人'");
        ensureColumn("ai_ops_skill", "frozen_at",
                "ALTER TABLE ai_ops_skill ADD COLUMN frozen_at TIMESTAMP NULL DEFAULT NULL COMMENT '冻结时间'");
        ensureColumn("ai_ops_skill", "skill_hash",
                "ALTER TABLE ai_ops_skill ADD COLUMN skill_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Skill规范化Hash'");
        ensureColumn("ai_ops_skill", "current_version",
                "ALTER TABLE ai_ops_skill ADD COLUMN current_version INT NOT NULL DEFAULT 1 COMMENT '当前指针版本'");
        ensureColumn("ai_ops_skill", "current_skill_hash",
                "ALTER TABLE ai_ops_skill ADD COLUMN current_skill_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT '当前指针Hash'");
        ensureColumn("ai_ops_skill", "version_seq",
                "ALTER TABLE ai_ops_skill ADD COLUMN version_seq INT NOT NULL DEFAULT 1 COMMENT '版本序列'");
        ensureColumn("ai_ops_skill", "current_package_hash",
                "ALTER TABLE ai_ops_skill ADD COLUMN current_package_hash VARCHAR(128) NOT NULL DEFAULT '' COMMENT '当前Skill Package Hash'");
        ensureColumn("ai_ops_skill", "package_manifest_json",
                "ALTER TABLE ai_ops_skill ADD COLUMN package_manifest_json MEDIUMTEXT NULL COMMENT '当前Skill Package Manifest'");
        ensureColumn("ai_ops_skill", "artifact_hashes_json",
                "ALTER TABLE ai_ops_skill ADD COLUMN artifact_hashes_json MEDIUMTEXT NULL COMMENT '当前Artifact Hash集合'");
    }

    private void backfillLegacyGovernance() {
        jdbcTemplate.update("""
                UPDATE ai_ops_skill
                SET lifecycle_status='ACTIVE',
                    mutation_mode='LOCKED',
                    execution_mode='QUARANTINED',
                    binding_mode=COALESCE(NULLIF(binding_mode, ''), 'FLOATING'),
                    lock_type='LEGACY_UNCLASSIFIED',
                    lock_reason=CASE
                        WHEN COALESCE(frozen_reason, '')='' THEN 'legacy FROZEN classification required'
                        ELSE frozen_reason
                    END,
                    lock_actor=CASE
                        WHEN COALESCE(frozen_by, '')='' THEN 'SYSTEM_LEGACY_MIGRATION'
                        ELSE frozen_by
                    END,
                    lock_approval_id='',
                    lock_at=COALESCE(frozen_at, create_time),
                    legacy_frozen_classification_required=1
                WHERE status='FROZEN' OR update_mode='FROZEN'
                """);
        jdbcTemplate.update("""
                UPDATE ai_ops_skill
                SET lifecycle_status=CASE UPPER(COALESCE(status, 'ENABLED'))
                        WHEN 'DRAFT' THEN 'DRAFT'
                        WHEN 'PAUSED' THEN 'PAUSED'
                        WHEN 'DEPRECATED' THEN 'DEPRECATED'
                        WHEN 'RETIRED' THEN 'RETIRED'
                        WHEN 'REJECTED' THEN 'RETIRED'
                        ELSE 'ACTIVE'
                    END,
                    mutation_mode=CASE UPPER(COALESCE(update_mode, 'MANUAL_ONLY'))
                        WHEN 'AUTO' THEN 'AUTO'
                        ELSE 'MANUAL_ONLY'
                    END,
                    execution_mode=CASE UPPER(COALESCE(status, 'ENABLED'))
                        WHEN 'DRAFT' THEN 'DISABLED'
                        WHEN 'PAUSED' THEN 'DISABLED'
                        WHEN 'DEPRECATED' THEN 'DISABLED'
                        WHEN 'RETIRED' THEN 'DISABLED'
                        WHEN 'REJECTED' THEN 'DISABLED'
                        WHEN 'DISABLED' THEN 'DISABLED'
                        ELSE 'ENABLED'
                    END
                WHERE legacy_frozen_classification_required=0
                  AND COALESCE(lock_type, 'NONE')='NONE'
                  AND status<>'FROZEN'
                  AND update_mode<>'FROZEN'
                """);
    }

    private void createVersionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_version (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  skill_id VARCHAR(128) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  scope VARCHAR(24) NOT NULL,
                  version INT NOT NULL,
                  skill_hash VARCHAR(128) NOT NULL DEFAULT '',
                  base_version INT NOT NULL DEFAULT 0,
                  base_skill_hash VARCHAR(128) NOT NULL DEFAULT '',
                  source_run_id VARCHAR(80) NOT NULL DEFAULT '',
                  source_session_id VARCHAR(80) NOT NULL DEFAULT '',
                  evolution_job_id VARCHAR(80) NOT NULL DEFAULT '',
                  publish_mode VARCHAR(32) NOT NULL DEFAULT '',
                  package_hash VARCHAR(128) NOT NULL DEFAULT '',
                  manifest_json MEDIUMTEXT NULL,
                  artifact_hashes_json MEDIUMTEXT NULL,
                  entrypoint VARCHAR(255) NOT NULL DEFAULT 'SKILL.md',
                  artifact_count INT NOT NULL DEFAULT 1,
                  package_size BIGINT NOT NULL DEFAULT 0,
                  content MEDIUMTEXT NOT NULL,
                  source_type VARCHAR(32) DEFAULT '',
                  source_trace_id VARCHAR(80) DEFAULT '',
                  change_summary TEXT,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_version (scope, project_id, skill_id, version),
                  KEY idx_skill_id (skill_id),
                  KEY idx_source_trace (source_trace_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Skill版本表'
                """);
    }

    private void ensureVersionColumns() {
        ensureColumn("ai_ops_skill_version", "skill_hash",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN skill_hash VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_skill_version", "base_version",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN base_version INT NOT NULL DEFAULT 0");
        ensureColumn("ai_ops_skill_version", "base_skill_hash",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN base_skill_hash VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_skill_version", "source_run_id",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN source_run_id VARCHAR(80) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_skill_version", "source_session_id",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN source_session_id VARCHAR(80) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_skill_version", "evolution_job_id",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN evolution_job_id VARCHAR(80) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_skill_version", "publish_mode",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN publish_mode VARCHAR(32) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_skill_version", "package_hash",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN package_hash VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn("ai_ops_skill_version", "manifest_json",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN manifest_json MEDIUMTEXT NULL");
        ensureColumn("ai_ops_skill_version", "artifact_hashes_json",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN artifact_hashes_json MEDIUMTEXT NULL");
        ensureColumn("ai_ops_skill_version", "entrypoint",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN entrypoint VARCHAR(255) NOT NULL DEFAULT 'SKILL.md'");
        ensureColumn("ai_ops_skill_version", "artifact_count",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN artifact_count INT NOT NULL DEFAULT 1");
        ensureColumn("ai_ops_skill_version", "package_size",
                "ALTER TABLE ai_ops_skill_version ADD COLUMN package_size BIGINT NOT NULL DEFAULT 0");
    }

    private void createArtifactTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_skill_artifact (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  artifact_id VARCHAR(80) NOT NULL,
                  scope VARCHAR(24) NOT NULL,
                  project_id VARCHAR(128) NOT NULL DEFAULT '',
                  skill_id VARCHAR(128) NOT NULL,
                  version INT NOT NULL,
                  package_hash VARCHAR(128) NOT NULL,
                  artifact_path VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                  artifact_role VARCHAR(32) NOT NULL DEFAULT 'RESOURCE',
                  media_type VARCHAR(128) NOT NULL DEFAULT 'text/plain; charset=utf-8',
                  content_encoding VARCHAR(16) NOT NULL DEFAULT 'UTF8',
                  content_hash VARCHAR(128) NOT NULL,
                  size_bytes BIGINT NOT NULL DEFAULT 0,
                  content MEDIUMTEXT NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_skill_artifact_version (scope, project_id, skill_id, version, artifact_path),
                  UNIQUE KEY uk_skill_artifact_id (artifact_id),
                  KEY idx_skill_artifact_package (package_hash),
                  KEY idx_skill_artifact_lookup (project_id, skill_id, version)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill Package append-only artifact'
                """);
    }

    private void ensureArtifactColumns() {
        ensureColumn("ai_ops_skill_artifact", "content_encoding",
                "ALTER TABLE ai_ops_skill_artifact ADD COLUMN content_encoding VARCHAR(16) NOT NULL DEFAULT 'UTF8' AFTER media_type");
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """, Integer.class, tableName, columnName);
            if (count == null || count == 0) jdbcTemplate.execute(ddl);
        } catch (DataAccessException error) {
            log.debug("检查/新增 Skill 字段失败 table={} column={} reason={}",
                    tableName, columnName, error.getMessage());
        }
    }
}
