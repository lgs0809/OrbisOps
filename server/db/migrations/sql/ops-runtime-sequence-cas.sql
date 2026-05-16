-- Cross-instance sequence allocation for Work Session events and checkpoints.

DROP PROCEDURE IF EXISTS ops_runtime_sequence_add_column;
DELIMITER $$
CREATE PROCEDURE ops_runtime_sequence_add_column(IN p_table_name VARCHAR(64), IN p_column_name VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table_name AND COLUMN_NAME=p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ops_runtime_sequence_add_column(
  'ai_ops_agent_run',
  'next_checkpoint_seq',
  'BIGINT NOT NULL DEFAULT 0 AFTER `run_manifest_hash`'
);
CALL ops_runtime_sequence_add_column(
  'ai_ops_agent_run',
  'cancel_requested_at',
  'DATETIME(3) NULL AFTER `cancel_requested`'
);

UPDATE ai_ops_agent_run AS run_row
LEFT JOIN (
  SELECT run_id, MAX(checkpoint_seq) AS max_checkpoint_seq
  FROM ai_ops_agent_run_checkpoint
  GROUP BY run_id
) AS checkpoint_row ON checkpoint_row.run_id = run_row.run_id
SET run_row.next_checkpoint_seq = GREATEST(
  run_row.next_checkpoint_seq,
  COALESCE(checkpoint_row.max_checkpoint_seq, 0)
);

CREATE TABLE IF NOT EXISTS ai_ops_graph_event_sequence (
  sequence_key VARCHAR(160) NOT NULL COMMENT 'runId或analysisId',
  next_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '最后分配的事件序号',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨实例Graph事件序号分配表';

INSERT INTO ai_ops_graph_event_sequence (sequence_key, next_sequence)
SELECT sequence_key, MAX(sequence_no)
FROM (
  SELECT COALESCE(NULLIF(run_id, ''), analysis_id) AS sequence_key, sequence_no
  FROM ai_ops_agent_node_trace
  WHERE COALESCE(NULLIF(run_id, ''), analysis_id) IS NOT NULL
) AS existing_events
GROUP BY sequence_key
ON DUPLICATE KEY UPDATE
  next_sequence=GREATEST(ai_ops_graph_event_sequence.next_sequence, VALUES(next_sequence));

DROP PROCEDURE IF EXISTS ops_runtime_sequence_add_column;
