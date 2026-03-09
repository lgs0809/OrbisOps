-- Persist only a redacted notification body and minimal trace metadata.
-- Full Agent request/response snapshots are not required for retry and may
-- contain unrelated operational context, so existing copies are removed.

DROP PROCEDURE IF EXISTS ops_channel_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE ops_channel_add_column_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ops_channel_column_sql = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_column_definition
        );
        PREPARE ops_channel_column_stmt FROM @ops_channel_column_sql;
        EXECUTE ops_channel_column_stmt;
        DEALLOCATE PREPARE ops_channel_column_stmt;
    END IF;
END$$
DELIMITER ;

CALL ops_channel_add_column_if_missing(
    'ai_ops_channel_notification_outbox',
    'message_text',
    "MEDIUMTEXT NULL COMMENT '已脱敏通知正文'"
);
CALL ops_channel_add_column_if_missing(
    'ai_ops_channel_notification_outbox',
    'metadata_json',
    "TEXT NULL COMMENT '最小追踪元数据'"
);
CALL ops_channel_add_column_if_missing(
    'ai_ops_channel_notification_outbox',
    'content_hash',
    "CHAR(64) NOT NULL DEFAULT '' COMMENT '脱敏正文SHA-256'"
);

DROP PROCEDURE IF EXISTS ops_channel_add_column_if_missing;

UPDATE ai_ops_channel_notification_outbox
SET status = CASE WHEN status = 'SUCCEEDED' THEN status ELSE 'CANCELLED' END,
    last_error = CASE
        WHEN status = 'SUCCEEDED' THEN last_error
        ELSE 'LEGACY_FULL_PAYLOAD_REMOVED_REQUEUE_REQUIRED'
    END,
    request_json = NULL,
    response_json = NULL,
    update_time = CURRENT_TIMESTAMP
WHERE request_json IS NOT NULL OR response_json IS NOT NULL;
