-- OPS-06: page checkpoints on the existing immutable proposal; no second worker or success backfill.
CREATE TABLE IF NOT EXISTS ai_ops_skill_evolution_batch_review (
  plan_id VARCHAR(80) NOT NULL,
  batch_index INT NOT NULL,
  input_hash CHAR(64) NOT NULL,
  review_json MEDIUMTEXT NOT NULL,
  review_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(plan_id,batch_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
