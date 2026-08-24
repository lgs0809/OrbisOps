-- Additive automatic alert grouping. Does not rewrite incidents, approvals or source alerts.
CREATE TABLE IF NOT EXISTS ai_ops_alert_correlation_scope (
  scope_key CHAR(64) PRIMARY KEY,
  project_id VARCHAR(80) NOT NULL,
  environment VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_alert_correlation_topology (
  scope_key CHAR(64) PRIMARY KEY,
  edges_json MEDIUMTEXT NOT NULL,
  updated_by VARCHAR(120) NOT NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_alert_correlation_group (
  group_id VARCHAR(80) PRIMARY KEY,
  project_id VARCHAR(80) NOT NULL,
  environment VARCHAR(80) NOT NULL,
  anchor_json MEDIUMTEXT NOT NULL,
  anchor_epoch BIGINT NULL,
  merged_into VARCHAR(80) NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_correlation_scope_window (project_id, environment, anchor_epoch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_alert_correlation_member (
  group_id VARCHAR(80) NOT NULL,
  incident_id VARCHAR(80) NOT NULL,
  signal_json MEDIUMTEXT NOT NULL,
  decision_json MEDIUMTEXT NOT NULL,
  last_event_id BIGINT NOT NULL,
  occurrence_count BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (group_id, incident_id),
  KEY idx_correlation_member_incident (incident_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_alert_correlation_decision (
  event_id BIGINT PRIMARY KEY,
  group_id VARCHAR(80) NOT NULL,
  signal_json MEDIUMTEXT NOT NULL,
  decision_json MEDIUMTEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_correlation_decision_group (group_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ai_ops_alert_correlation_revision (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  group_id VARCHAR(80) NOT NULL,
  action_type VARCHAR(40) NOT NULL,
  detail_json MEDIUMTEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_correlation_revision_group (group_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
