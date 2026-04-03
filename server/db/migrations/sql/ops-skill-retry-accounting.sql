-- Preserve old jobs and failure audits. New attempts distinguish durable
-- transport/configuration deferrals from ordinary failure exhaustion.
SET @retry_counter_missing = NOT EXISTS(SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='ai_ops_skill_evolution_job_state'
    AND column_name='ordinary_failures');
SET @ddl = IF(@retry_counter_missing,
  'ALTER TABLE ai_ops_skill_evolution_job_state ADD COLUMN ordinary_failures INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- Classification of every legacy attempt is not authoritative. Retain its
-- conservative budget; explicit normal retry starts a new budget, not a new source.
SET @backfill = IF(@retry_counter_missing,
  'UPDATE ai_ops_skill_evolution_job_state s JOIN ai_ops_skill_evolution_job j ON j.job_id=s.job_id SET s.ordinary_failures=j.attempts', 'SELECT 1');
PREPARE stmt FROM @backfill; EXECUTE stmt; DEALLOCATE PREPARE stmt;
