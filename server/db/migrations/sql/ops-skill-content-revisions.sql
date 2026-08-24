-- A candidate is the current projection of an unpublished logical proposal.
-- Every previous candidate/authored payload and actual content review is retained before correction.
-- Published Skill versions and frozen accepted input are never rewritten.
CREATE TABLE IF NOT EXISTS ai_ops_skill_content_revision (
  candidate_id VARCHAR(80) NOT NULL,
  revision_round INT NOT NULL,
  plan_id VARCHAR(80) NOT NULL,
  plan_hash CHAR(64) NOT NULL,
  previous_candidate_hash CHAR(64) NOT NULL,
  previous_candidate_json MEDIUMTEXT NOT NULL,
  previous_authored_json MEDIUMTEXT NOT NULL,
  previous_authored_hash CHAR(64) NOT NULL,
  review_json MEDIUMTEXT NOT NULL,
  status VARCHAR(24) NOT NULL,
  lease_token VARCHAR(36) NOT NULL DEFAULT '',
  lease_until TIMESTAMP(3) NULL,
  authored_json MEDIUMTEXT NULL,
  authored_hash CHAR(64) NOT NULL DEFAULT '',
  revised_candidate_hash CHAR(64) NOT NULL DEFAULT '',
  reason_code VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(candidate_id,revision_round),
  KEY idx_content_revision_plan(plan_id,revision_round),
  CONSTRAINT chk_content_revision_round CHECK (revision_round BETWEEN 1 AND 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
