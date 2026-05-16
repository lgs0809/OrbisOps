-- Session collaboration and immutable per-run participant ACL snapshots.

CREATE TABLE IF NOT EXISTS `ai_ops_chat_session_participant` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(80) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL DEFAULT '',
  `participant_user_id` VARCHAR(80) NOT NULL,
  `participant_role` VARCHAR(24) NOT NULL DEFAULT 'OBSERVER',
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `state_version` BIGINT NOT NULL DEFAULT 1,
  `added_by` VARCHAR(80) NOT NULL DEFAULT '',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_participant` (`session_id`, `participant_user_id`),
  KEY `idx_participant_user` (`participant_user_id`, `status`),
  KEY `idx_participant_project` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat Session owner and participants';

INSERT INTO `ai_ops_chat_session_participant`
  (`session_id`,`project_id`,`participant_user_id`,`participant_role`,`status`,`state_version`,`added_by`)
SELECT `session_id`,COALESCE(`project_id`,''),`user_id`,'OWNER','ACTIVE',1,`user_id`
FROM `ai_ops_chat_session`
WHERE COALESCE(`user_id`,'')<>''
ON DUPLICATE KEY UPDATE
  `project_id`=VALUES(`project_id`),`participant_role`='OWNER',`status`='ACTIVE';

CREATE TABLE IF NOT EXISTS `ai_ops_agent_run_participant` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(96) NOT NULL,
  `project_id` VARCHAR(80) NOT NULL,
  `session_id` VARCHAR(80) NOT NULL,
  `participant_user_id` VARCHAR(80) NOT NULL,
  `participant_role` VARCHAR(24) NOT NULL DEFAULT 'OBSERVER',
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `source` VARCHAR(32) NOT NULL DEFAULT 'SESSION_SNAPSHOT',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_participant` (`run_id`, `participant_user_id`),
  KEY `idx_run_participant_user` (`participant_user_id`, `status`),
  KEY `idx_run_participant_project` (`project_id`, `run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable participant ACL snapshot for a Work Session Run';

INSERT INTO `ai_ops_agent_run_participant`
  (`run_id`,`project_id`,`session_id`,`participant_user_id`,`participant_role`,`status`,`source`)
SELECT `run_id`,`project_id`,`session_id`,`user_id`,'OWNER','ACTIVE','RUN_OWNER'
FROM `ai_ops_agent_run`
WHERE COALESCE(`user_id`,'')<>''
ON DUPLICATE KEY UPDATE `participant_role`='OWNER',`status`='ACTIVE';
