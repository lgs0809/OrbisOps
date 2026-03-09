CREATE TABLE IF NOT EXISTS ai_client_api_health_check (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    check_id VARCHAR(64) NOT NULL COMMENT '健康检测记录ID',
    api_id VARCHAR(128) NOT NULL COMMENT 'Provider API ID',
    test_type VARCHAR(64) NOT NULL DEFAULT 'MODELS_ENDPOINT' COMMENT '检测类型',
    endpoint VARCHAR(512) NOT NULL COMMENT '检测端点，敏感查询参数已脱敏',
    status VARCHAR(32) NOT NULL COMMENT 'SUCCESS / FAILED',
    http_status INT NULL COMMENT 'HTTP 状态码',
    latency_ms BIGINT NULL COMMENT '检测耗时，毫秒',
    error_message VARCHAR(1000) NULL COMMENT '失败时的真实错误信息',
    tested_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '检测操作者',
    test_time DATETIME NULL COMMENT '检测时间',
    create_time DATETIME NOT NULL COMMENT '检测时间',
    KEY idx_api_time (api_id, test_time),
    KEY idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型 Provider 健康检测记录';
