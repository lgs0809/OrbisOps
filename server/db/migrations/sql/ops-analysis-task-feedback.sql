-- User feedback for the unified Chat/Channel/Alert/Schedule analysis task read model.

CREATE TABLE IF NOT EXISTS `ai_ops_analysis_feedback` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `feedback_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `run_id` VARCHAR(100) NOT NULL,
  `feedback_type` VARCHAR(32) NOT NULL COMMENT 'HELPFUL/INACCURATE/INSUFFICIENT_EVIDENCE',
  `comment_text` TEXT NULL,
  `created_by` VARCHAR(128) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_feedback_id` (`feedback_id`),
  KEY `idx_analysis_feedback_run` (`project_id`, `run_id`, `created_at`),
  KEY `idx_analysis_feedback_type` (`project_id`, `feedback_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unified analysis task user feedback';
